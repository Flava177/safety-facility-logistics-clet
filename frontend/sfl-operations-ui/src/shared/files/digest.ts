/**
 * SHA-256 of a file the operator has in front of them.
 *
 * ## Why this exists, and what it does not do
 *
 * S153 stores evidence **by reference**: the bytes live in the document and object-storage service,
 * and the facilities service records where they landed and what they hashed to. That is the
 * architecture standard and it is not being changed here — nothing in this module uploads anything.
 *
 * What it does change is who computes the digest. Before this, a technician standing in a plant room
 * with a photograph had to obtain a SHA-256 from somewhere else and type sixty-four hexadecimal
 * characters into a form. Nobody does that correctly, and a mistyped digest is the one error in this
 * system that surfaces years later — during an integrity check, on evidence nobody can now re-hash —
 * as a false report that the file was tampered with.
 *
 * The browser has the file. It can hash it. So the field is derived rather than typed, and the
 * remaining manual step is the storage reference, which genuinely cannot be known here.
 *
 * ## Why it reads the whole file into memory
 *
 * `crypto.subtle.digest` takes a buffer, not a stream — there is no incremental SHA-256 in the Web
 * Crypto API, and the platform has no hashing dependency to add one. So the file is read whole, and
 * {@link MAX_DIGEST_BYTES} caps what that is allowed to mean. Closure evidence is photographs and
 * signed PDFs; a cap in the tens of megabytes never fires in practice and stops a mis-selected video
 * from freezing the tab on a site laptop.
 *
 * Above the cap the caller is told so and the digest field stays manual, which is the behaviour that
 * existed before this — a narrower capability, not a broken one.
 *
 * ## Secure context
 *
 * `crypto.subtle` is undefined outside a secure context. `localhost` counts as one and production is
 * HTTPS, so this is only reachable if the dashboard is served over plain HTTP from a hostname — in
 * which case {@link digestUnavailable} says which of the two problems it is, rather than throwing
 * something about `undefined`.
 */

/** 64 MB. Well above any photograph or signed PDF, well below what freezes a site laptop. */
export const MAX_DIGEST_BYTES = 64 * 1024 * 1024;

export type DigestFailure =
  | { reason: 'too-large'; message: string }
  | { reason: 'unavailable'; message: string }
  | { reason: 'failed'; message: string };

export type DigestResult = { ok: true; hex: string } | { ok: false } & DigestFailure;

/** Whether the browser can hash at all, checked before a control offers to. */
export const digestUnavailable = (): string | null => {
  if (typeof crypto === 'undefined' || !crypto.subtle) {
    return window.isSecureContext === false
      ? 'Hashing needs a secure connection. Open the dashboard over HTTPS, or enter the digest by hand.'
      : 'This browser cannot compute a SHA-256. Enter the digest by hand.';
  }
  return null;
};

const toHex = (buffer: ArrayBuffer): string =>
  Array.from(new Uint8Array(buffer))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');

/**
 * Hashes a file, or explains why it could not.
 *
 * Never throws: every caller is a form field, and a form field that throws takes the dialog with it.
 * Lower-case hex, which is what the service's pattern accepts and what the database stores.
 */
export const sha256OfFile = async (file: File): Promise<DigestResult> => {
  const unavailable = digestUnavailable();
  if (unavailable) {
    return { ok: false, reason: 'unavailable', message: unavailable };
  }
  if (file.size > MAX_DIGEST_BYTES) {
    return {
      ok: false,
      reason: 'too-large',
      message: `This file is ${formatBytes(file.size)}. Files over ${formatBytes(
        MAX_DIGEST_BYTES,
      )} have to be hashed outside the browser — enter the digest by hand.`,
    };
  }
  try {
    const buffer = await file.arrayBuffer();
    return { ok: true, hex: toHex(await crypto.subtle.digest('SHA-256', buffer)) };
  } catch {
    // Reading a file can fail for reasons that are nothing to do with this code — the file moved,
    // the drive was unplugged, the browser refused a very large allocation.
    return {
      ok: false,
      reason: 'failed',
      message: 'The file could not be read. Choose it again, or enter the digest by hand.',
    };
  }
};

/** Byte counts as somebody would say them. */
export const formatBytes = (bytes: number): string => {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ['kB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`;
};
