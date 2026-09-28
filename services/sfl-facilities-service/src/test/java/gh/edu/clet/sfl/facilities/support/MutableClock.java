package gh.edu.clet.sfl.facilities.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock a test can move. Most of Phase 2 is about time passing - a debounce window, a sensor going
 * stale, an insurance expiry, an event's escalation window - and a fixed clock cannot say "an hour later".
 */
public final class MutableClock extends Clock {

    private Instant instant;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration by) {
        instant = instant.plus(by);
    }

    public void set(Instant at) {
        instant = at;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
