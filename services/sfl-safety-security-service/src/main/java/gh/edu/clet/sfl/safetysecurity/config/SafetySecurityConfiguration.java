package gh.edu.clet.sfl.safetysecurity.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The SSEMP filter chains, matching the four services that already had them.
 *
 * <p>Added 1 August 2026. This module had <strong>no security configuration at all</strong> — the
 * absence the go-live readiness pack recorded under G-01 and which was never closed with the rest of
 * that item. The consequence was not that the service was open: it was the opposite. With no chain
 * declared, Spring Security's default secured <em>everything</em> including {@code /actuator/health},
 * so the service answered {@code 401} to its own health probe, and {@code SFL_SECURITY_ENABLED=false}
 * had no effect because nothing read the property.
 *
 * <p>A service whose liveness probe returns 401 is a service every orchestrator treats as dead. It went
 * unnoticed for the same reason the missing main class did — nothing had ever started this module.
 *
 * <p>The two chains are the platform convention and the reasoning is A1's:
 *
 * <ul>
 *   <li><strong>Secure is what an absent property selects.</strong> {@code matchIfMissing = true} sits on
 *       the Keycloak chain, not the open one, so an environment that forgets the variable gets
 *       authentication rather than an open API.</li>
 *   <li><strong>Taking the open path is loud.</strong> It logs a warning naming this service on every
 *       startup, because the previous default let an unauthenticated deployment look normal.</li>
 *   <li><strong>The health probe and {@code /api/v1/system/info} stay reachable without a token.</strong>
 *       A load balancer cannot present one.</li>
 * </ul>
 *
 * <p>None of this makes any SSEMP system exist. It makes the foundation deployable and monitorable on
 * the same terms as its four siblings.
 */
@Configuration(proxyBeanMethods = false)
class SafetySecurityConfiguration {

    @Bean
    @ConditionalOnProperty(name = "sfl.security.enabled", havingValue = "false")
    SecurityFilterChain developmentSecurity(HttpSecurity http) throws Exception {
        LoggerFactory.getLogger(getClass()).warn(
                "sfl.security.enabled=false: every safety-security endpoint is UNAUTHENTICATED and the actor is "
                        + "whatever the X-SFL-* headers claim. Local development only.");
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "sfl.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain keycloakSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/api/v1/system/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakConverter())))
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> keycloakConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, realmRoles(jwt), jwt.getSubject());
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(Object::toString)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .toList();
    }
}
