import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DateField } from './DateField';

/**
 * The year is reachable in one gesture.
 *
 * <p>This is the whole point of the change: flatpickr's own header offers a static month and a year
 * stepper, so landing on a date a few years out is a run of clicks. These assertions are about the
 * *controls* rather than the value - a stepper arrives at 2031 too, eventually, which is exactly the
 * complaint.
 */

/** The dropdowns live in the calendar, which flatpickr appends to the document, not to the field. */
const calendar = () => document.querySelector('.flatpickr-calendar');
const yearSelect = () =>
  calendar()?.querySelector<HTMLSelectElement>('.flatpickr-yearDropdown-years') ?? null;
const monthSelect = () =>
  calendar()?.querySelector<HTMLSelectElement>('.flatpickr-monthDropdown-months') ?? null;

const yearsOffered = () => Array.from(yearSelect()?.options ?? []).map((option) => option.value);

describe('DateField', () => {
  it('offers the year as a dropdown rather than a stepper', () => {
    render(<DateField label="Expires on" value="2026-08-08" onChange={vi.fn()} />);

    expect(yearSelect()).not.toBeNull();
    expect(monthSelect()).not.toBeNull();
    expect(yearSelect()?.value).toBe('2026');
  });

  it('reaches years either side of the current one without a bound', () => {
    render(<DateField label="Issued on" value="2026-08-08" onChange={vi.fn()} />);

    const offered = yearsOffered();
    // A certificate issued several years ago and one expiring several years ahead are both the
    // ordinary case on the compliance form, so both directions have to be in the list.
    expect(offered).toContain('2016');
    expect(offered).toContain('2036');
  });

  it('selecting a year moves the calendar to it', async () => {
    render(<DateField label="Expires on" value="2026-08-08" onChange={vi.fn()} />);

    await userEvent.selectOptions(yearSelect() as HTMLSelectElement, '2031');

    expect(yearSelect()?.value).toBe('2031');
    expect(calendar()?.querySelector('.cur-year')).toHaveValue(2031);
  });

  it('does not offer years the field itself refuses', () => {
    render(
      <DateField
        label="Next due on"
        value="2026-08-08"
        minDate="2026-01-01"
        maxDate="2028-12-31"
        onChange={vi.fn()}
      />,
    );

    expect(yearsOffered()).toEqual(['2026', '2027', '2028']);
  });

  it('keeps a stored date reachable even when it predates the minimum', () => {
    // A record loaded with an out-of-range date must still show its own year. Dropping it would
    // make the control disagree with the value beside it.
    render(
      <DateField label="Issued on" value="2011-04-02" minDate="2026-01-01" onChange={vi.fn()} />,
    );

    expect(yearsOffered()).toContain('2011');
  });

  it('still shows the human date in the visible input', () => {
    render(<DateField label="Issued on" value="2026-08-08" onChange={vi.fn()} />);

    expect(screen.getByLabelText('Issued on')).toHaveValue('08 Aug 2026');
  });
});
