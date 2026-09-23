import { describe, expect, it } from 'vitest';
import type { SecurityIncident } from './dto';
import { incidentRiskScore, incidentWorkflow } from './workflow';

const incident = (status: SecurityIncident['status'], severity: SecurityIncident['severity']) => ({ status, severity }) as SecurityIncident;

describe('incident workflow controls', () => {
  it('matches the backend 5x5 ordinal risk score', () => {
    expect(incidentRiskScore('RARE', 'NEGLIGIBLE')).toBe(1);
    expect(incidentRiskScore('POSSIBLE', 'MODERATE')).toBe(9);
    expect(incidentRiskScore('ALMOST_CERTAIN', 'CATASTROPHIC')).toBe(25);
  });
  it('requires triage before investigation', () => {
    expect(incidentWorkflow.canInvestigate(incident('TRIAGE', null))).toBe(false);
    expect(incidentWorkflow.canInvestigate(incident('TRIAGE', 'HIGH'))).toBe(true);
  });
  it('only offers closure during investigation', () => {
    expect(incidentWorkflow.canClose(incident('TRIAGE', 'HIGH'))).toBe(false);
    expect(incidentWorkflow.canClose(incident('INVESTIGATING', 'HIGH'))).toBe(true);
    expect(incidentWorkflow.canClose(incident('CLOSED', 'HIGH'))).toBe(false);
  });
});
