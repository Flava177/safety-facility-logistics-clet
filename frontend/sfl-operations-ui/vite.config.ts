import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import tsconfigPaths from 'vite-tsconfig-paths';

/**
 * Build configuration for the SFL Operations dashboards.
 *
 * `base` is the single source of truth for where the bundle is mounted. This dashboard used to be
 * packaged into the Fleet service and served from `/ui/`; it is now a standalone application that
 * talks to the services over HTTP, so it mounts at the root and `VITE_BASENAME` overrides that for
 * anyone serving it under a path prefix. Every asset URL and the router basename derive from this
 * rather than being repeated in three places.
 *
 * `emptyOutDir` is deliberate: a build must never leave a previous bundle's assets behind, because
 * a stale file that still resolves is far harder to spot than a missing one.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const base = mode === 'production' ? env.VITE_BASENAME || '/' : '/';

  return {
    plugins: [react(), tailwindcss(), tsconfigPaths()],
    base,
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_APP_PORT || 5005),
    },
    preview: {
      port: Number(env.VITE_APP_PORT || 5005),
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true,
      sourcemap: false,
      chunkSizeWarningLimit: 1200,
    },
    define: {
      // Stamped into the shell so the running dashboard can be identified from the screen alone.
      __BUILD_STAMP__: JSON.stringify(new Date().toISOString()),
    },
  };
});
