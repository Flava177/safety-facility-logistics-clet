import { useCallback, useMemo, useState } from 'react';
import { FleetApiError, isFleetApiError } from 'shared/errors/FleetApiError';
import { FieldValidator } from './validators';

export type FieldErrors<T> = Partial<Record<keyof T & string, string>>;
export type ValidationSchema<T> = Partial<Record<keyof T & string, FieldValidator>>;

export interface FleetFormOptions<T extends object> {
  initialValues: T;
  schema?: ValidationSchema<T>;
  /** Cross-field rules that a per-field validator cannot express (date ranges, odometer floors). */
  crossFieldValidate?: (values: T) => FieldErrors<T>;
  onSubmit: (values: T) => Promise<void>;
}

export interface FleetForm<T extends object> {
  values: T;
  errors: FieldErrors<T>;
  touched: Partial<Record<keyof T & string, boolean>>;
  submitting: boolean;
  /** A failure that is not attributable to one field — shown as a form-level alert. */
  formError: FleetApiError | undefined;
  setValue: <K extends keyof T & string>(field: K, value: T[K]) => void;
  setValues: (values: Partial<T>) => void;
  blur: (field: keyof T & string) => void;
  /** Spread onto a field. The validation error wins; `hint` shows when the field is valid. */
  fieldProps: (
    field: keyof T & string,
    hint?: string,
  ) => {
    error: boolean;
    helperText: string | undefined;
    onBlur: () => void;
  };
  validateAll: () => boolean;
  submit: () => Promise<boolean>;
  reset: (values?: T) => void;
}

/**
 * Form state with inline validation and server-error mapping.
 *
 * Two behaviours matter for this dashboard. Errors show on blur and on submit, not on first
 * keystroke, so a half-typed registration number does not shout at the operator. And a failed
 * submit maps the service's `fieldErrors` onto the same `errors` map the client rules use, so a
 * server-only rule (duplicate identifier, odometer regression) lands on the field that caused it
 * rather than in a detached banner.
 */
export function useFleetForm<T extends object>(options: FleetFormOptions<T>): FleetForm<T> {
  const { initialValues, schema, crossFieldValidate, onSubmit } = options;

  const [values, setValuesState] = useState<T>(initialValues);
  const [errors, setErrors] = useState<FieldErrors<T>>({});
  const [touched, setTouched] = useState<Partial<Record<keyof T & string, boolean>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<FleetApiError | undefined>(undefined);

  const runValidation = useCallback(
    (candidate: T): FieldErrors<T> => {
      const next: FieldErrors<T> = {};
      if (schema) {
        (Object.keys(schema) as (keyof T & string)[]).forEach((field) => {
          const validate = schema[field];
          const message = validate?.(candidate[field]);
          if (message) {
            next[field] = message;
          }
        });
      }
      if (crossFieldValidate) {
        Object.assign(next, crossFieldValidate(candidate));
      }
      return next;
    },
    [schema, crossFieldValidate],
  );

  const setValue = useCallback(
    <K extends keyof T & string>(field: K, value: T[K]) => {
      const next = { ...values, [field]: value } as T;
      setValuesState(next);
      // Clear the field's error as soon as it becomes valid; never introduce a new one mid-typing.
      setErrors((currentErrors) => {
        if (!currentErrors[field] || runValidation(next)[field]) {
          return currentErrors;
        }
        const { [field]: _removed, ...rest } = currentErrors;
        return rest as FieldErrors<T>;
      });
    },
    [runValidation, values],
  );

  const setValues = useCallback((patch: Partial<T>) => {
    setValuesState((current) => ({ ...current, ...patch }));
  }, []);

  const blur = useCallback(
    (field: keyof T & string) => {
      setTouched((current) => ({ ...current, [field]: true }));
      setErrors((current) => {
        const message = runValidation(values)[field];
        if (!message) {
          const { [field]: _removed, ...rest } = current;
          return rest as FieldErrors<T>;
        }
        return { ...current, [field]: message };
      });
    },
    [runValidation, values],
  );

  const validateAll = useCallback(() => {
    const next = runValidation(values);
    setErrors(next);
    setTouched(
      (Object.keys(values) as (keyof T & string)[]).reduce(
        (accumulator, field) => ({ ...accumulator, [field]: true }),
        {},
      ),
    );
    return Object.keys(next).length === 0;
  }, [runValidation, values]);

  const submit = useCallback(async () => {
    setFormError(undefined);
    if (!validateAll()) {
      return false;
    }
    setSubmitting(true);
    try {
      await onSubmit(values);
      return true;
    } catch (cause) {
      if (isFleetApiError(cause)) {
        const mapped = cause.toFieldErrorMap();
        const known = Object.keys(mapped).filter((field) => field in values);
        if (known.length > 0) {
          setErrors((current) => ({
            ...current,
            ...(Object.fromEntries(known.map((field) => [field, mapped[field]])) as FieldErrors<T>),
          }));
        }
        // Always surface the envelope message too: it carries the SRS error wording.
        setFormError(cause);
      } else {
        setFormError(FleetApiError.transport('The request could not be completed.'));
      }
      return false;
    } finally {
      setSubmitting(false);
    }
  }, [onSubmit, validateAll, values]);

  const reset = useCallback(
    (next?: T) => {
      setValuesState(next ?? initialValues);
      setErrors({});
      setTouched({});
      setFormError(undefined);
    },
    [initialValues],
  );

  const fieldProps = useCallback(
    (field: keyof T & string, hint?: string) => ({
      error: Boolean(errors[field]),
      helperText: errors[field] ?? hint,
      onBlur: () => blur(field),
    }),
    [blur, errors],
  );

  return useMemo(
    () => ({
      values,
      errors,
      touched,
      submitting,
      formError,
      setValue,
      setValues,
      blur,
      fieldProps,
      validateAll,
      submit,
      reset,
    }),
    [
      values,
      errors,
      touched,
      submitting,
      formError,
      setValue,
      setValues,
      blur,
      fieldProps,
      validateAll,
      submit,
      reset,
    ],
  );
}
