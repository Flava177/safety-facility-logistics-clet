import { CustodyHandover, DispatchManifest } from 'modules/dispatch/api/dto';
import {
  CUSTODY_HOPS,
  CustodyHop,
  HOP_DESCRIPTIONS,
  SEAL_STATES,
  SealState,
} from 'modules/dispatch/api/enums';
import { custodyApi, receiptsApi, returnsApi } from 'modules/dispatch/api/dispatchApi';
import Alert from 'shared/components/Alert';
import FormDialog from 'shared/components/FormDialog';
import { DateTimeField } from 'shared/components/DateField';
import { Checkbox, EnumSelect, NumberInput, TextAreaInput, TextInput } from 'shared/components/fields';
import { EVIDENCE_RETENTION_CLASSES, EvidenceRetentionClass, humanise } from 'modules/fleet/api/enums';
import { useFleetForm } from 'shared/validation/useFleetForm';
import { compose, integerAtLeast, maxLength, required } from 'shared/validation/validators';

const twoColumn = 'grid gap-4 sm:grid-cols-2';

interface RecordHandoverDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  manifest: DispatchManifest;
  /** What is already recorded, so the dialog can suggest the next hop rather than the first. */
  recorded: CustodyHandover[];
}

/**
 * Record a custody handover — `POST /custody`.
 *
 * Append-only. There is no edit and no delete: the chain **is** the evidence, and a correction is
 * another handover, not a rewrite of this one.
 *
 * Two fields decide whether this handover reads as clean or as a gap. A compromised seal state
 * (`BROKEN` or `MISSING`) is recorded as a gap immediately, and a verified count that disagrees with
 * the manifest is recorded as a count mismatch. Both are shown as consequences here so the operator
 * knows what they are about to create.
 */
