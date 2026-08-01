import { ReactNode, useId } from 'react';
import { humanise } from 'modules/fleet/api/enums';
import Select, { type SelectOption } from './Select';
import { cn } from './cn';

/**
 * Form controls for the whole dashboard.
 *
 * Every control is white with a grey hairline, a real label above it, and one line reserved
 * underneath for a hint or an error — so a field never changes height when validation fires and a
 * form never jumps under the operator's cursor. Errors recolour the border and the helper line;
 * they never replace the label, because the operator still needs to know which field is wrong.
 */

/*
 * A form control's border is the only thing that says "this is a control", so SC 1.4.11 asks it to
 * reach 3:1 against the surrounding white. Borders/Main/default in the design system is Cloud Grey
 * 600; this uses grey-500 (4.9:1), which clears the bar while staying lighter than the text.
 * Placeholders are grey-500 for the same reason the label is grey-800 — both are read.
 *
 * Focus is not styled here. The dashboard has one focus treatment, defined once in `index.css` as a
 * 2px teal outline with an offset, so every focusable thing on the page looks focused the same way.
 */
const controlBase =
  'h-10 w-full rounded-md border bg-white px-3 text-theme-sm text-gray-900 transition-colors ' +
  'placeholder:text-gray-500 ' +
  'disabled:cursor-not-allowed disabled:border-gray-300 disabled:bg-gray-50 disabled:text-gray-500';

const controlTone = (error?: boolean) =>
  error ? 'border-error-800 hover:border-error-900' : 'border-gray-500 hover:border-gray-700';

interface FieldShellProps {
  id: string;
  label?: string;
  required?: boolean;
  error?: boolean;
  helperText?: string;
  className?: string;
  children: ReactNode;
}

/** Label + control + helper line. Exported so a bespoke control can sit in the same rhythm. */
export const FieldShell = ({
  id,
  label,
  required,
  error,
  helperText,
  className,
  children,
}: FieldShellProps) => (
  <div className={cn('min-w-0', className)}>
    {label && (
      <label
        htmlFor={id}
        className="mb-2 block text-theme-sm font-medium text-gray-800 select-none"
      >
        {label}
        {required && (
          <>
            <span className="ml-0.5 text-error-800" aria-hidden="true">
              *
            </span>
            <span className="sr-only"> (required)</span>
          </>
        )}
      </label>
    )}
    {children}
    {helperText ? (
      <p
        id={`${id}-help`}
        className={cn('mt-1.5 text-theme-xs', error ? 'text-error-800' : 'text-gray-600')}
      >
        {helperText}
      </p>
    ) : null}
  </div>
);

interface CommonProps {
  label?: string;
  required?: boolean;
  error?: boolean;
  helperText?: string;
  onBlur?: () => void;
  disabled?: boolean;
  placeholder?: string;
  className?: string;
  autoFocus?: boolean;
}

export interface TextInputProps extends CommonProps {
  value: string;
  onChange: (value: string) => void;
  maxLength?: number;
  /** `password` added for the sign-in form; the browser owns masking and manager integration. */
  type?: 'text' | 'email' | 'tel' | 'url' | 'password';
  /** Passed through so a sign-in form can ask for `current-password` and `username`. */
  autoComplete?: string;
  name?: string;
}

export const TextInput = ({
  value,
  onChange,
  label,
  required,
  error,
  helperText,
  onBlur,
  disabled,
  placeholder,
  className,
  maxLength,
  autoFocus,
  type = 'text',
  autoComplete,
  name,
}: TextInputProps) => {
  const id = useId();
  return (
    <FieldShell
      id={id}
      label={label}
      required={required}
      error={error}
      helperText={helperText}
      className={className}
    >
      <input
        id={id}
        type={type}
        name={name}
        autoComplete={autoComplete}
        value={value}
        maxLength={maxLength}
        placeholder={placeholder}
        disabled={disabled}
        autoFocus={autoFocus}
        aria-invalid={error || undefined}
        aria-describedby={helperText ? `${id}-help` : undefined}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        className={cn(controlBase, controlTone(error))}
      />
    </FieldShell>
  );
};

export interface NumberInputProps extends CommonProps {
  value: string;
  onChange: (value: string) => void;
  min?: number;
  max?: number;
  step?: number;
  /** Rendered inside the control on the right — "km", "L", "%". */
  suffix?: string;
}

/**
 * Numeric entry kept as a string in form state.
 *
 * Odometers and capacities are whole numbers on the wire; holding the raw string lets an empty
 * field stay empty (rather than collapsing to 0) and lets the validator report "must be a whole
 * number" instead of silently coercing.
 */
