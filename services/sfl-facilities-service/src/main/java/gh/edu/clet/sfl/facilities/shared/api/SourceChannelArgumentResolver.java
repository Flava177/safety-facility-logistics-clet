package gh.edu.clet.sfl.facilities.shared.api;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves a {@link SourceChannel} controller-method parameter - see {@link ActorContextArgumentResolver}. */
final class SourceChannelArgumentResolver implements HandlerMethodArgumentResolver {

    private final FacilitiesActorResolver actorResolver;

    SourceChannelArgumentResolver(FacilitiesActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SourceChannel.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return actorResolver.resolveSourceChannel(ServletRequests.require(webRequest));
    }
}
