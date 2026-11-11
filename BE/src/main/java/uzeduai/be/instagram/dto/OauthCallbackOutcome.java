package uzeduai.be.instagram.dto;

import java.util.UUID;

/**
 * OAuth callback result: either a connected account id, or a multi-account pick session id.
 */
public record OauthCallbackOutcome(UUID accountId, String pickId) {

    public static OauthCallbackOutcome connected(UUID accountId) {
        return new OauthCallbackOutcome(accountId, null);
    }

    public static OauthCallbackOutcome needsPick(String pickId) {
        return new OauthCallbackOutcome(null, pickId);
    }

    public boolean needsPick() {
        return pickId != null && !pickId.isBlank();
    }
}
