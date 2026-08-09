import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { loadActorPermissions } from 'shared/layout/actorPermissions';
import { loadServingPlatform } from 'shared/platform';
import './index.css';

const container = document.getElementById('root');

if (!container) {
  throw new Error('The #root element is missing from index.html.');
}

const render = () =>
  createRoot(container).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );

/**
 * Learn which platform this origin serves, ask that service what this actor may do, then render.
 *
 * The order matters and is the whole fix: the permission source is chosen by platform, so asking
 * before the platform is known would ask the wrong service - or, as the previous version did, ask all
 * three from an origin that only owns one, and report a fleet service as unreachable to somebody
 * looking at facilities.
 *
 * The navigation, the route guard and the landing destination are synchronous, so both answers have
 * to be in hand before the first paint - a sidebar that renders wide and then narrows looks like a
 * bug rather than a permission. Neither call rejects and both carry their own timeout, so a hung or
 * missing service delays the paint briefly and then gets out of the way. When permissions cannot be
 * answered nothing is offered and the shell says so; see `actorPermissions.ts`.
 */
loadServingPlatform()
  .then(loadActorPermissions)
  .finally(render);
