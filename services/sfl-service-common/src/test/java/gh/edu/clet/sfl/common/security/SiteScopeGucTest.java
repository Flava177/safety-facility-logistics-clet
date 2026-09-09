package gh.edu.clet.sfl.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link SiteScopeGuc#encode}, the boundary that has to reject anything that is not a plain
 * site identifier since {@code SET LOCAL} takes no bind parameter (see the class-level Javadoc).
 */
class SiteScopeGucTest {

    @Test
    void encodes_plain_site_codes_uppercase_and_sorted_by_distinctness() {
        String encoded = SiteScopeGuc.encode(Set.of("main", "hq"));

        assertThat(encoded.split(",")).containsExactlyInAnyOrder("MAIN", "HQ");
    }

    @Test
    void empty_or_null_scopes_encode_to_empty_string() {
        assertThat(SiteScopeGuc.encode(Set.of())).isEmpty();
        assertThat(SiteScopeGuc.encode(null)).isEmpty();
    }

    @Test
    void wildcard_scope_short_circuits_to_the_wildcard_alone() {
        assertThat(SiteScopeGuc.encode(Set.of("*", "MAIN"))).isEqualTo("*");
    }

    @Test
    void rejects_values_containing_sql_metacharacters() {
        String encoded = SiteScopeGuc.encode(Set.of("MAIN'; DROP TABLE x; --"));

        assertThat(encoded).isEmpty();
    }

    @Test
    void rejects_values_containing_quotes_or_whitespace_injection() {
        assertThat(SiteScopeGuc.encode(Set.of("MAIN' OR '1'='1"))).isEmpty();
        assertThat(SiteScopeGuc.encode(Set.of("MAIN SET LOCAL app.site_scopes = '*'"))).isEmpty();
    }

    @Test
    void allows_hyphen_underscore_and_dot_in_addition_to_alphanumerics() {
        String encoded = SiteScopeGuc.encode(Set.of("main-01", "main_02", "main.03"));

        assertThat(encoded.split(",")).containsExactlyInAnyOrder("MAIN-01", "MAIN_02", "MAIN.03");
    }

    @Test
    void drops_invalid_scopes_while_keeping_valid_ones_in_the_same_set() {
        String encoded = SiteScopeGuc.encode(Set.of("MAIN", "bad;scope"));

        assertThat(encoded).isEqualTo("MAIN");
    }
}
