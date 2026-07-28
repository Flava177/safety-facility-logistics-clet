import { useState } from 'react';
import { Alert, Box, Button, Divider, Stack, Tab, Tabs, Typography } from '@mui/material';
import { EvidenceResponse } from 'modules/fleet/api/dto';
import {
  EVIDENCE_RETENTION_CLASSES,
  EvidenceRetentionClass,
  humanise,
} from 'modules/fleet/api/enums';
import { auditApi, evidenceApi } from 'modules/fleet/api/fleetApi';
import DataState from 'shared/components/DataState';
import FormDialog from 'shared/components/FormDialog';
import KeyValueGrid from 'shared/components/KeyValueGrid';
import { useNotifier } from 'shared/components/Notifier';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import StatusChip from 'shared/components/StatusChip';
import { EnumSelect, TextInput } from 'shared/components/fields';
import { formatDateTime } from 'shared/components/format';
import { FleetApiError, isFleetApiError } from 'shared/errors/FleetApiError';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import { fleetPaths } from 'shared/layout/navigation';
import { useFleetForm } from 'shared/validation/useFleetForm';
import { compose, maxLength, required } from 'shared/validation/validators';
import IconifyIcon from 'components/base/IconifyIcon';

const twoColumn = {
  display: 'grid',
  gap: 2,
  gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
} as const;

type TabKey = 'evidence' | 'audit' | 'integrity';

/**
 * Evidence and audit governance.
 *
 * The service exposes evidence by id — there is no evidence search endpoint — so this screen is
 * built around lookup, registration and export approval rather than a browsable list. That
 * limitation is stated on the page instead of being papered over with a fake table.
 */
