import { ReactNode } from 'react';
import { FleetApiError, errorLabel } from 'shared/errors/FleetApiError';
import Alert from './Alert';
import Button from './Button';
import Modal, { ModalCloseButton, ModalSize } from './Modal';

interface FormDialogProps {
  open: boolean;
  title: string;
  description?: string;
  submitLabel: string;
  submitting: boolean;
  /** Blocks submission for reasons the form itself cannot fix (readiness, eligibility, state). */
  submitDisabled?: boolean;
  formError?: FleetApiError;
  maxWidth?: ModalSize;
  destructive?: boolean;
  onClose: () => void;
  onSubmit: () => void;
  children: ReactNode;
}

/**
 * The shell every fleet action dialog uses.
 *
 * Two guarantees: the submit button is disabled while a request is in flight, so a double click
 * cannot raise two trips; and a form-level failure is shown above the actions with the service's
 * own wording and correlation id rather than being swallowed.
 */
const FormDialog = ({
  open,
  title,
  description,
  submitLabel,
  submitting,
  submitDisabled,
  formError,
  maxWidth = 'md',
  destructive,
  onClose,
  onSubmit,
  children,
}: FormDialogProps) => (
  <Modal
    open={open}
    onClose={onClose}
    size={maxWidth}
    locked={submitting}
    labelledBy="fleet-form-dialog-title"
  >
    <form
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
      noValidate
    >
      <header className="flex items-start justify-between gap-4 border-b border-gray-200 px-6 py-4">
        <div className="min-w-0">
          <h2 id="fleet-form-dialog-title" className="text-theme-xl font-bold text-gray-900">
            {title}
          </h2>
          {description && <p className="mt-1 text-theme-sm text-gray-600">{description}</p>}
        </div>
        <ModalCloseButton onClose={onClose} disabled={submitting} />
      </header>

      <div className="custom-scrollbar max-h-[68vh] space-y-5 overflow-y-auto px-6 py-6">
        {children}

        {formError && (
          <Alert
            variant={formError.isForbidden ? 'warning' : 'error'}
            title={errorLabel(formError)}
            footnote={
              formError.correlationId ? `Correlation ID: ${formError.correlationId}` : undefined
            }
          >
            {formError.message}
          </Alert>
        )}
      </div>

      <footer className="flex items-center justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-4">
        <Button variant="ghost" onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          type="submit"
          variant={destructive ? 'danger' : 'primary'}
          loading={submitting}
          disabled={submitDisabled}
        >
          {submitting ? 'Working…' : submitLabel}
        </Button>
      </footer>
    </form>
  </Modal>
);

export default FormDialog;
