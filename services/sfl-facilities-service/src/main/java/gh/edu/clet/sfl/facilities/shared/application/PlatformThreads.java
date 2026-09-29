package gh.edu.clet.sfl.facilities.shared.application;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Marks the threads that do the platform's own work, so row-level security can scope them.
 *
 * <h2>The defect this closes</h2>
 *
 * <p>{@code SiteScopeGuc} sets {@code app.site_scopes} from the actor on the current HTTP request, and
 * returned an empty set when there was none - which the policies read, correctly, as "no rows". Its own
 * documentation says the scheduled sweeps "carry that scope explicitly", and nothing ever made them:
 * every {@code @Scheduled} job, the outbox drainer and the broker listener run with no request on the
 * thread. Connected as the owner (every environment today) that is invisible, because the owner
 * bypasses RLS. Connected as {@code sfl_app} (the ADR 0007 production target) the S153 escalation
 * sweep escalates nothing, the drainer publishes nothing, and the vehicle-service handler's fault
 * insert is refused by {@code WITH CHECK} - all silently, in the one environment nobody tests.
 *
 * <p>Phase 2 adds a dozen more sweeps (debounce, staleness, KPI periods, insurance expiry, pre-event
 * escalation). Fixing each job would rely on the next one remembering; fixing the thread does not.
 *
 * <h2>Why the thread, not the method</h2>
 *
 * <p>A scope set at the top of a job's body is set too late whenever the method is itself
 * {@code @Transactional} - the listener is - because the GUC is issued when the transaction begins,
 * before the body runs. A thread created by this factory carries the mark from its first instruction,
 * so the order of proxies stops mattering.
 *
 * <p>The mark means {@code *}, the cross-site scope. That matches what these jobs already are: service
 * accounts holding {@code *} (see {@code BookingScheduledJobs.SYSTEM}), deciding per record what to do.
 * A request thread is never marked, so an HTTP caller can never inherit it.
 */
public final class PlatformThreads {

    private static final ThreadLocal<Boolean> PLATFORM = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private PlatformThreads() {
    }

    /** {@code true} on a scheduler, drainer or broker-listener thread created by {@link #factory}. */
    public static boolean isPlatformThread() {
        return PLATFORM.get();
    }

    /** A thread factory whose threads are platform threads from their first instruction. */
    public static ThreadFactory factory(String namePrefix) {
        AtomicInteger sequence = new AtomicInteger();
        return work -> {
            Thread thread = new Thread(() -> {
                PLATFORM.set(Boolean.TRUE);
                work.run();
            }, namePrefix + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }

    /**
     * Runs {@code work} as platform work on the current thread, restoring the previous state after.
     *
     * <p>Only for work that is genuinely the platform's rather than the caller's - recording that a
     * vendor message was <em>rejected</em> is the case it exists for: the rejected sender's scope is
     * precisely what cannot be trusted, and a scoped principal forging another site's traffic must still
     * leave a record. Anything that starts a transaction inside {@code work} is scoped to {@code *}; the
     * caller's own transaction, already begun, is not affected. Never wrap a caller's business action in
     * this - that would widen an HTTP caller to every site.
     */
    public static <T> T callAsPlatform(java.util.function.Supplier<T> work) {
        boolean previous = PLATFORM.get();
        PLATFORM.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            PLATFORM.set(previous);
        }
    }

    public static void runAsPlatform(Runnable work) {
        callAsPlatform(() -> {
            work.run();
            return null;
        });
    }
}
