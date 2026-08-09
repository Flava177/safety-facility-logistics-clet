import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';
import tsconfigPaths from 'vite-tsconfig-paths';

/**
 * Test configuration for the SFL Operations dashboard.
 *
 * Separate from `vite.config.ts` on purpose: that file computes `base` from the build mode and
 * stamps a build time into `define`, neither of which a test run should inherit - a suite whose
 * behaviour depends on the mode it was started in is a suite that will eventually disagree with CI.
 *
 * `tsconfigPaths` is what lets a test import `shared/...` and `modules/...` the same way the
 * application does, so a test file sits beside the code it covers without relative-path noise.
 */
export default defineConfig({
  plugins: [react(), tsconfigPaths()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    css: false,
    restoreMocks: true,

    /*
     * Twenty seconds, against a default of five.
     *
     * These are not long-running tests; they are ordinary ones on a slow machine. A dialog test
     * renders a data table, a modal and two flatpickr calendars, then drives it a keystroke at a
     * time - and `userEvent` re-renders React on every one of them. The fuel-card issue test takes
     * about 0.8s on a developer machine and ran out of budget on a GitHub runner, which is the same
     * work at three to five times the wall clock.
     *
     * The failure that produces is actively misleading: vitest reports "Test timed out in 5000ms"
     * with no indication of which interaction was still going, and the first reading is that the
     * application hung. It cost two CI rounds here to establish that nothing was wrong with the code
     * at all.
     *
     * A generous ceiling costs nothing on a passing run - a test that succeeds in 0.8s still takes
     * 0.8s - and it only ever spends the extra on a run that was going to fail anyway. Keep any
     * `waitFor` timeout comfortably below this, or the wait cannot report its own assertion before
     * the test is killed.
     */
    testTimeout: 20_000,
    hookTimeout: 20_000,
  },
});
