package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import java.util.Set;

/**
 * The set of sites a query may return, resolved from the caller's principal.
 *
 * <p>Queries push this into SQL rather than filtering in memory, so a caller never loads rows they are
 * not entitled to see (SRS-SFL-S166-01: "Users shall only see and update records inside their assigned
 * site scopes and roles").
 *
 * @param allSites {@code true} when the principal holds the {@code *} wildcard scope
 * @param sites the explicit, normalised site codes; empty when {@code allSites} is {@code true}
 */
public record SiteScopeFilter(boolean allSites, Set<String> sites) {

    public SiteScopeFilter {
        sites = sites == null ? Set.of() : Set.copyOf(sites);
    }

    public static SiteScopeFilter all() {
        return new SiteScopeFilter(true, Set.of());
    }

    public static SiteScopeFilter of(Set<String> sites) {
        return new SiteScopeFilter(false, sites);
    }

    public boolean permits(String siteCode) {
        return allSites || (siteCode != null && sites.contains(siteCode.strip().toUpperCase(java.util.Locale.ROOT)));
    }

    public boolean isEmpty() {
        return !allSites && sites.isEmpty();
    }
}
