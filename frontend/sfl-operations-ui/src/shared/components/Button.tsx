import { ButtonHTMLAttributes, ReactNode, forwardRef } from 'react';
import Icon, { IconName } from './Icon';
import { cn } from './cn';

export type ButtonVariant = 'primary' | 'accent' | 'outline' | 'ghost' | 'link' | 'danger';
export type ButtonSize = 'sm' | 'md';

/**
 * Action styles, with their measured contrast against the surface they sit on.
 *
 * `primary` is CLET Navy — the same filled dark button the rest of the platform uses for "make a
 * new thing". `accent` keeps CLET Gold for the one action that starts a piece of field work, and
 * its label is navy: white on gold-700 is 2.9:1 and fails SC 1.4.3, while navy on the same fill is
 * 6.2:1. Gold is a colour to be seen with, not read through.
 */
const variants: Record<ButtonVariant, string> = {
  primary: 'bg-brand-800 text-white hover:bg-brand-700 active:bg-brand-900', // 16.4:1
  accent: 'bg-gold-700 text-brand-900 hover:bg-gold-800 active:bg-gold-900', // 6.2:1
  outline: 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 hover:text-gray-900',
  ghost: 'text-gray-700 hover:bg-gray-100 hover:text-gray-900', // 10.4:1
  link: 'text-teal-700 underline-offset-2 hover:underline', // 9.4:1
  danger: 'bg-error-800 text-white hover:bg-error-900', // 12.0:1
};

// Both sizes clear the 24x24 minimum of SC 2.5.8 with room to spare.
const sizes: Record<ButtonSize, string> = {
  sm: 'h-8 min-w-8 gap-1.5 px-3 text-theme-xs',
  md: 'h-10 min-w-10 gap-2 px-4 text-theme-sm',
};

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  startIcon?: IconName;
  endIcon?: IconName;
  /** Shows a spinner and blocks the click, so one submit cannot become two writes. */
  loading?: boolean;
  children?: ReactNode;
}

const Spinner = () => (
  <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.3" strokeWidth="3" />
    <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
  </svg>
);

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'outline',
      size = 'md',
      startIcon,
      endIcon,
      loading = false,
      disabled,
      className,
      children,
      type = 'button',
      ...rest
    },
    ref,
  ) => (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      // While a request is in flight the control is busy, not merely styled differently; assistive
      // technology should hear that rather than infer it from a spinner it cannot see.
      aria-busy={loading || undefined}
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-md font-medium whitespace-nowrap transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-60',
        variant === 'link' ? 'h-auto gap-1 px-0 text-theme-sm' : sizes[size],
        variants[variant],
        className,
      )}
      {...rest}
    >
      {loading ? <Spinner /> : startIcon ? <Icon name={startIcon} size={16} /> : null}
      {children}
      {endIcon && !loading ? <Icon name={endIcon} size={16} /> : null}
    </button>
  ),
);

Button.displayName = 'Button';

export default Button;

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  name: IconName;
  label: string;
  size?: number;
  tone?: 'default' | 'quiet';
}

/**
 * A square, icon-only control.
 *
 * `label` is required because it is the control's only accessible name, and the bordered variant
 * uses `gray-500`: with no text inside, the boundary is the only thing identifying the control, so
 * SC 1.4.11 asks it to reach 3:1 (this is 4.9:1).
 */
export const IconButton = ({
  name,
  label,
  size = 17,
  tone = 'default',
  className,
  type = 'button',
  ...rest
}: IconButtonProps) => (
  <button
    type={type}
    aria-label={label}
    title={label}
    className={cn(
      'inline-flex h-9 w-9 items-center justify-center rounded-md text-gray-600 transition-colors',
      tone === 'default' ? 'border border-gray-500 bg-white hover:bg-gray-50' : 'hover:bg-gray-100',
      'hover:text-gray-900 disabled:cursor-not-allowed disabled:opacity-60',
      className,
    )}
    {...rest}
  >
    <Icon name={name} size={size} />
  </button>
);
