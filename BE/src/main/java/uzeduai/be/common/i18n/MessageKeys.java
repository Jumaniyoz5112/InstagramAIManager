package uzeduai.be.common.i18n;

public final class MessageKeys {

    private MessageKeys() {
    }

    public static final String AUTH_EMAIL_TAKEN = "error.auth.email_taken";
    public static final String AUTH_TENANT_SLUG_TAKEN = "error.auth.tenant_slug_taken";
    public static final String AUTH_INVALID_CREDENTIALS = "error.auth.invalid_credentials";
    public static final String AUTH_REFRESH_MISSING = "error.auth.refresh_missing";
    public static final String AUTH_REFRESH_INVALID = "error.auth.refresh_invalid";
    public static final String AUTH_REFRESH_EXPIRED = "error.auth.refresh_expired";
    public static final String AUTH_REFRESH_REUSE = "error.auth.refresh_reuse";
    public static final String AUTH_USER_DISABLED = "error.auth.user_disabled";
    public static final String AUTH_USER_NOT_FOUND = "error.auth.user_not_found";
    public static final String AUTH_WORKSPACE_NOT_FOUND = "error.auth.workspace_not_found";
    public static final String AUTH_WORKSPACE_FORBIDDEN = "error.auth.workspace_forbidden";

    public static final String ACCESS_PAGE_FORBIDDEN = "error.access.page_forbidden";
    public static final String ACCESS_ADMIN_IMMUTABLE = "error.access.admin_immutable";
    public static final String ACCESS_INVALID_PAGE = "error.access.invalid_page";
    public static final String ACCESS_USER_NOT_FOUND = "error.access.user_not_found";

    public static final String TENANT_SLUG_TAKEN = "error.tenant.slug_taken";
    public static final String TENANT_USER_NOT_FOUND = "error.tenant.user_not_found";

    public static final String WORKSPACE_CREATE_FORBIDDEN = "error.workspace.create_forbidden";
    public static final String WORKSPACE_TENANT_NOT_FOUND = "error.workspace.tenant_not_found";
    public static final String WORKSPACE_NOT_MEMBER = "error.workspace.not_member";

    public static final String SECURITY_UNAUTHORIZED = "error.security.unauthorized";
    public static final String SECURITY_FORBIDDEN = "error.security.forbidden";
    public static final String VALIDATION_FAILED = "error.validation.failed";
    public static final String SERVER_INTERNAL = "error.server.internal";

    public static final String AUTH_LOGOUT_OK = "success.auth.logout";
}
