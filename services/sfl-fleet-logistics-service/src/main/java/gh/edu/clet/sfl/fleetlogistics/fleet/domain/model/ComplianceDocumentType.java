package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Vehicle compliance document types recognised in the Ghana operating context.
 *
 * <p>{@link #isMandatory()} marks the documents a vehicle must hold to be road-legal; a missing
 * mandatory document raises the {@code COMPLIANCE_DOCUMENT_MISSING} readiness blocker.
 */
public enum ComplianceDocumentType {
    ROADWORTHINESS_CERTIFICATE(true),
    INSURANCE_CERTIFICATE(true),
    VEHICLE_REGISTRATION(true),
    DVLA_INSPECTION_REPORT(false),
    COMMERCIAL_PERMIT(false),
    EMISSIONS_CERTIFICATE(false),
    FIRE_EXTINGUISHER_CERTIFICATE(false);

    private final boolean mandatory;

    ComplianceDocumentType(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
