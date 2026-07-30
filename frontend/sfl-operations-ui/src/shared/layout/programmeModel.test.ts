import { describe, expect, it } from 'vitest';
import { allSystems, programmesFor, systems, systemsFor } from './programmeModel';

/**
 * Who sees what.
 *
 * The first tests in this dashboard, and this is the file that earned them: the module is
 * deliberately free of imports so the decision it encodes can be exercised directly, and the
 * comments in it already describe two live bugs that shipped — `COMMAND_ROLE` losing the whole FTLMP
 * side of the console, and `SECURITY_OFFICER` never seeing the dispatch exception it was meant to
 * escalate. Both were mapping mistakes no type checker would catch.
 */
describe('programme entitlement', () => {
  describe('S152 facilities', () => {
    it('entitles a facilities manager to S152 and to IFIMP', () => {
      expect(systemsFor(['FACILITIES_MANAGER'])).toEqual(['S152']);
      expect(programmesFor(['FACILITIES_MANAGER'])).toEqual(['IFIMP']);
    });

    it('gives every IFIMP role the facilities system', () => {
      const ifimpRoles = [
        'FACILITIES_DIRECTOR',
        'FACILITIES_MANAGER',
        'IFIMP_MAINTENANCE_SUPERVISOR',
        'IFIMP_TECHNICIAN',
        'IFIMP_REQUESTER',
        'VENDOR_TECHNICIAN',
      ];
      ifimpRoles.forEach((role) => {
        expect(systemsFor([role]), `${role} should see S152`).toContain('S152');
      });
    });

    it('does not show facilities to a fleet driver', () => {
      // The regression this whole model exists to prevent: a driver opening their portal onto the
      // estate register. They are FTLMP, and S152 is IFIMP.
      expect(systemsFor(['FLEET_DRIVER'])).not.toContain('S152');
      expect(programmesFor(['FLEET_DRIVER'])).not.toContain('IFIMP');
    });

    it('gives a centre manager both facilities and dispatch', () => {
      // A centre manager receives consignments *and* declares their centre's operating mode, so
      // they are genuinely in two programmes. Transcribed from the two permission matrices.
      const granted = systemsFor(['CENTRE_MANAGER']);
      expect(granted).toContain('S152');
      expect(granted).toContain('S171');
      expect(programmesFor(['CENTRE_MANAGER'])).toEqual(
        expect.arrayContaining(['IFIMP', 'FTLMP']),
      );
    });

    it('keeps command and integration roles across every system', () => {
      expect(systemsFor(['COMMAND_ROLE'])).toEqual(allSystems);
      expect(systemsFor(['INTEGRATION_ENGINEER'])).toEqual(allSystems);
    });

    it('places S152 in IFIMP', () => {
      expect(systems.S152.programme).toBe('IFIMP');
      expect(systems.S152.label).toBe('Facility management');
    });
  });

  describe('the rules the model turns on', () => {
    it('gives a cross-programme role everything', () => {
      expect(systemsFor(['AUDITOR'])).toEqual(allSystems);
      expect(programmesFor(['SFL_ADMIN'])).toHaveLength(4);
    });

    it('grants an unrecognised role nothing', () => {
      // Fail-closed for programmes: an actor with no known role sees nothing, because an empty
      // sidebar is a question somebody asks where a full one is not.
      expect(programmesFor(['NOT_A_REAL_ROLE'])).toEqual([]);
      expect(systemsFor(['NOT_A_REAL_ROLE'])).toEqual([]);
    });

    it('unions the systems of several roles', () => {
      const granted = systemsFor(['FACILITIES_MANAGER', 'DISPATCH_CONTROLLER']);
      expect(granted).toEqual(expect.arrayContaining(['S152', 'S171']));
      expect(granted).not.toContain('S174');
    });

    it("derives a role's programmes from its systems", () => {
      // The union that stops the two maps drifting: adding a system to `roleSystems` entitles the
      // role to that system's programme automatically.
      expect(programmesFor(['IFIMP_TECHNICIAN'])).toContain('IFIMP');
    });
  });
});
