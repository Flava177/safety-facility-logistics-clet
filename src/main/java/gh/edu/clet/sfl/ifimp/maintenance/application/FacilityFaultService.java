package gh.edu.clet.sfl.ifimp.maintenance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import gh.edu.clet.sfl.ifimp.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.ifimp.maintenance.infrastructure.FacilityFaultRecord;
import gh.edu.clet.sfl.ifimp.maintenance.infrastructure.FacilityFaultRepository;
import gh.edu.clet.sfl.platform.persistence.AuditEventRecord;
import gh.edu.clet.sfl.platform.persistence.AuditEventRepository;
import gh.edu.clet.sfl.platform.persistence.OutboxMessageRecord;
import gh.edu.clet.sfl.platform.persistence.OutboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class FacilityFaultService {

    private final FacilityFaultRepository faults;
    private final AuditEventRepository auditEvents;
    private final OutboxMessageRepository outboxMessages;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FacilityFaultService(
            FacilityFaultRepository faults,
            AuditEventRepository auditEvents,
            OutboxMessageRepository outboxMessages,
            ObjectMapper objectMapper,
            Clock clock) {
        this.faults = faults;
        this.auditEvents = auditEvents;
        this.outboxMessages = outboxMessages;
        this.objectMapper = objectMapper;
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

        String payload = writeJson(fault);
        faults.save(FacilityFaultRecord.from(fault));
        auditEvents.save(new AuditEventRecord(
                UUID.randomUUID(), "ifimp.facility-fault.reported", "FacilityFault", id,
                command.reportedBy(), "sfl-api", command.correlationId(), now, payload));
        outboxMessages.save(new OutboxMessageRecord(
                UUID.randomUUID(), "ifimp.facility-fault.reported.v1", id,
                command.correlationId(), payload, now));
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the integration event", exception);
        }
    }
}
