import { useEffect, useMemo, useRef, useState } from 'react';
import Icon from './Icon';
import { cn } from './cn';

export interface FacetOption {
  value: string;
  label: string;
  /** How many rows carry this value. Omitted when the caller cannot count without a round trip. */
  count?: number;
  disabled?: boolean;
}

interface FacetFilterProps {
  label: string;
  options: FacetOption[];
  /** Selected values. Empty means no constraint, which is not the same as none selected. */
  selected: string[];
  onChange: (selected: string[]) => void;
  /** Shows a search box once the list is long enough to need one. */
  searchable?: boolean;
  disabled?: boolean;
  className?: string;
}

/**
 * A multi-select filter with counts.
 *
 * <h2>What this replaces, and why it is better rather than merely newer</h2>
 *
 * The registers filtered through single-value `Select` dropdowns: one status, one purpose, one type.
 * That forces a real question — "show me everything that needs attention" — to be asked several
 * times, because *overdue* and *escalated* and *blocked* are three separate filter runs, and the
 * operator has to hold the union in their head across three screens of results.
 *
 * <h2>Counts are the point, not decoration</h2>
 *
 * A facet whose options carry no counts is a dropdown with checkboxes. The count is what makes it a
 * facet: it tells the operator that filtering to `CRITICAL` will return four rows **before** they
 * spend a round trip finding out, and it tells them a value exists at all. A zero-count option is
 * shown rather than hidden, because "no critical faults" is an answer and an absent option is not.
 *
 * <h2>Empty means unconstrained</h2>
 *
 * Selecting nothing returns everything. The alternative — treating no selection as "match nothing" —
 * is defensible and wrong here, because it makes the resting state of every register an empty table.
 * The button label says which it is: "Any status" when unconstrained, the value when one is chosen,
 * "2 selected" beyond that.
 */
const FacetFilter = ({
  label,
  options,
  selected,
  onChange,
  searchable,
  disabled,
  className,
}: FacetFilterProps) => {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onEscape);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onEscape);
    };
  }, [open]);

  const visible = useMemo(() => {
    if (!query.trim()) {
      return options;
    }
    const wanted = query.trim().toLowerCase();
    return options.filter((option) => option.label.toLowerCase().includes(wanted));
  }, [options, query]);

  const toggle = (value: string) => {
    onChange(
      selected.includes(value)
        ? selected.filter((entry) => entry !== value)
        : [...selected, value],
    );
  };

  const summary = () => {
    if (selected.length === 0) {
      return `Any ${label.toLowerCase()}`;
    }
    if (selected.length === 1) {
      return options.find((option) => option.value === selected[0])?.label ?? selected[0];
    }
    return `${selected.length} selected`;
  };

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        aria-haspopup="listbox"
        // The count rides in the accessible name, so a screen-reader user is told how many
        // constraints are active without opening the panel.
        aria-label={`${label}: ${summary()}`}
        className={cn(
          'flex h-10 items-center gap-2 rounded-lg border bg-white px-3 text-theme-sm transition-colors',
          'disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-500',
          selected.length > 0
            ? 'border-teal-500 text-gray-900'
            : 'border-gray-300 text-gray-700 hover:border-gray-400',
        )}
      >
        <Icon name="filter" size={15} className="shrink-0 text-gray-500" aria-hidden="true" />
        <span className="font-medium">{label}</span>
        {selected.length > 0 && (
          <>
            <span aria-hidden="true" className="h-4 w-px bg-gray-300" />
            <span className="rounded bg-teal-50 px-1.5 py-0.5 text-theme-xs font-semibold text-teal-800">
              {summary()}
            </span>
          </>
        )}
        <Icon
          name="chevron-down"
          size={15}
          className={cn('shrink-0 text-gray-500 transition-transform', open && 'rotate-180')}
          aria-hidden="true"
        />
      </button>

      {open && (
        <div className="absolute z-99999 mt-1.5 w-64 rounded-lg border border-gray-200 bg-white py-1.5 shadow-theme-lg">
          {searchable && (
            <div className="border-b border-gray-100 px-2 pb-1.5">
              <input
                type="text"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={`Search ${label.toLowerCase()}`}
                aria-label={`Search ${label.toLowerCase()}`}
                className="h-8 w-full rounded-md border border-gray-200 px-2 text-theme-sm outline-none focus:border-teal-600"
              />
            </div>
          )}

          <ul role="listbox" aria-multiselectable="true" className="custom-scrollbar max-h-64 overflow-y-auto py-1">
            {visible.map((option) => {
              const checked = selected.includes(option.value);
              return (
                <li key={option.value}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={checked}
                    disabled={option.disabled}
                    onClick={() => toggle(option.value)}
                    className={cn(
                      'flex w-full items-center gap-2.5 px-3 py-1.5 text-left text-theme-sm transition-colors',
                      'hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-400',
                    )}
                  >
                    <span
                      aria-hidden="true"
                      className={cn(
                        'flex h-4 w-4 shrink-0 items-center justify-center rounded border',
                        checked ? 'border-teal-600 bg-teal-600 text-white' : 'border-gray-300',
                      )}
                    >
                      {checked && <Icon name="check-circle" size={12} />}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-gray-800">{option.label}</span>
                    {option.count !== undefined && (
                      // Tabular figures so the counts form a column rather than a ragged edge.
                      <span className="shrink-0 font-mono text-theme-xs text-gray-500 tabular-nums">
                        {option.count}
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
            {visible.length === 0 && (
              <li className="px-3 py-2 text-theme-sm text-gray-500">Nothing matches.</li>
            )}
          </ul>

          {selected.length > 0 && (
            <div className="border-t border-gray-100 px-2 pt-1.5">
              <button
                type="button"
                onClick={() => onChange([])}
                className="w-full rounded-md px-2 py-1.5 text-center text-theme-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
              >
                Clear {label.toLowerCase()}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default FacetFilter;
