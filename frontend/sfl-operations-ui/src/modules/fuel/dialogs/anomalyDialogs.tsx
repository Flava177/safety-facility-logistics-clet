import { FuelAnomalyCase } from 'modules/fuel/api/dto';
import { AnomalyAction, fuelAnomaliesApi } from 'modules/fuel/api/fuelApi';
import { ANOMALY_RULES, anomalyClosureBlockers } from 'modules/fuel/api/workflow';
import Alert from 'shared/components/Alert';
import FormDialog from 'shared/components/FormDialog';
import { TextAreaInput, TextInput } from 'shared/components/fields';
import { useFleetForm } from 'shared/validation/useFleetForm';
import { compose, maxLength, required } from 'shared/validation/validators';

interface AnomalyActionDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  anomaly: FuelAnomalyCase;
  action: AnomalyAction;
}

/**
 * All thirteen anomaly transitions in one dialog.
 *
 * `POST /anomalies/{id}/{action}` takes a single `ActionRequest { value, evidenceId }`, and the
 * service overloads `value` by action: an assignee for assign and reassign, the explanation text
 * for explain, and the reason for everything else. One endpoint, one request shape, one dialog —
 * what changes is the label on the field, whether it is mandatory, and whether evidence is asked
 * for. Splitting this into thirteen components would duplicate the same twenty lines thirteen times
 * and make it easy for one of them to drift from the service.
 *
 * `close` is the interesting one. `FuelAnomalyCase.close` demands three things — an explanation and
 * a decision already on the record, plus evidence supplied with the closure — and refuses with one
 * message naming all three. The dialog shows which of them are actually missing and blocks
 * submission, rather than letting the operator write a closure reason and then be told no.
 */
export const AnomalyActionDialog = ({
  open,
  onClose,
  onSaved,
  anomaly,
  action,
}: AnomalyActionDialogProps) => {
  const rule = ANOMALY_RULES[action];
  const needsValue = rule.requiredField !== null;
  const needsEvidence = Boolean(rule.requiresEvidence);
  const blockers = action === 'close' ? anomalyClosureBlockers(anomaly) : [];

  const form = useFleetForm({
    initialValues: { value: defaultValue(anomaly, action), evidenceId: '' },
    schema: {
      value: needsValue
        ? compose(required(fieldLabel(action)), maxLength(fieldLabel(action), 2000))
        : maxLength(fieldLabel(action), 2000),
      evidenceId: needsEvidence ? required('Evidence reference') : undefined,
    },
    onSubmit: async (values) => {
      await fuelAnomaliesApi.transition(anomaly.id, action, {
        value: values.value.trim() || null,
        evidenceId: values.evidenceId.trim() || null,
      });
      onSaved();
      onClose();
    },
  });

  const note = ACTION_NOTES[action];

  return (
    <FormDialog
      open={open}
      title={`${rule.label} · ${anomaly.anomalyNumber}`}
      description={anomalyDescription(anomaly)}
      submitLabel={rule.label}
      submitting={form.submitting}
      submitDisabled={blockers.length > 0}
      formError={form.formError}
      destructive={action === 'cancel' || action === 'reject'}
      onClose={onClose}
      onSubmit={form.submit}
    >
      {blockers.length > 0 && (
        <Alert variant="error" title="The service will refuse this closure">
          <ul className="mt-1 list-disc space-y-1 pl-4">
            {blockers.map((blocker) => (
              <li key={blocker}>{blocker}</li>
            ))}
          </ul>
        </Alert>
      )}

      {note && <Alert variant={rule.privileged ? 'warning' : 'info'}>{note}</Alert>}

      {(needsValue || action === 'explain') && (
        <>
          {isLongText(action) ? (
            <TextAreaInput
              label={fieldLabel(action)}
              required={needsValue}
              rows={4}
              value={form.values.value}
              onChange={(value) => form.setValue('value', value)}
              {...form.fieldProps('value')}
            />
          ) : (
            <TextInput
              label={fieldLabel(action)}
              required={needsValue}
              value={form.values.value}
              onChange={(value) => form.setValue('value', value)}
              {...form.fieldProps('value')}
            />
          )}
        </>
      )}

      {(needsEvidence || action === 'explain') && (
        <TextInput
          label="Evidence reference"
          required={needsEvidence}
          value={form.values.evidenceId}
          onChange={(value) => form.setValue('evidenceId', value)}
          {...form.fieldProps(
            'evidenceId',
            needsEvidence
              ? 'Register the closure evidence under Evidence & audit, then paste its identifier.'
              : 'Optional. Attach the receipt or statement the explanation refers to.',
          )}
        />
      )}
    </FormDialog>
  );
};

/** Reassigning pre-fills the current owner, which is what an operator is usually amending. */
const defaultValue = (anomaly: FuelAnomalyCase, action: AnomalyAction): string =>
  action === 'reassign' ? (anomaly.assignee ?? '') : '';

const fieldLabel = (action: AnomalyAction): string => {
  switch (action) {
    case 'assign':
    case 'reassign':
      return 'Assignee';
    case 'explain':
      return 'Explanation';
    case 'approve':
    case 'reject':
      return 'Decision reason';
    case 'escalate':
      return 'Reason for escalating';
    case 'hold':
      return 'Reason for the hold';
    case 'cancel':
      return 'Reason for cancelling';
    case 'close':
      return 'Closure reason';
    case 'reopen':
      return 'Reason for reopening';
    default:
      return 'Note';
  }
};

const isLongText = (action: AnomalyAction): boolean =>
  action !== 'assign' && action !== 'reassign';

/** What each action does that the operator cannot see from the button. */
const ACTION_NOTES: Partial<Record<AnomalyAction, string>> = {
  assign: 'The assignee is notified. Their name is recorded against the case.',
  reassign: 'The new assignee is notified. The case returns to the assigned state.',
  review: 'Moves the case to under review, from where a decision can be recorded.',
  'request-explanation':
    'Moves the case to awaiting explanation. Record the response when it arrives.',
  explain:
    'The explanation is one of the three things closure requires. Attach the supporting evidence if you have it.',
  approve:
    'Privileged — needs FUEL_ANOMALY_APPROVE. Records an approved decision; the case still has to be closed.',
  reject:
    'Privileged — needs FUEL_ANOMALY_APPROVE. Records a rejected decision; the case still has to be closed.',
  escalate:
    'Privileged — needs FUEL_ANOMALY_ESCALATE. Raises the escalation level and notifies the fleet manager. A material case is also surfaced to finance and audit.',
  hold: 'The assignee is notified that the case is blocked. Resume it when the block clears.',
  resume: 'Returns the case to under review.',
  cancel: 'Privileged. The case stays in the register and in the audit trail.',
  close: 'Privileged — needs FUEL_ANOMALY_APPROVE. Closure is final unless the case is reopened.',
  reopen: 'Privileged. The case returns to the queue and can be reassigned.',
};

const anomalyDescription = (anomaly: FuelAnomalyCase): string => {
  const parts = [
    anomaly.type.replace(/_/g, ' ').toLowerCase(),
    `${anomaly.severity.toLowerCase()} severity`,
  ];
  if (anomaly.material) {
    parts.push('material');
  }
  if (anomaly.assignee) {
    parts.push(`assigned to ${anomaly.assignee}`);
  }
  const sentence = parts.join(' · ');
  return sentence.charAt(0).toUpperCase() + sentence.slice(1);
};
