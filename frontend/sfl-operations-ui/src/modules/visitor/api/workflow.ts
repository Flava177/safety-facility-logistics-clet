import type { VisitorVisit } from './dto';

export const visitorWorkflow = {
  canDecide: (visit: VisitorVisit) => visit.status === 'PRE_REGISTERED',
  canAssignBadge: (visit: VisitorVisit) => visit.status === 'CONFIRMED',
  canCheckIn: (visit: VisitorVisit) => visit.status === 'CONFIRMED' && Boolean(visit.badgeNumber),
  canCheckOut: (visit: VisitorVisit) => visit.status === 'CHECKED_IN',
  canCancel: (visit: VisitorVisit) => visit.status === 'PRE_REGISTERED' || visit.status === 'CONFIRMED',
};