const GovernancePage = () => {
  const { notifyError, notifySuccess } = useNotifier();
  const [tab, setTab] = useState<TabKey>('evidence');
  const [lookupId, setLookupId] = useState('');
  const [evidence, setEvidence] = useState<EvidenceResponse | undefined>(undefined);
  const [lookupError, setLookupError] = useState<FleetApiError | undefined>(undefined);
  const [lookingUp, setLookingUp] = useState(false);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);

  const audit = useApiQuery(
    (signal) =>
      tab === 'audit' ? auditApi.search({ size: 50 }, signal) : Promise.resolve(undefined),
    [tab],
  );

  const integrity = useApiQuery(
    (signal) => (tab === 'integrity' ? auditApi.verifyChain(signal) : Promise.resolve(undefined)),
    [tab],
  );

  const lookup = async () => {
    if (!lookupId.trim()) {
      return;
    }
    setLookingUp(true);
    setLookupError(undefined);
    try {
      const found = await evidenceApi.findById(lookupId.trim());
      setEvidence(found);
    } catch (error) {
      setEvidence(undefined);
      setLookupError(
        isFleetApiError(error) ? error : FleetApiError.transport('The lookup failed.'),
      );
    } finally {
      setLookingUp(false);
    }
  };

  const recordAccess = async () => {
    if (!evidence) {
      return;
    }
    try {
      const updated = await evidenceApi.recordAccess(evidence.id);
      setEvidence(updated);
      notifySuccess('Access recorded against the evidence record.');
    } catch (error) {
      notifyError(error);
    }
  };

  return (
    <Box>
      <PageHeader
        title="Evidence & audit"
        subtitle="Register evidence references, request exports under approval, and verify the audit hash chain."
        crumbs={[{ label: 'Fleet', to: fleetPaths.dashboard }, { label: 'Evidence & audit' }]}
        actions={
          <Button
            variant="contained"
            color="secondary"
            onClick={() => setRegisterOpen(true)}
            startIcon={<IconifyIcon icon="material-symbols:add-rounded" />}
          >
            Register evidence
          </Button>
        }
      />

      <SectionCard flush>
        <Tabs
          value={tab}
          onChange={(_event, value: TabKey) => setTab(value)}
          sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}
          variant="scrollable"
          allowScrollButtonsMobile
        >
          <Tab label="Evidence" value="evidence" />
          <Tab label="Audit records" value="audit" />
          <Tab label="Chain integrity" value="integrity" />
        </Tabs>

        <Box sx={{ p: 2.5 }}>
          {tab === 'evidence' && (
            <Stack spacing={2.5}>
              <Alert severity="info">
                The service exposes evidence by identifier only — there is no evidence search
                endpoint. Paste a reference ID from a trip closure, inspection or compliance record
                to open it.
              </Alert>

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems="flex-start">
                <TextInput
                  label="Evidence reference ID"
                  value={lookupId}
                  onChange={setLookupId}
                  sx={{ maxWidth: { sm: 420 } }}
                />
                <Button
                  variant="contained"
                  color="secondary"
                  onClick={lookup}
                  disabled={lookingUp || !lookupId.trim()}
                >
                  {lookingUp ? 'Looking up…' : 'Open evidence'}
                </Button>
              </Stack>

              {lookupError && (
                <Alert severity={lookupError.isForbidden ? 'warning' : 'error'}>
                  {lookupError.message}
                  {lookupError.correlationId && (
                    <Typography variant="caption" display="block" color="text.secondary">
                      Correlation ID: {lookupError.correlationId}
                    </Typography>
                  )}
                </Alert>
              )}

              {evidence && (
                <SectionCard
                  title={evidence.fileName}
                  subtitle={`${evidence.relatedRecordType} ${evidence.relatedRecordId}`}
                  actions={
                    <>
                      <Button variant="soft" color="neutral" size="small" onClick={recordAccess}>
                        Record access
                      </Button>
                      <Button
                        variant="soft"
                        color="secondary"
                        size="small"
                        onClick={() => setExportOpen(true)}
                      >
                        Request export
                      </Button>
                    </>
                  }
                >
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <StatusChip value={evidence.retentionClass} tone="neutral" />
                      {evidence.legalHold && (
                        <StatusChip value="LEGAL_HOLD" label="Legal hold" tone="blocked" />
                      )}
                    </Stack>
                    <KeyValueGrid
                      items={[
                        { label: 'Evidence ID', value: evidence.id, span: 2 },
                        { label: 'Site', value: evidence.siteCode },
                        { label: 'Evidence type', value: evidence.evidenceType },
                        { label: 'Content type', value: evidence.contentType },
                        { label: 'Storage reference', value: evidence.storageReference, span: 2 },
                        { label: 'SHA-256', value: evidence.sha256Hash, span: 2 },
                        {
                          label: 'Retention class',
                          value: humanise(evidence.retentionClass),
                        },
                        {
                          label: 'Retention expires',
                          value: formatDateTime(evidence.retentionExpiresAt),
                        },
                        { label: 'Registered by', value: evidence.createdBy ?? '—' },
                        { label: 'Registered at', value: formatDateTime(evidence.createdAt) },
                        {
                          label: 'Correlation ID',
                          value: evidence.auditCorrelationId ?? '—',
                          span: 2,
                        },
                        { label: 'Record version', value: evidence.version },
                      ]}
                    />
                  </Stack>
                </SectionCard>
              )}
            </Stack>
          )}

          {tab === 'audit' && (
            <DataState
              loading={audit.initialising}
              error={audit.error}
              empty={(audit.data?.length ?? 0) === 0}
              emptyTitle="No audit records"
              emptyHint="Audit search requires an auditor role and a site scope."
              onRetry={audit.refetch}
              minHeight={220}
            >
              <Stack divider={<Divider />} spacing={0}>
                {(audit.data ?? []).map((record, index) => (
                  <Box key={String(record.id ?? index)} sx={{ py: 1.5 }}>
                    <Stack
                      direction="row"
                      spacing={1}
                      alignItems="center"
                      justifyContent="space-between"
                      flexWrap="wrap"
                      useFlexGap
                    >
                      <Typography variant="body2" fontWeight={700}>
                        {humanise(String(record.action ?? 'RECORD'))} ·{' '}
                        {String(record.resourceType ?? '')}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatDateTime(record.occurredAt ?? null)}
                      </Typography>
                    </Stack>
                    <Typography variant="caption" color="text.secondary" display="block">
                      {String(record.resourceId ?? '')} · actor{' '}
                      {String(record.actorId ?? 'unknown')}
                      {record.siteCode ? ` · ${String(record.siteCode)}` : ''}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            </DataState>
          )}

          {tab === 'integrity' && (
            <DataState
              loading={integrity.initialising}
              error={integrity.error}
              onRetry={integrity.refetch}
              minHeight={220}
            >
              {integrity.data && (
                <Stack spacing={2}>
                  <Alert severity={integrity.data.intact ? 'success' : 'error'}>
                    {integrity.data.intact
                      ? `Audit hash chain is intact across ${integrity.data.recordsChecked} records.`
                      : 'Audit integrity check failed. Escalate to compliance and security.'}
                  </Alert>
                  <KeyValueGrid
                    items={[
                      { label: 'Records checked', value: integrity.data.recordsChecked },
                      {
                        label: 'First divergent sequence',
                        value: integrity.data.firstDivergentSequence ?? '—',
                      },
                      { label: 'Reason', value: integrity.data.reason ?? '—' },
                      {
                        label: 'Expected value',
                        value: integrity.data.expectedValue ?? '—',
                        span: 2,
                      },
                      { label: 'Actual value', value: integrity.data.actualValue ?? '—', span: 2 },
                      { label: 'Head hash', value: integrity.data.headHash ?? '—', span: 2 },
                    ]}
                  />
                </Stack>
              )}
            </DataState>
          )}
        </Box>
      </SectionCard>

      <RegisterEvidenceDialog
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        onSaved={(created) => {
          notifySuccess('Evidence registered.', `Reference ID ${created.id}`);
          setEvidence(created);
          setLookupId(created.id);
        }}
      />

      {evidence && (
        <RequestExportDialog
          open={exportOpen}
          evidenceId={evidence.id}
          onClose={() => setExportOpen(false)}
          onSaved={() => notifySuccess('Export requested. It needs a separate approver.')}
        />
      )}
    </Box>
  );
};

