package gh.edu.clet.sfl.facilities.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves an {@link IdempotencyKey}-annotated {@code String} parameter - see {@link ActorContextArgumentResolver}. */
final class IdempotencyKeyArgumentResolver implements HandlerMethodArgumentResolver {

    private final FacilitiesActorResolver actorResolver;

    IdempotencyKeyArgumentResolver(FacilitiesActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(IdempotencyKey.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return actorResolver.resolveIdempotencyKey(request);
    }
}
