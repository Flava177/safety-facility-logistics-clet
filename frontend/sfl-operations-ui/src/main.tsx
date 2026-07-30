import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { loadActorPermissions } from 'shared/layout/actorPermissions';
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
 * Ask the services what this actor may do, then render.
 *
 * The navigation, the route guard and the landing destination are synchronous, so the answer has to be
 * in hand before the first paint — a sidebar that renders wide and then narrows looks like a bug rather
 * than a permission. `loadActorPermissions` never rejects and carries its own timeout, and `finally`
 * means a hung or missing service delays the paint briefly and then gets out of the way rather than
 * leaving a blank page. When it cannot be answered nothing is narrowed; see `actorPermissions.ts`.
 */
loadActorPermissions().finally(render);
