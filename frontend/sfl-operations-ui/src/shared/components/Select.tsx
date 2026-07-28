import { useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import Icon from './Icon';
import { cn } from './cn';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

interface SelectProps {
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  error?: boolean;
  onBlur?: () => void;
  id?: string;
  describedBy?: string;
  className?: string;
}

/**
 * The console's dropdown.
 *
 * A native `<select>` was correct but felt abrupt: the operating system paints the list instantly,
 * in its own font, ignoring everything around it. This is a listbox that opens in the page — it
 * eases in, matches the console's type and spacing, and marks the current choice.
 *
 * It keeps everything the native control gave away for free, because a dropdown that cannot be
 * driven from the keyboard is a dropdown half the operators cannot use: Up/Down move through
 * options, Home/End jump to the ends, Enter or Space commits, Escape closes and returns focus to
 * the trigger, and typing letters jumps to the first option starting with them. Roles and
 * `aria-activedescendant` follow the ARIA listbox pattern so a screen reader announces the same
 * thing a sighted user sees.
 */
const Select = ({
  value,
  onChange,
  options,
  placeholder = 'Select…',
  disabled,
  error,
  onBlur,
  id,
  describedBy,
  className,
}: SelectProps) => {
  const generatedId = useId();
  const listId = `${id ?? generatedId}-listbox`;
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const rootRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const typeAhead = useRef({ buffer: '', at: 0 });
  const [rect, setRect] = useState<{ top: number; left: number; width: number; drop: 'down' | 'up' }>(
    { top: 0, left: 0, width: 0, drop: 'down' },
  );

  const selectedIndex = useMemo(
    () => options.findIndex((option) => option.value === value),
    [options, value],
  );
  const selected = selectedIndex >= 0 ? options[selectedIndex] : undefined;

  const close = useCallback(
    (returnFocus: boolean) => {
      setOpen(false);
      setActiveIndex(-1);
      if (returnFocus) {
        triggerRef.current?.focus();
      }
    },
    [],
  );

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      const insideField = rootRef.current?.contains(target);
      const insideList = listRef.current?.contains(target);
      if (!insideField && !insideList) {
        setOpen(false);
        setActiveIndex(-1);
        // Closing without choosing is the moment the field was visited and left.
        onBlur?.();
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [open, onBlur]);

  // Keep the highlighted row in view when it moves by keyboard rather than by pointer.
  useLayoutEffect(() => {
    if (!open || activeIndex < 0) {
      return;
    }
    const node = listRef.current?.children[activeIndex] as HTMLElement | undefined;
    node?.scrollIntoView({ block: 'nearest' });
  }, [open, activeIndex]);

  /**
   * Measures the trigger and decides which way the list opens. Fixed positioning takes the list
   * out of every ancestor's overflow, so a dialog body that scrolls can no longer clip it.
   */
  const place = useCallback(() => {
    const trigger = triggerRef.current;
    if (!trigger) {
      return;
    }
    const box = trigger.getBoundingClientRect();
    const below = window.innerHeight - box.bottom;
    setRect({
      top: below < 260 && box.top > below ? box.top : box.bottom,
      left: box.left,
      width: box.width,
      drop: below < 260 && box.top > below ? 'up' : 'down',
    });
  }, []);

  useLayoutEffect(() => {
    if (!open) {
      return undefined;
    }
    place();
    // The trigger moves if anything behind the list scrolls or the window resizes; `true` catches
    // scrolling in nested containers, not just on the document.
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [open, place]);

  const commit = (index: number) => {
    const option = options[index];
    if (!option || option.disabled) {
      return;
    }
    // Deliberately no onBlur here: choosing a value is the commit, and the form clears the field's
    // error on change. Firing blur in the same tick made the validator read the previous value.
    onChange(option.value);
    close(true);
  };

  const step = (from: number, direction: 1 | -1) => {
    const count = options.length;
    for (let offset = 1; offset <= count; offset += 1) {
      const next = (from + direction * offset + count * offset) % count;
      if (!options[next]?.disabled) {
        return next;
      }
    }
    return from;
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (disabled) {
      return;
    }
    const { key } = event;

    if (!open && (key === 'ArrowDown' || key === 'ArrowUp' || key === 'Enter' || key === ' ')) {
      event.preventDefault();
      setOpen(true);
      setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0);
      return;
    }
    if (!open) {
      return;
    }

    switch (key) {
      case 'Escape':
        event.preventDefault();
        close(true);
        break;
      case 'Tab':
        setOpen(false);
        setActiveIndex(-1);
        onBlur?.();
        break;
      case 'ArrowDown':
        event.preventDefault();
        setActiveIndex((current) => step(current < 0 ? -1 : current, 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        setActiveIndex((current) => step(current < 0 ? 0 : current, -1));
        break;
      case 'Home':
        event.preventDefault();
        setActiveIndex(step(-1, 1));
        break;
      case 'End':
        event.preventDefault();
        setActiveIndex(step(0, -1));
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        commit(activeIndex);
        break;
      default:
        if (key.length === 1 && /\S/.test(key)) {
          const now = Date.now();
          const buffer = now - typeAhead.current.at < 700 ? typeAhead.current.buffer + key : key;
          typeAhead.current = { buffer, at: now };
          const match = options.findIndex(
            (option) => !option.disabled && option.label.toLowerCase().startsWith(buffer.toLowerCase()),
          );
          if (match >= 0) {
            setActiveIndex(match);
          }
        }
    }
  };

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        ref={triggerRef}
        id={id}
        type="button"
        role="combobox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-haspopup="listbox"
        aria-invalid={error || undefined}
        aria-describedby={describedBy}
        disabled={disabled}
        onClick={() => {
          if (disabled) return;
          setOpen((current) => !current);
          setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0);
        }}
        onKeyDown={onKeyDown}
        className={cn(
          'flex h-10 w-full items-center justify-between gap-2 rounded-md border bg-white px-3 text-left text-theme-sm transition-colors',
          'disabled:cursor-not-allowed disabled:border-gray-300 disabled:bg-gray-50 disabled:text-gray-500',
          error
            ? 'border-error-800 hover:border-error-900'
            : 'border-gray-500 hover:border-gray-700',
          selected ? 'text-gray-900' : 'text-gray-500',
        )}
      >
        <span className="truncate">{selected?.label ?? placeholder}</span>
        <Icon
          name="chevron-down"
          size={16}
          className={cn(
            'shrink-0 text-gray-600 transition-transform duration-150',
            open && 'rotate-180',
          )}
          aria-hidden="true"
        />
      </button>

      {open &&
        createPortal(
          <ul
            ref={listRef}
            id={listId}
            role="listbox"
            aria-activedescendant={activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
            tabIndex={-1}
            style={{
              position: 'fixed',
              top: rect.drop === 'down' ? rect.top + 6 : undefined,
              bottom: rect.drop === 'up' ? window.innerHeight - rect.top + 6 : undefined,
              left: rect.left,
              width: rect.width,
            }}
            className={cn(
              'custom-scrollbar z-999999 max-h-64 overflow-y-auto rounded-lg border border-gray-200 bg-white py-1.5 shadow-theme-lg',
              'motion-safe:animate-[select-in_130ms_ease-out]',
            )}
          >
          {options.map((option, index) => {
            const isSelected = option.value === value;
            return (
              <li
                key={option.value || `__empty-${index}`}
                id={`${listId}-${index}`}
                role="option"
                aria-selected={isSelected}
                aria-disabled={option.disabled || undefined}
                onMouseEnter={() => setActiveIndex(index)}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => commit(index)}
                className={cn(
                  'mx-1.5 flex cursor-pointer items-center justify-between gap-2 rounded-md px-2.5 py-2 text-theme-sm transition-colors',
                  option.disabled && 'cursor-not-allowed text-gray-400',
                  !option.disabled && index === activeIndex && 'bg-gray-100',
                  !option.disabled && isSelected ? 'font-semibold text-gray-900' : 'text-gray-700',
                )}
              >
                <span className="truncate">{option.label}</span>
                {isSelected && (
                  <Icon name="check-circle" size={15} className="shrink-0 text-teal-700" />
                )}
              </li>
            );
          })}
            {options.length === 0 && (
              <li className="px-4 py-3 text-theme-sm text-gray-500">Nothing to choose from.</li>
            )}
          </ul>,
          document.body,
        )}
    </div>
  );
};

export default Select;
