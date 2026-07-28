import { ReactNode } from 'react';
import { MenuItem, TextField, TextFieldProps } from '@mui/material';
import { humanise } from 'modules/fleet/api/enums';

type BaseProps = Omit<TextFieldProps, 'select' | 'onChange' | 'value'>;

interface EnumSelectProps<T extends string> extends BaseProps {
  value: T | '';
  options: readonly T[];
  onChange: (value: T | '') => void;
  /** Adds a blank option — use for filters, never for a `@NotNull` request field. */
  allowEmpty?: boolean;
  emptyLabel?: string;
  renderOptionLabel?: (option: T) => ReactNode;
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
    <TextField
      {...rest}
      select
      fullWidth
      size="small"
      value={value}
      onChange={(event) => onChange(event.target.value as T | '')}
    >
      {allowEmpty && (
        <MenuItem value="">
          <em>{emptyLabel}</em>
        </MenuItem>
      )}
      {options.map((option) => (
        <MenuItem key={option} value={option}>
          {renderOptionLabel ? renderOptionLabel(option) : humanise(option)}
        </MenuItem>
      ))}
    </TextField>
  );
}

interface TextInputProps extends Omit<TextFieldProps, 'onChange' | 'value'> {
  value: string;
  onChange: (value: string) => void;
}

export const TextInput = ({ value, onChange, ...rest }: TextInputProps) => (
  <TextField
    {...rest}
    fullWidth
    size="small"
    value={value}
    onChange={(event) => onChange(event.target.value)}
  />
);

interface NumberInputProps extends Omit<TextFieldProps, 'onChange' | 'value' | 'type'> {
  value: string;
  onChange: (value: string) => void;
  min?: number;
}

/**
 * Numeric entry kept as a string in form state.
 *
 * Odometers and capacities are whole numbers on the wire; holding the raw string lets an empty
 * field stay empty (rather than collapsing to 0) and lets the validator report "must be a whole
 * number" instead of silently coercing.
 */
export const NumberInput = ({ value, onChange, min = 0, ...rest }: NumberInputProps) => (
  <TextField
    {...rest}
    fullWidth
    size="small"
    type="number"
    value={value}
    onChange={(event) => onChange(event.target.value)}
    slotProps={{ htmlInput: { min, step: 1, inputMode: 'numeric' } }}
  />
);

interface DateInputProps extends Omit<TextFieldProps, 'onChange' | 'value' | 'type'> {
  value: string;
  onChange: (value: string) => void;
  withTime?: boolean;
}

export const DateInput = ({ value, onChange, withTime, ...rest }: DateInputProps) => (
  <TextField
    {...rest}
    fullWidth
    size="small"
    type={withTime ? 'datetime-local' : 'date'}
    value={value}
    onChange={(event) => onChange(event.target.value)}
    slotProps={{ inputLabel: { shrink: true } }}
  />
);
