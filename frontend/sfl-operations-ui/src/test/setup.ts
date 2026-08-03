import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

/**
 * Test bootstrap.
 *
 * `cleanup` after every test is what keeps one test's DOM out of the next one's queries — without
 * it a `getByText` can match a node the previous test rendered and the failure appears in the wrong
 * file entirely.
 */
afterEach(() => {
  cleanup();
});

/**
 * jsdom implements neither of these, and both are used by the dashboard's layout code.
 *
 * Stubbing them here rather than in each test keeps the failure — "matchMedia is not a function" —
 * out of tests that have nothing to do with responsive behaviour.
 */
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }),
});

if (!window.ResizeObserver) {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
}

/**
 * jsdom has no layout, so it ships no `scrollIntoView`. The shared Select keeps the highlighted
 * row in view when the list opens, which means every test that opens a listbox would otherwise
 * die on "node?.scrollIntoView is not a function" — a failure about scrolling, in tests that are
 * about choosing an option.
 */
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = function scrollIntoView() {};
}
