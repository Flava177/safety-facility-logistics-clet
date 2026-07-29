import { ReactNode, useId } from 'react';
import { activationTone } from 'modules/emergency/api/workflow';
import type { ActivationStatus } from 'modules/emergency/api/enums';
import { humanise } from 'modules/fleet/api/enums';
import StatusChip from 'shared/components/StatusChip';
import { Checkbox } from 'shared/components/fields';
import { cn } from 'shared/components/cn';

/**
 * An activation's status, in the tone this module reads it in.
 *
 * Always this rather than a bare `StatusChip`, because `ACTIVE` means a live emergency here and the
 * shared table reads it as "fine". See `activationTone`.
 */
export const ActivationStatusChip = ({
  status,
  size,
}: {
  status: ActivationStatus;
  size?: 'sm' | 'md';
}) => <StatusChip value={status} tone={activationTone(status)} size={size} />;

export interface CheckboxOption {
  value: string;
  label: string;
  hint?: string;
  disabled?: boolean;
}

interface CheckboxGroupProps {
  label: string;
  options: CheckboxOption[];
  values: string[];
  onChange: (values: string[]) => void;
  required?: boolean;
  error?: boolean;
  helperText?: string;
  emptyMessage?: string;
  columns?: 1 | 2;
}

/**
 * A checkbox list bound to an array field.
 *
 * Channels, audience groups and recipient zones are all "choose any number of these", and all three
 * change what a send does rather than merely describing it — a multi-select that hides its
 * selections behind a summary line is the wrong control for a decision an operator has to be able
 * to check at a glance before pressing send. So every option stays visible, with its consequence
 * beside it.
 *
 * Built from the shared `Checkbox` and the shared label/helper rhythm, so it sits in a form
 * alongside `TextInput` and `EnumSelect` without looking like a different application.
 */
export const CheckboxGroup = ({
  label,
  options,
  values,
  onChange,
  required,
  error,
  helperText,
  emptyMessage = 'Nothing to choose from.',
  columns = 1,
}: CheckboxGroupProps) => {
  const id = useId();
  const toggle = (value: string, checked: boolean) =>
    onChange(checked ? [...values, value] : values.filter((entry) => entry !== value));

  return (
    <fieldset className="min-w-0">
      <legend className="mb-2 block text-theme-sm font-medium text-gray-800">
        {label}
        {required && (
          <>
            <span className="ml-0.5 text-error-800" aria-hidden="true">
              *
            </span>
            <span className="sr-only"> (required)</span>
          </>
        )}
      </legend>
      {options.length === 0 ? (
        <p className="rounded-md border border-dashed border-gray-300 px-3 py-3 text-theme-sm text-gray-600">
          {emptyMessage}
        </p>
      ) : (
        <div
          className={cn(
            'rounded-md border px-3 py-3',
            error ? 'border-error-800' : 'border-gray-300',
            columns === 2 ? 'grid gap-2.5 sm:grid-cols-2' : 'space-y-2.5',
          )}
        >
          {options.map((option) => (
            <Checkbox
              key={option.value}
              checked={values.includes(option.value)}
              disabled={option.disabled}
              onChange={(checked) => toggle(option.value, checked)}
              label={option.label}
              hint={option.hint}
            />
          ))}
        </div>
      )}
      {helperText && (
        <p
          id={`${id}-help`}
          className={cn('mt-1.5 text-theme-xs', error ? 'text-error-800' : 'text-gray-600')}
        >
          {helperText}
        </p>
      )}
    </fieldset>
  );
};

/**
 * A consequence of what has been entered, shown before it is committed.
 *
 * The emergency dialogs use this for the two things the operator most needs to see before pressing
 * send: how many people a selection actually reaches, and what obligation the send creates. It is
 * styled as a statement rather than as a warning, because most of the time it is neither good news
 * nor bad — it is simply what is about to happen.
 */
export const ConsequencePanel = ({
  title,
  tone = 'neutral',
  children,
}: {
  title: string;
  tone?: 'neutral' | 'warning';
  children: ReactNode;
}) => (
  <div
    className={cn(
      'rounded-md border px-4 py-3',
      tone === 'warning' ? 'border-error-200 bg-error-50' : 'border-gray-200 bg-gray-50',
    )}
  >
    <p
      className={cn(
        'text-theme-sm font-semibold',
        tone === 'warning' ? 'text-error-800' : 'text-gray-800',
      )}
    >
      {title}
    </p>
    <div className="mt-1.5 space-y-1 text-theme-sm text-gray-700">{children}</div>
  </div>
);

/** A label and a figure, for the dense summary rows inside a `ConsequencePanel`. */
export const ConsequenceLine = ({ label, value }: { label: string; value: ReactNode }) => (
  <div className="flex items-baseline justify-between gap-4">
    <span className="text-gray-600">{label}</span>
    <span className="font-medium text-gray-900">{value}</span>
  </div>
);

/** Channel names as a readable list — "SMS, Email and Push". */
export const listChannels = (channels: string[]): string => {
  const names = channels.map((channel) => humanise(channel));
  if (names.length === 0) {
    return 'no channel';
  }
  if (names.length === 1) {
    return names[0];
  }
  return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
};
