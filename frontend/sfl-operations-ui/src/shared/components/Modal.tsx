import { ReactNode, useEffect, useRef } from 'react';
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
  const surface = useRef<HTMLDivElement>(null);

  /**
   * Put the caret where the work is.
   *
   * <p>Nothing moved focus when a dialog opened, so it stayed on the button *behind* the modal. Two
   * consequences, and the second is the worse one: every form needed a click before it could be
   * typed into, and a keyboard or screen-reader user was left outside a dialog that had just claimed
   * `aria-modal`.
   *
   * <p>Fixed here rather than by adding `autoFocus` to each dialog's first field, because that is
   * fifteen places to remember and one to forget — and the emergency module had already done it that
   * way, which is why some dialogs behaved and most did not.
   *
   * <p>**An explicit `autoFocus` still wins, but not by being looked for.** React sets `autoFocus` as
   * a property and focuses during commit — it leaves no `autofocus` attribute to query, and it has
   * already run by the time this effect fires. So the rule is simply: if focus is already inside the
   * dialog, somebody has chosen, and we do not second-guess them. Searching for `[autofocus]` finds
   * nothing in React and would have quietly stolen focus from the emergency dialogs that use it.
   *
   * <p>Otherwise the first form control that can actually take focus. `querySelectorAll` happily
   * returns fields inside a collapsed `<details>` — which "Register a vehicle" now has — and calling
   * `.focus()` on one does nothing, leaving focus outside the modal. `closest('details:not([open])')`
   * is the precise test for that, and unlike `offsetParent` it does not depend on layout, so it means
   * the same thing in a browser and in jsdom.
   *
   * <p>Failing everything, the dialog surface itself, so focus is at least inside.
   *
   * <p>Buttons are deliberately not candidates: the close `×` is the first focusable element in every
   * `FormDialog`, and landing there makes Enter discard the form.
   */
  useEffect(() => {
    if (!open) {
      return;
    }
    const root = surface.current;
    if (!root || root.contains(document.activeElement)) {
      return;
    }
    const reachable = (element: HTMLElement) =>
      !element.closest('details:not([open])') && !element.closest('[hidden]');
    const target =
      [
        ...root.querySelectorAll<HTMLElement>(
          'input:not([type="hidden"]):not([disabled]):not([readonly]), textarea:not([disabled]):not([readonly]), select:not([disabled])',
        ),
      ].find(reachable) ?? root;
    if (target === root) {
      root.tabIndex = -1;
    }
    target.focus({ preventScroll: true });
  }, [open]);

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
        ref={surface}
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
