package gh.edu.clet.sfl.facilities.maintenance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.FacilityFaultRecord;
import gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.FacilityFaultRepository;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacilityFaultService {

    private final FacilityFaultRepository faults;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public FacilityFaultService(FacilityFaultRepository faults, ServiceOutbox outbox, Clock clock) {
        this.faults = faults;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public FacilityFault report(ReportFacilityFaultCommand command) {
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String faultNumber = "FLT-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        FacilityFault fault = FacilityFault.report(
                id,
                faultNumber,
                command.siteCode(),
                command.locationCode(),
                command.title(),
                command.description(),
                command.category(),
                command.priority(),
                command.reportedBy(),
                now);

        faults.save(FacilityFaultRecord.from(fault));
        outbox.record("ifimp.facility-fault.reported", 1, "FacilityFault", id, fault.siteCode(),
                command.correlationId(), command.reportedBy(), fault);
        return fault;
    }

    @Transactional(readOnly = true)
    public List<FacilityFault> findAll() {
        return faults.findAll().stream().map(FacilityFaultRecord::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public FacilityFault findById(UUID id) {
        return faults.findById(id)
                .map(FacilityFaultRecord::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Facility fault was not found: " + id));
    }
}