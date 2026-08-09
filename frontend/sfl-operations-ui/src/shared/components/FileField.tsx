import { useId, useRef, useState } from 'react';
import { MAX_UPLOAD_BYTES, sizeRejectionReason } from 'shared/evidence/evidenceFilesApi';
import Button from './Button';
import Icon from './Icon';
import { FieldShell } from './fields';
import { cn } from './cn';

interface FileFieldProps {
  label: string;
  value: File | null;
  onChange: (file: File | null) => void;
  accept?: string;
  required?: boolean;
  error?: boolean;
  helperText?: string;
  disabled?: boolean;
  onBlur?: () => void;
  /**
   * The ceiling for this field, defaulting to the evidence cap.
   *
   * <p>A photograph and a data file are different things arriving through the same control. The
   * default suits the common case - a receipt, a certificate - and a bulk import passes
   * `MAX_IMPORT_BYTES`. Whatever is passed must match what the service enforces for that endpoint,
   * or the field promises something the upload will not honour.
   */
  maxBytes?: number;
}

/**
 * File selection, in the dashboard's field rhythm.
 *
 * Lived in the fuel module while the CSV import was the only screen that took a file, with a note
 * saying the shared kit gains a component when a second module needs it rather than in anticipation.
 * Three now do - the fuel import, the dispatch scan batch, and S153 evidence - and dispatch was
 * already importing it across a module boundary, which is the shape of a component that should have
 * moved a release earlier.
 *
 * Built on `FieldShell` so it sits in the same label/control/helper rhythm as every other field and
 * inherits the one focus treatment.
 *
 * A native `<input type="file">` cannot be styled and reads differently on every browser, so the
 * real input is visually hidden and a button drives it - but it stays in the accessible tree with
 * the field's own id, so a screen reader announces "Choose a file" against the right label rather
 * than a decorative button with no relationship to it.
 *
 * <h2>The size cap lives here, not in each caller</h2>
 *
 * Every upload in the dashboard passes through this component, so this is the one place a limit can
 * be applied without six forms having to remember it. It was applied in exactly one of them: the
 * evidence field checked the cap and the CSV import, the dispatch scan batch and the facilities
 * attachment took a file of any size and found out from the container, which answers an oversized
 * multipart with a bare failure and no field to attach it to.
 *
 * The refusal is a courtesy, not the boundary - `UploadedFileScanner` enforces the same number on
 * the bytes it actually received. It exists so somebody on a phone connection learns at the moment
 * they pick the file rather than after uploading it.
 */
const FileField = ({
  label,
  value,
  onChange,
  accept,
  required,
  error,
  helperText,
  disabled,
  onBlur,
  maxBytes = MAX_UPLOAD_BYTES,
}: FileFieldProps) => {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [refusal, setRefusal] = useState<string | null>(null);

  /** Clears the DOM input as well as state, so re-picking the same file fires `change` again. */
  const clearInput = () => {
    if (inputRef.current) {
      inputRef.current.value = '';
    }
  };

  const choose = (chosen: File | null) => {
    const reason = chosen ? sizeRejectionReason(chosen, maxBytes) : null;
    setRefusal(reason);
    if (reason) {
      // The refused file is not handed to the form at all. Reporting it while leaving it selected
      // would let a submit go ahead with a file the service is certain to reject.
      clearInput();
      onChange(null);
      return;
    }
    onChange(chosen);
  };

  const showError = error || Boolean(refusal);

  return (
    <FieldShell
      id={id}
      label={label}
      required={required}
      error={showError}
      helperText={refusal ?? helperText ?? `Up to ${Math.round(maxBytes / (1024 * 1024))} MB.`}
    >
      <div
        className={cn(
          'flex min-h-10 flex-wrap items-center gap-3 rounded-md border bg-white px-3 py-2',
          showError ? 'border-error-800' : 'border-gray-500',
          disabled && 'border-gray-300 bg-gray-50',
        )}
      >
        <input
          ref={inputRef}
          id={id}
          type="file"
          accept={accept}
          disabled={disabled}
          aria-invalid={showError || undefined}
          aria-describedby={`${id}-help`}
          onBlur={onBlur}
          onChange={(event) => choose(event.target.files?.[0] ?? null)}
          className="sr-only"
        />
        <Button
          size="sm"
          variant="outline"
          startIcon="upload"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
        >
          {value ? 'Choose a different file' : 'Choose a file'}
        </Button>

        {value ? (
          <span className="flex min-w-0 flex-1 items-center gap-2 text-theme-sm text-gray-900">
            <Icon name="document" size={15} className="shrink-0 text-gray-600" />
            <span className="truncate font-medium">{value.name}</span>
            <span className="shrink-0 text-theme-xs text-gray-600">
              {(value.size / 1024).toFixed(1)} kB
            </span>
          </span>
        ) : (
          <span className="text-theme-sm text-gray-500">No file chosen</span>
        )}

        {value && !disabled && (
          <button
            type="button"
            aria-label="Remove the chosen file"
            title="Remove"
            onClick={() => {
              // The DOM input keeps its own value, so clearing state alone would let the same file
              // fail to re-fire `change` when picked again.
              clearInput();
              choose(null);
            }}
            className="ml-auto flex h-6 w-6 shrink-0 items-center justify-center rounded text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900"
          >
            <Icon name="close" size={14} />
          </button>
        )}
      </div>
    </FieldShell>
  );
};

export default FileField;
