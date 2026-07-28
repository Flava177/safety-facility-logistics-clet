/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_VERSION?: string;
  readonly VITE_APP_PORT?: string;
  readonly VITE_BASENAME?: string;
  readonly VITE_ASSET_BASE_URL?: string;

  /** Base URL of the SFL Fleet & Logistics service (default `http://localhost:8093`). */
  readonly VITE_FLEET_API_BASE_URL?: string;

  /** Development actor headers — ignored once the services run behind OIDC. */
  readonly VITE_SFL_USER?: string;
  readonly VITE_SFL_DISPLAY_NAME?: string;
  readonly VITE_SFL_ROLES?: string;
  readonly VITE_SFL_SITES?: string;

  /** `true` enables clearly-labelled development fallbacks for endpoints the backend lacks. */
  readonly VITE_FLEET_DEV_FALLBACK?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
