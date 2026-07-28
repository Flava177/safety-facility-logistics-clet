import { ReactNode, useEffect } from 'react';
import Icon from './Icon';
import { cn } from './cn';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl';

const sizes: Record<ModalSize, string> = {
  sm: 'max-w-lg',
  md: 'max-w-3xl',
  lg: 'max-w-5xl',
  xl: 'max-w-6xl',
};

interface ModalProps {
  open: boolean;
  onClose: () => void;
  size?: ModalSize;
  /** Suppresses backdrop-click and Escape while a request is in flight. */
  locked?: boolean;
  labelledBy?: string;
  children: ReactNode;
  className?: string;
}

/**
 * Centred dialog surface.
 *
 * Closing is blocked while `locked` so a stray click cannot dismiss a form mid-submit and leave the
 * operator unsure whether the write landed.
 */
const Modal = ({
  open,
  onClose,
  size = 'md',
  locked = false,
  labelledBy,
  children,
  className,
}: ModalProps) => {
  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !locked) {
        onClose();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, locked, onClose]);

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-99999 flex items-start justify-center overflow-y-auto p-4 sm:p-6">
      <div
        className="fixed inset-0 bg-brand-950/50 backdrop-blur-[2px]"
        onClick={locked ? undefined : onClose}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        className={cn(
          'relative my-auto w-full rounded-lg bg-white shadow-theme-xl',
          sizes[size],
          className,
        )}
      >
        {children}
      </div>
    </div>
  );
};

export default Modal;

export const ModalCloseButton = ({
  onClose,
  disabled,
}: {
  onClose: () => void;
  disabled?: boolean;
}) => (
  <button
    type="button"
    onClick={onClose}
    disabled={disabled}
    aria-label="Close"
    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 disabled:opacity-50"
  >
    <Icon name="close" size={18} />
  </button>
);
