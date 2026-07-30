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
}
