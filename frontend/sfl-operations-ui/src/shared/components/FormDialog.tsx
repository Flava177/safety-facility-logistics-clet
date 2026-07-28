import { ReactNode } from 'react';
import {
  Alert,
  AlertTitle,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import { FleetApiError } from 'shared/errors/FleetApiError';

interface FormDialogProps {
  open: boolean;
  title: string;
  description?: string;
  submitLabel: string;
  submitting: boolean;
  /** Blocks submission for reasons the form itself cannot fix (readiness, eligibility, state). */
  submitDisabled?: boolean;
  formError?: FleetApiError;
  maxWidth?: 'xs' | 'sm' | 'md' | 'lg';
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
  maxWidth = 'sm',
  destructive,
  onClose,
  onSubmit,
  children,
}: FormDialogProps) => (
  <Dialog
    open={open}
    onClose={submitting ? undefined : onClose}
    fullWidth
    maxWidth={maxWidth}
    aria-labelledby="fleet-form-dialog-title"
  >
    <DialogTitle id="fleet-form-dialog-title" sx={{ pb: description ? 0.5 : 2 }}>
      {title}
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, fontWeight: 400 }}>
          {description}
        </Typography>
      )}
    </DialogTitle>
    <Divider />

    <DialogContent sx={{ pt: 2.5 }}>
      <Stack spacing={2}>
        {children}
        {formError && (
          <Alert severity={formError.isForbidden ? 'warning' : 'error'}>
            <AlertTitle sx={{ mb: 0.25 }}>{formError.code.replace(/_/g, ' ')}</AlertTitle>
            <Typography variant="body2">{formError.message}</Typography>
            {formError.correlationId && (
              <Typography variant="caption" color="text.secondary">
                Correlation ID: {formError.correlationId}
              </Typography>
            )}
          </Alert>
        )}
      </Stack>
    </DialogContent>

    <Divider />
    <DialogActions sx={{ px: 3, py: 2 }}>
      <Button variant="text" color="neutral" onClick={onClose} disabled={submitting}>
        Cancel
      </Button>
      <Button
        variant="contained"
        color={destructive ? 'error' : 'secondary'}
        onClick={onSubmit}
        disabled={submitting || submitDisabled}
        startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : undefined}
      >
        {submitting ? 'Working…' : submitLabel}
      </Button>
    </DialogActions>
  </Dialog>
);

export default FormDialog;
