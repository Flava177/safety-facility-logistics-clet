import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import FacetFilter from './FacetFilter';
import FilterBar from './FilterBar';
import { TextInput } from './fields';

/**
 * The chip row, and the rule that a page which does not use it is unchanged.
 *
 * <p>Thirty-six registers pass `children` and `onReset` and nothing else. The second test is the
 * one that protects them: adding chips must not have quietly taken the Reset button away from the
 * pages that still rely on it.
 */

describe('FilterBar active filters', () => {
  it('names each constraint and drops the one that is dismissed', async () => {
    const clearStatus = vi.fn();
    render(
      <FilterBar
        active={[
          { key: 'status', label: 'Status', value: 'Confirmed', onClear: clearStatus },
          { key: 'purpose', label: 'Purpose', value: 'Examination', onClear: vi.fn() },
        ]}
      >
        <div>controls</div>
      </FilterBar>,
    );

    expect(screen.getByText('Confirmed')).toBeInTheDocument();
    expect(screen.getByText('Examination')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /remove the status filter/i }));

    expect(clearStatus).toHaveBeenCalledTimes(1);
  });

  it('offers one way to clear everything, not two', () => {
    render(
      <FilterBar
        onReset={vi.fn()}
        active={[{ key: 'status', label: 'Status', value: 'Confirmed', onClear: vi.fn() }]}
      >
        <div>controls</div>
      </FilterBar>,
    );

    // "Clear all" lives in the chip row; the Reset button would be the same action twice.
    expect(screen.getByRole('button', { name: /clear all/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /reset/i })).not.toBeInTheDocument();
  });

  it('leaves a page that has not adopted chips exactly as it was', () => {
    render(
      <FilterBar onReset={vi.fn()}>
        <div>controls</div>
      </FilterBar>,
    );

    expect(screen.getByRole('button', { name: /reset/i })).toBeInTheDocument();
    expect(screen.queryByText('Filtered by')).not.toBeInTheDocument();
  });

  it('shows no chip row when nothing is filtered', () => {
    render(
      <FilterBar active={[]}>
        <div>controls</div>
      </FilterBar>,
    );

    expect(screen.queryByText('Filtered by')).not.toBeInTheDocument();
  });
});

/**
 * The controls sit on one line whatever hangs underneath them.
 *
 * <p>jsdom does no layout, so the assertion cannot be "these are at the same y". What it can pin is
 * the property that decides it: the row aligns at the top, so a helper line under one field pushes
 * nothing sideways. Bottom alignment is what made the five registers ragged, and it is the thing
 * that would come back.
 */
describe('FilterBar alignment', () => {
  const fieldRow = (container: HTMLElement) =>
    container.querySelector('div.flex.flex-1.flex-wrap');

  it('aligns the field row at the top, not the bottom', () => {
    const { container } = render(
      <FilterBar>
        <TextInput label="Site code" value="CLET-HQ" onChange={vi.fn()} />
        <TextInput
          label="Masked reference"
          value=""
          onChange={vi.fn()}
          helperText="Contains-match on the provider-masked reference."
        />
      </FilterBar>,
    );

    const row = fieldRow(container);
    expect(row).toHaveClass('items-start');
    expect(row).not.toHaveClass('items-end');
  });

  it('gives every field the same cell width so the columns line up', () => {
    const { container } = render(
      <FilterBar>
        <TextInput label="Site code" value="" onChange={vi.fn()} />
        <TextInput label="Status" value="" onChange={vi.fn()} helperText="Two lines of hint text." />
      </FilterBar>,
    );

    const cells = Array.from(fieldRow(container)?.children ?? []);
    expect(cells).toHaveLength(2);
    cells.forEach((cell) => expect(cell).toHaveClass('sm:w-[13.5rem]'));
  });

  it('reserves the label line above a control that has no label', () => {
    // FacetFilter and the Reset button both sit in a row of labelled fields. Without the reserved
    // line they float level with the labels instead of with the controls.
    const { container } = render(
      <FilterBar onReset={vi.fn()}>
        <FacetFilter label="Status" options={[{ value: 'OPEN', label: 'Open' }]} selected={[]} onChange={vi.fn()} />
      </FilterBar>,
    );

    // One for the facet button, one for the trailing Reset block.
    expect(container.querySelectorAll('span[aria-hidden="true"].mb-2.block')).toHaveLength(2);
  });
});
