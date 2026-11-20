package uzeduai.be.analysis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.analysis.domain.InsightJobStatus;
import uzeduai.be.analysis.dto.ScenarioVideoResponse;
import uzeduai.be.analysis.entity.ContentScenario;
import uzeduai.be.analysis.entity.ContentScenarioVideo;
import uzeduai.be.analysis.repository.ContentScenarioRepository;
import uzeduai.be.analysis.repository.ContentScenarioVideoRepository;
import uzeduai.be.analysis.video.ScenarioVideoRenderer;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.storage.StoragePort;
import uzeduai.be.storage.StoredObject;
import uzeduai.be.tenant.context.TenantMembershipService;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.UUID;

@Service
public class ScenarioVideoService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioVideoService.class);

    private final ContentScenarioRepository scenarioRepository;
    private final ContentScenarioVideoRepository videoRepository;
    private final ScenarioVideoRenderer renderer;
    private final StoragePort storagePort;
    private final TenantMembershipService tenantMembershipService;
    private final ScenarioVideoService self;

    public ScenarioVideoService(
            ContentScenarioRepository scenarioRepository,
            ContentScenarioVideoRepository videoRepository,
            ScenarioVideoRenderer renderer,
            StoragePort storagePort,
            TenantMembershipService tenantMembershipService,
            @Lazy ScenarioVideoService self
    ) {
        this.scenarioRepository = scenarioRepository;
        this.videoRepository = videoRepository;
        this.renderer = renderer;
        this.storagePort = storagePort;
        this.tenantMembershipService = tenantMembershipService;
        this.self = self;
    }

    public ScenarioVideoResponse render(UUID scenarioId, CustomUserDetails principal) {
        ContentScenario scenario = requireReadyScenario(scenarioId, principal);
        ContentScenarioVideo video = new ContentScenarioVideo();
        video.setTenantId(principal.getTenantId());
        video.setWorkspaceId(principal.getWorkspaceId());
        video.setScenarioId(scenario.getId());
        video.setStatus(InsightJobStatus.RUNNING);
        video.setContentType("video/mp4");
        video = self.persist(video);

        ScenarioVideoRenderer.RenderedClip clip = null;
        try {
            clip = renderer.render(scenario.getResultJson());
            StoredObject stored;
            try (InputStream in = Files.newInputStream(clip.file())) {
                stored = storagePort.store(
                        principal.getTenantId(),
                        principal.getWorkspaceId(),
                        "scenario-videos",
                        "scenario.mp4",
                        in,
                        Files.size(clip.file())
                );
            }
            video.setStatus(InsightJobStatus.READY);
            video.setStorageKey(stored.storageKey());
            video.setSizeBytes(stored.sizeBytes());
            video.setDurationSec(BigDecimal.valueOf(clip.durationSec()).setScale(2, RoundingMode.HALF_UP));
            video.setErrorMessage(null);
            return toResponse(self.persist(video));
        } catch (Exception ex) {
            log.warn("Scenario video render failed: {}", ex.getMessage());
            video.setStatus(InsightJobStatus.FAILED);
            video.setErrorMessage(trim(ex.getMessage(), 1000));
            self.persist(video);
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.SCENARIO_VIDEO_FAILED);
        } finally {
            if (clip != null) {
                deleteQuietly(clip.workDir());
            }
        }
    }

    @Transactional(readOnly = true)
    public ScenarioVideoResponse latest(UUID scenarioId, CustomUserDetails principal) {
        requireReadyScenario(scenarioId, principal);
        ContentScenarioVideo video = videoRepository
                .findFirstByScenarioIdAndTenantIdAndStatusOrderByCreatedAtDesc(
                        scenarioId, principal.getTenantId(), InsightJobStatus.READY)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.SCENARIO_VIDEO_NOT_FOUND));
        return toResponse(video);
    }

    @Transactional(readOnly = true)
    public InputStreamResource openFile(UUID scenarioId, CustomUserDetails principal) {
        ScenarioVideoResponse latest = latest(scenarioId, principal);
        ContentScenarioVideo video = videoRepository.findById(latest.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.SCENARIO_VIDEO_NOT_FOUND));
        if (video.getStorageKey() == null || !storagePort.exists(video.getStorageKey())) {
            throw new ApiException(HttpStatus.NOT_FOUND, MessageKeys.SCENARIO_VIDEO_NOT_FOUND);
        }
        return new InputStreamResource(storagePort.open(video.getStorageKey()));
    }

    @Transactional(readOnly = true)
    public long fileSize(UUID scenarioId, CustomUserDetails principal) {
        ContentScenarioVideo video = videoRepository
                .findFirstByScenarioIdAndTenantIdAndStatusOrderByCreatedAtDesc(
                        scenarioId, principal.getTenantId(), InsightJobStatus.READY)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.SCENARIO_VIDEO_NOT_FOUND));
        return video.getSizeBytes() == null ? -1 : video.getSizeBytes();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ContentScenarioVideo persist(ContentScenarioVideo video) {
        return videoRepository.saveAndFlush(video);
    }

    private ContentScenario requireReadyScenario(UUID scenarioId, CustomUserDetails principal) {
        if (principal.getWorkspaceId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        ContentScenario scenario = scenarioRepository.findByIdAndTenantId(scenarioId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.SCENARIO_NOT_FOUND));
        if (!scenario.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        if (scenario.getStatus() != InsightJobStatus.READY || scenario.getResultJson() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.SCENARIO_NOT_FOUND);
        }
        return scenario;
    }

    private static ScenarioVideoResponse toResponse(ContentScenarioVideo video) {
        return new ScenarioVideoResponse(
                video.getId(),
                video.getScenarioId(),
                video.getStatus(),
                video.getContentType(),
                video.getSizeBytes(),
                video.getDurationSec(),
                video.getErrorMessage(),
                video.getCreatedAt()
        );
    }

    private static void deleteQuietly(java.nio.file.Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // temp cleanup is best-effort
                }
            });
        } catch (IOException ignored) {
            // temp cleanup is best-effort
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
