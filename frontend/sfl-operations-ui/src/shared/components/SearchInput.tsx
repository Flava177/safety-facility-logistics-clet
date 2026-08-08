import { useEffect, useId, useRef, useState } from 'react';
import Icon from './Icon';
import { cn } from './cn';
import { FieldShell, controlBase, controlTone } from './fields';

interface SearchInputProps {
  label: string;
  /** The committed value - what the caller actually filters on. */
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  /** Say what the server matches on. An operator cannot guess, and guessing wrong reads as no results. */
  helperText?: string;
  disabled?: boolean;
  className?: string;
  /** Milliseconds of quiet before the value is committed. */
  delay?: number;
}

/** Long enough that a typed word is one request, short enough that the table does not feel stalled. */
const DEFAULT_DELAY_MS = 350;

/**
 * A text filter that waits for the operator to stop typing.
 *
 * <h2>The problem this exists to fix</h2>
 *
 * The registers filter through `TextInput`, which calls back on every keystroke, and every one of
 * those keystrokes is a filter change - which resets the page to zero and re-runs the query. Typing
 * a registration number into the vehicle register issued seven requests, six of them for prefixes
 * nobody wanted, and the table flickered through six wrong answers on the way to the right one.
 * The last response to arrive wins, which is usually but not always the last one sent.
 *
 * <p>Committing on a pause makes that one request. Nothing else about the page changes: the value
 * still goes to the server as the same named parameter, so this is not a new kind of filtering, it
 * is the same filtering asked once instead of once per character.
 *
 * <h2>Why a local draft rather than a controlled input</h2>
 *
 * The field has to stay responsive while the committed value lags behind it, so what is typed lives
 * here and only the settled value goes out. The effect below adopts an externally-changed value -
 * Reset, a filter chip being cleared, a value seeded from the URL - while ignoring our own commit
 * coming back, which would otherwise fight the cursor.
 *
 * <h2>Enter and Escape</h2>
 *
 * Enter commits immediately, because somebody who has finished typing and pressed it should not then
 * wait out a delay that exists for people who have not. Escape clears, which is the shortest path
 * back to an unfiltered table and the one every search field in every other application offers.
 */
export const SearchInput = ({
  label,
  value,
  onChange,
  placeholder,
  helperText,
  disabled,
  className,
  delay = DEFAULT_DELAY_MS,
}: SearchInputProps) => {
  const id = useId();
  const [draft, setDraft] = useState(value);
  const committed = useRef(value);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (value !== committed.current) {
      committed.current = value;
      setDraft(value);
    }
  }, [value]);

  useEffect(
    () => () => {
      if (timer.current) {
        clearTimeout(timer.current);
      }
    },
    [],
  );

  const commit = (next: string) => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    if (next === committed.current) {
      return;
    }
    committed.current = next;
    onChange(next);
  };

  const type = (next: string) => {
    setDraft(next);
    if (timer.current) {
      clearTimeout(timer.current);
    }
    timer.current = setTimeout(() => commit(next), delay);
  };

  return (
    <FieldShell id={id} label={label} helperText={helperText} className={className}>
      <div className="relative">
        <Icon
          name="search"
          size={16}
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-gray-500"
        />
        <input
          id={id}
          type="text"
          role="searchbox"
          value={draft}
          disabled={disabled}
          placeholder={placeholder ?? `Search ${label.toLowerCase()}`}
          aria-describedby={helperText ? `${id}-help` : undefined}
          onChange={(event) => type(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              commit(draft);
            }
            if (event.key === 'Escape' && draft !== '') {
              event.preventDefault();
              setDraft('');
              commit('');
            }
          }}
          onBlur={() => commit(draft)}
          className={cn(controlBase, controlTone(false), 'pl-9', draft !== '' && 'pr-9')}
        />
        {draft !== '' && !disabled && (
          <button
            type="button"
            onClick={() => {
              setDraft('');
              commit('');
            }}
            className={cn(
              'absolute top-1/2 right-1.5 flex h-7 w-7 -translate-y-1/2 items-center justify-center',
              'rounded-md text-lg leading-none text-gray-500 transition-colors',
              'hover:bg-gray-100 hover:text-gray-700',
            )}
          >
            <span aria-hidden="true">&times;</span>
            <span className="sr-only">Clear {label.toLowerCase()}</span>
          </button>
        )}
      </div>
    </FieldShell>
  );
};

export default SearchInput;
