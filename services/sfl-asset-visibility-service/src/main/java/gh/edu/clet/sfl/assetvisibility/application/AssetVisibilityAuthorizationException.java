package gh.edu.clet.sfl.assetvisibility.application;

import java.util.Map;

/**
 * Refusal by the AVAMP access policy.
 *
 * <p>Carries {@code ASSETVIS_UNAUTHORIZED_SCOPE} and structured details — the permission that was
 * required, the resource, and the site when the refusal was a scope one. A caller that is told only
 * "forbidden" has to guess which of the two it was, and the two have different remedies: one is a
 * role request, the other is a site-scope request.
 *
 * <p>Maps to **403**, never 401. A1 established that distinction across the platform and it is not
 * cosmetic: 401 tells a client to authenticate again, which for a correctly authenticated actor
 * lacking a permission means an infinite and pointless sign-in loop.
 */
public class AssetVisibilityAuthorizationException extends RuntimeException {

    private static final String CODE = "ASSETVIS_UNAUTHORIZED_SCOPE";

    private final transient Map<String, Object> details;

    public AssetVisibilityAuthorizationException(Map<String, Object> details) {
        super("You are not authorised to access this site or record.");
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String code() {
        return CODE;
    }

    public Map<String, Object> details() {
        return details;
    }
}
