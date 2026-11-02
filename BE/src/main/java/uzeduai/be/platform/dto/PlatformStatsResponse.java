package uzeduai.be.platform.dto;

public record PlatformStatsResponse(
        long tenantCount,
        long userCount,
        long workspaceCount,
        long membershipCount
) {
}
