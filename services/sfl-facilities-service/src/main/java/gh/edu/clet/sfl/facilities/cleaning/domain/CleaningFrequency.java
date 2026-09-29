package gh.edu.clet.sfl.facilities.cleaning.domain;

/**
 * How often a routine schedule recurs - SRS-SFL-S169-01 ("configurable by site, space type and
 * frequency").
 *
 * <p>Three shapes cover what a janitorial rota actually says. "Every day at 07:00 and 16:00" is
 * {@link #DAILY}; "Fridays, deep clean" is {@link #WEEKLY}; "Monday, Wednesday and Friday after the
 * lectures" is {@link #SPECIFIC_WEEKDAYS}. Monthly and ad-hoc patterns are not modelled: a monthly
 * deep clean is a weekly schedule nobody should be asked to trust, and a one-off is an ad-hoc task.
 */
public enum CleaningFrequency {

    /** Every day, at each configured time of day. Carries no days of the week. */
    DAILY,
    /** Once a week, on exactly one named day. */
    WEEKLY,
    /** On each of one or more named days. */
    SPECIFIC_WEEKDAYS
}
