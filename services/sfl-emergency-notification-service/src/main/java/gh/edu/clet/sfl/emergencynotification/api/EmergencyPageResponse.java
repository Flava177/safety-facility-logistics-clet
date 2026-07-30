package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository.EmergencyPage;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository.Paging;
import java.util.List;

/**
 * The emergency collection envelope, deliberately identical in shape to the fleet
 * {@code PageResponse}, the fuel {@code FuelPageResponse} and the dispatch
 * {@code DispatchPageResponse}.
 *
 * <p>Before this, every S174 collection returned a bare {@code List<T>} capped at 200 by the
 * application service, with no {@code size} parameter to raise it — no total, no page, and no way
 * for a client to know whether it held the register or the first two hundred rows of it. A dashboard
 * could only be honest about that by guessing from whether the list came back full.
 *
 * <p>{@code sort} is echoed back because the request may not have named one: a client that cannot
 * see the ordering it got cannot tell a stable page from a shifting one.
 */
public record EmergencyPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort) {

    public static <T> EmergencyPageResponse<T> of(EmergencyPage<T> page) {
        return new EmergencyPageResponse<>(page.content(), page.page(), page.size(), page.totalElements(),
                page.totalPages(), page.page() == 0, page.page() >= page.totalPages() - 1, page.sort());
    }

    /** Normalises the paging parameters every emergency collection endpoint accepts. */
    public static Paging paging(int page, int size, String sort) {
        return new Paging(page, size, sort);
    }
}
