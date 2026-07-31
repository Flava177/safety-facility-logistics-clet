import { useState } from 'react';
import Alert from 'shared/components/Alert';
import FormDialog from 'shared/components/FormDialog';
import { NumberInput, SelectInput, TextAreaInput, TextInput } from 'shared/components/fields';
import { FleetApiError, isFleetApiError } from 'shared/errors/FleetApiError';
import type { AttachEvidenceRequest } from '../api/dto';
import type { EvidenceType, RetentionClass } from '../api/enums';
import { evidenceTypes, retentionClasses } from '../api/enums';
import { humaniseCode } from '../components/facilitiesFormat';

interface AttachEvidenceDialogProps {
  onClose: () => void;
  onSubmit: (request: AttachEvidenceRequest) => Promise<void>;
}

/** Lower-case hex SHA-256. The service applies the same pattern, and so does the database. */
const SHA_256 = /^[0-9a-fA-F]{64}$/;

/**
 * Attaching closure evidence — SRS-SFL-S153-03.
 *
 * ## Why this form asks for a reference and a hash rather than a file
 *
 * The architecture standard stores evidence **by reference**: the file goes to the document and
 * object-storage service, which returns where it landed and what it hashed to, and this service
 * records those two facts. Nothing here ever holds the bytes, so there is no upload control — and
 * that is a deliberate design rather than a missing feature. It is recorded in the gap report as
 * the reason this dialog looks unusual.
 *
 * The digest is validated at the edge as well as in the domain because a mistyped hash is the one
 * error that would otherwise surface years later, during an integrity check, on evidence nobody can
 * re-hash.
 *
 * ## Why the retention class cannot be skipped
 *
 * Disposal is the irreversible half of retention. Evidence with no class attached has no defensible
 * date on which anybody may delete it, so in practice it is either kept forever or deleted by
 * whoever is clearing space — and only one of those failures is visible. The service refuses without
 * it; the form says why.
 */
const AttachEvidenceDialog = ({ onClose, onSubmit }: AttachEvidenceDialogProps) => {
  const [evidenceType, setEvidenceType] = useState<EvidenceType>('AFTER_PHOTO');
  const [fileReference, setFileReference] = useState('');
  const [fileName, setFileName] = useState('');
  const [mediaType, setMediaType] = useState('');
  const [sizeBytes, setSizeBytes] = useState('');
  const [contentHash, setContentHash] = useState('');
  const [retentionClass, setRetentionClass] = useState<RetentionClass>('OPERATIONAL');
  const [notes, setNotes] = useState('');
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<FleetApiError | undefined>();

  const missingReference = fileReference.trim().length === 0;
  const badHash = !SHA_256.test(contentHash.trim());
  const invalid = missingReference || badHash;

  const submit = async () => {
    setTouched(true);
    if (invalid) {
      return;
    }
    setSubmitting(true);
    setFormError(undefined);
    try {
      await onSubmit({
        evidenceType,
        fileReference: fileReference.trim(),
        fileName: fileName.trim() || null,
        mediaType: mediaType.trim() || null,
        sizeBytes: sizeBytes.trim() === '' ? null : Number(sizeBytes),
        contentHash: contentHash.trim().toLowerCase(),
        retentionClass,
        notes: notes.trim() || null,
      });
    } catch (cause) {
      setFormError(isFleetApiError(cause) ? cause : undefined);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <FormDialog
      open
      title="Attach evidence"
      description="By reference. The file itself lives in document storage."
      submitLabel="Attach"
      submitting={submitting}
      formError={formError}
      onClose={onClose}
      onSubmit={submit}
    >
      <div className="space-y-4">
        <SelectInput
          label="What it is"
          value={evidenceType}
          onChange={(value) => setEvidenceType(value as EvidenceType)}
          required
          options={evidenceTypes.map((value) => ({ value, label: humaniseCode(value) }))}
          helperText={
            evidenceType === 'INVOICE'
              ? 'An invoice proves money was spent, not that the work was done — it does not count towards closure.'
              : 'Counts towards the closure requirement.'
          }
        />

        <TextInput
          label="File reference"
          value={fileReference}
          onChange={setFileReference}
          onBlur={() => setTouched(true)}
          required
          maxLength={500}
          placeholder="e.g. s3://clet-evidence/work-orders/…/after.jpg"
          error={touched && missingReference}
          helperText={
            touched && missingReference
              ? 'Where the file is stored is required.'
              : 'Given back by document storage when the file was uploaded.'
          }
        />

        <TextInput
          label="SHA-256"
          value={contentHash}
          onChange={setContentHash}
          onBlur={() => setTouched(true)}
          required
          maxLength={64}
          placeholder="64 hexadecimal characters"
          error={touched && badHash}
          helperText={
            touched && badHash
              ? 'Must be a 64-character hex SHA-256 digest.'
              : 'Recorded at upload, so a later integrity check can tell if the stored file changed.'
          }
        />

        <SelectInput
          label="Retention class"
          value={retentionClass}
          onChange={(value) => setRetentionClass(value as RetentionClass)}
          required
          options={retentionClasses.map((value) => ({ value, label: humaniseCode(value) }))}
          helperText="Mandatory. It is what sets the date this may eventually be disposed of."
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextInput
            label="File name"
            value={fileName}
            onChange={setFileName}
            maxLength={300}
            placeholder="after.jpg"
          />
          <TextInput
            label="Media type"
            value={mediaType}
            onChange={setMediaType}
            maxLength={120}
            placeholder="image/jpeg"
          />
        </div>

        <NumberInput label="Size (bytes)" value={sizeBytes} onChange={setSizeBytes} min={0} />

        <TextAreaInput
          label="Notes"
          value={notes}
          onChange={setNotes}
          rows={2}
          placeholder="Anything a reviewer would need to make sense of it."
        />

        <Alert variant="info" title="No file is uploaded here">
          <p className="text-theme-sm">
            This service stores references and hashes, never the files themselves. Upload to document
            storage first, then record what it gave you back.
          </p>
        </Alert>
      </div>
    </FormDialog>
  );
};

export default AttachEvidenceDialog;
