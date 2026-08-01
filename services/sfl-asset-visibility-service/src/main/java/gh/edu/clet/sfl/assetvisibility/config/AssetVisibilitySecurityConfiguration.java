package gh.edu.clet.sfl.assetvisibility.config;

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

@Configuration(proxyBeanMethods = false)
class AssetVisibilitySecurityConfiguration {

    @Bean
    @ConditionalOnProperty(name = "sfl.security.enabled", havingValue = "false")
    SecurityFilterChain developmentSecurity(HttpSecurity http) throws Exception {
        LoggerFactory.getLogger(getClass()).warn(
                "sfl.security.enabled=false: every asset-visibility endpoint is UNAUTHENTICATED and the actor is "
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
