package gh.edu.clet.sfl.facilities.shared.application.port;

import java.util.List;

/**
 * A page of query results with the total available, shared by every module's search port so each one
 * does not redeclare {@code FacilitiesRepository.Page}, the S152 original this mirrors.
 *
 * <p>{@code totalElements} is expected to reflect what the caller may see, not what exists: an
 * application service that filters a page by site scope after the query reports the filtered total,
 * the same rule {@code FacilitiesMasterDataService.searchRooms} established - a total counting records
 * the caller may not see would let them infer another site's estate size.
 */
public record RepositoryPage<T>(List<T> items, long totalElements, int page, int size) {

    public RepositoryPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> RepositoryPage<T> of(List<T> items, long totalElements, int page, int size) {
        return new RepositoryPage<>(items, totalElements, page, size);
    }
}
