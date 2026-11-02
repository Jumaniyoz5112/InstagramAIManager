package uzeduai.be.access.dto;

public record PageAccessResponse(
        String code,
        String name,
        String description,
        String path,
        int sortOrder,
        boolean adminOnly
) {
}
