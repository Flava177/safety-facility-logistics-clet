import { apiClient } from 'shared/api/client';
import type { PreRegisterVisitRequest, VisitorPage, VisitorSearchParams, VisitorVisit } from './dto';

const service = 'safetySecurity' as const;
const base = '/api/v1/visitors';

export const visitorApi = {
  search: (params: VisitorSearchParams, signal?: AbortSignal) =>
    apiClient.get<VisitorPage>(`${base}/visits`, params, signal, service),
  get: (id: string, signal?: AbortSignal) =>
    apiClient.get<VisitorVisit>(`${base}/visits/${id}`, undefined, signal, service),
  rollCall: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<VisitorVisit[]>(`${base}/roll-call`, { siteCode }, signal, service),
  preRegister: (body: PreRegisterVisitRequest) =>
    apiClient.post<VisitorVisit>(`${base}/visits`, body, { service }),
  decide: (id: string, body: { approve: boolean; reason?: string; watchlistOverrideReason?: string; expectedVersion: number }) =>
    apiClient.patch<VisitorVisit>(`${base}/visits/${id}/decision`, body, { service }),
  assignBadge: (id: string, body: { badgeNumber: string; accessZones: string[]; expectedVersion: number }) =>
    apiClient.patch<VisitorVisit>(`${base}/visits/${id}/badge`, body, { service }),
  checkIn: (id: string, expectedVersion: number) =>
    apiClient.patch<VisitorVisit>(`${base}/visits/${id}/check-in`, { expectedVersion }, { service }),
  checkOut: (id: string, expectedVersion: number) =>
    apiClient.patch<VisitorVisit>(`${base}/visits/${id}/check-out`, { expectedVersion }, { service }),
  cancel: (id: string, body: { reason: string; expectedVersion: number }) =>
    apiClient.patch<VisitorVisit>(`${base}/visits/${id}/cancellation`, body, { service }),
};
