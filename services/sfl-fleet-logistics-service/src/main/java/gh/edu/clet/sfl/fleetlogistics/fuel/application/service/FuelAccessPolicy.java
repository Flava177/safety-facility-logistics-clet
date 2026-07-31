package gh.edu.clet.sfl.fleetlogistics.fuel.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.policy.FuelPermissionMatrix;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FuelAccessPolicy {
    public void require(ActorContext actor,SflPermission permission,String site,String resource,String id){
        if(!FuelPermissionMatrix.grants(actor.principal().roles(),permission)||!actor.principal().canAccessSite(site)){
            // Omit an absent id rather than blanking it. `Map.of` rejects nulls, and the `""` this used
            // to substitute slipped past the audit writer's null guard and lost the denial record.
            Map<String,Object> details=new LinkedHashMap<>();
            details.put("requiredPermission",permission.name());
            details.put("siteCode",site);
            details.put("resourceType",resource);
            if(id!=null&&!id.isBlank())details.put("resourceId",id);
            throw new FleetAuthorizationException(details);
        }
    }
    public boolean has(ActorContext actor,SflPermission permission){return FuelPermissionMatrix.grants(actor.principal().roles(),permission);}
    /** Permission-only guard for cross-site integration administration (outbox health/replay), which is not scoped to a single site. */
    public void requirePermission(ActorContext actor,SflPermission permission,String resource){
        if(!FuelPermissionMatrix.grants(actor.principal().roles(),permission)) throw new FleetAuthorizationException(Map.of("requiredPermission",permission.name(),"resourceType",resource));
    }
    public boolean isDriverOnly(ActorContext actor){return actor.principal().hasRole(SflRole.FLEET_DRIVER)&&actor.principal().roles().stream().noneMatch(Set.of(SflRole.FLEET_MANAGER,SflRole.FLEET_LOGISTICS_OFFICER,SflRole.SFL_ADMIN)::contains);}

    /**
     * Refuses a driver-only actor a record they did not create.
     *
     * <p>The {@link #isDriverOnly} narrowing was applied to the logbook <em>list</em> — in SQL, on
     * {@code created_by} — and nowhere else. So a driver holding a colleague's logbook id read it in
     * full through the detail endpoint: journey, route, purpose and passenger notes. **A narrowing
     * only the collection obeys is decorative**, because the record still crosses the boundary, just
     * one at a time rather than in a page. The rule has to be enforced wherever a record is returned.
     *
     * <p>Ownership is {@code createdBy}, deliberately the same column the list query filters on, so
     * the collection and the record cannot disagree about who owns what. Creation is guarded
     * separately and more strictly — against the driver reference on the trip — because "may I create
     * this?" and "is this mine?" are different questions and the first has a stronger answer.
     *
     * <p>Raised as {@link FleetAuthorizationException} rather than an {@code IllegalStateException},
     * so the refusal is the SRS's own 403 envelope and is written to the audit chain as a denial. The
     * ownership checks this joins used to throw {@code IllegalStateException}, which reached the
     * caller as a 500 and left no evidence that anyone had been refused.
     */
    public void requireOwnRecord(ActorContext actor,String createdBy,String site,String resource,String id){
        if(!isDriverOnly(actor)||(createdBy!=null&&createdBy.equalsIgnoreCase(actor.actorId())))return;
        Map<String,Object> details=new LinkedHashMap<>();
        details.put("reason","A driver may act only on their own "+resource+" records");
        details.put("siteCode",site);
        details.put("resourceType",resource);
        if(id!=null&&!id.isBlank())details.put("resourceId",id);
        throw new FleetAuthorizationException(details);
    }
}
