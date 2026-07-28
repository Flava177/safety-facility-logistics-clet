import { Alert, Box } from '@mui/material';
import { WorkflowItemResponse } from 'modules/fleet/api/dto';
import {
  FLEET_WORKFLOW_TYPES,
  FleetWorkflowType,
  OPERATING_MODES,
  OperatingMode,
  WORKFLOW_PRIORITIES,
  WORKFLOW_SEVERITIES,
  WorkflowPriority,
  WorkflowSeverity,
} from 'modules/fleet/api/enums';
import { workflowApi } from 'modules/fleet/api/fleetApi';
import FormDialog from 'shared/components/FormDialog';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { useFleetForm } from 'shared/validation/useFleetForm';
import { compose, maxLength, required } from 'shared/validation/validators';

const twoColumn = {
  display: 'grid',
  gap: 2,
  gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
} as const;

interface BaseProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

/* Raise — POST /api/v1/fleet/workflow-items */
export const RaiseWorkflowItemDialog = ({
  open,
  onClose,
  onSaved,
  defaultSiteCode,
  relatedRecordType,
  relatedRecordId,
}: BaseProps & {
  defaultSiteCode: string;
  relatedRecordType?: string;
  relatedRecordId?: string;
}) => {
  const form = useFleetForm({
    initialValues: {
      workflowType: '' as FleetWorkflowType | '',
      siteCode: defaultSiteCode,
      title: '',
      description: '',
      priority: 'MEDIUM' as WorkflowPriority,
      severity: 'MODERATE' as WorkflowSeverity,
      operatingMode: 'ROUTINE' as OperatingMode,
      assignee: '',
    },
    schema: {
      workflowType: required('Workflow type'),
      siteCode: compose(required('Site code'), maxLength('Site code', 40)),
      title: compose(required('Title'), maxLength('Title', 200)),
      description: compose(required('Description'), maxLength('Description', 2000)),
      priority: required('Priority'),
      severity: required('Severity'),
      assignee: maxLength('Assignee', 160),
    },
    onSubmit: async (values) => {
      await workflowApi.raise({
        workflowType: values.workflowType as FleetWorkflowType,
        relatedRecordType: relatedRecordType ?? null,
        relatedRecordId: relatedRecordId ?? null,
        siteCode: values.siteCode.trim().toUpperCase(),
        title: values.title.trim(),
        description: values.description.trim(),
        priority: values.priority,
        severity: values.severity,
        operatingMode: values.operatingMode,
        assignee: values.assignee.trim() || null,
      });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Raise a workflow item"
      description="Priority and severity drive the SLA target the service applies."
      submitLabel="Raise item"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Box sx={twoColumn}>
        <EnumSelect
          label="Workflow type"
          required
          value={form.values.workflowType}
          options={FLEET_WORKFLOW_TYPES}
          onChange={(value) => form.setValue('workflowType', value)}
          {...form.fieldProps('workflowType')}
        />
        <TextInput
          label="Site code"
          required
          value={form.values.siteCode}
          onChange={(value) => form.setValue('siteCode', value)}
          {...form.fieldProps('siteCode')}
        />
        <EnumSelect
          label="Priority"
          required
          value={form.values.priority}
          options={WORKFLOW_PRIORITIES}
          onChange={(value) => form.setValue('priority', (value || 'MEDIUM') as WorkflowPriority)}
          {...form.fieldProps('priority')}
        />
        <EnumSelect
          label="Severity"
          required
          value={form.values.severity}
          options={WORKFLOW_SEVERITIES}
          onChange={(value) => form.setValue('severity', (value || 'MODERATE') as WorkflowSeverity)}
          {...form.fieldProps('severity')}
        />
        <EnumSelect
          label="Operating mode"
          value={form.values.operatingMode}
          options={OPERATING_MODES}
          onChange={(value) =>
            form.setValue('operatingMode', (value || 'ROUTINE') as OperatingMode)
          }
          {...form.fieldProps('operatingMode')}
        />
        <TextInput
          label="Assignee"
          value={form.values.assignee}
          onChange={(value) => form.setValue('assignee', value)}
          helperText="Optional — leave blank to raise unassigned."
          {...form.fieldProps('assignee')}
        />
      </Box>
      <TextInput
        label="Title"
        required
        value={form.values.title}
        onChange={(value) => form.setValue('title', value)}
        {...form.fieldProps('title')}
      />
      <TextInput
        label="Description"
        required
        multiline
        minRows={3}
        value={form.values.description}
        onChange={(value) => form.setValue('description', value)}
        {...form.fieldProps('description')}
      />
    </FormDialog>
  );
};

/* Assign — PATCH /api/v1/fleet/workflow-items/{id}/assignment */
export const AssignWorkflowItemDialog = ({
  open,
  onClose,
  onSaved,
  item,
}: BaseProps & { item: WorkflowItemResponse }) => {
  const form = useFleetForm({
    initialValues: { assignee: item.assignee ?? '', reason: '' },
    schema: {
      assignee: compose(required('Assignee'), maxLength('Assignee', 160)),
      reason: maxLength('Reason', 1000),
    },
    onSubmit: async (values) => {
      await workflowApi.assign(item.id, {
        assignee: values.assignee.trim(),
        reason: values.reason.trim() || undefined,
        expectedVersion: item.version,
      });
      onSaved();
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title={item.assignee ? 'Reassign item' : 'Assign item'}
      description={`${item.workflowNumber} · ${item.title}`}
      submitLabel="Save assignment"
      submitting={form.submitting}
      formError={form.formError}
      onClose={onClose}
      onSubmit={form.submit}
    >
      <TextInput
        label="Assignee"
        required
        value={form.values.assignee}
        onChange={(value) => form.setValue('assignee', value)}
        {...form.fieldProps('assignee')}
      />
      <TextInput
        label="Reason"
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        {...form.fieldProps('reason')}
      />
    </FormDialog>
  );
};

/* Close — PATCH /api/v1/fleet/workflow-items/{id}/closure */
export const CloseWorkflowItemDialog = ({
  open,
  onClose,
  onSaved,
  item,
}: BaseProps & { item: WorkflowItemResponse }) => {
  const form = useFleetForm({
    initialValues: { closureReason: '', closureEvidenceId: '' },
    schema: {
      closureReason: compose(required('Closure reason'), maxLength('Closure reason', 1000)),
      closureEvidenceId: required('Closure evidence'),
    },
    onSubmit: async (values) => {
      await workflowApi.close(item.id, {
        closureReason: values.closureReason.trim(),
        closureEvidenceId: values.closureEvidenceId.trim(),
        expectedVersion: item.version,
      });
      onSaved();
      onClose();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Close workflow item"
      description="Closure reason and evidence are both mandatory."
      submitLabel="Close item"
      submitting={form.submitting}
      formError={form.formError}
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Alert severity="info" variant="outlined">
        Register the evidence under Evidence &amp; audit first. Closing without it is refused with
        FLEET_CLOSURE_EVIDENCE_MISSING.
      </Alert>
      <TextInput
        label="Closure evidence reference ID"
        required
        value={form.values.closureEvidenceId}
        onChange={(value) => form.setValue('closureEvidenceId', value)}
        {...form.fieldProps('closureEvidenceId')}
      />
      <TextInput
        label="Closure reason"
        required
        multiline
        minRows={3}
        value={form.values.closureReason}
        onChange={(value) => form.setValue('closureReason', value)}
        {...form.fieldProps('closureReason')}
      />
    </FormDialog>
  );
};

/**
 * Reason-only transitions: escalate, cancel, reopen, hold and resume.
 *
 * One dialog because the shape is identical; the caller supplies the verb and the request. Hold
 * and resume are the only two where the reason is optional.
 */
export const ReasonTransitionDialog = ({
  open,
  onClose,
  onSaved,
  item,
  transition,
}: BaseProps & {
  item: WorkflowItemResponse;
  transition: 'escalate' | 'cancel' | 'reopen' | 'hold' | 'resume';
}) => {
  const optionalReason = transition === 'hold' || transition === 'resume';

  const labels: Record<typeof transition, { title: string; submit: string; note?: string }> = {
    escalate: {
      title: 'Escalate item',
      submit: 'Escalate',
      note: 'Manual escalation is privileged — the service refuses it without the approval permission.',
    },
    cancel: {
      title: 'Cancel item',
      submit: 'Cancel item',
      note: 'Privileged. The record stays in history.',
    },
    reopen: { title: 'Reopen item', submit: 'Reopen', note: 'Privileged.' },
    hold: { title: 'Place item on hold', submit: 'Place on hold' },
    resume: { title: 'Resume item', submit: 'Resume' },
  };

  const form = useFleetForm({
    initialValues: { reason: '' },
    schema: {
      reason: optionalReason
        ? maxLength('Reason', 1000)
        : compose(required('Reason'), maxLength('Reason', 1000)),
    },
    onSubmit: async (values) => {
      const reason = values.reason.trim();
      const expectedVersion = item.version;
      if (transition === 'escalate') {
        await workflowApi.escalate(item.id, { reason, expectedVersion });
      } else if (transition === 'cancel') {
        await workflowApi.cancel(item.id, { reason, expectedVersion });
      } else if (transition === 'reopen') {
        await workflowApi.reopen(item.id, { reason, expectedVersion });
      } else {
        await workflowApi.holdOrResume(item.id, {
          resume: transition === 'resume',
          reason: reason || undefined,
          expectedVersion,
        });
      }
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title={labels[transition].title}
      description={`${item.workflowNumber} · ${item.title}`}
      submitLabel={labels[transition].submit}
      submitting={form.submitting}
      formError={form.formError}
      destructive={transition === 'cancel'}
      onClose={onClose}
      onSubmit={form.submit}
    >
      {labels[transition].note && <Alert severity="info">{labels[transition].note}</Alert>}
      <TextInput
        label="Reason"
        required={!optionalReason}
        multiline
        minRows={3}
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        {...form.fieldProps('reason')}
      />
    </FormDialog>
  );
};

/* Comment — POST /api/v1/fleet/workflow-items/{id}/comments */
export const AddCommentDialog = ({
  open,
  onClose,
  onSaved,
  item,
}: BaseProps & { item: WorkflowItemResponse }) => {
  const form = useFleetForm({
    initialValues: { body: '' },
    schema: { body: compose(required('Comment'), maxLength('Comment', 4000)) },
    onSubmit: async (values) => {
      await workflowApi.comment(item.id, { body: values.body.trim() });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Add a comment"
      description="Comments are immutable once recorded."
      submitLabel="Add comment"
      submitting={form.submitting}
      formError={form.formError}
      onClose={onClose}
      onSubmit={form.submit}
    >
      <TextInput
        label="Comment"
        required
        multiline
        minRows={4}
        value={form.values.body}
        onChange={(value) => form.setValue('body', value)}
        {...form.fieldProps('body')}
      />
    </FormDialog>
  );
};
