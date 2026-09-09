package gh.edu.clet.sfl.facilities.shared.api;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link ActorContextArgumentResolver}, {@link SourceChannelArgumentResolver} and {@link
 * IdempotencyKeyArgumentResolver} so every controller can declare {@code ActorContext}, {@code
 * SourceChannel} and {@code @IdempotencyKey String} as ordinary method parameters instead of each
 * carrying its own copy of the three-line delegation to {@link FacilitiesActorResolver}.
 *
 * <p>Implements {@link WebMvcConfigurer} directly rather than exposing one from a {@code @Bean}
 * factory method on an unrelated {@code @Configuration} class: {@code @WebMvcTest}'s slice filter
 * includes a component by checking whether the candidate class itself is assignable to {@code
 * WebMvcConfigurer} - a class whose {@code @Bean} method merely returns one is not itself that type,
 * so a controller slice test importing only {@link FacilitiesActorResolver} would resolve {@code
 * ActorContext} parameters via Spring's default record-binding fallback instead (constructing one
 * from empty request parameters, which fails {@code ActorContext}'s own null-check) - discovered by
 * running {@code BookingControllerTest} against the {@code @Bean}-method version of this class before
 * switching to this shape.
 */
@Component
class FacilitiesActorArgumentResolverConfiguration implements WebMvcConfigurer {

    private final FacilitiesActorResolver actorResolver;

    FacilitiesActorArgumentResolverConfiguration(FacilitiesActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorContextArgumentResolver(actorResolver));
        resolvers.add(new SourceChannelArgumentResolver(actorResolver));
        resolvers.add(new IdempotencyKeyArgumentResolver(actorResolver));
    }
}