export const RecordHandoverDialog = ({
  open,
  onClose,
  onSaved,
  manifest,
  recorded,
}: RecordHandoverDialogProps) => {
  // The next hop that has not been recorded, so the common case needs no thought.
  const suggestedHop =
    CUSTODY_HOPS.find((hop) => !recorded.some((handover) => handover.hop === hop)) ?? 'DISPATCH';

  const form = useFleetForm({
    initialValues: {
      hop: suggestedHop as CustodyHop,
      transferringCustodian: '',
      receivingCustodian: '',
      occurredAt: '',
      sealState: 'INTACT' as SealState,
      verifiedCount: String(manifest.itemCount),
      notes: '',
      evidenceStorageReference: '',
      evidenceFileName: '',
      evidenceSha256: '',
      retentionClass: '' as EvidenceRetentionClass | '',
    },
    schema: {
      hop: required('Hop'),
      transferringCustodian: compose(
        required('Transferring custodian'),
        maxLength('Transferring custodian', 200),
      ),
      receivingCustodian: compose(
        required('Receiving custodian'),
        maxLength('Receiving custodian', 200),
      ),
      sealState: required('Seal state'),
      verifiedCount: integerAtLeast('Verified count', 0),
      notes: maxLength('Notes', 1000),
      evidenceStorageReference: maxLength('Evidence reference', 500),
      evidenceFileName: maxLength('Evidence file name', 255),
      evidenceSha256: maxLength('Evidence checksum', 128),
      
    },
    onSubmit: async (values) => {
      await custodyApi.record({
        dispatchId: manifest.id,
        hop: values.hop,
        transferringCustodian: values.transferringCustodian.trim(),
        receivingCustodian: values.receivingCustodian.trim(),
        occurredAt: values.occurredAt ? new Date(values.occurredAt).toISOString() : null,
        sealState: values.sealState,
        verifiedCount: values.verifiedCount === '' ? null : Number(values.verifiedCount),
        notes: values.notes.trim() || null,
        evidenceFileName: values.evidenceFileName.trim() || null,
        evidenceContentType: null,
        evidenceStorageReference: values.evidenceStorageReference.trim() || null,
        evidenceSha256: values.evidenceSha256.trim() || null,
        retentionClass: values.retentionClass || null,
      });
      onSaved();
      onClose();
    },
  });

  const sealCompromised = ['BROKEN', 'MISSING'].includes(form.values.sealState);
  const countMismatch =
    form.values.verifiedCount !== '' &&
    manifest.itemCount > 0 &&
    Number(form.values.verifiedCount) !== manifest.itemCount;
  const alreadyRecorded = recorded.some((handover) => handover.hop === form.values.hop);

  return (
    <FormDialog
      open={open}
      title="Record a custody handover"
      description={`${manifest.manifestNumber} · ${manifest.route}`}
      submitLabel="Record handover"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="lg"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <Alert variant="info" title="This cannot be edited afterwards">
        The chain of custody is append-only — it is the evidence. If something is recorded wrongly,
        the correction is another handover, not a change to this one.
      </Alert>

      <div className={twoColumn}>
        <EnumSelect
          label="Hop"
          required
          value={form.values.hop}
          options={CUSTODY_HOPS}
          onChange={(value) => form.setValue('hop', (value || 'DISPATCH') as CustodyHop)}
          {...form.fieldProps('hop', HOP_DESCRIPTIONS[form.values.hop])}
        />
        <DateTimeField
          label="Occurred at"
          value={form.values.occurredAt}
          onChange={(value) => form.setValue('occurredAt', value)}
          {...form.fieldProps('occurredAt', 'Leave blank to record it as now.')}
        />
        <TextInput
          label="Transferring custodian"
          required
          value={form.values.transferringCustodian}
          onChange={(value) => form.setValue('transferringCustodian', value)}
          {...form.fieldProps('transferringCustodian', 'Who is handing the consignment over.')}
        />
        <TextInput
          label="Receiving custodian"
          required
          value={form.values.receivingCustodian}
          onChange={(value) => form.setValue('receivingCustodian', value)}
          {...form.fieldProps('receivingCustodian', 'Who becomes accountable for it.')}
        />
        <EnumSelect
          label="Seal state"
          required
          value={form.values.sealState}
          options={SEAL_STATES}
          onChange={(value) => form.setValue('sealState', (value || 'INTACT') as SealState)}
          {...form.fieldProps('sealState')}
        />
        <NumberInput
          label="Verified count"
          value={form.values.verifiedCount}
          onChange={(value) => form.setValue('verifiedCount', value)}
          {...form.fieldProps(
            'verifiedCount',
            manifest.itemCount > 0
              ? `The manifest expects ${manifest.itemCount}.`
              : 'Leave blank not to check the count at this hop.',
          )}
        />
      </div>

      {alreadyRecorded && (
        <Alert variant="warning" title="This hop already has a handover">
          Recording a second one is allowed and is sometimes right — a consignment can change hands
          twice in transit. It will appear alongside the first, not replace it.
        </Alert>
      )}

      {(sealCompromised || countMismatch) && (
        <Alert variant="error" title="This will be recorded as a custody gap">
          <ul className="mt-1 list-disc space-y-1 pl-4">
            {sealCompromised && (
              <li>
                A {humanise(form.values.sealState).toLowerCase()} seal is a compromised seal, and the
                chain records it as a gap at this hop.
              </li>
            )}
            {countMismatch && (
              <li>
                {form.values.verifiedCount} verified against {manifest.itemCount} expected is a count
                mismatch at this hop.
              </li>
            )}
          </ul>
          <p className="mt-2">
            Record it anyway if it is what happened — that is what the chain is for. The manifest
            will not close until the gap is resolved through an exception case.
          </p>
        </Alert>
      )}

      <TextAreaInput
        label="Notes"
        rows={2}
        value={form.values.notes}
        onChange={(value) => form.setValue('notes', value)}
        {...form.fieldProps('notes')}
      />

      <div className={twoColumn}>
        <TextInput
          label="Evidence storage reference"
          value={form.values.evidenceStorageReference}
          onChange={(value) => form.setValue('evidenceStorageReference', value)}
          {...form.fieldProps('evidenceStorageReference', 'Optional. A photograph or signature.')}
        />
        <TextInput
          label="Evidence file name"
          value={form.values.evidenceFileName}
          onChange={(value) => form.setValue('evidenceFileName', value)}
          {...form.fieldProps('evidenceFileName')}
        />
        <TextInput
          label="Evidence checksum"
          value={form.values.evidenceSha256}
          onChange={(value) => form.setValue('evidenceSha256', value)}
          {...form.fieldProps('evidenceSha256')}
        />
        <EnumSelect
          label="Retention class"
          value={form.values.retentionClass}
          options={EVIDENCE_RETENTION_CLASSES}
          onChange={(value) => form.setValue('retentionClass', value)}
          allowEmpty
          emptyLabel="Not set"
          {...form.fieldProps('retentionClass', 'How long the evidence must be kept.')}
        />
      </div>
    </FormDialog>
  );
};

interface ConfirmReceiptDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  manifest: DispatchManifest;
}

