import { cn } from './cn';

export interface QuickFilterOption<T extends string> {
  value: T;
  label: string;
  /** Shown as a badge. Omit when the page cannot count without a round trip. */
  count?: number;
}

interface QuickFiltersProps<T extends string> {
  /** Names the axis for a screen reader; not rendered, because the options say it. */
  label: string;
  options: readonly QuickFilterOption<T>[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}

/**
 * The two or three questions a register is actually asked, as one click each.
 *
 * <h2>Why this is not the same as another dropdown</h2>
 *
 * Every filter on these screens costs the same three actions - open, read, choose - whether it is
 * the question asked once a month or the one asked every morning. "Show me what is still open" is
 * the second kind, and putting it behind a closed control prices it as the first.
 *
 * <p>These are mutually exclusive by construction: a register is being looked at one way at a time,
 * and the selected pill says which. That is the difference from {@link FacetFilter}, which is for
 * unions the operator composes themselves and rightly costs a moment's thought.
 *
 * <h2>Radio semantics, not tabs</h2>
 *
 * `role="radiogroup"` rather than `role="tablist"`, because nothing here swaps a panel - the same
 * table is being narrowed. Arrow keys move between options and selection follows focus, which is
 * what a radio group does and what somebody who has used one anywhere else will expect.
 */
export function QuickFilters<T extends string>({
  label,
  options,
  value,
  onChange,
  className,
}: QuickFiltersProps<T>) {
  const move = (from: number, step: number) => {
    const next = options[(from + step + options.length) % options.length];
    if (next) {
      onChange(next.value);
    }
  };

  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cn('inline-flex flex-wrap items-center gap-1 rounded-lg bg-gray-100 p-1', className)}
    >
      {options.map((option, index) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(option.value)}
            onKeyDown={(event) => {
              if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
                event.preventDefault();
                move(index, 1);
              }
              if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
                event.preventDefault();
                move(index, -1);
              }
            }}
            className={cn(
              'flex items-center gap-1.5 rounded-md px-3 py-1.5 text-theme-sm font-medium transition-colors',
              selected
                ? 'bg-white text-gray-900 shadow-theme-xs'
                : 'text-gray-600 hover:text-gray-900',
            )}
          >
            {option.label}
            {option.count !== undefined && (
              <span
                className={cn(
                  'rounded px-1.5 py-0.5 text-theme-xs tabular-nums',
                  selected ? 'bg-teal-50 text-teal-800' : 'bg-gray-200 text-gray-600',
                )}
              >
                {option.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

export default QuickFilters;
