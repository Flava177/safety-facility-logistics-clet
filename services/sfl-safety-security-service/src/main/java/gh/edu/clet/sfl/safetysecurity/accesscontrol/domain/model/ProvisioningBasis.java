package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

/** How an {@link AccessProvisioning} entitlement came to exist - SRS-SFL-S160a-02. A manual grant is
 * the exception, not the norm, and always carries a reason/approver/expiry (see {@link AccessProvisioning}). */
public enum ProvisioningBasis {
    JOINER,
    MOVER,
    LEAVER,
    MANUAL
}
