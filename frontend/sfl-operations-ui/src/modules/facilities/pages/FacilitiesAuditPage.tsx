import { useState } from 'react';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import DataState from 'shared/components/DataState';
import DataTable, { Column } from 'shared/components/DataTable';
import FilterBar from 'shared/components/FilterBar';
import PageHeader from 'shared/components/PageHeader';
import SectionCard from 'shared/components/SectionCard';
import Select from 'shared/components/Select';
import SiteSelect, { defaultSite } from 'shared/components/SiteSelect';
import StatusChip from 'shared/components/StatusChip';
import { useNotifier } from 'shared/components/Notifier';
import { useApiQuery } from 'shared/hooks/useApiQuery';
import type { AuditChainVerification, AuditEvent } from '../api/dto';
import { auditActions } from '../api/enums';
import type { AuditAction } from '../api/enums';
import { searchAudit, verifyAuditChain } from '../api/facilitiesApi';
import { canVerifyAuditChain } from '../api/workflow';
import { formatDateTime, humaniseCode, orDash } from '../components/facilitiesFormat';

/**
 * The audit trail, and the replay that proves it has not been altered.
 *
 * Two things here that are easy to get wrong and matter a great deal:
 *
 * **Denials are in the trail.** `AUTHORIZATION_DENIED` is offered first in the action filter, because
 * a refused attempt to read another site's estate is the event a compliance review is looking for,
 * and burying it among thirty state changes hides it.
 *
 * **The integrity result is reported honestly.** A broken chain says which record it broke at and
 * what was expected against what was found — not a red badge. SRS-SFL-S152-03 makes this an escalation
 * to compliance and security, and an escalation needs something an investigator can act on.
 */
const FacilitiesAuditPage = () => {
  const notify = useNotifier();
  const [siteCode, setSiteCode] = useState<string>(defaultSite);
  const [action, setAction] = useState<string>('');
  const [verification, setVerification] = useState<AuditChainVerification | null>(null);
  const [verifying, setVerifying] = useState(false);

  const { data, loading, error, refetch } = useApiQuery(
    (signal) =>
      searchAudit(
        {
          siteCode: siteCode || undefined,
          action: (action as AuditAction) || undefined,
          limit: 200,
        },
        signal,
      ),
    [siteCode, action],
  );

  const runVerification = async () => {
    setVerifying(true);
    try {
      const result = await verifyAuditChain();
      setVerification(result);
      if (result.intact) {
        notify.notifySuccess(`Audit chain intact — ${result.recordsVerified} records verified.`);
      } else {
        notify.notifyError(
          new Error('Audit integrity check failed. Escalate to compliance and security.'),
        );
      }
      // Running the check is itself audited, so the list below has just gained a row.
      refetch();
    } catch (cause) {
      notify.notifyError(cause);
    } finally {
      setVerifying(false);
    }
  };

  const columns: Column<AuditEvent>[] = [
    {
      key: 'sequenceNo',
      header: '#',
      width: 70,
      align: 'right',
      cell: (event) => <span className="font-mono text-theme-xs text-gray-500">{event.sequenceNo}</span>,
    },
    {
      key: 'occurredAt',
      header: 'When',
      width: 190,
      cell: (event) => formatDateTime(event.occurredAt),
    },
    {
      key: 'action',
      header: 'Action',
      width: 230,
      cell: (event) => (
        <StatusChip
          value={event.action}
          tone={event.action === 'AUTHORIZATION_DENIED' ? 'blocked' : 'neutral'}
          label={humaniseCode(event.action)}
        />
      ),
    },
    {
      key: 'actor',
      header: 'Actor',
      width: 160,
      cell: (event) => event.actorDisplayName || event.actorId,
    },
    {
      key: 'resource',
      header: 'Resource',
      hideBelowLg: true,
      cell: (event) => (
        <span className="text-gray-600">
          {event.resourceType} · <span className="font-mono text-theme-xs">{event.resourceId}</span>
        </span>
      ),
    },
    {
      key: 'site',
      header: 'Site',
      width: 100,
      align: 'right',
      cell: (event) => (
        <span className="text-gray-600">{event.siteScope === '*' ? 'Platform' : event.siteScope}</span>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Audit & integrity"
        subtitle="Every state change, and the hash-chain replay that proves none was altered"
        actions={
          canVerifyAuditChain() ? (
            <Button variant="outline" onClick={runVerification} disabled={verifying}>
              {verifying ? 'Verifying…' : 'Verify chain'}
            </Button>
          ) : undefined
        }
      />

      <div className="space-y-5">
        {verification && (
          <Alert
            variant={verification.intact ? 'success' : 'error'}
            title={
              verification.intact
                ? `Chain intact — ${verification.recordsVerified} records verified`
                : 'Audit integrity check failed. Escalate to compliance and security.'
            }
          >
            {verification.intact ? (
              <p className="text-theme-sm">
                Every record replays against its predecessor. Head hash{' '}
                <span className="font-mono text-theme-xs">
                  {verification.headHash?.slice(0, 16)}…
                </span>
              </p>
            ) : (
              <div className="space-y-1 text-theme-sm">
                <p>{orDash(verification.reason)}</p>
                <p>
                  Broke at sequence <strong>{orDash(verification.brokenAtSequence)}</strong> after{' '}
                  {verification.recordsVerified} good records.
                </p>
                <p className="font-mono text-theme-xs break-all">
                  expected {orDash(verification.expected)}
                  <br />
                  found {orDash(verification.found)}
                </p>
              </div>
            )}
          </Alert>
        )}

        <FilterBar>
          <SiteSelect value={siteCode} onChange={setSiteCode} allowEmpty emptyLabel="All sites" />
          <Select
            value={action}
            onChange={setAction}
            placeholder="Any action"
            options={[
              { value: '', label: 'Any action' },
              ...auditActions.map((value) => ({ value, label: humaniseCode(value) })),
            ]}
          />
        </FilterBar>

        <SectionCard
          title="Audit trail"
          subtitle="Append-only and hash-chained. Most recent first."
        >
          <DataState
            loading={loading}
            error={error}
            empty={!data || data.length === 0}
            emptyTitle="No audit records match"
            emptyHint="Widen the site or clear the action filter."
            onRetry={refetch}
          >
            {data && (
              <DataTable
                rows={data}
                columns={columns}
                getRowId={(event) => event.id}
                caption="Audit trail"
                dense
              />
            )}
          </DataState>
        </SectionCard>
      </div>
    </>
  );
};

export default FacilitiesAuditPage;
