package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable pagination envelope for fleet collection endpoints.
 *
 * <p>Sorting always carries a deterministic tiebreak so paging cannot skip or repeat a record when
 * several rows share a sort value.
 *
 * <p>{@code scopeNotice} is set when the server narrowed the list beyond what the caller asked for —
 * a driver's trip list is their own trips, not their site's. It is null on every unnarrowed list. The
 * reason travels with the data rather than being reconstructed by the client from the roles it holds:
 * a client that has to infer why a list is short will eventually infer it differently from the server,
 * and the visible failure is a user reporting missing records that were never theirs.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort,
        String scopeNotice) {

    /** An unnarrowed page — the ordinary case, and what every list but trips returns. */
    public PageResponse(List<T> content, int page, int size, long totalElements, int totalPages, boolean first,
            boolean last, String sort) {
        this(content, page, size, totalElements, totalPages, first, last, sort, null);
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), page.getSort().toString());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, java.util.function.Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(),
                page.getSort().toString());
    }
}
