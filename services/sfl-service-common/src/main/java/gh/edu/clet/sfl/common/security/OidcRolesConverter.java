package gh.edu.clet.sfl.common.security;

import java.util.Collection;
import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a JWT's roles claim into {@link GrantedAuthority} instances, without hard-coding one identity
 * provider's shape for it.
 *
 * <p>There is no single OIDC-standard roles claim - every provider invents its own. This reads
 * whichever claim name it is configured with (see {@code sfl.security.roles-claim} on each service's
 * {@code SecurityConfiguration}) via {@link OidcRoleClaims}, which accepts either shape a provider
 * might put there: a flat list of role-name strings, or a map whose keys are role names (Zitadel's
 * {@code urn:zitadel:iam:org:project:roles} shape).
 *
 * <p>Swapping identity provider is a property change (the claim name) plus, if the new provider's
 * project/application is configured to emit the claim in the other of the two shapes above, nothing at
 * all - both are handled the same way here.
 *
 * <p>This only feeds {@link org.springframework.security.core.Authentication#getAuthorities()}. Each
 * service's own {@code *ActorResolver} reads {@link OidcRoleClaims} directly to build its
 * {@code Set<SflRole>} - the two must stay pointed at the same claim, which is exactly what routing
 * both through {@code sfl.security.roles-claim} guarantees.
 */
public final class OidcRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String rolesClaim;

    public OidcRolesConverter(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        return OidcRoleClaims.roleNames(jwt, rolesClaim).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                .toList();
    }
}
