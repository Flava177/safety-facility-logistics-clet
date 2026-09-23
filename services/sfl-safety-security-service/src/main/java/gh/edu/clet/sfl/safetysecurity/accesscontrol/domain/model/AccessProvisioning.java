package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlErrorCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One person's access entitlement to one zone - SRS-SFL-S160a-02. A {@link ProvisioningBasis#JOINER}/
 * {@link ProvisioningBasis#MOVER}/{@link ProvisioningBasis#LEAVER} row comes from an HRMS/IAM
 * joiner-mover-leaver event; a {@link ProvisioningBasis#MANUAL} row is a human grant and always
 * carries a reason, an approver and an expiry - "the exception, not the norm".
 */
public record AccessProvisioning(UUID id, String siteCode, String personRef, String zoneCode,
        ProvisioningBasis basis, String reason, String approverId, Instant expiresAt, ProvisioningStatus status,
        RecordMetadata metadata) {

    public AccessProvisioning {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(personRef, "personRef");
        require(zoneCode, "zoneCode");
        Objects.requireNonNull(basis, "basis is required");
        reason = blankToNull(reason);
        approverId = blankToNull(approverId);
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (basis == ProvisioningBasis.MANUAL && (reason == null || approverId == null || expiresAt == null)) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_PROVISIONING_MANUAL_GRANT_REQUIRES_REASON);
        }
    }

    public static AccessProvisioning fromJoinerMoverEvent(UUID id, String siteCode, String personRef,
            String zoneCode, ProvisioningBasis basis, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AccessProvisioning(id, siteCode, personRef, zoneCode, basis, null, null, null,
                ProvisioningStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public static AccessProvisioning manualGrant(UUID id, String siteCode, String personRef, String zoneCode,
            String reason, String approverId, Instant expiresAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AccessProvisioning(id, siteCode, personRef, zoneCode, ProvisioningBasis.MANUAL, reason, approverId,
                expiresAt, ProvisioningStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** SRS-SFL-S160a-02: a leaver event triggers automatic revocation across the relevant zones. */
    public AccessProvisioning revoke(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new AccessProvisioning(id, siteCode, personRef, zoneCode, basis, reason, approverId, expiresAt,
                ProvisioningStatus.REVOKED, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isEffective(Instant now) {
        return status == ProvisioningStatus.ACTIVE && (expiresAt == null || now.isBefore(expiresAt));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
