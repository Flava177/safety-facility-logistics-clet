import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';
import tsconfigPaths from 'vite-tsconfig-paths';

/**
 * Test configuration for the SFL Operations dashboard.
 *
 * Separate from `vite.config.ts` on purpose: that file computes `base` from the build mode and
 * stamps a build time into `define`, neither of which a test run should inherit — a suite whose
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
  },
});
