package gh.edu.clet.sfl.common.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the raw role-name strings out of a JWT's configured roles claim - the piece
 * {@link OidcRolesConverter} and every service's {@code *ActorResolver} both need, so it exists once
 * rather than as six near-identical private methods.
 *
 * <p>Accepts either shape a provider might put at the claim: a flat list of role-name strings, or a
 * map whose keys are role names (Zitadel's {@code urn:zitadel:iam:org:project:roles} shape). See
 * {@link OidcRolesConverter} for why both are handled rather than one provider's assumed.
 */
public final class OidcRoleClaims {

    private OidcRoleClaims() {
    }

    public static Set<String> roleNames(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (claim instanceof Map<?, ?> map) {
            return map.keySet().stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }
        if (claim instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }
}
