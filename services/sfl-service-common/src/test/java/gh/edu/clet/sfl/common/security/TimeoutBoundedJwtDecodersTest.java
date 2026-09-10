package gh.edu.clet.sfl.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Proves the decoder actually gives up within the configured bound rather than hanging on a stalled
 * identity provider - see {@link TimeoutBoundedJwtDecoders} for why the default (Boot-auto-configured)
 * decoder cannot make this guarantee.
 */
class TimeoutBoundedJwtDecodersTest {

    private static final Duration BOUND = Duration.ofMillis(300);

    @Test
    void a_stalled_identity_provider_fails_within_the_configured_timeout_instead_of_hanging() throws IOException {
        // Accepts the TCP connection (so this exercises the read timeout, not the connect timeout) and
        // then never writes a response - exactly what a Zitadel instance that accepted the connection but
        // stalled mid-request looks like from this service's side.
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try (Socket ignored = serverSocket.accept()) {
                    Thread.sleep(Duration.ofSeconds(30).toMillis());
                } catch (IOException | InterruptedException expected) {
                    // Test teardown closes the server socket; either exception here is expected noise.
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            JwtDecoder decoder = TimeoutBoundedJwtDecoders.fromIssuerLocation(
                    "http://localhost:" + serverSocket.getLocalPort(), BOUND, BOUND);

            Instant start = Instant.now();
            assertThatThrownBy(() -> decoder.decode("not-a-real-token")).isInstanceOf(RuntimeException.class);
            Duration elapsed = Duration.between(start, Instant.now());

            // Generous headroom above the 300ms bound so this is not flaky under CI scheduling jitter,
            // while still failing if the read timeout were not wired through at all (which would hang
            // for however long the JVM's default socket timeout is - effectively forever).
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        }
    }
}
