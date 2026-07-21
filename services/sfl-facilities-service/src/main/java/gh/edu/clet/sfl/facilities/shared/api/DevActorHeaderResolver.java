package gh.edu.clet.sfl.facilities.shared.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import org.springframework.stereotype.Component;

@Component
public class DevActorHeaderResolver {

    public ActorContext resolve(String userId, String displayName, String rolesHeader, String sitesHeader,
            String correlationId) {
        Set<SflRole> roles = parseRoles(rolesHeader);
        Set<String> sites = parseSites(sitesHeader);
        return new ActorContext(new SiteScopedPrincipal(userId, displayName, roles, sites, false), correlationId);
    }

    private Set<SflRole> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(value -> SflRole.valueOf(value.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> parseSites(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}