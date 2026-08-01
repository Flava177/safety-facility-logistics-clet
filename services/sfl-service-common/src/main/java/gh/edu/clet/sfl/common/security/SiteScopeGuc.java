package gh.edu.clet.sfl.common.security;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Puts the caller's site scopes onto the database session, so PostgreSQL can enforce them.
 *
 * <p>This is the half of ADR 0007 that carries the principal across the boundary. The policies
 * themselves live in each service's migration and read {@code app.site_scopes}; nothing sets that
 * without this class, and a policy reading an unset GUC returns no rows — deliberately, because a
 * second layer that opens up when the first forgets to speak is not a second layer.
 *
 * <h2>Why a transaction listener and not a connection wrapper</h2>
 *
 * <p>{@code SET LOCAL} only has meaning inside a transaction: outside one it applies to a statement
 * that has already ended. Setting the GUC when a connection is handed out would therefore have to use
 * {@code SET} instead, and a plain {@code SET} survives the connection's return to the pool — so the
 * next request to borrow it would inherit a stranger's scopes. That is the exact failure this design
 * is accused of and the reason it is built this way: {@code SET LOCAL} is rolled back with the
 * transaction, whether it commits or not, so a pooled connection is always clean.
 *
 * <h2>Why the value is quoted rather than bound</h2>
 *
 * <p>{@code SET LOCAL} does not accept a bind parameter. The value is therefore escaped and quoted
 * here, and — more importantly — the input is constrained rather than trusted: a site code that is not
 * a plain identifier is dropped before it reaches the statement. Site codes are normalised upstream by
 * {@code EstateCodes}, so anything failing that test is not a site code that could have matched a row
 * anyway, and dropping it narrows rather than widens.
 *
 * <h2>What happens when there is no actor</h2>
 *
 * <p>The scopes go to empty and the policies return nothing. That is correct for a request with no
 * principal. It is also why the scheduled sweeps, which run as a service account with {@code *},
 * carry that scope explicitly rather than relying on an absent GUC meaning "everything".
 */
public final class SiteScopeGuc implements TransactionExecutionListener {

    /** Set by this class, read by every {@code site_in_scope} policy function. */
    public static final String SETTING = "app.site_scopes";

    /** The cross-site scope, matching {@code SiteScopeFilter.all()} and {@code crossProgrammeRoles}. */
    public static final String ALL_SITES = "*";

    private final DataSource dataSource;
    private final Supplier<Set<String>> currentScopes;

    /**
     * @param currentScopes the scopes of the actor on this request. A supplier rather than a value
     *        because a transaction listener is a singleton and the actor is per request; resolving it
     *        eagerly would pin the first caller's scopes onto every later one.
     */
    public SiteScopeGuc(DataSource dataSource, Supplier<Set<String>> currentScopes) {
        this.dataSource = dataSource;
        this.currentScopes = currentScopes;
    }

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
        if (beginFailure != null) {
            return;
        }
        // Read-only transactions are scoped too. A read is exactly what RLS exists to narrow.
        String value = encode(currentScopes.get());
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL " + SETTING + " = '" + value + "'");
        } catch (SQLException exception) {
            // Fail the transaction rather than continue with the previous statement's scopes. A
            // transaction that could not be scoped must not run: the policies would then be reading
            // whatever the last SET LOCAL on this connection left behind, and silently returning the
            // wrong rows is worse than an error.
            throw new IllegalStateException("Could not apply site scopes to the database session", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /**
     * The scopes as a comma-separated list the policy can split.
     *
     * <p>Anything that is not a plain site identifier is dropped. {@code SET LOCAL} takes no bind
     * parameter, so this is the boundary that has to be sound — and constraining the input is a
     * stronger guarantee than escaping it, because a dropped scope narrows what the caller sees while
     * a mis-escaped one could widen it.
     */
    static String encode(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        if (scopes.contains(ALL_SITES)) {
            return ALL_SITES;
        }
        return scopes.stream()
                .filter(SiteScopeGuc::isPlainIdentifier)
                .map(scope -> scope.strip().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static boolean isPlainIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String candidate = value.strip();
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
