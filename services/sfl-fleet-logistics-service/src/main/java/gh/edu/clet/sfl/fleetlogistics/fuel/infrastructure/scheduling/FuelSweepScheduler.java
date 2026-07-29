package gh.edu.clet.sfl.fleetlogistics.fuel.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Idempotent reconciliation and SLA sweep. Stable anomaly keys prevent duplicate cases. */
@Component @ConditionalOnProperty(name="sfl.fuel.scheduling.enabled",havingValue="true",matchIfMissing=true)
public class FuelSweepScheduler {
    private static final Logger log=LoggerFactory.getLogger(FuelSweepScheduler.class);private final FuelRepository repo;private final FuelApplicationService service;private final JdbcTemplate jdbc;private final Clock clock;
    public FuelSweepScheduler(FuelRepository r,FuelApplicationService s,JdbcTemplate j,Clock c){repo=r;service=s;jdbc=j;clock=c;}
    /** One sweep pass reads a bounded window per site; the next pass picks up whatever it did not reach. */
    private static final int SWEEP_WINDOW=100;

    @Scheduled(fixedDelayString="${sfl.fuel.scheduling.fixed-delay:PT5M}",initialDelayString="${sfl.fuel.scheduling.initial-delay:PT45S}") public void sweep(){for(String site:sites()){ActorContext actor=system(site);
        var received=new FuelRepository.TransactionQuery(List.of(site),site,FuelTransaction.Status.RECEIVED,null,null,null,null,null,null,new FuelRepository.Paging(0,SWEEP_WINDOW,"occurredAt,asc"));
        for(FuelTransaction t:repo.findTransactions(received).content())try{service.reconcile(t.id(),actor,SourceChannel.SYSTEM);}catch(RuntimeException e){log.warn("Fuel reconciliation sweep could not process {}",t.id(),e);}
        for(UUID id:lateReceiptTransactions(site))try{service.reconcile(id,actor,SourceChannel.SYSTEM);}catch(RuntimeException e){log.warn("Fuel receipt sweep could not process {}",id,e);}
        for(MissingLogbook row:missingLogbooks(site))try{service.raiseMissingLogbook(site,row.tripId(),row.vehicleId(),row.driverId(),actor,SourceChannel.SYSTEM);}catch(RuntimeException e){log.warn("Fuel logbook sweep could not process trip {}",row.tripId(),e);}
        // Overdue and still open. `openOnly` replaces the status check this loop used to make in
        // Java, so a case that is already closed or cancelled is never fetched to be skipped.
        var overdue=new FuelRepository.AnomalyQuery(List.of(site),null,null,null,null,null,null,Boolean.TRUE,clock.instant(),null,null,null,new FuelRepository.Paging(0,SWEEP_WINDOW,"slaDueAt,asc"));
        for(FuelAnomalyCase a:repo.findAnomalies(overdue).content())if(a.status()!=FuelAnomalyCase.Status.ESCALATED)try{service.transitionAnomaly(a.id(),"escalate","SLA threshold breached",null,actor,SourceChannel.SYSTEM);}catch(RuntimeException e){log.warn("Fuel SLA sweep could not escalate {}",a.id(),e);}}}
    private List<String> sites(){return jdbc.query("""
        SELECT DISTINCT site_code FROM fleet_logistics.fuel_transactions
        UNION SELECT DISTINCT site_code FROM fleet_logistics.fuel_anomaly_cases
        UNION SELECT DISTINCT site_code FROM fleet_logistics.trips WHERE status='COMPLETED'
        """,(r,n)->r.getString(1));}
    private List<UUID> lateReceiptTransactions(String site){return jdbc.query("""
        SELECT t.id FROM fleet_logistics.fuel_transactions t
        WHERE t.site_code=? AND t.status='RECONCILED' AND t.receipt_evidence_id IS NULL
          AND EXISTS (SELECT 1 FROM fleet_logistics.fuel_policies p WHERE p.site_code=t.site_code
              AND p.status='ACTIVE' AND p.receipt_required=true AND p.effective_from<=t.occurred_at
              AND (p.effective_to IS NULL OR p.effective_to>t.occurred_at)
              AND t.occurred_at + make_interval(hours => p.receipt_grace_hours) <= now())
        LIMIT 100
        """,(r,n)->(UUID)r.getObject(1),site);}
    private List<MissingLogbook> missingLogbooks(String site){return jdbc.query("""
        SELECT t.id,t.vehicle_id,t.driver_id FROM fleet_logistics.trips t
        WHERE t.site_code=? AND t.status='COMPLETED' AND t.vehicle_id IS NOT NULL AND t.driver_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM fleet_logistics.driver_logbooks l WHERE l.trip_id=t.id AND l.status<>'CANCELLED')
        LIMIT 100
        """,(r,n)->new MissingLogbook((UUID)r.getObject(1),(UUID)r.getObject(2),(UUID)r.getObject(3)),site);}
    private ActorContext system(String site){return new ActorContext(new SiteScopedPrincipal("fuel-scheduler","Fuel Scheduler",Set.of(SflRole.SFL_ADMIN),Set.of(site),true),"fuel-sweep-"+clock.instant().toEpochMilli());}
    private record MissingLogbook(UUID tripId,UUID vehicleId,UUID driverId){}
}
