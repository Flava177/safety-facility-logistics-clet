package gh.edu.clet.sfl.facilities.energy.domain;

/**
 * S157-03's data-completeness indicator, as a reader of a KPI sees it.
 *
 * <p>Three values rather than a boolean because "not complete" covers two very different situations: a
 * month-to-date figure with most readings in ({@link #PARTIAL}), and a period with so much missing that
 * the number should not be relied on ({@link #LOW} - the SRS's Incomplete Period). Both are published;
 * neither is ever labelled {@link #COMPLETE}.
 */
public enum CompletenessFlag {
    COMPLETE,
    PARTIAL,
    LOW
}
