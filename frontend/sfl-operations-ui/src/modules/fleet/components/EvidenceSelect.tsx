import { useMemo, useState } from 'react';
import { evidenceApi } from 'modules/fleet/api/fleetApi';
import Button from 'shared/components/Button';
import { SelectInput, TextInput, type SelectOption } from 'shared/components/fields';
import { useApiQuery } from 'shared/hooks/useApiQuery';

/**
 * Picks evidence already filed against a record.
 *
 * Every closure dialog in this dashboard used to ask an operator to paste an evidence reference id.
 * The identifier is a UUID that appears on no paperwork, so the real workflow was: open Evidence &
 * audit in another tab, find the record, copy the id, come back. The S166 gap register called that
 * the main usability cost in the whole dashboard, and it was worst exactly where it mattered most —
 * closing a workflow item, where evidence is mandatory and the service refuses the close without it.
 *
 * `GET /evidence?relatedRecordType=&relatedRecordId=` is the whole fix. The record is the only thing
 * an operator reliably knows, and it is what a workflow item already carries.
 *
 * **The text field does not go away.** Evidence filed against a different record is a legitimate
 * reference — a site-wide certificate closes a dozen items and belongs to none of them — so the
 * picker offers what it found and gets out of the way when the answer is somewhere else. It also
 * falls back to the text field when the record has no evidence at all, which is the state an
 * operator is in the first time they close anything.
 */
interface EvidenceSelectProps {
  /** Both are needed to query. Either being absent means the picker cannot run, not that it failed. */
  relatedRecordType: string | null;
  relatedRecordId: string | null;
  value: string;
  onChange: (value: string) => void;
  label?: string;
  required?: boolean;
  error?: boolean;
  helperText?: string;
  onBlur?: () => void;
  disabled?: boolean;
  className?: string;
}

export const EvidenceSelect = ({
  relatedRecordType,
  relatedRecordId,
  value,
  onChange,
  label = 'Evidence reference',
  required,
  error,
  helperText,
  onBlur,
  disabled,
  className,
}: EvidenceSelectProps) => {
  const [manual, setManual] = useState(false);

  const evidence = useApiQuery(
    (signal) =>
      relatedRecordType && relatedRecordId
        ? evidenceApi.search({ relatedRecordType, relatedRecordId }, signal)
        : Promise.resolve(undefined),
    [relatedRecordType, relatedRecordId],
  );

  const options = useMemo<SelectOption[]>(
    () =>
      (evidence.data ?? []).map((reference) => ({
        value: reference.id,
        // File name first: it is what the operator recognises. The type disambiguates two scans of
        // the same job, and the legal hold is worth seeing before it is cited in a closure.
        label: `${reference.fileName} · ${reference.evidenceType}${reference.legalHold ? ' · legal hold' : ''}`,
      })),
    [evidence.data],
  );

  /** No record to query, nothing filed against it, or the operator asked for the text field. */
  const typing = manual || !relatedRecordType || !relatedRecordId || options.length === 0;

  if (typing) {
    return (
      <div className={className}>
        <TextInput
          label={label}
          required={required}
          value={value}
          onChange={onChange}
          error={error}
          onBlur={onBlur}
          disabled={disabled}
          helperText={
            helperText ??
            (evidence.loading
              ? 'Looking for evidence filed against this record…'
              : options.length === 0 && relatedRecordType && relatedRecordId
                ? `Nothing is filed against ${relatedRecordType} ${relatedRecordId.slice(0, 8)} yet — register it under Evidence and audit, then paste the reference here.`
                : 'Paste the reference id from Evidence and audit.')
          }
        />
        {options.length > 0 && (
          <Button size="sm" variant="ghost" onClick={() => setManual(false)} className="mt-1.5">
            Choose from this record instead
          </Button>
        )}
      </div>
    );
  }

  return (
    <div className={className}>
      <SelectInput
        label={label}
        required={required}
        value={value}
        onChange={onChange}
        options={options}
        error={error}
        onBlur={onBlur}
        disabled={disabled}
        helperText={
          helperText ??
          `${options.length} filed against this ${relatedRecordType.toLowerCase()}.`
        }
      />
      <Button
        size="sm"
        variant="ghost"
        onClick={() => {
          setManual(true);
          // A selected id would otherwise sit in a field the operator is about to retype, and look
          // like something they chose.
          onChange('');
        }}
        className="mt-1.5"
      >
        Use a reference from elsewhere
      </Button>
    </div>
  );
};

export default EvidenceSelect;
