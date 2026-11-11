package uzeduai.be.analysis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.analysis.client.AiAnalysisClient;
import uzeduai.be.analysis.config.AiServiceProperties;
import uzeduai.be.analysis.domain.AnalysisKind;
import uzeduai.be.analysis.domain.InsightJobStatus;
import uzeduai.be.analysis.dto.GenerateScenarioRequest;
import uzeduai.be.analysis.dto.InsightReportResponse;
import uzeduai.be.analysis.dto.LeaderboardItemResponse;
import uzeduai.be.analysis.dto.ScenarioResponse;
import uzeduai.be.analysis.entity.ContentAnalysis;
import uzeduai.be.analysis.entity.ContentAnalysisResult;
import uzeduai.be.analysis.entity.ContentInsightReport;
import uzeduai.be.analysis.entity.ContentScenario;
import uzeduai.be.analysis.repository.ContentAnalysisRepository;
import uzeduai.be.analysis.repository.ContentAnalysisResultRepository;
import uzeduai.be.analysis.repository.ContentInsightReportRepository;
import uzeduai.be.analysis.repository.ContentScenarioRepository;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.entity.InstagramMedia;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.instagram.repository.InstagramMediaRepository;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ContentInsightsService {

    private static final Logger log = LoggerFactory.getLogger(ContentInsightsService.class);

    private final AiServiceProperties properties;
    private final AiAnalysisClient aiClient;
    private final InstagramMediaRepository mediaRepository;
    private final InstagramAccountRepository accountRepository;
    private final ContentAnalysisRepository analysisRepository;
    private final ContentAnalysisResultRepository resultRepository;
    private final ContentInsightReportRepository insightReportRepository;
    private final ContentScenarioRepository scenarioRepository;
    private final TenantMembershipService tenantMembershipService;

    public ContentInsightsService(
            AiServiceProperties properties,
            AiAnalysisClient aiClient,
            InstagramMediaRepository mediaRepository,
            InstagramAccountRepository accountRepository,
            ContentAnalysisRepository analysisRepository,
            ContentAnalysisResultRepository resultRepository,
            ContentInsightReportRepository insightReportRepository,
            ContentScenarioRepository scenarioRepository,
            TenantMembershipService tenantMembershipService
    ) {
        this.properties = properties;
        this.aiClient = aiClient;
        this.mediaRepository = mediaRepository;
        this.accountRepository = accountRepository;
        this.analysisRepository = analysisRepository;
        this.resultRepository = resultRepository;
        this.insightReportRepository = insightReportRepository;
        this.scenarioRepository = scenarioRepository;
        this.tenantMembershipService = tenantMembershipService;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardItemResponse> leaderboard(UUID accountId, int limit, CustomUserDetails principal) {
        InstagramAccount account = requireAccount(accountId, principal);
        List<ScoredMedia> scored = scoreAccountMedia(account);
        int size = Math.min(Math.max(limit, 1), 50);
        List<LeaderboardItemResponse> out = new ArrayList<>();
        for (int i = 0; i < Math.min(size, scored.size()); i++) {
            out.add(toLeaderboardItem(scored.get(i), i + 1, bucketForIndex(i, scored.size())));
        }
        return out;
    }

    @Transactional
    public InsightReportResponse generateInsights(UUID accountId, CustomUserDetails principal) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MessageKeys.ANALYSIS_DISABLED);
        }
        InstagramAccount account = requireAccount(accountId, principal);
        List<ScoredMedia> scored = scoreAccountMedia(account);
        if (scored.size() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSIGHTS_INSUFFICIENT_DATA);
        }

        int topN = Math.max(1, properties.getInsightsTopN());
        int lowN = Math.max(1, properties.getInsightsLowN());
        List<ScoredMedia> corpus = buildCorpus(scored, topN, lowN);
        Map<UUID, Map<String, Object>> analysisByMedia = loadAnalyses(
                corpus.stream().map(s -> s.media().getId()).toList()
        );

        Map<String, Object> stats = buildStats(scored);
        List<Map<String, Object>> postsPayload = new ArrayList<>();
        for (int i = 0; i < corpus.size(); i++) {
            ScoredMedia sm = corpus.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mediaId", sm.media().getId().toString());
            row.put("mediaType", sm.media().getMediaType() != null ? sm.media().getMediaType().name() : null);
            row.put("caption", sm.media().getCaption());
            row.put("likeCount", sm.media().getLikeCount());
            row.put("commentsCount", sm.media().getCommentsCount());
            row.put("sharesCount", sm.media().getSharesCount());
            row.put("savesCount", sm.media().getSavesCount());
            row.put("engagementScore", sm.score());
            row.put("bucket", bucketForIndex(indexInScored(scored, sm), scored.size()));
            row.put("analysis", analysisByMedia.get(sm.media().getId()));
            postsPayload.add(row);
        }

        ContentInsightReport report = new ContentInsightReport();
        report.setTenantId(principal.getTenantId());
        report.setWorkspaceId(principal.getWorkspaceId());
        report.setInstagramAccountId(account.getId());
        report.setStatus(InsightJobStatus.RUNNING);
        report.setStartedAt(Instant.now());
        report.setPromptVersion(properties.getInsightsPromptVersion());
        report.setModelName(properties.getTextModelName());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("stats", stats);
        snapshot.put("posts", postsPayload);
        report.setInputSnapshotJson(snapshot);
        report = insightReportRepository.save(report);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("accountId", account.getId().toString());
            payload.put("stats", stats);
            payload.put("posts", postsPayload);
            AiAnalysisClient.AnalyzeResponse ai = aiClient.insights(payload);
            if (!isValidInsightResult(ai.result())) {
                throw new IllegalStateException("AI insights missing corpus pattern fields");
            }
            report.setStatus(InsightJobStatus.READY);
            report.setResultJson(ai.result());
            report.setSchemaVersion(ai.schemaVersion());
            report.setModelName(ai.model());
            report.setFinishedAt(Instant.now());
            report.setErrorMessage(null);
            report = insightReportRepository.save(report);
            return toInsightResponse(report);
        } catch (Exception ex) {
            log.warn("Insights generation failed: {}", ex.getMessage());
            report.setStatus(InsightJobStatus.FAILED);
            report.setErrorMessage(trim(ex.getMessage(), 1000));
            report.setFinishedAt(Instant.now());
            insightReportRepository.save(report);
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.INSIGHTS_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public InsightReportResponse latestInsights(UUID accountId, CustomUserDetails principal) {
        InstagramAccount account = requireAccount(accountId, principal);
        ContentInsightReport report = insightReportRepository
                .findFirstByTenantIdAndWorkspaceIdAndInstagramAccountIdOrderByCreatedAtDesc(
                        principal.getTenantId(), principal.getWorkspaceId(), account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSIGHTS_NOT_FOUND));
        return toInsightResponse(report);
    }

    @Transactional
    public ScenarioResponse generateScenario(
            UUID reportId,
            GenerateScenarioRequest request,
            CustomUserDetails principal
    ) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MessageKeys.ANALYSIS_DISABLED);
        }
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        ContentInsightReport report = insightReportRepository.findByIdAndTenantId(reportId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSIGHTS_NOT_FOUND));
        if (!report.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        if (report.getStatus() != InsightJobStatus.READY || report.getResultJson() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSIGHTS_NOT_FOUND);
        }

        ContentScenario scenario = new ContentScenario();
        scenario.setTenantId(principal.getTenantId());
        scenario.setWorkspaceId(principal.getWorkspaceId());
        scenario.setInsightReportId(report.getId());
        scenario.setStatus(InsightJobStatus.RUNNING);
        scenario.setStartedAt(Instant.now());
        scenario.setBrief(blankToNull(request != null ? request.brief() : null));
        scenario.setPromptVersion(properties.getScenarioPromptVersion());
        scenario.setModelName(properties.getTextModelName());
        scenario = scenarioRepository.save(scenario);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("brief", scenario.getBrief());
            payload.put("insights", report.getResultJson());
            if (request != null && request.preferredFormat() != null && !request.preferredFormat().isBlank()) {
                payload.put("preferredFormat", request.preferredFormat().trim());
            }
            AiAnalysisClient.AnalyzeResponse ai = aiClient.scenario(payload);
            if (!isValidScenarioResult(ai.result())) {
                throw new IllegalStateException("AI scenario missing required fields");
            }
            scenario.setStatus(InsightJobStatus.READY);
            scenario.setResultJson(ai.result());
            scenario.setSchemaVersion(ai.schemaVersion());
            scenario.setModelName(ai.model());
            scenario.setFinishedAt(Instant.now());
            scenario.setErrorMessage(null);
            scenario = scenarioRepository.save(scenario);
            return toScenarioResponse(scenario);
        } catch (Exception ex) {
            log.warn("Scenario generation failed: {}", ex.getMessage());
            scenario.setStatus(InsightJobStatus.FAILED);
            scenario.setErrorMessage(trim(ex.getMessage(), 1000));
            scenario.setFinishedAt(Instant.now());
            scenarioRepository.save(scenario);
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.SCENARIO_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public ScenarioResponse latestScenario(UUID reportId, CustomUserDetails principal) {
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        ContentInsightReport report = insightReportRepository.findByIdAndTenantId(reportId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSIGHTS_NOT_FOUND));
        if (!report.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        ContentScenario scenario = scenarioRepository.findFirstByInsightReportIdOrderByCreatedAtDesc(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.SCENARIO_NOT_FOUND));
        return toScenarioResponse(scenario);
    }

    private List<ScoredMedia> scoreAccountMedia(InstagramAccount account) {
        List<InstagramMedia> media = mediaRepository.findByTenantIdAndInstagramAccountId(
                account.getTenantId(), account.getId());
        return media.stream()
                .map(m -> new ScoredMedia(m, engagementScore(m)))
                .sorted(Comparator.comparingDouble(ScoredMedia::score).reversed()
                        .thenComparing(s -> s.media().getPublishedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<ScoredMedia> buildCorpus(List<ScoredMedia> scored, int topN, int lowN) {
        Set<UUID> used = new HashSet<>();
        List<ScoredMedia> corpus = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            ScoredMedia sm = scored.get(i);
            if (used.add(sm.media().getId())) {
                corpus.add(sm);
            }
        }
        for (int i = scored.size() - 1; i >= 0 && corpus.size() < topN + lowN; i--) {
            ScoredMedia sm = scored.get(i);
            if (used.add(sm.media().getId())) {
                corpus.add(sm);
            }
        }
        // Enrich mid-band with ANALYZED posts so patterns use vision+user corrections, not one top post.
        int maxExtra = Math.max(2, topN / 2);
        int added = 0;
        for (ScoredMedia sm : scored) {
            if (added >= maxExtra) {
                break;
            }
            if (used.contains(sm.media().getId()) || !isAnalyzed(sm)) {
                continue;
            }
            used.add(sm.media().getId());
            corpus.add(sm);
            added++;
        }
        return corpus;
    }

    private static boolean isAnalyzed(ScoredMedia sm) {
        return sm.media().getAnalysisStatus() != null
                && "ANALYZED".equals(sm.media().getAnalysisStatus().name());
    }

    private Map<UUID, Map<String, Object>> loadAnalyses(List<UUID> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        List<ContentAnalysis> jobs = analysisRepository.findByInstagramMediaIdInAndAnalysisKind(
                mediaIds, AnalysisKind.CONTENT);
        Map<UUID, UUID> analysisIdByMedia = new HashMap<>();
        for (ContentAnalysis job : jobs) {
            analysisIdByMedia.put(job.getInstagramMediaId(), job.getId());
        }
        List<ContentAnalysisResult> results = resultRepository.findByContentAnalysisIdIn(
                analysisIdByMedia.values().stream().toList());
        Map<UUID, Map<String, Object>> jsonByAnalysis = new HashMap<>();
        for (ContentAnalysisResult r : results) {
            jsonByAnalysis.put(r.getContentAnalysisId(), r.getResultJson());
        }
        Map<UUID, Map<String, Object>> out = new HashMap<>();
        for (Map.Entry<UUID, UUID> e : analysisIdByMedia.entrySet()) {
            Map<String, Object> json = jsonByAnalysis.get(e.getValue());
            if (json != null) {
                out.put(e.getKey(), json);
            }
        }
        return out;
    }

    private Map<String, Object> buildStats(List<ScoredMedia> scored) {
        double sumLikes = 0;
        double sumComments = 0;
        double sumShares = 0;
        double sumSaves = 0;
        double sumScore = 0;
        int analyzed = 0;
        for (ScoredMedia sm : scored) {
            sumLikes += nz(sm.media().getLikeCount());
            sumComments += nz(sm.media().getCommentsCount());
            sumShares += nz(sm.media().getSharesCount());
            sumSaves += nz(sm.media().getSavesCount());
            sumScore += sm.score();
            if (sm.media().getAnalysisStatus() != null
                    && "ANALYZED".equals(sm.media().getAnalysisStatus().name())) {
                analyzed++;
            }
        }
        int n = scored.size();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("postCount", n);
        stats.put("analyzedCount", analyzed);
        stats.put("avgLikes", n == 0 ? 0 : sumLikes / n);
        stats.put("avgComments", n == 0 ? 0 : sumComments / n);
        stats.put("avgShares", n == 0 ? 0 : sumShares / n);
        stats.put("avgSaves", n == 0 ? 0 : sumSaves / n);
        stats.put("avgEngagementScore", n == 0 ? 0 : sumScore / n);
        if (!scored.isEmpty()) {
            stats.put("topScore", scored.getFirst().score());
            stats.put("lowScore", scored.getLast().score());
        }
        return stats;
    }

    private LeaderboardItemResponse toLeaderboardItem(ScoredMedia sm, int rank, String bucket) {
        InstagramMedia m = sm.media();
        String caption = m.getCaption();
        String preview = caption == null ? null : (caption.length() > 120 ? caption.substring(0, 120) : caption);
        String summary = null;
        var job = analysisRepository.findByInstagramMediaIdAndAnalysisKind(m.getId(), AnalysisKind.CONTENT);
        if (job.isPresent()) {
            var result = resultRepository.findByContentAnalysisId(job.get().getId()).orElse(null);
            if (result != null && result.getResultJson() != null) {
                summary = extractSummary(result.getResultJson());
            }
        }
        return new LeaderboardItemResponse(
                m.getId(),
                m.getIgMediaId(),
                m.getMediaType(),
                preview,
                firstNonBlank(m.getThumbnailUrl(), m.getMediaUrl()),
                m.getLikeCount(),
                m.getCommentsCount(),
                m.getSharesCount(),
                m.getSavesCount(),
                sm.score(),
                rank,
                bucket,
                m.getAnalysisStatus(),
                summary
        );
    }

    private InsightReportResponse toInsightResponse(ContentInsightReport report) {
        return new InsightReportResponse(
                report.getId(),
                report.getInstagramAccountId(),
                report.getStatus(),
                report.getModelName(),
                report.getPromptVersion(),
                report.getSchemaVersion(),
                report.getInputSnapshotJson(),
                report.getResultJson(),
                report.getErrorMessage(),
                report.getStartedAt(),
                report.getFinishedAt(),
                report.getCreatedAt()
        );
    }

    private ScenarioResponse toScenarioResponse(ContentScenario scenario) {
        return new ScenarioResponse(
                scenario.getId(),
                scenario.getInsightReportId(),
                scenario.getStatus(),
                scenario.getBrief(),
                scenario.getModelName(),
                scenario.getPromptVersion(),
                scenario.getSchemaVersion(),
                scenario.getResultJson(),
                scenario.getErrorMessage(),
                scenario.getStartedAt(),
                scenario.getFinishedAt(),
                scenario.getCreatedAt()
        );
    }

    private InstagramAccount requireAccount(UUID accountId, CustomUserDetails principal) {
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        InstagramAccount account = accountRepository.findByIdAndTenantId(accountId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSTAGRAM_ACCOUNT_NOT_FOUND));
        if (!account.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        return account;
    }

    private void requireWorkspace(CustomUserDetails principal) {
        if (principal.getTenantId() == null || principal.getWorkspaceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED);
        }
    }

    public static double engagementScore(InstagramMedia m) {
        return nz(m.getLikeCount())
                + 3.0 * nz(m.getCommentsCount())
                + 2.0 * nz(m.getSharesCount())
                + 2.0 * nz(m.getSavesCount());
    }

    static boolean isValidInsightResult(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        if (result.containsKey("mediaId") && !result.containsKey("executiveSummaryUz")) {
            return false;
        }
        Object summary = result.get("executiveSummaryUz");
        if (!(summary instanceof String s) || s.trim().length() < 20) {
            return false;
        }
        Object wins = result.get("winningPatterns");
        return wins instanceof List<?> list && !list.isEmpty();
    }

    static boolean isValidScenarioResult(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        Object title = result.get("titleUz");
        Object caption = result.get("captionPrimaryUz");
        if (!(title instanceof String t) || t.isBlank()) {
            return false;
        }
        return caption instanceof String c && c.trim().length() >= 10;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : Math.max(0, v);
    }

    private static String bucketForIndex(int index, int size) {
        if (size <= 0) {
            return "mid";
        }
        if (index < Math.max(1, size / 3)) {
            return "top";
        }
        if (index >= size - Math.max(1, size / 3)) {
            return "low";
        }
        return "mid";
    }

    private static int indexInScored(List<ScoredMedia> scored, ScoredMedia target) {
        for (int i = 0; i < scored.size(); i++) {
            if (Objects.equals(scored.get(i).media().getId(), target.media().getId())) {
                return i;
            }
        }
        return 0;
    }

    private static String extractSummary(Map<String, Object> result) {
        Object summary = result.get("correctedSummary");
        if (summary == null || summary.toString().isBlank()) {
            summary = result.get("summaryUz");
        }
        if (summary == null || summary.toString().isBlank()) {
            summary = result.get("mainTopic");
        }
        return summary == null ? null : summary.toString();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ScoredMedia(InstagramMedia media, double score) {
    }
}
