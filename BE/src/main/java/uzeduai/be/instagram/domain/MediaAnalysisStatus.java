package uzeduai.be.instagram.domain;

public enum MediaAnalysisStatus {
    NOT_ANALYZED,
    QUEUED,
    RUNNING,
    ANALYZED,
    FAILED,
    /** User opted out — do not auto-queue vision analysis. */
    SKIPPED
}
