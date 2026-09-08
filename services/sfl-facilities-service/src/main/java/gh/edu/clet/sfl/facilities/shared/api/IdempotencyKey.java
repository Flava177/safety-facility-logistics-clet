package gh.edu.clet.sfl.facilities.shared.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller-method {@code String} parameter as the {@code Idempotency-Key} header, resolved
 * by {@link IdempotencyKeyArgumentResolver}. A bare {@code String} return type is too ambiguous to
 * resolve by type alone the way {@link ActorContextArgumentResolver} resolves {@code ActorContext} -
 * every path variable and request parameter is a {@code String} too - so this needs a marker.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotencyKey {
}
