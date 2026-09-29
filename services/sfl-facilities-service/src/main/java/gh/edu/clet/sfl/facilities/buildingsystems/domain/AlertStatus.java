package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * Whether the condition an alert records is still present.
 *
 * <p>Independent of the linked work order on purpose. A temperature that came back into range has
 * cleared; the compressor that caused it may still need replacing, and the work order stays open in S153
 * until a technician closes it. A second breach on the asset while that work order is open is linked to
 * this alert (S156-02) and makes it {@link #ACTIVE} again, rather than raising a duplicate.
 */
public enum AlertStatus {
    ACTIVE,
    CLEARED
}
