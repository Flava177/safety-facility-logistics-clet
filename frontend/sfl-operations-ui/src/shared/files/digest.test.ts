import { afterEach, describe, expect, it, vi } from 'vitest';
import { MAX_DIGEST_BYTES, digestUnavailable, formatBytes, sha256OfFile } from './digest';

/**
 * The digest helper.
 *
 * Worth testing because of what it replaces: sixty-four hexadecimal characters typed by hand, where
 * a single wrong one produces a false tamper report years later, during an integrity check, on
 * evidence nobody can now re-hash. A helper that quietly returned the wrong digest — or the right
 * digest in upper case, which the database stores verbatim — would be worse than the typing.
 *
 * The known-answer vector is the one every SHA-256 implementation is checked against, so this test
 * fails if the browser's Web Crypto is stubbed with something that merely returns bytes.
 */

const fileOf = (content: string, name = 'after.jpg', type = 'image/jpeg'): File =>
  new File([content], name, { type });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('sha256OfFile', () => {
  it('produces the known SHA-256 of "abc"', async () => {
    // The canonical NIST vector. If this passes, the helper is hashing rather than pretending to.
    const result = await sha256OfFile(fileOf('abc'));

    expect(result.ok).toBe(true);
    expect(result.ok && result.hex).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    );
  });

  it('produces the known SHA-256 of the empty file', async () => {
    // Evidence of a zero-byte file is a real mistake somebody makes; it must hash, not throw.
    const result = await sha256OfFile(fileOf(''));

    expect(result.ok && result.hex).toBe(
      'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    );
  });

  it('returns lower-case hex, which is what the service pattern and the column store', async () => {
    const result = await sha256OfFile(fileOf('abc'));
    expect(result.ok && result.hex).toMatch(/^[0-9a-f]{64}$/);
  });

  it('refuses a file over the cap instead of freezing the tab', async () => {
    /*
      `crypto.subtle.digest` cannot stream, so the whole file is read into memory. The cap is what
      stops a mis-selected video from taking a site laptop with it — and the refusal has to name the
      size, because "too large" with no number is not something an operator can act on.
    */
    const huge = fileOf('x');
    Object.defineProperty(huge, 'size', { value: MAX_DIGEST_BYTES + 1 });

    const result = await sha256OfFile(huge);

    expect(result.ok).toBe(false);
    expect(!result.ok && result.reason).toBe('too-large');
    expect(!result.ok && result.message).toContain('64 MB');
    // The fallback is the behaviour that existed before the helper: type it by hand.
    expect(!result.ok && result.message).toContain('by hand');
  });

  it('never throws when the file cannot be read', async () => {
    // A form field that throws takes the dialog with it. The drive was unplugged; say so and carry on.
    const broken = fileOf('abc');
    vi.spyOn(broken, 'arrayBuffer').mockRejectedValue(new Error('NotReadableError'));

    const result = await sha256OfFile(broken);

    expect(result.ok).toBe(false);
    expect(!result.ok && result.reason).toBe('failed');
  });
});

describe('digestUnavailable', () => {
  it('says nothing when Web Crypto is there', () => {
    expect(digestUnavailable()).toBeNull();
  });

  it('blames the connection when the context is insecure', () => {
    // Serving the dashboard over plain HTTP from a hostname is the only way to reach this, and the
    // fix is a deployment change — so the message has to name it rather than say "not supported".
    vi.stubGlobal('crypto', {});
    vi.stubGlobal('window', { ...window, isSecureContext: false });

    expect(digestUnavailable()).toContain('HTTPS');
  });
});

describe('formatBytes', () => {
  it('reads the way somebody would say it', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 kB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
    // A decimal below ten and none above it: "9.5 MB" is information, "64.0 MB" is noise.
    expect(formatBytes(MAX_DIGEST_BYTES)).toBe('64 MB');
  });
});
