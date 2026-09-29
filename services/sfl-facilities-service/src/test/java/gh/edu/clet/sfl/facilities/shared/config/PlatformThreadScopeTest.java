package gh.edu.clet.sfl.facilities.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.application.PlatformThreads;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The scope row-level security sees when there is no HTTP request - the defect PlatformThreads closes.
 *
 * <p>Before Phase 2 this was always empty, so under {@code sfl_app} every scheduled sweep, the outbox
 * drainer and the broker listener would have read no rows at all.
 */
class PlatformThreadScopeTest {

    private final ObjectProvider<FacilitiesActorResolver> noResolver =
            new StaticListableBeanFactory().getBeanProvider(FacilitiesActorResolver.class);

    @Test
    @DisplayName("an ordinary thread with no request scopes to nothing - fail closed")
    void ordinary_thread_sees_nothing() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(FacilitiesRowLevelSecurityConfiguration.currentScopes(noResolver)).isEmpty();
    }

    @Test
    @DisplayName("a platform thread - scheduler, drainer, listener - scopes to every site")
    void platform_thread_sees_every_site() throws InterruptedException {
        AtomicReference<Set<String>> seen = new AtomicReference<>();
        Thread thread = PlatformThreads.factory("test-platform-").newThread(
                () -> seen.set(FacilitiesRowLevelSecurityConfiguration.currentScopes(noResolver)));
        thread.start();
        thread.join();

        assertThat(seen.get()).containsExactly("*");
    }

    @Test
    @DisplayName("an explicit platform block scopes to every site and restores the thread afterwards")
    void explicit_platform_block_is_bounded() {
        assertThat(PlatformThreads.callAsPlatform(
                () -> FacilitiesRowLevelSecurityConfiguration.currentScopes(noResolver))).containsExactly("*");
        assertThat(FacilitiesRowLevelSecurityConfiguration.currentScopes(noResolver)).isEmpty();
    }
}
