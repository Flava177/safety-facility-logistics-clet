package gh.edu.clet.sfl.facilities.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * {@code NativeWebRequest.getNativeRequest(HttpServletRequest.class)} returns {@code null} if the
 * request Spring is dispatching is not a servlet one - which cannot happen for any argument resolver
 * in this package, since this service only ever runs on the servlet stack, but the method's own
 * contract allows it. Failing loudly here, rather than passing a possible null into the actor
 * resolver three call sites over, is one line instead of three duplicated null checks.
 */
final class ServletRequests {

    private ServletRequests() {
    }

    static HttpServletRequest require(NativeWebRequest webRequest) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException(
                    "Expected a servlet HttpServletRequest, but the current request is not one");
        }
        return request;
    }
}
