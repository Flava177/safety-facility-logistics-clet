package gh.edu.clet.sfl.assetvisibility.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Who is calling AVAMP.
 *
 * <p><strong>The defect this closes.</strong> Every controller method took the actor as
 * {@code @RequestHeader(name = "X-SFL-User", defaultValue = "development-user")} and passed it
 * straight into the command. That is fine while security is off and the header *is* the actor. It
 * stops being fine the moment authentication is on: the request now carries a verified JWT
 * principal, and the service would still have attributed the action to whatever string the caller
 * chose to put in a header — or, if they sent none, to a user literally called
 * {@code development-user}. Every asset registration, custody change and evidence link is written
 * into the audit trail under that name.
 *
 * <p>The other three services resolve this properly and have since they were built; AVAMP was the
 * one that did not, because it predates them and its controllers never grew past the header. This
 * brings it into line rather than inventing a fourth convention: JWT subject first, headers only
 * when there is no authenticated principal.
 *
 * <p><strong>The header path is not deleted.</strong> With {@code sfl.security.enabled=false} there
 * is no JWT to read, the dashboard sends {@code X-SFL-*}, and local development works exactly as it
 * did. What changed is the precedence — a header can no longer override a verified identity, which
 * is the whole point.
 */
@Component
public class AssetVisibilityActorResolver {

    static final String HEADER_USER = "X-SFL-User";
    static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

    /** The actor id every command is attributed to, and every audit row is written under. */
    public String resolveActor(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            // `sub` rather than `preferred_username`: the subject is stable across a rename, and an
            // audit trail that changes retroactively when somebody marries is not an audit trail.
            return jwt.getSubject();
        }
        String header = request.getHeader(HEADER_USER);
        return header == null || header.isBlank() ? "development-user" : header.strip();
    }

    public String resolveCorrelationId(HttpServletRequest request) {
        return request.getHeader(HEADER_CORRELATION_ID);
    }
}
