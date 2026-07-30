package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.FleetPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.policy.FuelPermissionMatrix;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the calling actor is permitted to do, across the three systems this service carries.
 *
 * <p>The dashboard needs this to stop offering screens the actor cannot read. It could not derive the
 * answer itself: there are 103 permissions across 26 roles, and transcribing that into TypeScript
 * would have created exactly the drift that hiding a navigation entry is supposed to prevent — the
 * sidebar would eventually promise something the service refuses, or hide something it allows. So the
 * front end asks, and this answers from the same matrices the enforcement path uses.
 *
 * <p><strong>Authorised like any other API route.</strong> It is not on either security chain's
 * permit-all list, so in production it falls to {@code anyRequest().authenticated()} and answers for
 * the authenticated principal. Locally, where {@code sfl.security.enabled=false}, it is reachable on
 * the {@code X-SFL-*} headers exactly as every other endpoint is.
 *
 * <p>It grants nothing either way. Every endpoint authorises independently, so an actor who overstates
 * their roles gains no access — only a sidebar that offers screens the service will refuse. Once IAM
 * lands this becomes a claim on the token and the route can go.
 *
 * <p>S174's permissions are <strong>not</strong> included. Emergency notification is a separate
 * deployable with its own matrix (ADR 0004), and this service cannot answer for it — the emergency
 * service exposes the same route for that. Answering with a partial list and letting the dashboard
 * treat it as complete would hide every emergency screen from an entitled coordinator.
 */
@RestController
@RequestMapping("/api/v1/fleet/actor")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Fleet Governance")
public class ActorPermissionsController {

    private final FleetActorResolver actors;

    ActorPermissionsController(FleetActorResolver actors) {
        this.actors = actors;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<String>> permissions(HttpServletRequest request) {
        ActorContext actor = actors.resolve(request);
        Set<SflRole> roles = actor.principal().roles();

        EnumSet<SflPermission> granted = EnumSet.noneOf(SflPermission.class);
        granted.addAll(FleetPermissionMatrix.permissionsFor(roles));
        // Fuel and dispatch expose only a predicate, so they are asked one permission at a time. 103
        // in-memory set lookups per call, which is cheaper than keeping a fourth copy of the mapping.
        Arrays.stream(SflPermission.values())
                .filter(permission -> FuelPermissionMatrix.grants(roles, permission)
                        || DispatchPermissionMatrix.grants(roles, permission))
                .forEach(granted::add);

        return ApiResponse.ok(granted.stream().map(Enum::name).sorted().toList());
    }
}
