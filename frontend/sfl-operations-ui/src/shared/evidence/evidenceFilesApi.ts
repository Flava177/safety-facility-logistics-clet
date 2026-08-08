import { apiClient, fetchBlob, type FetchedBlob } from 'shared/api/client';
import { FleetApiError } from 'shared/errors/FleetApiError';

/**
 * Uploading, previewing and downloading evidence files.
 *
 * <h2>What changed, and why there are now two evidence helpers</h2>
 *
 * <p>`fileEvidence.ts` next door derives a storage reference and a digest in the browser and sends
 * *metadata only*, because until the file store existed there was nowhere for bytes to go. It is
 * still correct for the forms that only record that a document exists.
 *
 * <p>This one sends the file. The service computes the digest itself from what it received, decides
 * the content type from the bytes rather than the file name, and refuses anything that is not a PDF
 * or a JPEG - so nothing here needs to be trusted, and nothing here should pretend to be
 * authoritative. In particular this module deliberately does **not** hash anything: a digest computed
 * here and sent alongside the file attests to nothing, and having one would invite somebody to
 * believe it.
 *
 * <p>The client-side checks below are courtesy, not security. They exist so a driver holding a
 * 30 MB video learns that at the moment they pick it rather than after a slow upload on a phone
 * connection. Every one of them is repeated, authoritatively, on the server.
 */

/** What the service will store. Anything else is refused, by both ends. */
export const ACCEPTED_FILE_TYPES = ['application/pdf', 'image/jpeg'] as const;

/** The `accept` attribute for a file input. Extensions included: iOS ignores bare MIME types. */
export const ACCEPTED_FILE_ACCEPT = '.pdf,.jpg,.jpeg,application/pdf,image/jpeg';

/**
 * Mirrors `UploadedFileScanner.MAX_BYTES`. Kept in step by the test, not by hope.
 *
 * One number for every upload in the dashboard, not one per form. A limit that differs between the
 * evidence dialog and the CSV import is a limit nobody can state, and the operator finds out which
 * is which by having a file refused.
 */
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

/** The cap in whole megabytes, so no message restates the number and gets it wrong. */
export const MAX_UPLOAD_MB = MAX_UPLOAD_BYTES / (1024 * 1024);

export const ACCEPTED_FILE_DESCRIPTION = `PDF, JPG or JPEG, up to ${MAX_UPLOAD_MB} MB`;

/**
 * Refuses a file that is empty or over the cap, whatever its type.
 *
 * <p>Split out of {@link rejectionReason} because the size rule is app-wide and the type rule is
 * not: a CSV import and a dispatch scan batch take neither PDFs nor JPEGs, and before this they
 * took files of any size at all. The bytes still have to survive the service's own check; this only
 * moves the refusal to the moment the file is picked.
 */
export const sizeRejectionReason = (file: File): string | null => {
  if (file.size === 0) {
    return 'That file is empty.';
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return `That file is ${(file.size / (1024 * 1024)).toFixed(1)} MB. The limit is ${MAX_UPLOAD_MB} MB.`;
  }
  return null;
};

/**
 * The retention classes the fleet service actually defines.
 *
 * <p>Typed rather than left as `string` because a plausible-looking invention is refused at the
 * boundary with a type-conversion error, which is exactly what happened the first time this ran
 * against a real service - a fuel receipt was uploaded as `FINANCE_7_YEARS`, a class that does not
 * exist. A union turns that into a compile failure instead of a 400 at a fuel pump.
 *
 * <p>Declared here rather than imported from `modules/fleet` so that `shared` does not depend on a
 * feature module; the four values are a service-wide contract, not a fleet-screen detail.
 */
export type EvidenceRetentionClassValue =
  | 'OPERATIONAL_1_YEAR'
  | 'COMPLIANCE_7_YEARS'
  | 'INCIDENT_10_YEARS'
  | 'LEGAL_HOLD';

