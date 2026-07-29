package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository.DispatchPage;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository.Paging;
import java.util.List;

/**
 * The dispatch collection envelope, deliberately identical in shape to the fleet {@code PageResponse}
 * and the fuel {@code FuelPageResponse}.
 *
 * <p>Before this, every dispatch collection returned a bare {@code List<T>} capped by a {@code size}
 * limit — no total, no page, and no way for a client to know whether it held the register or the
 * first hundred rows of it. A dashboard could only be honest about that by guessing from whether the
 * list came back full, which is the kind of thing an operator discovers when a consignment is
 * missing.
 *
 * <p>{@code sort} is echoed back because the request may not have named one: a client that cannot
 * see the ordering it got cannot tell a stable page from a shifting one.
 */
public record DispatchPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort) {

    public static <T> DispatchPageResponse<T> of(DispatchPage<T> page) {
        return new DispatchPageResponse<>(page.content(), page.page(), page.size(), page.totalElements(),
                page.totalPages(), page.page() == 0, page.page() >= page.totalPages() - 1, page.sort());
    }

    /** Normalises the paging parameters every dispatch collection endpoint accepts. */
    public static Paging paging(int page, int size, String sort) {
        return new Paging(page, size, sort);
    }
}
