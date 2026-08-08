import { Children, ReactNode } from 'react';
import Button from './Button';
import { FieldLabelSpacer } from './fields';
import { cn } from './cn';

/** One constraint currently in force, in the operator's words rather than the wire's. */
export interface ActiveFilter {
  /** Stable across renders. The field name is the obvious choice. */
  key: string;
  /** What is constrained - "Status", "Site". */
  label: string;
  /** The constraint itself, already humanised: "Confirmed", not "CONFIRMED". */
  value: string;
  onClear: () => void;
}

interface FilterBarProps {
  children: ReactNode;
  onReset?: () => void;
  /** Disables reset when nothing is filtered, so the control tells the truth about state. */
  resetDisabled?: boolean;
  trailing?: ReactNode;
  /**
   * The one-click views for this register, on their own row above the fields.
   *
   * Its own row because it is a different kind of question. The fields below narrow a list; this
   * chooses which list is being looked at, and an operator reaches for it far more often - so it is
   * the thing they should not have to open anything to reach.
   */
  quickFilters?: ReactNode;
  /**
   * The constraints in force, rendered as removable chips.
   *
   * Optional, and a page that omits it behaves exactly as it did before this existed.
   */
  active?: ActiveFilter[];
}

/**
 * The filter row for register screens.
 *
 * <h2>Why this is a wrapping flex row and not a grid</h2>
 *
 * It was a four-column grid, and a grid gives every control the same width whether or not it wants
 * one. That is fine while every child is a labelled field of the same shape, and it falls apart the
 * moment a bar mixes them: a `SiteSelect` carries a label and a `FacetFilter` button does not, so
 * one is two lines tall and the other one, and stretched cells left the short ones floating with a
 * gap of dead space beside each. The booking diary showed it plainly - four controls, three
 * different heights, and the reset button a hand's width from the last of them.
 *
 * <p>So: intrinsic column width, and wrap when the row is full.
 *
 * <h2>Why the row aligns at the top and not at the bottom</h2>
 *
 * It aligned with `items-end`, which lines up the bottom edge of each cell. That is right while
 * every cell is the same shape and wrong as soon as one is not, because a cell's height is label
 * plus control plus *helper text* - so a field carrying a two-line hint sat six or ten pixels higher
 * than its neighbours, and the row of controls the operator actually reads came out ragged. It was
 * visible on five registers: reconciliation, fuel transactions, fuel cards, driver logbooks and scan
 * imports. Two pages had already worked around it by passing `helperText=" "` to force a blank line
 * onto the fields that lacked one, which is the workaround that gives the cause away.
 *
 * <p>Aligning at the top puts every label on one line and therefore every control on one line, and
 * lets the helper text hang below at whatever length it needs. The cost is the case `items-end` was
 * chosen for - a control with no label, which now needs the label's height reserved above it. That
 * is what {@link FieldLabelSpacer} is for, and it is one line in the two components that need it
 * rather than a constraint on every field in the bar.
 *
 * <h2>Chips, and why they are not just decoration</h2>
 *
 * A closed dropdown does not say what it is doing. With four of them a register can be filtered
 * three ways and look untouched, and the operator's next move is to open each one in turn to find
 * out why the table is empty - which is the same work as filtering, done backwards. The chip row
 * states every constraint in force and lets each be dropped where it is read.
 *
 * <p>A page that passes `active` gets "Clear all" in that row and no separate Reset button, because
 * two controls that do the same thing in the same bar is a question the operator has to answer
 * before they can act.
 */
const FilterBar = ({
  children,
  onReset,
  resetDisabled,
  trailing,
  quickFilters,
  active,
}: FilterBarProps) => {
  const chips = active ?? [];
  const showChipRow = chips.length > 0;
  // Reset survives for the pages that have not adopted chips; where chips exist they own clearing.
  const showResetButton = onReset !== undefined && active === undefined;

  return (
    <div className="border-b border-gray-200 px-5 pt-5 pb-4">
      {quickFilters && <div className="mb-4 flex flex-wrap items-center gap-3">{quickFilters}</div>}
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
        <div className="flex flex-1 flex-wrap items-start gap-x-4 gap-y-4">
          {Children.map(children, (child) =>
            child === null || child === undefined || child === false ? null : (
              <div className="w-full sm:w-[13.5rem]">{child}</div>
            ),
          )}
        </div>
        {(trailing || showResetButton) && (
          // The spacer puts these on the control line with the fields. Without it they sit level
          // with the labels, which is where the eye is least likely to look for a button.
          <div className="shrink-0">
            <span className="hidden lg:block">
              <FieldLabelSpacer />
            </span>
            <div className="flex items-center gap-2">
              {trailing}
              {showResetButton && (
                <Button
                  variant="outline"
                  onClick={onReset}
                  disabled={resetDisabled}
                  startIcon="filter"
                >
                  Reset
                </Button>
              )}
            </div>
          </div>
        )}
      </div>

      {showChipRow && (
        <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
          <span className="text-theme-xs font-medium tracking-wide text-gray-500 uppercase">
            Filtered by
          </span>
          {chips.map((chip) => (
            <span
              key={chip.key}
              className={cn(
                'inline-flex items-center gap-1.5 rounded-full border border-teal-200 bg-teal-50',
                'py-1 pr-1 pl-2.5 text-theme-xs text-teal-900',
              )}
            >
              <span className="font-medium">{chip.label}:</span>
              <span>{chip.value}</span>
              <button
                type="button"
                onClick={chip.onClear}
                className={cn(
                  'flex h-5 w-5 items-center justify-center rounded-full leading-none',
                  'text-teal-700 transition-colors hover:bg-teal-200 hover:text-teal-900',
                )}
              >
                <span aria-hidden="true">&times;</span>
                <span className="sr-only">
                  Remove the {chip.label.toLowerCase()} filter, {chip.value}
                </span>
              </button>
            </span>
          ))}
          {onReset && (
            <button
              type="button"
              onClick={onReset}
              className="ml-1 text-theme-xs font-medium text-gray-600 underline-offset-2 transition-colors hover:text-gray-900 hover:underline"
            >
              Clear all
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default FilterBar;
