import { useId, useState } from 'react';
import Icon from './Icon';
import { cn } from './cn';

interface FloatingFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'email' | 'password';
  name?: string;
  autoComplete?: string;
  autoFocus?: boolean;
  required?: boolean;
  error?: boolean;
  disabled?: boolean;
  onBlur?: () => void;
}

/**
 * A text field whose label sits inside the control and floats to the border once it has content.
 *
 * <h2>Why this is its own component and not a variant of `TextInput`</h2>
 *
 * `TextInput` reserves a line above for the label and a line below for the helper, so a form never
 * changes height when validation fires — that rhythm is deliberate and every operational form in the
 * application depends on it. A floating label breaks it by design: the label lives *in* the control.
 * Making `TextInput` do both would mean a prop that changes its layout contract, and the sign-in page
 * is currently the only screen that wants this.
 *
 * <h2>The label is a real label, not a placeholder</h2>
 *
 * The obvious implementation is `placeholder="Email"` with `:placeholder-shown` CSS. It is also the
 * one that fails: a placeholder is not an accessible name, so a screen reader announces "edit text,
 * blank" and the field is unlabelled the moment the user types. This renders a real `<label>` bound
 * by `htmlFor` and moves it with a transform — the accessibility tree is identical to a normal
 * labelled field, and the animation is presentation only.
 *
 * `peer-placeholder-shown` still drives the resting position, so an empty field shows the label in
 * place. That needs a placeholder attribute to exist, hence the single space: a real placeholder
 * string would sit *behind* the label and show through during the transition.
 */
const FloatingField = ({
  label,
  value,
  onChange,
  type = 'text',
  name,
  autoComplete,
  autoFocus,
  required,
  error,
  disabled,
  onBlur,
}: FloatingFieldProps) => {
  const id = useId();
  const [revealed, setRevealed] = useState(false);
  const isPassword = type === 'password';
  const inputType = isPassword && revealed ? 'text' : type;

  return (
    <div className="relative">
      <input
        id={id}
        name={name}
        type={inputType}
        value={value}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        disabled={disabled}
        required={required}
        aria-invalid={error || undefined}
        placeholder=" "
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        className={cn(
          'peer h-14 w-full rounded-xl border bg-white px-4 pt-5 pb-1.5 text-theme-sm text-gray-900',
          'transition-colors outline-none placeholder:text-transparent',
          'disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-500',
          isPassword && 'pr-12',
          error
            ? 'border-error-800 focus:border-error-900'
            : 'border-gray-300 hover:border-gray-400 focus:border-teal-700',
        )}
      />

      <label
        htmlFor={id}
        className={cn(
          'pointer-events-none absolute left-4 select-none',
          'transition-all duration-150 ease-out',
          // Resting: vertically centred, at body size. Floated: small, near the top.
          'top-1/2 -translate-y-1/2 text-theme-sm',
          'peer-focus:top-2 peer-focus:translate-y-0 peer-focus:text-theme-xs',
          'peer-[:not(:placeholder-shown)]:top-2',
          'peer-[:not(:placeholder-shown)]:translate-y-0',
          'peer-[:not(:placeholder-shown)]:text-theme-xs',
          error ? 'text-error-800' : 'text-gray-500 peer-focus:text-teal-800',
        )}
      >
        {label}
      </label>

      {isPassword && (
        <button
          type="button"
          onClick={() => setRevealed((shown) => !shown)}
          // The control toggles, so the name has to say what pressing it will do rather than what
          // the field currently is — and `aria-pressed` carries the state separately.
          aria-label={revealed ? 'Hide password' : 'Show password'}
          aria-pressed={revealed}
          disabled={disabled}
          className={cn(
            'absolute top-1/2 right-3 flex h-8 w-8 -translate-y-1/2 items-center justify-center',
            'rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-800',
            'focus-visible:ring-2 focus-visible:ring-teal-600 focus-visible:outline-none',
          )}
        >
          <Icon name={revealed ? 'eye-off' : 'eye'} size={18} />
        </button>
      )}
    </div>
  );
};

export default FloatingField;
