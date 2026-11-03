package uzeduai.be.instagram.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.instagram.client.InstagramGraphClient;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.entity.InstagramMedia;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.instagram.repository.InstagramMediaRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class InstagramMediaUrlService {

    private final InstagramProperties properties;
    private final InstagramGraphClient graphClient;
    private final InstagramMediaRepository mediaRepository;
    private final InstagramAccountRepository accountRepository;

    public InstagramMediaUrlService(
            InstagramProperties properties,
            InstagramGraphClient graphClient,
            InstagramMediaRepository mediaRepository,
            InstagramAccountRepository accountRepository
    ) {
        this.properties = properties;
        this.graphClient = graphClient;
        this.mediaRepository = mediaRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public InstagramMedia refreshUrlIfExpired(InstagramMedia media) {
        if (!isExpired(media.getMediaUrlFetchedAt())) {
            return media;
        }
        InstagramAccount account = accountRepository.findById(media.getInstagramAccountId()).orElse(null);
        if (account == null || account.getAccessToken() == null) {
            return media;
        }
        return graphClient.fetchMediaFields(media.getIgMediaId(), account.getAccessToken(), "media_url,thumbnail_url")
                .map(fields -> {
                    Object mediaUrl = fields.get("media_url");
                    Object thumb = fields.get("thumbnail_url");
                    if (mediaUrl != null) {
                        media.setMediaUrl(mediaUrl.toString());
                    }
                    if (thumb != null) {
                        media.setThumbnailUrl(thumb.toString());
                    }
                    media.setMediaUrlFetchedAt(Instant.now());
                    return mediaRepository.save(media);
                })
                .orElse(media);
    }

    private boolean isExpired(Instant fetchedAt) {
        if (fetchedAt == null) {
            return true;
        }
        return fetchedAt.isBefore(Instant.now().minus(properties.getMediaUrlTtlHours(), ChronoUnit.HOURS));
    }
}
