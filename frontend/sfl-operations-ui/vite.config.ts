import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import tsconfigPaths from 'vite-tsconfig-paths';

/**
 * Build configuration for the SFL Operations console.
 *
 * `base` is the single source of truth for where the bundle is mounted. The production build is
 * served by the Fleet service from `/ui/`, so every asset URL and the router basename are derived
 * from it rather than being repeated in three places.
 *
 * `emptyOutDir` is deliberate: a build must never leave a previous bundle's assets behind, because
 * a stale file that still resolves is far harder to spot than a missing one.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const base = mode === 'production' ? env.VITE_BASENAME || '/ui/' : '/';

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
      // Stamped into the shell so the running console can be identified from the screen alone.
      __BUILD_STAMP__: JSON.stringify(new Date().toISOString()),
    },
  };
});
