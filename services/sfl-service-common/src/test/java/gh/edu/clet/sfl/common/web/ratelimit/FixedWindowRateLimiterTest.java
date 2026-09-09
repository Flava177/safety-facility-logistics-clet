package gh.edu.clet.sfl.common.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
