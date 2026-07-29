import { useEffect, useId, useRef } from 'react';
import flatpickr from 'flatpickr';
import type { Instance } from 'flatpickr/dist/types/instance';
import Icon from './Icon';
import { FieldShell } from './fields';
import { cn } from './cn';

/**
 * Date and date-time entry.
 *
 * A real calendar rather than the browser's native control, which looks different on every machine
 * and — on the desktops this dashboard runs on — is a grey box with no month view. The visible input
 * shows a human date ("14 Mar 2026"); form state keeps the wire-friendly string it always did
 * (`YYYY-MM-DD`, or `YYYY-MM-DDTHH:mm` for date-times), so validation and request mapping are
 * unchanged.
 */

const DATE_FORMAT = 'Y-m-d';
const DATE_TIME_FORMAT = 'Y-m-d\\TH:i';

const controlClasses =
  'h-10 w-full cursor-pointer rounded-md border border-gray-500 bg-white pr-10 pl-3 text-theme-sm ' +
  'text-gray-900 transition-colors placeholder:text-gray-500 hover:border-gray-700 ' +
  'disabled:cursor-not-allowed disabled:border-gray-300 disabled:bg-gray-50 disabled:text-gray-500';

const errorClasses = 'border-error-800 hover:border-error-900';

/**
 * Clearing a bound means passing `undefined`, which flatpickr's own option map does not type. The
 * cast is confined to this one helper rather than spreading through the component.
 */
const setBound = (instance: Instance | null, key: 'minDate' | 'maxDate', bound?: string) => {
  (instance as unknown as { set: (option: string, value: unknown) => void } | null)?.set(
    key,
    bound || undefined,
  );
};

interface BaseProps {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  error?: boolean;
  helperText?: string;
  onBlur?: () => void;
  disabled?: boolean;
  minDate?: string;
  maxDate?: string;
  placeholder?: string;
  className?: string;
}

interface PickerProps extends BaseProps {
  withTime: boolean;
}

const Picker = ({
  label,
  value,
  onChange,
  required,
  error,
  helperText,
  onBlur,
  disabled,
  minDate,
  maxDate,
  placeholder,
  className,
  withTime,
}: PickerProps) => {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const instanceRef = useRef<Instance | null>(null);
  // Held in a ref so changing the handler never tears down and rebuilds the calendar.
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    if (!inputRef.current) {
      return undefined;
    }

    const instance = flatpickr(inputRef.current, {
      enableTime: withTime,
      time_24hr: true,
      minuteIncrement: 5,
      dateFormat: withTime ? DATE_TIME_FORMAT : DATE_FORMAT,
      altInput: true,
      altFormat: withTime ? 'd M Y  H:i' : 'd M Y',
      altInputClass: cn(controlClasses, error && errorClasses),
      allowInput: false,
      monthSelectorType: 'static',
      onChange: (_dates: Date[], dateString: string) => onChangeRef.current(dateString),
    }) as Instance;

    instanceRef.current = instance;
    return () => {
      instance.destroy();
      instanceRef.current = null;
    };
    // The calendar is created once per field; value, bounds and styling are pushed in below.
  }, [withTime, error]);

  // Keep the calendar in step with form state that changed elsewhere (reset, prefill, clear).
  useEffect(() => {
    const instance = instanceRef.current;
    if (!instance) {
      return;
    }
    const current = instance.input.value;
    if (current !== value) {
      instance.setDate(value || '', false);
    }
  }, [value]);

  useEffect(() => {
    setBound(instanceRef.current, 'minDate', minDate);
  }, [minDate]);

  useEffect(() => {
    setBound(instanceRef.current, 'maxDate', maxDate);
  }, [maxDate]);

  useEffect(() => {
    const altInput = instanceRef.current?.altInput;
    if (altInput) {
      altInput.disabled = Boolean(disabled);
      altInput.placeholder = placeholder ?? (withTime ? 'Select date and time' : 'Select date');
      altInput.id = id;
      if (onBlur) {
        altInput.onblur = () => onBlur();
      }
    }
  }, [disabled, placeholder, withTime, id, onBlur]);

  return (
    <FieldShell
      id={id}
      label={label}
      required={required}
      error={error}
      helperText={helperText}
      className={className}
    >
      <div className="relative">
        <input ref={inputRef} type="text" defaultValue={value} className="hidden" />
        {value && !required && !disabled ? (
          <button
            type="button"
            aria-label={`Clear ${label ?? 'date'}`}
            title="Clear"
            onClick={() => {
              instanceRef.current?.clear();
              onChange('');
            }}
            className="absolute top-1/2 right-2 z-1 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900"
          >
            <Icon name="close" size={14} />
          </button>
        ) : (
          <Icon
            name="calendar"
            size={17}
            className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-gray-600"
          />
        )}
      </div>
    </FieldShell>
  );
};

export const DateField = (props: BaseProps) => <Picker {...props} withTime={false} />;

export const DateTimeField = (props: BaseProps) => <Picker {...props} withTime />;
