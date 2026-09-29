package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comma-separated id and code lists, as V16 stores evidence ids and rule codes.
 *
 * <p>Text rather than a join table: the lists are bounded (twenty or fifty evidence ids), always read with
 * their owner, never queried by member, and a join table per list would be three more tables under RLS
 * for no query anybody runs.
 */
final class IdLists {

    private IdLists() {
    }

    static String join(Collection<UUID> ids) {
        return ids == null || ids.isEmpty() ? null : ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    static List<UUID> ids(String text) {
        return text == null || text.isBlank() ? List.of()
                : Arrays.stream(text.split(",")).map(String::strip).filter(value -> !value.isEmpty())
                        .map(UUID::fromString).toList();
    }

    static Set<Integer> codes(String text) {
        return text == null || text.isBlank() ? Set.of()
                : Arrays.stream(text.split(",")).map(String::strip).filter(value -> !value.isEmpty())
                        .map(Integer::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
