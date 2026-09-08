package gh.edu.clet.sfl.facilities.shared.api;

import gh.edu.clet.sfl.common.security.ActorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves an {@link ActorContext} controller-method parameter via {@link FacilitiesActorResolver},
 * replacing the {@code private ActorContext actor(HttpServletRequest http)} method every controller
 * used to carry a copy of - thirteen identical three-line methods doing the same delegation, and
 * every one of them a place the delegation could drift or be forgotten. A controller now simply
 * declares an {@code ActorContext} parameter and gets it resolved the same way every other one does.
 */
final class ActorContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final FacilitiesActorResolver actorResolver;

    ActorContextArgumentResolver(FacilitiesActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ActorContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return actorResolver.resolve(request);
    }
}
