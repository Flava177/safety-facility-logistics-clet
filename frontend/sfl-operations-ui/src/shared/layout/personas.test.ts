import { afterEach, describe, expect, it, vi } from 'vitest';

/**
 * The persona rule, which is the one thing in this dashboard a permission cannot express.
 *
 * Each case re-imports the module with a different actor, because `actorRoles` is resolved once at
 * load — deliberately, so the sidebar cannot change under a render. `vi.resetModules()` is what makes
 * that testable without making it mutable in production.
 */
const withRoles = async (roles: string) => {
  vi.resetModules();
  vi.doMock('./programmes', () => ({
    actorRoles: roles.split(',').map((role) => role.trim()).filter(Boolean),
  }));
  return import('./personas');
};

afterEach(() => {
  vi.doUnmock('./programmes');
  vi.resetModules();
});

describe('personas', () => {
  it('treats a driver with no broader role as a driver', async () => {
    const { isPersona } = await withRoles('FLEET_DRIVER');
    expect(isPersona('driver')).toBe(true);
  });

  it('does not treat a fleet manager as a driver, even on the driver rota', async () => {
    // The rule the services already enforce: `FuelAccessPolicy.isDriverOnly` returns false here, so
    // the service would hand this actor the whole site's logbooks. A "my driving day" landing would
    // then be showing somebody else's records under a personal heading.
    const { isPersona } = await withRoles('FLEET_DRIVER,FLEET_MANAGER');
    expect(isPersona('driver')).toBe(false);
  });

  it('does not treat an administrator as a driver', async () => {
    const { isPersona } = await withRoles('FLEET_DRIVER,SFL_ADMIN');
    expect(isPersona('driver')).toBe(false);
  });

  it('treats a lone requester as a requester', async () => {
    const { isPersona } = await withRoles('IFIMP_REQUESTER');
    expect(isPersona('requester')).toBe(true);
  });

  it('treats a manager who also holds the requester role as a manager', async () => {
    // Transcribed from `FacilityFaultService.requesterFilter`: "a manager who also happens to hold
    // the requester role is a manager; treating the union of roles as its narrowest member would
    // make adding a role to somebody take capability away".
    const { isPersona } = await withRoles('IFIMP_REQUESTER,FACILITIES_MANAGER');
    expect(isPersona('requester')).toBe(false);
  });

  it('treats both in-house and contract technicians as the technician persona', async () => {
    const inHouse = await withRoles('IFIMP_TECHNICIAN');
    expect(inHouse.isPersona('technician')).toBe(true);

    const contractor = await withRoles('VENDOR_TECHNICIAN');
    expect(contractor.isPersona('technician')).toBe(true);
  });

  it('does not treat a supervisor as a technician — they run the queue', async () => {
    const { isPersona } = await withRoles('IFIMP_TECHNICIAN,IFIMP_MAINTENANCE_SUPERVISOR');
    expect(isPersona('technician')).toBe(false);
  });

  it('separates the mailroom officer from the dispatch controller', async () => {
    const mailroom = await withRoles('MAILROOM_OFFICER');
    expect(mailroom.isPersona('mailroom')).toBe(true);
    expect(mailroom.isPersona('centre')).toBe(false);

    const controller = await withRoles('MAILROOM_OFFICER,DISPATCH_CONTROLLER');
    expect(controller.isPersona('mailroom')).toBe(false);
  });

  it('treats a centre manager as the centre persona', async () => {
    const { isPersona } = await withRoles('CENTRE_MANAGER');
    expect(isPersona('centre')).toBe(true);
  });

  it('keeps assurance for an auditor who is also an administrator', async () => {
    // Deliberately not excluded by admin roles, unlike every other persona: an administrator who is
    // also an auditor still audits, and the assurance view is read-only anyway.
    const { isPersona } = await withRoles('AUDITOR,SFL_ADMIN');
    expect(isPersona('assurance')).toBe(true);
  });

  it('gives an operator no persona at all', async () => {
    const { isPersona } = await withRoles('FLEET_LOGISTICS_OFFICER');
    expect(isPersona('driver')).toBe(false);
    expect(isPersona('mailroom')).toBe(false);
    expect(isPersona('centre')).toBe(false);
    expect(isPersona('requester')).toBe(false);
    expect(isPersona('technician')).toBe(false);
    expect(isPersona('assurance')).toBe(false);
  });
});
