package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import java.util.Collection;
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
 * OAuth2/OIDC resource-server configuration.
 *
 * <p>Provider-neutral by design: token validation is pure OIDC/JWKS driven by
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}, and roles are read from the standard
 * {@code realm_access.roles} claim. Swapping identity provider is configuration, not code, and there is
 * no local user or password store anywhere in this service.
 *
 * <p>The telematics webhook is intentionally not JWT-authenticated: it authenticates with a per-source
 * HMAC signature and an allowlist check inside the controller (SRS-SFL-S166-04), which is what vendor
 * callbacks can actually produce.
 */
@Configuration(proxyBeanMethods = false)
class FleetSecurityConfiguration {

    @Bean
    @ConditionalOnProperty(name = "sfl.security.enabled", havingValue = "false")
    SecurityFilterChain developmentSecurity(HttpSecurity http) throws Exception {
        LoggerFactory.getLogger(getClass()).warn(
                "sfl.security.enabled=false: every fleet endpoint is UNAUTHENTICATED and the actor is "
                        + "whatever the X-SFL-* headers claim. Local development only.");
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "sfl.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain resourceServerSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/", "/index.html", "/assets/**", "/ui", "/ui/**", "/fleet/**", "/fuel/**",
                                "/dispatch/**", "/sfl-logo.png", "/favicon.ico").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/api/v1/system/info").permitAll()
                        .requestMatchers("/api/v1/integrations/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(realmRoleConverter())))
                .build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> realmRoleConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(
                        "ROLE_" + role.toUpperCase(java.util.Locale.ROOT)))
                .toList();
    }
}
