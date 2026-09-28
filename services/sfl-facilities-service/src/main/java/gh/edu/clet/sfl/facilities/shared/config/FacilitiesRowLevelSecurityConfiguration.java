package gh.edu.clet.sfl.facilities.shared.config;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SiteScopeGuc;
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.application.PlatformThreads;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registers the site-scope GUC on the transaction manager - ADR 0007.
 *
 * <p>Enabled by default, and harmless when it is not needed: setting {@code app.site_scopes} on a
 * connection whose role bypasses RLS costs one statement per transaction and changes nothing. That is
 * deliberate - it means the setting is already correct in every environment before any of them adopts
 * the {@code sfl_app} role, so switching a database over is a connection-string change rather than a
 * deployment that has to land in lockstep with a migration.
 *
 * <p>{@code sfl.security.rls.set-scope-enabled=false} turns it off for a database that predates V14.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sfl.security.rls.set-scope-enabled", havingValue = "true",
        matchIfMissing = true)
class FacilitiesRowLevelSecurityConfiguration {

    /**
     * Declared as a plain bean, never registered by hand.
     *
     * <p>Spring Boot discovers every {@code TransactionExecutionListener} bean and hands them to the
     * transaction manager itself. Asking for the manager here in order to call {@code addListener}
     * looks tidier and is a circular reference - the manager needs the listeners to be built, and the
     * listener would need the manager. Recorded because the manual form is the obvious first attempt.
     */
    @Bean
    SiteScopeGuc facilitiesSiteScopeGuc(DataSource dataSource,
            ObjectProvider<FacilitiesActorResolver> actorResolver) {
        return new SiteScopeGuc(dataSource, () -> currentScopes(actorResolver));
    }

    /**
     * The scopes of whoever is on this request, or none.
     *
     * <p>Outside a request the answer depends on the thread. A scheduled sweep or the broker listener
     * runs on a {@link PlatformThreads platform thread} and scopes to {@code *}, matching the service
     * account it acts as; anything else genuinely actor-less scopes to nothing and reads no rows.
     */
    static Set<String> currentScopes(ObjectProvider<FacilitiesActorResolver> actorResolver) {
        // Platform work first. A scheduler, drainer or broker-listener thread - or a block explicitly
        // run as platform work, such as recording a rejected vendor message - is the service account's
        // own and scopes to every site. Before Phase 2 there was no such branch, and under sfl_app every
        // sweep and the outbox drainer would have read zero rows, silently. See PlatformThreads.
        if (PlatformThreads.isPlatformThread()) {
            return Set.of(SiteScopeGuc.ALL_SITES);
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Set.of();
        }
        FacilitiesActorResolver resolver = actorResolver.getIfAvailable();
        if (resolver == null) {
            return Set.of();
        }
        try {
            HttpServletRequest request = attributes.getRequest();
            ActorContext actor = resolver.resolve(request);
            return actor.principal().siteScopes();
        } catch (RuntimeException resolutionFailed) {
            // An unresolvable actor scopes to nothing rather than to everything. The request will fail
            // on its own terms a moment later; it must not read rows in the meantime.
            return Set.of();
        }
    }
}
