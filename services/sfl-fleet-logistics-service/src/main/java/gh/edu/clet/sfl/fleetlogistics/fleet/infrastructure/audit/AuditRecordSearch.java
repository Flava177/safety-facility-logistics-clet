package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort.AuditQuery;
import java.util.List;

/**
 * Filtered audit search, added as a repository fragment.
 *
 * <p>It exists because the derived JPQL version could not be executed at all. That query expressed
 * every optional filter as {@code (:param is null or column = :param)}, which Hibernate renders as
 * {@code (? is null or column = ?)} — and PostgreSQL cannot infer a type for a parameter whose only
 * appearance is a bare {@code IS NULL} test. Every call failed with
 * {@code could not determine data type of parameter $11}, whatever combination of filters was
 * supplied, so audit search and the Evidence &amp; audit screen built on it returned 500 across the
 * board.
 *
 * <p>Building the predicates instead of parameterising them removes the whole class of problem: an
 * absent filter contributes no SQL, so there is no untyped null to infer. It also produces a query
 * PostgreSQL can plan against the indexes, rather than one wrapped in {@code OR} tests that defeat
 * them.
 */
interface AuditRecordSearch {

    List<AuditRecordEntity> searchRecords(boolean allSites, List<String> siteScopes, AuditQuery query);
}
