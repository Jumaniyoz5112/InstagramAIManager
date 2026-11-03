package uzeduai.be.instagram.dto;

import uzeduai.be.instagram.domain.InstagramMediaType;

import java.util.UUID;

public record InstagramMediaChildResponse(
        UUID id,
        String igMediaId,
        InstagramMediaType mediaType,
        String mediaUrl,
        String thumbnailUrl,
        int position
) {
}
