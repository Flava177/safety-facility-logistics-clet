import { FleetApiError } from 'shared/errors/FleetApiError';

/**
 * Turning a chosen file into the four things the platform actually stores.
 *
 * <p>SFL never holds evidence bytes - S166-03 and the facilities equivalent both store a *reference*
 * plus a SHA-256, and the file itself lives in the document store. That is why every evidence form
 * has fields called "storage reference", "file name" and "checksum" rather than an upload button.
 *
 * <p>It is also why those forms were miserable. Three of them were plain text inputs the operator was
 * expected to fill by hand, checksum included - and a hand-typed SHA-256 is not a checksum, it is a
 * sixty-four character opportunity to make the hash chain unverifiable. The evidence registration
 * dialog on Evidence & audit already solved this with a file picker that derives all four; these
 * helpers are that solution lifted out of it so the custody and handover forms can use it too.
 *
 * <p>The file is never uploaded. It is read in the browser to compute the digest and then dropped.
 */

/**
 * A deterministic, human-traceable storage key for a file that has no document store yet.
 *
 * <p>The `local-demo://` scheme is deliberate and load-bearing: it makes it obvious in the database
 * that Release 1 recorded a reference no object store will resolve, rather than leaving a plausible
 * looking path that quietly points nowhere.
 */
export const evidenceStorageReference = (
  siteCode: string,
  relatedRecordType: string,
  relatedRecordId: string,
  fileName: string,
  now: number = Date.now(),
): string => {
  const safeFile = fileName.replace(/[^\w.\-() ]+/g, '_').trim().replace(/\s+/g, '-') || 'evidence';
  const safeType = relatedRecordType.replace(/[^\w.-]+/g, '_').trim().toLowerCase() || 'record';
  const safeRecord = relatedRecordId.replace(/[^\w.-]+/g, '_').trim().slice(0, 120) || 'reference';
  return `local-demo://fleet-evidence/${siteCode.toUpperCase()}/${safeType}/${safeRecord}/${now}-${safeFile}`;
};

/** SHA-256 of the file's bytes, lower-case hex - the form the services store and compare. */
export const sha256Hex = async (file: File): Promise<string> => {
  if (!globalThis.crypto?.subtle) {
    throw FleetApiError.transport('This browser cannot compute the SHA-256 evidence hash.');
  }
  const digest = await globalThis.crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
};

/** Everything a chosen file contributes to an evidence payload. */
export interface FileEvidence {
  fileName: string;
  contentType: string;
  storageReference: string;
  sha256Hash: string;
}

/**
 * Derives the whole evidence quartet from one chosen file.
 *
 * <p>Returns `null` for no file, because evidence is optional on most of these forms and "no file"
 * has to stay distinguishable from "a file that produced empty values".
 */
export const describeEvidenceFile = async (
  file: File | null,
  siteCode: string,
  relatedRecordType: string,
  relatedRecordId: string,
): Promise<FileEvidence | null> => {
  if (!file) {
    return null;
  }
  return {
    fileName: file.name,
    // A file the browser cannot type is still evidence; the store just learns nothing from it.
    contentType: file.type || 'application/octet-stream',
    storageReference: evidenceStorageReference(siteCode, relatedRecordType, relatedRecordId, file.name),
    sha256Hash: await sha256Hex(file),
  };
};
