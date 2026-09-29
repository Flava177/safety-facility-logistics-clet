package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The two comma-separated columns on {@code construction_projects} - work types and affected room ids.
 * See V21 for why they are not child tables.
 */
final class ConstructionColumns {

    private ConstructionColumns() {
    }

    static String join(List<?> values) {
        return values == null || values.isEmpty() ? null
                : String.join(",", values.stream().map(String::valueOf).toList());
    }

    static List<String> strings(String column) {
        return column == null || column.isBlank() ? List.of()
                : Arrays.stream(column.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    static List<UUID> uuids(String column) {
        return strings(column).stream().map(UUID::fromString).toList();
    }
}