/* Register evidence — POST /api/v1/fleet/evidence */
const RegisterEvidenceDialog = ({
  open,
  onClose,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  onSaved: (evidence: EvidenceResponse) => void;
}) => {
  const form = useFleetForm({
    initialValues: {
      siteCode: '',
      relatedRecordType: '',
      relatedRecordId: '',
      evidenceType: '',
      fileName: '',
      contentType: 'application/pdf',
      storageReference: '',
      sha256Hash: '',
      retentionClass: 'OPERATIONAL_1_YEAR' as EvidenceRetentionClass,
    },
    schema: {
      siteCode: compose(required('Site code'), maxLength('Site code', 40)),
      relatedRecordType: required('Related record type'),
      relatedRecordId: required('Related record ID'),
      evidenceType: required('Evidence type'),
      fileName: required('File name'),
      contentType: required('Content type'),
      storageReference: required('Storage reference'),
      sha256Hash: required('SHA-256 hash'),
      retentionClass: required('Retention class'),
    },
    onSubmit: async (values) => {
      const created = await evidenceApi.register({
        siteCode: values.siteCode.trim().toUpperCase(),
        relatedRecordType: values.relatedRecordType.trim(),
        relatedRecordId: values.relatedRecordId.trim(),
        evidenceType: values.evidenceType.trim(),
        fileName: values.fileName.trim(),
        contentType: values.contentType.trim(),
        storageReference: values.storageReference.trim(),
        sha256Hash: values.sha256Hash.trim(),
        retentionClass: values.retentionClass,
      });
      onSaved(created);
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Register an evidence reference"
      description="The service stores a reference and a hash, not the file itself. A retention class is mandatory."
      submitLabel="Register evidence"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="md"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Box sx={twoColumn}>
        <TextInput
          label="Site code"
          required
          value={form.values.siteCode}
          onChange={(value) => form.setValue('siteCode', value)}
          {...form.fieldProps('siteCode')}
        />
        <EnumSelect
          label="Retention class"
          required
          value={form.values.retentionClass}
          options={EVIDENCE_RETENTION_CLASSES}
          onChange={(value) =>
            form.setValue(
              'retentionClass',
              (value || 'OPERATIONAL_1_YEAR') as EvidenceRetentionClass,
            )
          }
          {...form.fieldProps('retentionClass')}
        />
        <TextInput
          label="Related record type"
          required
          value={form.values.relatedRecordType}
          onChange={(value) => form.setValue('relatedRecordType', value)}
          helperText="For example Trip, VehicleInspection or ComplianceDocument."
          {...form.fieldProps('relatedRecordType')}
        />
        <TextInput
          label="Related record ID"
          required
          value={form.values.relatedRecordId}
          onChange={(value) => form.setValue('relatedRecordId', value)}
          {...form.fieldProps('relatedRecordId')}
        />
        <TextInput
          label="Evidence type"
          required
          value={form.values.evidenceType}
          onChange={(value) => form.setValue('evidenceType', value)}
          {...form.fieldProps('evidenceType')}
        />
        <TextInput
          label="File name"
          required
          value={form.values.fileName}
          onChange={(value) => form.setValue('fileName', value)}
          {...form.fieldProps('fileName')}
        />
        <TextInput
          label="Content type"
          required
          value={form.values.contentType}
          onChange={(value) => form.setValue('contentType', value)}
          {...form.fieldProps('contentType')}
        />
        <TextInput
          label="Storage reference"
          required
          value={form.values.storageReference}
          onChange={(value) => form.setValue('storageReference', value)}
          {...form.fieldProps('storageReference')}
        />
      </Box>
      <TextInput
        label="SHA-256 hash"
        required
        value={form.values.sha256Hash}
        onChange={(value) => form.setValue('sha256Hash', value)}
        {...form.fieldProps('sha256Hash')}
      />
    </FormDialog>
  );
};

/* Request export — POST /api/v1/fleet/evidence/{id}/export-requests */
const RequestExportDialog = ({
  open,
  evidenceId,
  onClose,
  onSaved,
}: {
  open: boolean;
  evidenceId: string;
  onClose: () => void;
  onSaved: () => void;
}) => {
  const form = useFleetForm({
    initialValues: { reason: '' },
    schema: { reason: compose(required('Reason'), maxLength('Reason', 1000)) },
    onSubmit: async (values) => {
      await evidenceApi.requestExport(evidenceId, { reason: values.reason.trim() });
      onSaved();
      onClose();
      form.reset();
    },
  });

  return (
    <FormDialog
      open={open}
      title="Request an evidence export"
      description="Exports require a recorded reason and approval by someone other than the requester."
      submitLabel="Request export"
      submitting={form.submitting}
      formError={form.formError}
      onClose={onClose}
      onSubmit={form.submit}
    >
      <TextInput
        label="Reason"
        required
        multiline
        minRows={3}
        value={form.values.reason}
        onChange={(value) => form.setValue('reason', value)}
        {...form.fieldProps('reason')}
      />
    </FormDialog>
  );
};

export default GovernancePage;
