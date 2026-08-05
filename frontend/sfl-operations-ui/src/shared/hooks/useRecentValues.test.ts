import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useRecentValues } from './useRecentValues';

/**
 * The origin/destination typing aid.
 *
 * <p>Worth testing for two reasons, neither of them the happy path. The first is **scoping**: the key
 * carries the site, and a bug that dropped it would offer one centre's routes to an operator filing
 * against another — the same class of leak the platform enforces against everywhere else, arriving
 * through a convenience feature nobody thinks of as security-relevant.
 *
 * <p>The second is **failing quietly**. Private-mode browsers throw from `localStorage`, and a typing
 * aid that takes a form down with it would be a poor trade. Every path here has to degrade to "no
 * suggestions".
 */

beforeEach(() => {
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe('useRecentValues', () => {
  it('offers nothing before anything has been typed', () => {
    const { result } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    expect(result.current.values).toEqual([]);
  });

  it('puts the most recent value first', () => {
    const { result } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    act(() => result.current.remember('Accra HQ'));
    act(() => result.current.remember('Kumasi Centre'));
    expect(result.current.values).toEqual(['Kumasi Centre', 'Accra HQ']);
  });

  it('promotes a repeat rather than duplicating it, ignoring case', () => {
    const { result } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    act(() => result.current.remember('Accra HQ'));
    act(() => result.current.remember('Kumasi Centre'));
    act(() => result.current.remember('accra hq'));
    expect(result.current.values).toEqual(['accra hq', 'Kumasi Centre']);
  });

  it('ignores blank input, so an untouched field never pollutes the list', () => {
    const { result } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    act(() => result.current.remember('   '));
    expect(result.current.values).toEqual([]);
  });

  it('caps the list so it stays scannable', () => {
    const { result } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    for (let i = 0; i < 12; i += 1) {
      act(() => result.current.remember(`Stop ${i}`));
    }
    expect(result.current.values).toHaveLength(8);
    expect(result.current.values[0]).toBe('Stop 11');
  });

  it('keeps one site out of another site’s suggestions', () => {
    const hq = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    act(() => hq.result.current.remember('Accra HQ'));

    const kumasi = renderHook(() => useRecentValues('origin', 'KUMASI'));
    expect(kumasi.result.current.values).toEqual([]);
  });

  it('keeps origin out of destination', () => {
    const origin = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    act(() => origin.result.current.remember('Accra HQ'));

    const destination = renderHook(() => useRecentValues('destination', 'CLET-HQ'));
    expect(destination.result.current.values).toEqual([]);
  });

  it('swaps the list when the site changes rather than merging the two', () => {
    const { result, rerender } = renderHook(({ site }) => useRecentValues('origin', site), {
      initialProps: { site: 'CLET-HQ' },
    });
    act(() => result.current.remember('Accra HQ'));

    rerender({ site: 'KUMASI' });
    expect(result.current.values).toEqual([]);

    rerender({ site: 'CLET-HQ' });
    expect(result.current.values).toEqual(['Accra HQ']);
  });

  it('persists before the caller unmounts', () => {
    // The bug this exists for. Every caller is a dialog that closes on success — `remember(...)`
    // then `onClose()` — so the component is gone before React would run a state updater. The first
    // version wrote inside `setValues` and therefore persisted nothing at all, which no test caught
    // because `renderHook` keeps the component mounted.
    const { result, unmount } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    act(() => result.current.remember('Accra HQ'));
    unmount();

    expect(JSON.parse(localStorage.getItem('sfl.recent.origin.CLET-HQ') ?? 'null')).toEqual([
      'Accra HQ',
    ]);
    const reopened = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    expect(reopened.result.current.values).toEqual(['Accra HQ']);
  });

  it('converges rather than overwrites when two dialogs are open at once', () => {
    const first = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    const second = renderHook(() => useRecentValues('origin', 'CLET-HQ'));

    act(() => first.result.current.remember('Accra HQ'));
    act(() => second.result.current.remember('Kumasi Centre'));

    // The second dialog opened before the first wrote, so its state was stale. Reading storage at
    // write time rather than trusting that state is what keeps the earlier value.
    expect(JSON.parse(localStorage.getItem('sfl.recent.origin.CLET-HQ') ?? 'null')).toEqual([
      'Kumasi Centre',
      'Accra HQ',
    ]);
  });

  it('reads a corrupt store as no suggestions instead of throwing', () => {
    localStorage.setItem('sfl.recent.origin.CLET-HQ', 'not json');
    const { result } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    expect(result.current.values).toEqual([]);
  });

  it('discards non-string entries someone hand-edited in', () => {
    localStorage.setItem('sfl.recent.origin.CLET-HQ', JSON.stringify(['Accra HQ', 42, null]));
    const { result } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    expect(result.current.values).toEqual(['Accra HQ']);
  });

  it('still suggests within the session when the write is refused', () => {
    // Private mode: reads work, writes throw. The field must keep working.
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });
    const { result } = renderHook(() => useRecentValues('origin', 'CLET-HQ'));
    act(() => result.current.remember('Accra HQ'));
    expect(result.current.values).toEqual(['Accra HQ']);
    setItem.mockRestore();
  });
});