/**
 * Confirm receipt at the destination — `POST /receipts`.
 *
 * The **outcome is derived, not chosen**: `ReceiptVariancePolicy` compares seal state, counts and
 * recipient against the manifest and decides CLEAN or which of the five variances it is. This dialog
 * previews that decision so the operator sees what their entries will produce, and shows the
 * variance as a consequence rather than as a field to set.
 *
 * `captureCorrelationId` makes the confirmation idempotent, which is what lets a receipt taken
 * offline at the destination be replayed safely when connectivity returns.
 */
export const ConfirmReceiptDialog = ({
  open,
  onClose,
  onSaved,
  manifest,
}: ConfirmReceiptDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      sealState: 'INTACT' as SealState,
      sealVerified: true,
      expectedCount: String(manifest.itemCount),
      verifiedCount: String(manifest.itemCount),
      recipientName: '',
      expectedRecipient: '',
      capturedAt: '',
      edgeCaptured: false,
      captureCorrelationId: '',
      signatureStorageReference: '',
      signatureFileName: '',
      signatureSha256: '',
      retentionClass: '' as EvidenceRetentionClass | '',
    },
    schema: {
      sealState: required('Seal state'),
      expectedCount: integerAtLeast('Expected count', 0),
      verifiedCount: compose(required('Verified count'), integerAtLeast('Verified count', 0)),
      recipientName: compose(required('Recipient'), maxLength('Recipient', 200)),
      expectedRecipient: maxLength('Expected recipient', 200),
      captureCorrelationId: maxLength('Capture correlation ID', 120),
      signatureStorageReference: maxLength('Signature reference', 500),
      signatureFileName: maxLength('Signature file name', 255),
      signatureSha256: maxLength('Signature checksum', 128),
      
    },
    onSubmit: async (values) => {
      await receiptsApi.confirm({
        dispatchId: manifest.id,
        sealState: values.sealState,
        sealVerified: values.sealVerified,
        expectedCount: values.expectedCount === '' ? null : Number(values.expectedCount),
        verifiedCount: Number(values.verifiedCount),
        recipientName: values.recipientName.trim(),
        expectedRecipient: values.expectedRecipient.trim() || null,
        captureCorrelationId: values.captureCorrelationId.trim() || null,
        edgeCaptured: values.edgeCaptured,
        capturedAt: values.capturedAt ? new Date(values.capturedAt).toISOString() : null,
        signatureFileName: values.signatureFileName.trim() || null,
        signatureContentType: null,
        signatureStorageReference: values.signatureStorageReference.trim() || null,
        signatureSha256: values.signatureSha256.trim() || null,
        retentionClass: values.retentionClass || null,
      });
      onSaved();
      onClose();
    },
  });

  /** The same comparison `ReceiptVariancePolicy` makes, so the preview cannot disagree with it. */
  const expected = form.values.expectedCount === '' ? null : Number(form.values.expectedCount);
  const verified = form.values.verifiedCount === '' ? null : Number(form.values.verifiedCount);
  const predicted: string[] = [];
  if (['BROKEN', 'MISSING'].includes(form.values.sealState) || !form.values.sealVerified) {
    predicted.push('Broken seal');
  }
  if (expected !== null && verified !== null && verified < expected) {
    predicted.push('Short count');
  }
  if (expected !== null && verified !== null && verified > expected) {
    predicted.push('Over count');
  }
  if (
    form.values.expectedRecipient.trim() &&
    form.values.recipientName.trim() &&
    form.values.expectedRecipient.trim().toLowerCase() !==
      form.values.recipientName.trim().toLowerCase()
  ) {
    predicted.push('Wrong recipient');
  }
  if (!form.values.signatureStorageReference.trim()) {
    predicted.push('Missing signature');
  }

  return (
    <FormDialog
      open={open}
      title="Confirm receipt"
      description={`${manifest.manifestNumber} · ${manifest.destinationCentre ?? manifest.route}`}
      submitLabel="Confirm receipt"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="lg"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <div className={twoColumn}>
        <EnumSelect
          label="Seal state"
          required
          value={form.values.sealState}
          options={SEAL_STATES}
          onChange={(value) => form.setValue('sealState', (value || 'INTACT') as SealState)}
          {...form.fieldProps('sealState')}
        />
        <TextInput
          label="Received by"
          required
          value={form.values.recipientName}
          onChange={(value) => form.setValue('recipientName', value)}
          {...form.fieldProps('recipientName', 'Who signed for the consignment.')}
        />
        <NumberInput
          label="Expected count"
          value={form.values.expectedCount}
          onChange={(value) => form.setValue('expectedCount', value)}
          {...form.fieldProps('expectedCount', `The manifest carries ${manifest.itemCount}.`)}
        />
        <NumberInput
          label="Verified count"
          required
          value={form.values.verifiedCount}
          onChange={(value) => form.setValue('verifiedCount', value)}
          {...form.fieldProps('verifiedCount', 'What was actually counted on arrival.')}
        />
        <TextInput
          label="Expected recipient"
          value={form.values.expectedRecipient}
          onChange={(value) => form.setValue('expectedRecipient', value)}
          {...form.fieldProps('expectedRecipient', 'Optional. Checked against who signed.')}
        />
        <DateTimeField
          label="Captured at"
          value={form.values.capturedAt}
          onChange={(value) => form.setValue('capturedAt', value)}
          {...form.fieldProps('capturedAt', 'When the receipt was taken, if not now.')}
        />
      </div>

      <Checkbox
        checked={form.values.sealVerified}
        onChange={(checked) => form.setValue('sealVerified', checked)}
        label="The seal was checked against the manifest"
        hint="Leaving this unchecked is itself a variance — an unverified seal is not an intact one."
      />

      <Alert variant={predicted.length === 0 ? 'success' : 'warning'} title="Outcome">
        {predicted.length === 0 ? (
          'These entries will record a clean receipt.'
        ) : (
          <>
            These entries will record a variance: <strong>{predicted.join(', ')}</strong>. The
            service decides the outcome from the seal, the counts and the recipient — it is not
            chosen here. A variance raises an exception case and blocks the manifest from closing.
          </>
        )}
      </Alert>

      <Checkbox
        checked={form.values.edgeCaptured}
        onChange={(checked) => form.setValue('edgeCaptured', checked)}
        label="Captured offline at the destination"
        hint="Mark this when the receipt was taken without connectivity and is being replayed now."
      />

      <div className={twoColumn}>
        <TextInput
          label="Capture correlation ID"
          value={form.values.captureCorrelationId}
          onChange={(value) => form.setValue('captureCorrelationId', value)}
          {...form.fieldProps(
            'captureCorrelationId',
            'Makes the confirmation idempotent, so a replay cannot double-record it.',
          )}
        />
        <TextInput
          label="Signature storage reference"
          value={form.values.signatureStorageReference}
          onChange={(value) => form.setValue('signatureStorageReference', value)}
          {...form.fieldProps('signatureStorageReference', 'Absent, this counts as a variance.')}
        />
        <TextInput
          label="Signature file name"
          value={form.values.signatureFileName}
          onChange={(value) => form.setValue('signatureFileName', value)}
          {...form.fieldProps('signatureFileName')}
        />
        <EnumSelect
          label="Retention class"
          value={form.values.retentionClass}
          options={EVIDENCE_RETENTION_CLASSES}
          onChange={(value) => form.setValue('retentionClass', value)}
          allowEmpty
          emptyLabel="Not set"
          {...form.fieldProps('retentionClass', 'How long the evidence must be kept.')}
        />
      </div>
    </FormDialog>
  );
};

