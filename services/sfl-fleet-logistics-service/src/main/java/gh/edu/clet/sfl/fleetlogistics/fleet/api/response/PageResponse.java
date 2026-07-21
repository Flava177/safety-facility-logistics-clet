package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable pagination envelope for fleet collection endpoints.
 *
 * <p>Sorting always carries a deterministic tiebreak so paging cannot skip or repeat a record when
 * several rows share a sort value.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort) {

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
