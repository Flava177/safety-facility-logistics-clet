package gh.edu.clet.sfl.common.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    @Test
    void allows_up_to_the_limit_within_a_window() {
        AtomicLong clock = new AtomicLong(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 1000, clock::get);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void resets_once_the_window_rolls_over() {
        AtomicLong clock = new AtomicLong(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, 1000, clock::get);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.set(1000);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void tracks_each_key_independently() {
        AtomicLong clock = new AtomicLong(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, 1000, clock::get);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isFalse();
    }

    @Test
    void rejects_non_positive_configuration() {
        assertThatThrownBy(() -> new FixedWindowRateLimiter(0, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedWindowRateLimiter(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expired_keys_are_evicted_rather_than_accumulating_forever() {
        AtomicLong clock = new AtomicLong(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(5, 1000, clock::get);

        for (int i = 0; i < 500; i++) {
            limiter.tryAcquire("client-" + i);
        }
        assertThat(limiter.trackedKeyCount()).isEqualTo(500);

        // Past the window for every one of those keys, and past the sweep interval (which is also
        // windowMillis) - the next call must both roll its own window over *and* sweep the 500 stale
        // ones left behind by traffic that never returns.
        clock.set(2000);
        limiter.tryAcquire("client-new");

        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void a_key_that_keeps_calling_is_never_evicted_out_from_under_itself() {
        AtomicLong clock = new AtomicLong(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, 1000, clock::get);

        assertThat(limiter.tryAcquire("a")).isTrue();
        clock.set(1000);
        // "a"'s window rolls over here - the sweep this call triggers must not evict the fresh window
        // it just installed for the very same key.
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void concurrent_access_from_many_keys_and_threads_stays_correct_and_bounded() throws InterruptedException {
        AtomicLong clock = new AtomicLong(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 1000, clock::get);
        int threadCount = 8;
        int callsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        try {
            for (int t = 0; t < threadCount; t++) {
                int thread = t;
                pool.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(go);
                    for (int i = 0; i < callsPerThread; i++) {
                        // A mix of shared and thread-local keys, so both contended and uncontended
                        // windows are exercised concurrently with the sweep.
                        limiter.tryAcquire(i % 2 == 0 ? "shared" : "thread-" + thread);
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        // threadCount thread-local keys plus one shared key - never more, and no key was lost or
        // duplicated under concurrent compute()/sweep() races.
        assertThat(limiter.trackedKeyCount()).isEqualTo(threadCount + 1);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
