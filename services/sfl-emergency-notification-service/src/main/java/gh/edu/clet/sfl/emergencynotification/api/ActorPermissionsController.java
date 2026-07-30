package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.emergencynotification.domain.policy.EmergencyPermissionMatrix;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the calling actor is permitted to do in S174.
 *
 * <p>The twin of the fleet service's route of the same name, and it exists for the same reason: the
 * dashboard needs to stop offering screens the actor cannot read, and could not derive that itself
 * without transcribing 103 permissions across 26 roles into TypeScript. It answers from the same
 * matrix the enforcement path uses, so the sidebar and the service cannot disagree.
 *
 * <p>Two routes rather than one because S174 is a separate deployable with its own matrix (ADR 0004).
 * The fleet service cannot answer for emergency and does not try; the dashboard asks both and merges.
 * That also means the emergency screens degrade honestly when this service is down: the dashboard
 * simply learns nothing about S174 permissions and stops narrowing, rather than hiding every emergency
 * screen from a coordinator who is entitled to all of them.
 *
 * <p><strong>Authorised like any other API route.</strong> It is not on either security chain's
 * permit-all list, so in production it falls to {@code anyRequest().authenticated()} and answers for
 * the authenticated principal. Locally, where {@code sfl.security.enabled=false}, it is reachable on
 * the {@code X-SFL-*} headers exactly as every other endpoint is.
 *
 * <p>It grants nothing either way. Every endpoint authorises independently, so an actor who overstates
 * their roles gains no access — only a sidebar that offers screens the service will refuse. Once IAM
 * lands this becomes a claim on the token and the route can go.
 */
@RestController
@RequestMapping("/api/v1/emergency/actor")
@Tag(name = "Emergency Governance")
public class ActorPermissionsController {

    private final EmergencyActorResolver actors;

    ActorPermissionsController(EmergencyActorResolver actors) {
        this.actors = actors;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<String>> permissions(HttpServletRequest request) {
        ActorContext actor = actors.resolve(request);
        Set<SflRole> roles = actor.principal().roles();

        return ApiResponse.ok(Arrays.stream(SflPermission.values())
                .filter(permission -> EmergencyPermissionMatrix.grants(roles, permission))
                .map(Enum::name)
                .sorted()
                .toList());
    }
}
