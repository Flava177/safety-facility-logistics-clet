import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';

/**
 * ESLint for the SFL Operations dashboards.
 *
 * Every plugin this needs was already in `package.json` and the `lint` script has been there from
 * the start — **there was no config file, so the script had never run once.** Several
 * `eslint-disable-next-line react-hooks/exhaustive-deps` comments in this codebase were therefore
 * written against a linter that was not checking anything; each was re-examined when this landed.
 *
 * The rule set is deliberately narrow. TypeScript already runs in strict mode with `noUnusedLocals`
 * and `noUnusedParameters`, so the compiler owns unused code and type safety; a linter that
 * duplicates it only produces two voices saying the same thing. What is left is what `tsc` cannot
 * see: React's rules of hooks, and dependency arrays.
 */
export default tseslint.config(
  {
    // Build output and coverage are generated; linting them says nothing about the source.
    ignores: ['dist/**', 'coverage/**', 'node_modules/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: {
        ...globals.browser,
        /** Stamped into the bundle by `define` in `vite.config.ts`. */
        __BUILD_STAMP__: 'readonly',
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,

      /**
       * A hook with a wrong dependency array is a stale-closure bug that renders correctly the
       * first time, so it is an error rather than a warning. Where a dependency is genuinely
       * deliberate, the disable comment beside it has to say why.
       */
      'react-hooks/exhaustive-deps': 'error',

      /**
       * Off, deliberately.
       *
       * This rule wants a module to export components and nothing else, so that an edit hot-swaps
       * instead of reloading the page. Every place it fires here, the co-export is the better
       * structure: `useNotifier` and `useSidebar` belong beside the providers they read, and
       * `SiteSelect` ships with the site list that configures it. Splitting seven modules to
       * improve hot-reload granularity would make the code worse to read in exchange for a
       * development-only convenience.
       */
      'react-refresh/only-export-components': 'off',

      /**
       * The compiler already reports unused locals and parameters, and does it more accurately.
       * Leaving this on produces the same finding twice with different wording.
       */
      '@typescript-eslint/no-unused-vars': 'off',

      /**
       * `any` is not banned outright because the wire boundary genuinely has untyped shapes — an
       * unmapped error body, a `Record<string, unknown>` detail map — and forcing a cast there
       * makes the code less honest rather than more. It is a warning so a new one is visible.
       */
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
  {
    // Config files run in Node and are not part of the browser bundle.
    files: ['*.config.{js,ts}', 'vite.config.ts'],
    languageOptions: { globals: globals.node },
  },
);
