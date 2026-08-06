import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * What each S174 role is offered, at the level of individual controls.
 *
 * <h2>Why the emergency module needed its own</h2>
 *
 * <p>The activation screens gated on the *record's state* and nothing else - `canTransition` answers
 * "could an activation in this status take this step", which is a real question and the wrong one on
 * its own. Every role that could open a page was therefore offered every control on it: compose,
 * approve, send, all-clear, manage audiences, edit templates, start a drill. The service refused each
 * in turn, correctly, after the operator had already committed to the click.
 *
 * <p>The permission each control now carries is the one `ActivationService` itself requires - submit
 * and cancel are ACTIVATION_CREATE, approve and reject are ACTIVATION_APPROVE, send and degraded
 * fallback are ACTIVATION_SEND, and all-clear is its own grant - so what the screen offers and what
 * the service accepts cannot drift apart without this failing.
 */

const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

const workflow = await import('./workflow');

/** Reads activations and nothing more - the shape of a command-role or reporting observer. */
const OBSERVER = ['EMERGENCY_ACTIVATION_READ', 'EMERGENCY_REPORT_READ', 'EMERGENCY_AUDIENCE_READ'];

/** Composes and cancels, but cannot approve their own or send it. */
const COMPOSER = [...OBSERVER, 'EMERGENCY_ACTIVATION_CREATE'];

/** Approves, and separately may send and stand down. */
const COORDINATOR = [
  ...COMPOSER,
  'EMERGENCY_ACTIVATION_APPROVE',
  'EMERGENCY_ACTIVATION_SEND',
  'EMERGENCY_ALL_CLEAR_SEND',
];

const holding = (granted: string[]) =>
  permits.mockImplementation((p) => p === undefined || granted.includes(p));

describe('an observer who only reads', () => {
  beforeEach(() => {
    permits.mockReset();
    holding(OBSERVER);
  });

  it('is offered no control that changes anything', () => {
    expect(workflow.canCreateActivations()).toBe(false);
    expect(workflow.canApproveActivations()).toBe(false);
    expect(workflow.canSendActivations()).toBe(false);
    expect(workflow.canSendAllClear()).toBe(false);
    expect(workflow.canManageAudiences()).toBe(false);
    expect(workflow.canManageTemplates()).toBe(false);
    expect(workflow.canManageScenarios()).toBe(false);
  });

  it('cannot break glass', () => {
    expect(workflow.canBreakGlass()).toBe(false);
  });
});

describe('a composer', () => {
  beforeEach(() => {
    permits.mockReset();
    holding(COMPOSER);
  });

  it('may compose and cancel but never approve or send their own activation', () => {
    // The separation that makes the approval step mean anything.
    expect(workflow.canCreateActivations()).toBe(true);
    expect(workflow.canApproveActivations()).toBe(false);
    expect(workflow.canSendActivations()).toBe(false);
    expect(workflow.canBreakGlass()).toBe(false);
  });
});

describe('a coordinator', () => {
  beforeEach(() => {
    permits.mockReset();
    holding(COORDINATOR);
  });

  it('may approve, send and stand down, and still not break glass', () => {
    expect(workflow.canApproveActivations()).toBe(true);
    expect(workflow.canSendActivations()).toBe(true);
    expect(workflow.canSendAllClear()).toBe(true);
    // The narrowest grant in S174: sending to a whole site with no approval, on one authority. It is
    // not implied by any of the three above, and must never be folded into them.
    expect(workflow.canBreakGlass()).toBe(false);
  });
});

describe('state and permission are separate questions', () => {
  it('a granted actor is still refused a transition the activation cannot take', () => {
    permits.mockReset();
    holding(COORDINATOR);
    const draft = { status: 'DRAFT' } as Parameters<typeof workflow.canTransition>[0];

    // Holding the grant does not make a draft sendable; the screens must check both, which is what
    // the `canTransition(...) && canX()` pairing on the detail page exists to do.
    expect(workflow.canTransition(draft, 'activate')).toBe(false);
    expect(workflow.canSendActivations()).toBe(true);
  });
});
