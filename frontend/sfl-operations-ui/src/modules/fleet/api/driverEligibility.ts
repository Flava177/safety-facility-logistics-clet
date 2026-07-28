import dayjs from 'dayjs';
import { formatDate, todayIsoDate } from 'shared/components/format';
import { DriverResponse } from './dto';

/**
 * Why a driver is not eligible, in the operator's words.
 *
 * The driver endpoints return an eligibility status but no blocker list — only the assessment
 * endpoint carries one, and the register never calls it. A bare "Ineligible" chip therefore reads
 * as a defect rather than a finding, so these reasons are derived from the record itself: licence
 * expiry, medical clearance and lifecycle are the only inputs the response supports, and nothing
 * here asserts a rule those three fields cannot evidence.
 */

/** Whole days from today to `isoDate`; negative once the date has passed. */
const daysUntil = (isoDate: string): number => dayjs(isoDate).diff(dayjs(todayIsoDate()), 'day');

const days = (count: number): string => `${count} day${count === 1 ? '' : 's'}`;

/** Anything expiring further out than this is routine planning, not a reason. */
const HORIZON_DAYS = 30;

/** "expired 28 days ago (30 Jun 2026)" / "expires in 12 days (9 Aug 2026)", or nothing. */
const describeExpiry = (subject: string, isoDate: string, remaining: number): string | null => {
  if (remaining < 0) {
    return `${subject} expired ${days(Math.abs(remaining))} ago, on ${formatDate(isoDate)}.`;
  }
  if (remaining === 0) {
    return `${subject} expires today, ${formatDate(isoDate)}.`;
  }
  return remaining <= HORIZON_DAYS
    ? `${subject} expires in ${days(remaining)}, on ${formatDate(isoDate)}.`
    : null;
};

const describeLifecycle = (driver: DriverResponse): string | null => {
  switch (driver.lifecycleStatus) {
    case 'SUSPENDED':
      return driver.suspensionReason
        ? `Driver is suspended: ${driver.suspensionReason}`
        : 'Driver is suspended.';
    case 'INACTIVE':
      return 'Driver is inactive, so cannot be assigned.';
    case 'ARCHIVED':
      return 'Driver is archived, so cannot be assigned.';
    default:
      return null;
  }
};

/**
 * The reasons that actually apply, most operationally specific first.
 *
 * Lifecycle comes last on purpose: it already has its own chip on every screen that shows this,
 * whereas a lapsed licence or medical clearance is otherwise invisible until an assignment fails.
 * An eligible driver with everything in date yields an empty list, which callers render as nothing.
 */
export const describeDriverEligibility = (driver: DriverResponse): string[] => {
  const reasons: string[] = [];

  const licence = describeExpiry(
    'Licence',
    driver.licenceExpiresOn,
    driver.daysUntilLicenceExpiry,
  );
  if (licence) {
    reasons.push(licence);
  }

  if (driver.medicalClearanceExpiresOn === null) {
    reasons.push('Medical clearance is not recorded.');
  } else {
    const medical = describeExpiry(
      'Medical clearance',
      driver.medicalClearanceExpiresOn,
      daysUntil(driver.medicalClearanceExpiresOn),
    );
    if (medical) {
      reasons.push(medical);
    }
  }

  const lifecycle = describeLifecycle(driver);
  if (lifecycle) {
    reasons.push(lifecycle);
  }

  return reasons;
};
