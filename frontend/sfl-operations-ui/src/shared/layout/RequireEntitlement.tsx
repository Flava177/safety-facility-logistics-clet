import { ReactNode } from 'react';
import { Link } from 'react-router';
import Icon from 'shared/components/Icon';
import {
  SystemCode,
  actorProgrammes,
  actorSystems,
  entitledTo,
  entitledToSystem,
  programmes,
  systems,
} from './programmes';
import { landingPath } from './navigation';

/**
 * Refuses a route the actor is not entitled to, at whichever grain fails.
 *
 * The sidebar already stops offering these destinations, but a typed address, a bookmark or a link in
 * an old email reaches them anyway. Without this the operator would get the screen and then a page of
 * `403`s from the service — technically correct, and a poor way to be told.
 *
 * **The refusal names the grain that actually failed**, because the two are different conversations.
 * A driver reaching courier and dispatch is inside their own programme and lacks one system of it, so
 * "Fleet, Transport & Logistics is not part of your work" would be false — they are looking at it. A
 * security officer reaching the fleet register is outside their programme entirely. Same page,
 * different sentence, and only one of them is true in each case.
 *
 * **This is not the enforcement point and must never be treated as one.** Every service authorises
 * every call independently: S174 refuses an unentitled actor with `EMERGENCY_UNAUTHORIZED_SCOPE`
 * whether or not this component exists. What this does is answer the question honestly and in one
 * place, instead of leaving the operator to infer it from a broken screen.
 */
const RequireEntitlement = ({
  system,
  children,
}: {
  system: SystemCode;
  children: ReactNode;
}) => {
  const target = systems[system];
  const programme = programmes[target.programme];

  if (entitledTo(target.programme) && entitledToSystem(system)) {
    return <>{children}</>;
  }

  // Programme first: it is the coarser failure and the more useful thing to be told.
  const programmeRefused = !entitledTo(target.programme);
  const home = landingPath();

  const heading = programmeRefused
    ? `${programme.label} is not part of your work`
    : `${target.label} is not part of your work`;

  const covered = programmeRefused
    ? actorProgrammes.map((code) => programmes[code].label)
    : actorSystems.map((code) => systems[code].label);

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center text-center">
      <span className="mb-4 flex h-14 w-14 items-center justify-center rounded-lg bg-gray-100 text-gray-600">
        <Icon name="lock" size={26} />
      </span>
      <p className="text-title-sm font-bold text-gray-900">{heading}</p>
      <p className="mt-2 max-w-lg text-theme-sm text-gray-600">
        {programmeRefused ? (
          <>
            This screen belongs to SFL.{programme.code} — {programme.scope.toLowerCase()}.
          </>
        ) : (
          <>
            This screen belongs to {target.code}, one of the {programme.label} systems.
          </>
        )}{' '}
        Your roles cover{' '}
        {covered.length > 0
          ? covered.join(', ')
          : programmeRefused
            ? 'no SFL programme'
            : 'none of its systems'}
        . If you need it, ask for the role that carries it rather than a link to this page — the
        service would refuse the request regardless.
      </p>
      {home && (
        <Link
          to={home}
          className="mt-5 inline-flex h-11 items-center gap-2 rounded-lg bg-brand-800 px-4 text-theme-sm font-medium text-white transition-colors hover:bg-brand-700"
        >
          <Icon name="dashboard" size={17} />
          Back to your dashboard
        </Link>
      )}
    </div>
  );
};

export default RequireEntitlement;

/**
 * What an actor with no programme at all sees.
 *
 * Distinct from the refusal above: nothing has been asked for and nothing is being withheld — the
 * account simply carries no role this dashboard recognises. Saying so is better than an empty shell
 * that reads as a broken deployment.
 */
export const NoProgrammePage = () => (
  <div className="flex min-h-[60vh] flex-col items-center justify-center text-center">
    <span className="mb-4 flex h-14 w-14 items-center justify-center rounded-lg bg-gray-100 text-gray-600">
      <Icon name="user" size={26} />
    </span>
    <p className="text-title-sm font-bold text-gray-900">No programme is assigned to you</p>
    <p className="mt-2 max-w-lg text-theme-sm text-gray-600">
      SFL covers four programmes — facilities and infrastructure, safety and security, fleet and
      logistics, and asset visibility. Your roles grant none of them, so there is nothing to show.
      Ask for the role that covers the work you do.
    </p>
  </div>
);
