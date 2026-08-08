import type { Instance } from 'flatpickr/dist/types/instance';

/**
 * Replaces flatpickr's year stepper with a dropdown.
 *
 * <h2>Why the stepper had to go</h2>
 *
 * <p>flatpickr renders the year as a number input flanked by up/down arrows. It is typeable, but
 * nothing about it says so: what it looks like is a pair of arrows, so that is what people use, and
 * reaching a certificate issued four years ago means forty-eight clicks on a control that moves one
 * month at a time until you notice the year arrows, then four more. Compliance registration is the
 * screen where this bites hardest - an issue date is usually in the past and an expiry date is
 * usually years ahead - but it is the same picker everywhere, so it was the same problem everywhere.
 *
 * <p>A dropdown states the range and lands on any year in it in one gesture. The month is handled by
 * flatpickr's own `monthSelectorType: 'dropdown'`; this supplies the half it has no option for.
 *
 * <h2>Hidden, not removed</h2>
 *
 * <p>The original wrapper stays in the DOM with `display: none`. flatpickr writes the current year
 * into that input as part of its own redraw and reads it back when the arrows or a date selection
 * move the year; removing the node makes `changeYear` operate on an element that is no longer there.
 * Hiding it keeps flatpickr's internals intact and costs nothing.
 */

/** How far the list reaches when the field itself does not say. */
const YEARS_BEHIND = 15;
const YEARS_AHEAD = 15;

export interface YearBounds {
  /** `YYYY-MM-DD`, or `YYYY-MM-DDTHH:mm`. Absent means unbounded. */
  minDate?: string;
  maxDate?: string;
}

export interface YearSelect {
  /** Rebuilds the option list. Called when the field's own min/max change after mounting. */
  refresh: (bounds: YearBounds) => void;
  destroy: () => void;
}

const yearOf = (isoDate: string | undefined): number | null => {
  if (!isoDate) {
    return null;
  }
  const year = Number(isoDate.slice(0, 4));
  return Number.isFinite(year) && year > 0 ? year : null;
};

/**
 * The years to offer.
 *
 * <p>The field's own bounds win where it has them - a picker that refuses everything before today
 * should not offer 2011 - and the default window applies where it does not. The currently selected
 * year is always included even when it falls outside both: a record loaded with an old date must
 * still show that date rather than silently snapping to the nearest offered one.
 */
const yearRange = (bounds: YearBounds, selectedYear: number): { first: number; last: number } => {
  const thisYear = new Date().getFullYear();
  const min = yearOf(bounds.minDate);
  const max = yearOf(bounds.maxDate);
  return {
    first: Math.min(min ?? thisYear - YEARS_BEHIND, selectedYear),
    last: Math.max(max ?? thisYear + YEARS_AHEAD, selectedYear),
  };
};

export const attachYearSelect = (instance: Instance, bounds: YearBounds): YearSelect | null => {
  const yearInput = instance.currentYearElement;
  const wrapper = yearInput?.parentElement;
  if (!yearInput || !wrapper?.parentElement) {
    return null;
  }

  wrapper.style.display = 'none';

  const select = document.createElement('select');
  select.className = 'flatpickr-yearDropdown-years';
  select.setAttribute('aria-label', 'Year');
  wrapper.parentElement.insertBefore(select, wrapper);

  let current: YearBounds = bounds;

  const populate = () => {
    const selected = instance.currentYear;
    const { first, last } = yearRange(current, selected);
    // Rebuilt wholesale rather than diffed: the list is at most a few dozen options and it changes
    // only when a bound changes, which is rare and never mid-interaction.
    select.replaceChildren();
    for (let year = first; year <= last; year += 1) {
      const option = document.createElement('option');
      option.value = String(year);
      option.textContent = String(year);
      select.appendChild(option);
    }
    select.value = String(selected);
  };

  const sync = () => {
    if (select.value !== String(instance.currentYear)) {
      // A year reached by the month arrows rolling over, or by loading a value, has to move the
      // dropdown too - otherwise the calendar and the control naming it disagree.
      if (!Array.from(select.options).some((option) => option.value === String(instance.currentYear))) {
        populate();
        return;
      }
      select.value = String(instance.currentYear);
    }
  };

  const onChange = () => instance.changeYear(Number(select.value));

  select.addEventListener('change', onChange);
  instance.config.onYearChange.push(sync);
  instance.config.onValueUpdate.push(sync);

  populate();

  return {
    refresh: (next: YearBounds) => {
      current = next;
      populate();
    },
    destroy: () => {
      select.removeEventListener('change', onChange);
      select.remove();
      wrapper.style.display = '';
    },
  };
};
