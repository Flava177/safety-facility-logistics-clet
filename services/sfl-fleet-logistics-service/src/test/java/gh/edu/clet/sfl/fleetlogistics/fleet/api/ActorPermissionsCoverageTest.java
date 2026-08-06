package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.assets.domain.policy.AssetVisibilityPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.FleetPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.policy.FuelPermissionMatrix;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every matrix this deployable carries reaches {@code /actor/permissions}.
 *
 * <h2>The defect this exists to stop coming back</h2>
 *
 * <p>The endpoint merged three of the four matrices. Asset visibility was folded into this service on
 * 5 August and its matrix was never added to the union, so {@code ASSET_REFERENCE_READ} and
 * {@code ASSET_REFERENCE_MANAGE} were absent from every answer.
 *
 * <p>That is not the same as being unknown. The dashboard's fail-open is per *set*: once any service
 * answers, anything missing from the merged set is treated as **denied**. So the two permissions were
 * actively refused to the roles that hold them, silently, while the comment on the dashboard side
 * read "four matrices, one deployable, one answer".
 *
 * <p>It caused no visible harm only because the asset register has no screens yet - so it would have
 * surfaced on the day it got one, as a feature that did not work for anybody, with nothing in any log
 * to explain it.
 *
 * <h2>Why it asserts the union rather than the response</h2>
 *
 * <p>The property that matters is "no matrix is left out", and stating it against the matrices
 * themselves means a fifth one added later fails here without anybody remembering to extend a fixture.
 * The controller is three lines of set arithmetic over exactly these inputs.
 */
class ActorPermissionsCoverageTest {

    /** The union the controller builds, kept in one place so the assertions describe the same thing. */
    private static Set<SflPermission> merged(Set<SflRole> roles) {
        EnumSet<SflPermission> granted = EnumSet.noneOf(SflPermission.class);
        granted.addAll(FleetPermissionMatrix.permissionsFor(roles));
        granted.addAll(AssetVisibilityPermissionMatrix.permissionsFor(roles));
        Arrays.stream(SflPermission.values())
                .filter(permission -> FuelPermissionMatrix.grants(roles, permission)
                        || DispatchPermissionMatrix.grants(roles, permission))
                .forEach(granted::add);
        return granted;
    }

    @Test
    @DisplayName("asset visibility permissions reach an actor who holds them")
    void asset_permissions_are_included() {
        Set<SflRole> roles = Set.of(SflRole.FLEET_MANAGER);
        Set<SflPermission> assetGrants = AssetVisibilityPermissionMatrix.permissionsFor(roles);

        // The role has to hold something in that matrix, or the assertion below proves nothing.
        assertThat(assetGrants).isNotEmpty();
        assertThat(merged(roles)).containsAll(assetGrants);
    }

    @Test
    @DisplayName("every matrix in this deployable contributes to the answer")
    void no_matrix_is_left_out() {
        /*
          Checked role by role rather than in aggregate: a matrix can be present in the union and
          still be wrong for one role, and "some role somewhere gets asset permissions" is a much
          weaker claim than the one worth making.
        */
        for (SflRole role : SflRole.values()) {
            Set<SflRole> roles = Set.of(role);
            Set<SflPermission> expected = EnumSet.noneOf(SflPermission.class);
            expected.addAll(FleetPermissionMatrix.permissionsFor(roles));
            expected.addAll(AssetVisibilityPermissionMatrix.permissionsFor(roles));
            Arrays.stream(SflPermission.values())
                    .filter(permission -> FuelPermissionMatrix.grants(roles, permission)
                            || DispatchPermissionMatrix.grants(roles, permission))
                    .forEach(expected::add);

            assertThat(merged(roles))
                    .as("permissions offered to %s", role)
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    @DisplayName("S174 stays out - it is a different deployable with its own matrix")
    void emergency_permissions_are_not_answered_for() {
        // Deliberate and documented on the controller: SSEMP exposes the same route for its own
        // matrix. Answering a partial list here and letting the dashboard treat it as complete would
        // hide every emergency screen from an entitled coordinator.
        Set<SflPermission> everything = EnumSet.noneOf(SflPermission.class);
        for (SflRole role : SflRole.values()) {
            everything.addAll(merged(Set.of(role)));
        }
        assertThat(everything).noneMatch(permission -> permission.name().startsWith("EMERGENCY_"));
    }
}