/** The evidence record the service returns after an upload. */
export interface EvidenceFile {
  id: string;
  siteCode: string;
  relatedRecordType: string;
  relatedRecordId: string;
  evidenceType: string;
  fileName: string;
  contentType: string;
  storageReference: string;
  sha256Hash: string;
  retentionClass: string;
  retentionExpiresAt: string | null;
  legalHold: boolean;
  /** False for anything registered before the file store: metadata with no file behind it. */
  hasContent: boolean;
  createdBy: string;
  createdAt: string;
}

export interface UploadEvidenceRequest {
  siteCode: string;
  relatedRecordType: string;
  relatedRecordId: string;
  evidenceType: string;
  retentionClass: EvidenceRetentionClassValue;
  retentionExpiresAt?: string | null;
  file: File;
}

/**
 * The reason this file cannot be uploaded, or null if it can.
 *
 * <p>Extension and declared type are both checked because they disagree more often than one might
 * expect - a file saved as `scan.pdf` from a phone gallery can arrive as `image/jpeg`, and it is the
 * bytes that decide. Here the two are treated as a hint; the service settles it.
 */
export const rejectionReason = (file: File): string | null => {
  const oversize = sizeRejectionReason(file);
  if (oversize) {
    return oversize;
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  const extensionOk = ['pdf', 'jpg', 'jpeg'].includes(extension);
  const typeOk =
    !file.type ||
    file.type === 'application/octet-stream' ||
    (ACCEPTED_FILE_TYPES as readonly string[]).includes(file.type) ||
    file.type === 'image/jpg';
  if (!extensionOk || !typeOk) {
    return `Only ${ACCEPTED_FILE_DESCRIPTION} can be uploaded.`;
  }
  return null;
};

const BASE = '/api/v1/fleet/evidence';

export const evidenceFilesApi = {
  /**
   * Uploads a file and registers it as evidence, returning the record.
   *
   * <p>One call, so a form can attach the returned id to whatever it is submitting without asking a
   * person to carry an identifier between two screens. That was the actual problem: a driver has no
   * Evidence & audit screen, so "register it there and paste the reference" described a workflow
   * they could not perform.
   */
  upload: async (request: UploadEvidenceRequest): Promise<EvidenceFile> => {
    const local = rejectionReason(request.file);
    if (local) {
      throw FleetApiError.validation(local);
    }
    const form = new FormData();
    form.append('siteCode', request.siteCode);
    form.append('relatedRecordType', request.relatedRecordType);
    form.append('relatedRecordId', request.relatedRecordId);
    form.append('evidenceType', request.evidenceType);
    form.append('retentionClass', request.retentionClass);
    if (request.retentionExpiresAt) {
      form.append('retentionExpiresAt', request.retentionExpiresAt);
    }
    form.append('file', request.file, request.file.name);
    return apiClient.postForm<EvidenceFile>(`${BASE}/files`, form);
  },

  /** The file for a preview pane. The caller must revoke `objectUrl`. */
  preview: (evidenceId: string, fileName?: string): Promise<FetchedBlob> =>
    fetchBlob(`${BASE}/${evidenceId}/content`, { disposition: 'inline' }, fileName ?? 'evidence'),

  /** The same bytes, saved to disk. */
  download: async (evidenceId: string, fileName?: string): Promise<void> => {
    const fetched = await fetchBlob(
      `${BASE}/${evidenceId}/content`,
      { disposition: 'attachment' },
      fileName ?? 'evidence',
    );
    const anchor = document.createElement('a');
    anchor.href = fetched.objectUrl;
    anchor.download = fetched.fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    // Revoked next frame: revoking synchronously cancels the save in some browsers.
    window.setTimeout(() => URL.revokeObjectURL(fetched.objectUrl), 0);
  },

  /** Evidence filed against one record, newest first. */
  forRecord: (relatedRecordType: string, relatedRecordId: string, signal?: AbortSignal) =>
    apiClient.get<EvidenceFile[]>(BASE, { relatedRecordType, relatedRecordId }, signal),
};
