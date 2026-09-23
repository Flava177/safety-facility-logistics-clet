import type { Impact, Likelihood, SecurityIncident } from './dto';

const likelihoods: Likelihood[] = ['RARE', 'UNLIKELY', 'POSSIBLE', 'LIKELY', 'ALMOST_CERTAIN'];
const impacts: Impact[] = ['NEGLIGIBLE', 'MINOR', 'MODERATE', 'MAJOR', 'CATASTROPHIC'];

export const incidentRiskScore = (likelihood: Likelihood, impact: Impact): number =>
  (likelihoods.indexOf(likelihood) + 1) * (impacts.indexOf(impact) + 1);

export const incidentWorkflow = {
  canTriage: (incident: SecurityIncident) => incident.status !== 'CLOSED',
  canInvestigate: (incident: SecurityIncident) => incident.status !== 'CLOSED' && incident.severity !== null,
  canClose: (incident: SecurityIncident) => incident.status === 'INVESTIGATING',
};
