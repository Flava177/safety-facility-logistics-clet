import { ReactNode } from 'react';
import { RecordMetadata } from 'modules/fuel/api/dto';
import Icon from 'shared/components/Icon';
import WorkflowTimeline, { TimelineEntry } from 'shared/components/WorkflowTimeline';
import { humanise } from 'modules/fleet/api/enums';

/**
 * Where a figure or a panel came from, said in the panel.
 *
 * Two kinds of claim need marking on the fuel screens. A figure the console **derived** from records
 * it fetched is not a service indicator, and must never sit in a KPI row looking like one. And a
 * history the console **reconstructed** from a record's own provenance is not the audit trail — the
 * fuel aggregates expose no transition endpoint and the fleet audit search currently fails (gaps
 * 10), so intermediate steps are genuinely not recoverable.
 *
 * Both are recorded in `docs/fuel/S168_Fuel_Frontend_Gap_Register.md`.
 */
export const DerivedNote = ({ children }: { children: ReactNode }) => (
  <p className="mt-3 flex items-start gap-1.5 text-theme-xs text-gray-600">
    <Icon name="info" size={13} className="mt-0.5 shrink-0 text-teal-700" />
    <span>{children}</span>
  </p>
);

/** One extra milestone an aggregate carries beyond `RecordMetadata` — "Submitted", "Approved". */
export interface ProvenanceMilestone {
  label: string;
  at: string | null | undefined;
  actor?: string | null;
  detail?: string | null;
  tone?: TimelineEntry['tone'];
}

interface RecordProvenanceProps {
  metadata: RecordMetadata;
  /** The record's own dated milestones, in the order they can occur. */
  milestones?: ProvenanceMilestone[];
  /** Names the aggregate in the caption — "logbook", "case", "transaction". */
  recordNoun: string;
}

/**
 * A timeline built from the record itself.
 *
 * `createdAt`/`createdBy` and `lastModifiedAt`/`lastModifiedBy` are real, stored provenance, as are
 * the aggregate's own timestamps. What is missing is everything in between: a logbook that went
 * draft → submitted → under review → returned → resubmitted → approved shows its creation, its
 * submission, its approval and its most recent change, and says plainly that the steps between were
 * not available. Inventing them would be worse than the gap.
 */
const RecordProvenance = ({ metadata, milestones = [], recordNoun }: RecordProvenanceProps) => {
  const entries: TimelineEntry[] = [];

  if (metadata.createdAt) {
    entries.push({
      id: 'created',
      title: 'Record created',
      detail: `Source channel ${humanise(metadata.sourceChannel).toLowerCase()}`,
      actor: metadata.createdBy,
      occurredAt: metadata.createdAt,
    });
  }

  milestones.forEach((milestone, index) => {
    if (milestone.at) {
      entries.push({
        id: `milestone-${index}`,
        title: milestone.label,
        detail: milestone.detail ?? undefined,
        actor: milestone.actor ?? undefined,
        occurredAt: milestone.at,
        tone: milestone.tone,
      });
    }
  });

  // Only worth a row when it is not simply restating the creation of an untouched record.
  if (metadata.lastModifiedAt && metadata.lastModifiedAt !== metadata.createdAt) {
    entries.push({
      id: 'modified',
      title: `Last change · version ${metadata.version}`,
      detail: `Source channel ${humanise(metadata.sourceChannel).toLowerCase()}`,
      actor: metadata.lastModifiedBy,
      occurredAt: metadata.lastModifiedAt,
      tone: 'accent',
    });
  }

  entries.sort((left, right) => left.occurredAt.localeCompare(right.occurredAt));

  return (
    <>
      <WorkflowTimeline
        entries={entries}
        emptyMessage={`This ${recordNoun} carries no dated provenance.`}
      />
      <DerivedNote>
        Reconstructed from the {recordNoun}’s own provenance. The fuel service exposes no transition
        history, so intermediate steps between these points are not shown.
      </DerivedNote>
    </>
  );
};

export default RecordProvenance;
