import { useState } from 'react';
import { sflActor } from 'shared/api/config';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import FormDialog from 'shared/components/FormDialog';
import { TextInput } from 'shared/components/fields';
import { allProgrammes, programmes } from 'shared/layout/programmeModel';
import {
  ActorOverride,
  clearActorOverride,
  readActorOverride,
  writeActorOverride,
} from './actorOverride';

/**
 * Changes the actor the dashboard sends, without a rebuild.
 *
 * See `actorOverride.ts` for why this exists, why it stores to the session and why applying it
 * reloads the page. This file is the panel; it is referenced only behind `devToolsEnabled`, so a
 * production build drops it.
 *
 * The presets are the point. Typing seven role names to check one navigation rule is how a check
 * stops getting run, and every preset below is a real bundle of real `SflRole` names taken from
 * `roleProgrammes` — nothing here grants a role the services would silently ignore.
 *
 * Two of the presets are expected to land on the no-programme page, and that is deliberate: IFIMP
 * and AVAMP have no dashboard screens yet (ADR 0006), so a facilities manager signing in should see
 * that stated rather than see a fleet dashboard.
 */
interface Preset {
  label: string;
  detail: string;
  actor: ActorOverride;
}

const PRESETS: Preset[] = [
  {
    label: 'Fleet manager',
    detail: 'FTLMP — fleet, fuel and dispatch. No emergency section.',
    actor: {
      user: 'fleet.manager',
      displayName: 'Fleet Manager',
      roles: 'FLEET_MANAGER,DISPATCH_CONTROLLER,FLEET_REPORTING_VIEWER',
      sites: 'CLET-HQ',
      programmes: '',
    },
  },
  {
    label: 'Driver',
    detail: 'FTLMP with a driver’s roles — the case ADR 0005 was written for.',
    actor: {
      user: 'kwame.driver',
      displayName: 'Kwame Driver',
      roles: 'FLEET_DRIVER',
      sites: 'CLET-HQ',
      programmes: '',
    },
  },
  {
    label: 'Emergency coordinator',
    detail: 'SSEMP only. The fleet, fuel and dispatch sections disappear.',
    actor: {
      user: 'emergency.coordinator',
      displayName: 'Emergency Coordinator',
      roles: 'EMERGENCY_COORDINATOR,COMMAND_ROLE',
      sites: 'CLET-HQ',
      programmes: '',
    },
  },
  {
    label: 'Facilities manager',
    detail: 'IFIMP, which has no dashboard screens yet — expect the no-programme page.',
    actor: {
      user: 'facilities.manager',
      displayName: 'Facilities Manager',
      roles: 'FACILITIES_MANAGER',
      sites: 'CLET-HQ',
      programmes: '',
    },
  },
  {
    label: 'SFL administrator',
    detail: 'Every programme, which is what a superadmin is for.',
    actor: {
      user: 'sfl.admin',
      displayName: 'SFL Administrator',
      roles: 'SFL_ADMIN',
      sites: 'CLET-HQ',
      programmes: '',
    },
  },
];

interface ActorSwitcherProps {
  open: boolean;
  onClose: () => void;
}

export const ActorSwitcher = ({ open, onClose }: ActorSwitcherProps) => {
  const stored = readActorOverride();
  const [draft, setDraft] = useState<ActorOverride>({
    user: stored?.user ?? sflActor.user,
    displayName: stored?.displayName ?? sflActor.displayName,
    roles: stored?.roles ?? sflActor.roles,
    sites: stored?.sites ?? sflActor.sites,
    programmes: stored?.programmes ?? '',
  });

  const set = <K extends keyof ActorOverride>(key: K, value: ActorOverride[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));

  const apply = () => {
    writeActorOverride(draft);
    // A reload, not a state update. Programme entitlement, the navigation sections and the site list
    // are module-level constants; recomputing some of them and not others is worse than reloading.
    window.location.reload();
  };

  const reset = () => {
    clearActorOverride();
    window.location.reload();
  };

  return (
    <FormDialog
      open={open}
      title="Change the development actor"
      description="Sends different X-SFL-* headers and reloads. Session-only, and never present in a production build."
      submitLabel="Apply and reload"
      submitting={false}
      maxWidth="lg"
      onClose={onClose}
      onSubmit={apply}
    >
      <div>
        <h3 className="text-theme-sm font-semibold text-gray-800">See it as</h3>
        <div className="mt-2 grid gap-2 sm:grid-cols-2">
          {PRESETS.map((preset) => (
            <button
              key={preset.label}
              type="button"
              onClick={() => setDraft(preset.actor)}
              className="rounded-xl border border-gray-200 p-3 text-left transition-colors hover:border-brand-500 hover:bg-brand-50"
            >
              <span className="block text-theme-sm font-semibold text-gray-800">{preset.label}</span>
              <span className="mt-0.5 block text-theme-xs text-gray-600">{preset.detail}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <TextInput
          label="User"
          value={draft.user}
          onChange={(value) => set('user', value)}
          helperText="X-SFL-User. Appears as the actor on every audit record written."
        />
        <TextInput
          label="Display name"
          value={draft.displayName}
          onChange={(value) => set('displayName', value)}
          helperText="Shown in this bar only."
        />
      </div>

      <TextInput
        label="Roles"
        value={draft.roles}
        onChange={(value) => set('roles', value)}
        helperText="Comma-separated SflRole names. A name the services do not recognise grants nothing rather than failing the request — so a typo reads as a missing permission."
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <TextInput
          label="Sites"
          value={draft.sites}
          onChange={(value) => set('sites', value)}
          helperText="X-SFL-Sites. Most “why can’t I see that record?” questions are this field."
        />
        <TextInput
          label="Programmes"
          value={draft.programmes}
          onChange={(value) => set('programmes', value)}
          helperText={`Optional override of ${allProgrammes.join(', ')}. Blank derives them from the roles, which is the real behaviour.`}
        />
      </div>

      <Alert variant="info">
        This changes what the dashboard <strong>asks for</strong>, not what it is allowed to have. Every
        service authorises each call from the headers it receives, so an actor without the permission
        is refused whether or not the sidebar offered the screen. Navigation scoping is a usability
        control — see ADR 0005.
      </Alert>

      {stored && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning-200 bg-warning-50 p-3">
          <p className="text-theme-xs text-gray-700">
            An override is in force. The environment defaults are{' '}
            <code className="rounded bg-white px-1">VITE_SFL_*</code> in <code>.env</code>.
          </p>
          <Button size="sm" variant="outline" onClick={reset}>
            Clear and reload
          </Button>
        </div>
      )}

      <p className="text-theme-xs text-gray-500">
        Entitlement is recomputed on reload. The four programmes are{' '}
        {allProgrammes.map((code) => `${code} (${programmes[code].label})`).join(', ')} — and only
        those with screens built can be opened.
      </p>
    </FormDialog>
  );
};

export default ActorSwitcher;
