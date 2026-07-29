package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository.FuelPage;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository.Paging;
import java.util.List;

/**
 * The fuel collection envelope, deliberately identical in shape to the fleet {@code PageResponse}.
 *
 * <p>Before this, every fuel collection returned a bare {@code List<T>} capped by a {@code size}
 * limit — no total, no page, no way for a client to know whether it had the register or the first
 * hundred rows of it. A dashboard can only be honest about that by guessing from whether the list came
 * back full, which is the kind of thing an operator discovers when a record is missing.
 *
 * <p>{@code sort} is echoed back because the request may not have named one: a client that cannot
 * see the ordering it got cannot tell a stable page from a shifting one.
 */
public record FuelPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort) {

    public static <T> FuelPageResponse<T> of(FuelPage<T> page) {
        return new FuelPageResponse<>(page.content(), page.page(), page.size(), page.totalElements(),
                page.totalPages(), page.page() == 0, page.page() >= page.totalPages() - 1, page.sort());
    }

    /** Normalises the paging parameters every fuel collection endpoint accepts. */
    static Paging paging(int page, int size, String sort) {
        return new Paging(page, size, sort);
    }
}
