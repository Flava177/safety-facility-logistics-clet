package gh.edu.clet.sfl.fleetlogistics.fuel.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelCard;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fuel-card register — SRS-SFL-S168fuel-04, which had no implementation at all.
 *
 * <p>{@code fuel_transactions.masked_card_reference} has been captured since V10 as a bare string.
 * The platform could tell you which card was used and could answer nothing about it: whether the card
 * is ours, whether it is still active, whether it belongs to the vehicle that was filled. The C9
 * mapping gives S168_fuel "anti-fraud controls" as its purpose, and the commonest fuel fraud is a card
 * assigned to one vehicle filling another — which needs a register to be visible at all.
 *
 * <p><strong>Issuing is a manager's act.</strong> A fuel card is a payment instrument, so
 * {@code FUEL_CARD_MANAGE} sits with {@code FLEET_MANAGER} and {@code SFL_ADMIN}; the logistics officer
 * who runs fuel operations day to day gets {@code FUEL_CARD_READ} and cannot issue one. A driver gets
 * neither — the register says which cards exist and who holds them, which is not a driver's business.
 */
@Service
public class FuelCardService {

    private final FuelRepository repository;
    private final FuelAccessPolicy access;
    private final AuditPort audit;
    private final Clock clock;

    public FuelCardService(FuelRepository repository, FuelAccessPolicy access, AuditPort audit, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    public record IssueCard(String siteCode, String maskedReference, String provider, UUID vehicleId,
            UUID driverId, LocalDate issuedOn, LocalDate expiresOn, BigDecimal dailyLimit,
            BigDecimal monthlyLimit, BigDecimal perTransactionLimit, String notes, ActorContext actor,
            SourceChannel channel) {
    }

    @Transactional
    public FuelCard issue(IssueCard command) {
        access.require(command.actor(), SflPermission.FUEL_CARD_MANAGE, command.siteCode(), "FuelCard", null);
        SiteCode site = SiteCode.of(command.siteCode());
        // A live card already answering to this reference means somebody is re-issuing without
        // cancelling, and the partial unique index would refuse it anyway — better to say why here.
        repository.findLiveCardByReference(site.value(), command.maskedReference()).ifPresent(existing -> {
            throw new IllegalStateException("A live fuel card already exists for reference "
                    + command.maskedReference() + " at " + site.value()
                    + ". Cancel it before issuing a replacement.");
        });

        FuelCard card = FuelCard.issue(UUID.randomUUID(), site, command.maskedReference(), command.provider(),
                command.vehicleId(), command.driverId(),
                command.issuedOn() == null ? today() : command.issuedOn(), command.expiresOn(),
                command.dailyLimit(), command.monthlyLimit(), command.perTransactionLimit(), command.notes(),
                RecordMetadata.createdBy(command.actor().actorId(), clock.instant(), command.channel(),
                        command.actor().correlationId()));

        FuelCard saved = repository.saveCard(card);
        audit.record(command.actor(), command.channel(), site, AuditAction.CREATE, "FuelCard",
                saved.id().toString(), null, saved);
        return saved;
    }

    public record TransitionCard(UUID cardId, String action, String reason, UUID vehicleId, UUID driverId,
            ActorContext actor, SourceChannel channel) {
    }

    /**
     * Assignment and lifecycle, in one place.
     *
     * <p>One entry point rather than five, because every one of these is the same shape — read, check,
     * move, audit — and five near-identical methods is where the audit call gets forgotten on the
     * sixth.
     */
    @Transactional
    public FuelCard transition(TransitionCard command) {
        FuelCard before = repository.findCard(command.cardId())
                .orElseThrow(() -> RecordNotFoundException.of("FuelCard", command.cardId()));
        access.require(command.actor(), SflPermission.FUEL_CARD_MANAGE, before.siteCode().value(), "FuelCard",
                command.cardId().toString());

        RecordMetadata metadata = before.metadata().modifiedBy(command.actor().actorId(), clock.instant(),
                command.channel(), command.actor().correlationId());
        FuelCard after = switch (command.action()) {
            case "assign" -> before.assignTo(command.vehicleId(), command.driverId(), metadata);
            case "suspend" -> before.suspend(requireReason(command.reason(), "suspend"), metadata);
            case "reinstate" -> before.reinstate(metadata);
            case "cancel" -> before.cancel(requireReason(command.reason(), "cancel"), metadata);
            default -> throw new IllegalArgumentException("Unknown fuel card action '" + command.action()
                    + "'. Supported: assign, suspend, reinstate, cancel.");
        };

        FuelCard saved = repository.saveCard(after);
        audit.record(command.actor(), command.channel(), saved.siteCode(), auditActionFor(command.action()),
                "FuelCard", saved.id().toString(), before, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public FuelCard card(UUID id, ActorContext actor) {
        FuelCard card = repository.findCard(id)
                .orElseThrow(() -> RecordNotFoundException.of("FuelCard", id));
        access.require(actor, SflPermission.FUEL_CARD_READ, card.siteCode().value(), "FuelCard", id.toString());
        return card;
    }

    @Transactional(readOnly = true)
    public FuelRepository.FuelPage<FuelCard> cards(String site, FuelCard.Status status, UUID vehicleId,
            UUID driverId, String maskedReference, FuelRepository.Paging paging, ActorContext actor) {
        access.require(actor, SflPermission.FUEL_CARD_READ, site, "FuelCard", null);
        return repository.findCards(new FuelRepository.CardQuery(java.util.List.of(SiteCode.of(site).value()),
                status, vehicleId, driverId, maskedReference, paging));
    }

    private static AuditAction auditActionFor(String action) {
        return switch (action) {
            case "assign" -> AuditAction.ASSIGN;
            case "cancel" -> AuditAction.CANCEL;
            default -> AuditAction.STATE_TRANSITION;
        };
    }

    /** Suspending or cancelling somebody's card without a reason is an argument at a filling station. */
    private static String requireReason(String reason, String action) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to " + action + " a fuel card");
        }
        return reason.strip();
    }

    private LocalDate today() {
        return clock.instant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
