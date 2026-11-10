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

    public static final String PLATFORM_TENANT_NOT_FOUND = "error.platform.tenant_not_found";
    public static final String PLATFORM_INVALID_SORT = "error.platform.invalid_sort";
    public static final String PLATFORM_INVALID_PAGE_SIZE = "error.platform.invalid_page_size";

    public static final String INSTAGRAM_DISABLED = "error.instagram.disabled";
    public static final String INSTAGRAM_WORKSPACE_REQUIRED = "error.instagram.workspace_required";
    public static final String INSTAGRAM_OAUTH_STATE_INVALID = "error.instagram.oauth_state_invalid";
    public static final String INSTAGRAM_ACCOUNT_NOT_FOUND = "error.instagram.account_not_found";
    public static final String INSTAGRAM_MEDIA_NOT_FOUND = "error.instagram.media_not_found";
    public static final String INSTAGRAM_SYNC_IN_PROGRESS = "error.instagram.sync_in_progress";
    public static final String INSTAGRAM_NEEDS_REAUTH = "error.instagram.needs_reauth";
    public static final String INSTAGRAM_ACCOUNT_DISCOVERY_FAILED = "error.instagram.account_discovery_failed";
    public static final String INSTAGRAM_PUBLISH_DISABLED = "error.instagram.publish_disabled";
    public static final String INSTAGRAM_PUBLISH_NOT_FOUND = "error.instagram.publish_not_found";
    public static final String INSTAGRAM_PUBLISH_INVALID = "error.instagram.publish_invalid";
    public static final String INSTAGRAM_PUBLISH_PUBLIC_URL_REQUIRED = "error.instagram.publish_public_url_required";
    public static final String INSTAGRAM_PUBLISH_LIMIT = "error.instagram.publish_limit";
    public static final String INSTAGRAM_PUBLISH_FAILED = "error.instagram.publish_failed";
    public static final String INSTAGRAM_PUBLISH_ACCOUNT_INACTIVE = "error.instagram.publish_account_inactive";
    public static final String INSTAGRAM_PUBLISH_ASSET_NOT_FOUND = "error.instagram.publish_asset_not_found";

    public static final String SECURITY_UNAUTHORIZED = "error.security.unauthorized";
    public static final String SECURITY_FORBIDDEN = "error.security.forbidden";
    public static final String VALIDATION_FAILED = "error.validation.failed";
    public static final String SERVER_INTERNAL = "error.server.internal";

    public static final String AUTH_LOGOUT_OK = "success.auth.logout";
}