export const NumberInput = ({
  value,
  onChange,
  label,
  required,
  error,
  helperText,
  onBlur,
  disabled,
  placeholder,
  className,
  min = 0,
  max,
  step = 1,
  suffix,
}: NumberInputProps) => {
  const id = useId();
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
        <input
          id={id}
          type="number"
          inputMode="numeric"
          value={value}
          min={min}
          max={max}
          step={step}
          placeholder={placeholder}
          disabled={disabled}
          aria-invalid={error || undefined}
          aria-describedby={helperText ? `${id}-help` : undefined}
          onChange={(event) => onChange(event.target.value)}
          onBlur={onBlur}
          className={cn(controlBase, controlTone(error), suffix && 'pr-12')}
        />
        {suffix && (
          <span className="pointer-events-none absolute top-1/2 right-3.5 -translate-y-1/2 text-theme-xs font-medium text-gray-600">
            {suffix}
          </span>
        )}
      </div>
    </FieldShell>
  );
};

export interface TextAreaInputProps extends CommonProps {
  value: string;
  onChange: (value: string) => void;
  rows?: number;
  maxLength?: number;
}

export const TextAreaInput = ({
  value,
  onChange,
  label,
  required,
  error,
  helperText,
  onBlur,
  disabled,
  placeholder,
  className,
  rows = 3,
  maxLength,
}: TextAreaInputProps) => {
  const id = useId();
  return (
    <FieldShell
      id={id}
      label={label}
      required={required}
      error={error}
      helperText={helperText}
      className={className}
    >
      <textarea
        id={id}
        rows={rows}
        value={value}
        maxLength={maxLength}
        placeholder={placeholder}
        disabled={disabled}
        aria-invalid={error || undefined}
        aria-describedby={helperText ? `${id}-help` : undefined}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        className={cn(
          controlBase,
          controlTone(error),
          'h-auto resize-y py-2.5 leading-relaxed',
        )}
      />
    </FieldShell>
  );
};

export type { SelectOption };

interface SelectShellProps extends CommonProps {
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  emptyLabel?: string;
  allowEmpty?: boolean;
}

/** Wraps the dashboard's listbox in the shared label + helper rhythm. */
export const SelectInput = ({
  value,
  onChange,
  options,
  label,
  required,
  error,
  helperText,
  onBlur,
  disabled,
  className,
  allowEmpty,
  emptyLabel = 'Any',
}: SelectShellProps) => {
  const id = useId();
  const choices: SelectOption[] = allowEmpty
    ? [{ value: '', label: emptyLabel }, ...options]
    : options;

  return (
    <FieldShell
      id={id}
      label={label}
      required={required}
      error={error}
      helperText={helperText}
      className={className}
    >
      <Select
        id={id}
        value={value}
        onChange={onChange}
        options={choices}
        disabled={disabled}
        error={error}
        onBlur={onBlur}
        describedBy={helperText ? `${id}-help` : undefined}
        placeholder={allowEmpty ? emptyLabel : 'Select…'}
      />
    </FieldShell>
  );
};

interface EnumSelectProps<T extends string> extends CommonProps {
  value: T | '';
  options: readonly T[];
  onChange: (value: T | '') => void;
  /** Adds a blank option — use for filters, never for a `@NotNull` request field. */
  allowEmpty?: boolean;
  emptyLabel?: string;
  renderOptionLabel?: (option: T) => string;
}

/** Select bound to a backend enum, so an operator can never submit a value the service rejects. */
export function EnumSelect<T extends string>({
  value,
  options,
  onChange,
  allowEmpty,
  emptyLabel = 'Any',
  renderOptionLabel,
  ...rest
}: EnumSelectProps<T>) {
  return (
    <SelectInput
      {...rest}
      value={value}
      allowEmpty={allowEmpty}
      emptyLabel={emptyLabel}
      onChange={(next) => onChange(next as T | '')}
      options={options.map((option) => ({
        value: option,
        label: renderOptionLabel ? renderOptionLabel(option) : humanise(option),
      }))}
    />
  );
}

interface CheckboxProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
  hint?: string;
  disabled?: boolean;
}

export const Checkbox = ({ checked, onChange, label, hint, disabled }: CheckboxProps) => {
  const id = useId();
  return (
    <div className="flex min-h-6 items-start gap-2.5">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
        className="mt-0.5 h-5 w-5 shrink-0 rounded border-gray-500 accent-teal-700 disabled:opacity-50"
      />
      <label htmlFor={id} className="min-w-0 select-none">
        <span className="block text-theme-sm font-medium text-gray-800">{label}</span>
        {hint && <span className="block text-theme-xs text-gray-600">{hint}</span>}
      </label>
    </div>
  );
};
