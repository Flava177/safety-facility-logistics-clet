import { sflActor } from 'shared/api/config';
import { readActorOverride } from 'shared/dev/actorOverride';
import {
  ProgrammeCode,
  allProgrammes,
  isCrossProgramme,
  parseList,
  portalLabelFor,
  programmesFor,
} from './programmeModel';

/**
 * Programme entitlement for the **current actor**.
 *
 * The mapping itself lives in `programmeModel.ts`, which imports nothing — that separation is what
 * lets the decision "who sees what" be exercised directly instead of only through a running
 * application. This file is the thin part: read the actor's roles, apply the override, expose the
 * answer.
 *
 * The rule: **a user sees the programmes they are entitled to.** A driver or a head of fleet sees
 * fleet, fuel and dispatch — not CCTV access management, not intrusion detection, not visitor
 * badges. A manager or superadmin sees everything. ADR 0005.
 *
 * **This is a usability control, not a security control.** Until IAM is integrated there is no
 * authenticated identity, and even once there is, hiding a navigation entry protects nothing: the
 * services authorise every call independently, and S174 refuses an unentitled actor with
 * `EMERGENCY_UNAUTHORIZED_SCOPE` whether or not the sidebar ever offered it. Never treat this as
 * the enforcement point.
 */

export type { ProgrammeCode };
export { allProgrammes, programmes } from './programmeModel';

export const actorRoles: string[] = parseList(sflActor.roles);

/** `true` when the actor holds a role that sees every programme. */
export const isCrossProgrammeActor: boolean = isCrossProgramme(actorRoles);

/**
 * The programmes this actor may see.
 *
 * Derived from roles, because that is what IAM will do — there is no separate entitlement list to
 * drift out of step with the roles the actor already sends. `VITE_SFL_PROGRAMMES` overrides it
 * outright, which is how to look at one programme's screens without rewriting a role list; the
 * development actor switcher sets the same field, and takes precedence over the environment because
 * it is the more recent instruction.
 */
export const actorProgrammes: ProgrammeCode[] = (() => {
  const requested =
    readActorOverride()?.programmes || (import.meta.env.VITE_SFL_PROGRAMMES as string | undefined) || '';
  const override = parseList(requested).filter((code): code is ProgrammeCode =>
    allProgrammes.includes(code as ProgrammeCode),
  );

  return override.length > 0 ? override : programmesFor(actorRoles);
})();

export const entitledTo = (code: ProgrammeCode): boolean => actorProgrammes.includes(code);

export const portalLabel = (): string => portalLabelFor(actorProgrammes);
