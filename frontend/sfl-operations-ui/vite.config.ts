import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import tsconfigPaths from 'vite-tsconfig-paths';

/**
 * Build configuration for the SFL Operations dashboards.
 *
 * `base` is the single source of truth for where the bundle is mounted. The production build is
 * served by sfl-portal-service from `/home/`, so every asset URL and the router basename are
 * derived from it rather than being repeated in three places.
 *
 * It was `/ui/` and served by the Fleet service, which put fleet's name in front of facilities and
 * safety-security users. The portal owns the address now and names no service. This value and
 * `PortalBundle.PATH` must move together - the mount point is baked into every asset URL at build
 * time, so a mismatch serves a shell whose scripts all 404.
 *
 * `emptyOutDir` is deliberate: a build must never leave a previous bundle's assets behind, because
 * a stale file that still resolves is far harder to spot than a missing one.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const base = mode === 'production' ? env.VITE_BASENAME || '/home/' : '/';

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
