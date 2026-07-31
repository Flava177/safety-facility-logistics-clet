package gh.edu.clet.sfl.facilities.shared.api;

import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import java.util.List;
import java.util.function.Function;

/**
 * The paged-list envelope for every S152 search endpoint.
 *
 * <p>{@code totalElements} reflects what the caller may see, not what exists: the application layer
 * filters by site scope after the query, and reporting the unfiltered total would let a caller infer
 * the size of another site's estate from a page they cannot read.
 */
public record PageResponse<T>(
        List<T> items,
        long totalElements,
        int totalPages,
        int page,
        int size) {

    public static <D, R> PageResponse<R> from(FacilitiesRepository.Page<D> page, Function<D, R> mapper) {
        int size = Math.max(1, page.size());
        int totalPages = (int) Math.ceil(page.totalElements() / (double) size);
        return new PageResponse<>(page.items().stream().map(mapper).toList(), page.totalElements(),
                totalPages, page.page(), page.size());
    }
}