interface ReconcileReturnDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  manifest: DispatchManifest;
}

/**
 * Reconcile the return leg — `POST /returns/reconcile`.
 *
 * Shortfall, extras and the outcome are derived by `ReturnReconciliationPolicy` from the counts;
 * this previews the arithmetic so the operator sees what a discrepancy will look like before it is
 * recorded, and knows that a discrepancy raises a case.
 */
export const ReconcileReturnDialog = ({
  open,
  onClose,
  onSaved,
  manifest,
}: ReconcileReturnDialogProps) => {
  const form = useFleetForm({
    initialValues: {
      expectedCount: String(manifest.itemCount),
      returnedCount: '',
      brokenSeals: '0',
      notes: '',
      evidenceStorageReference: '',
      evidenceFileName: '',
      evidenceSha256: '',
      retentionClass: '' as EvidenceRetentionClass | '',
    },
    schema: {
      expectedCount: integerAtLeast('Expected count', 0),
      returnedCount: compose(required('Returned count'), integerAtLeast('Returned count', 0)),
      brokenSeals: compose(required('Broken seals'), integerAtLeast('Broken seals', 0)),
      notes: maxLength('Notes', 1000),
      evidenceStorageReference: maxLength('Evidence reference', 500),
      evidenceFileName: maxLength('Evidence file name', 255),
      evidenceSha256: maxLength('Evidence checksum', 128),
      
    },
    onSubmit: async (values) => {
      await returnsApi.reconcile({
        dispatchId: manifest.id,
        expectedCount: values.expectedCount === '' ? null : Number(values.expectedCount),
        returnedCount: Number(values.returnedCount),
        brokenSeals: Number(values.brokenSeals),
        notes: values.notes.trim() || null,
        evidenceFileName: values.evidenceFileName.trim() || null,
        evidenceContentType: null,
        evidenceStorageReference: values.evidenceStorageReference.trim() || null,
        evidenceSha256: values.evidenceSha256.trim() || null,
        retentionClass: values.retentionClass || null,
      });
      onSaved();
      onClose();
    },
  });

  const expected = form.values.expectedCount === '' ? 0 : Number(form.values.expectedCount);
  const returned = form.values.returnedCount === '' ? null : Number(form.values.returnedCount);
  const broken = form.values.brokenSeals === '' ? 0 : Number(form.values.brokenSeals);
  const shortfall = returned === null ? null : Math.max(expected - returned, 0);
  const extras = returned === null ? null : Math.max(returned - expected, 0);
  const discrepancy = returned !== null && (shortfall! > 0 || extras! > 0 || broken > 0);

  return (
    <FormDialog
      open={open}
      title="Reconcile the return leg"
      description={`${manifest.manifestNumber} · ${manifest.itemCount} item${manifest.itemCount === 1 ? '' : 's'} went out`}
      submitLabel="Record reconciliation"
      submitting={form.submitting}
      formError={form.formError}
      maxWidth="lg"
      onClose={onClose}
      onSubmit={form.submit}
    >
      <div className={twoColumn}>
        <NumberInput
          label="Expected back"
          value={form.values.expectedCount}
          onChange={(value) => form.setValue('expectedCount', value)}
          {...form.fieldProps('expectedCount', 'Defaults to what the manifest carried out.')}
        />
        <NumberInput
          label="Returned"
          required
          value={form.values.returnedCount}
          onChange={(value) => form.setValue('returnedCount', value)}
          {...form.fieldProps('returnedCount', 'What actually came back.')}
        />
        <NumberInput
          label="Broken seals"
          required
          value={form.values.brokenSeals}
          onChange={(value) => form.setValue('brokenSeals', value)}
          {...form.fieldProps('brokenSeals', 'A single broken seal makes the return a discrepancy.')}
        />
      </div>

      {returned !== null && (
        <Alert variant={discrepancy ? 'warning' : 'success'} title="Outcome">
          {discrepancy ? (
            <>
              This will record a <strong>discrepancy</strong>
              {shortfall! > 0 && ` — ${shortfall} short`}
              {extras! > 0 && ` — ${extras} more than expected`}
              {broken > 0 && ` — ${broken} broken seal${broken === 1 ? '' : 's'}`}. An exception case
              is raised and the manifest cannot close until it is resolved.
            </>
          ) : (
            'The return matches the manifest. This will record a clean reconciliation.'
          )}
        </Alert>
      )}

      <TextAreaInput
        label="Notes"
        rows={2}
        value={form.values.notes}
        onChange={(value) => form.setValue('notes', value)}
        {...form.fieldProps('notes')}
      />

      <div className={twoColumn}>
        <TextInput
          label="Evidence storage reference"
          value={form.values.evidenceStorageReference}
          onChange={(value) => form.setValue('evidenceStorageReference', value)}
          {...form.fieldProps('evidenceStorageReference')}
        />
        <TextInput
          label="Evidence file name"
          value={form.values.evidenceFileName}
          onChange={(value) => form.setValue('evidenceFileName', value)}
          {...form.fieldProps('evidenceFileName')}
        />
        <TextInput
          label="Evidence checksum"
          value={form.values.evidenceSha256}
          onChange={(value) => form.setValue('evidenceSha256', value)}
          {...form.fieldProps('evidenceSha256')}
        />
        <EnumSelect
          label="Retention class"
          value={form.values.retentionClass}
          options={EVIDENCE_RETENTION_CLASSES}
          onChange={(value) => form.setValue('retentionClass', value)}
          allowEmpty
          emptyLabel="Not set"
          {...form.fieldProps('retentionClass', 'How long the evidence must be kept.')}
        />
      </div>
    </FormDialog>
  );
};
