import type { PageResponse, QueryParams } from 'shared/api/types';

export type VisitStatus =
  | 'PRE_REGISTERED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'CHECKED_OUT'
  | 'REJECTED'
  | 'CANCELLED'
  | 'NO_SHOW';

export type VisitPurpose =
  | 'MEETING'
  | 'INTERVIEW'
  | 'EVENT'
  | 'DELIVERY'
  | 'MAINTENANCE_CONTRACTOR'
  | 'OTHER';

export interface RecordMetadata {
  createdBy: string;
  createdAt: string;
  lastModifiedBy: string;
  lastModifiedAt: string;
  version: number;
  sourceChannel: string;
  correlationId: string | null;
}

export interface VisitorVisit {
  id: string;
  siteCode: string;
  visitorName: string;
  visitorOrganization: string | null;
  visitorContact: string | null;
  hostId: string;
  hostName: string | null;
  purpose: VisitPurpose;
  status: VisitStatus;
  expectedArrival: string;
  expectedDeparture: string | null;
  approvalRequired: boolean;
  approvalId: string | null;
  watchlistFlagged: boolean;
  watchlistOverrideReason: string | null;
  badgeNumber: string | null;
  accessZones: string[];
  checkedInAt: string | null;
  checkedOutAt: string | null;
  closureReason: string | null;
  metadata: RecordMetadata;
}

export interface VisitorSearchParams extends QueryParams {
  siteCode?: string;
  status?: VisitStatus;
  hostId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PreRegisterVisitRequest {
  siteCode: string;
  visitorName: string;
  visitorOrganization?: string;
  visitorContact?: string;
  hostId: string;
  hostName?: string;
  purpose: VisitPurpose;
  expectedArrival: string;
  expectedDeparture?: string;
}

export type VisitorPage = PageResponse<VisitorVisit>;
