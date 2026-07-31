import { Link } from 'react-router';
import Alert from 'shared/components/Alert';
import PageHeader from 'shared/components/PageHeader';
import { facilitiesPaths, fleetPaths } from 'shared/layout/navigation';

/**
 * One assurance landing for the Auditor / Compliance / Data Protection Officer — SRS §2.3.
 *
 * ## Why one view and not four
 *
 * `AUDITOR` and `COMPLIANCE_OFFICER` are in `crossProgrammeRoles`, so they are entitled to every
 * system by design. That is precisely the argument for a consolidated landing: an auditor's question
 * is "is the record trustworthy", and answering it by visiting four per-module audit screens in turn
 * makes the platform's shape the auditor's problem.
 *
 * ## Why it links rather than duplicates
 *
 * Each service owns its own hash chain, its own evidence register and its own denial records, and
 * they must not be merged behind one query — that would put this dashboard in the position of
 * asserting a single chain where there are four independent ones, which is exactly the claim an
 * auditor must not be handed. So this page is a directory with the caveats attached, not a
 * synthesised view.
 *
 * The one thing it does assert is the caveat itself: verifying facilities does not verify fleet, and
 * a green tick on one chain says nothing about another.
 *
 * ## A surprise worth stating in the open
 *
 * `FACILITIES_AUDIT_INTEGRITY_CHECK` is **not** held by `FACILITIES_DIRECTOR`, and that is correct —
 * an integrity failure escalates *to* compliance, so compliance runs the check. It surprised the
 * S153 pass enough to be written down, so it is written down here too rather than being discovered
 * again by a director who cannot find the button.
 */
const AssurancePage = () => (
  <div className="space-y-8">
    <PageHeader
      title="Audit & evidence"
      subtitle="Chain verification, evidence and denial records across every system"
    />

    <Alert variant="info" title="Four chains, not one">
      Each service hash-chains its own audit log independently. Verifying facilities says nothing
      about fleet, and there is deliberately no combined check — a single green tick over four
      separate chains would be a claim this dashboard has no standing to make.
    </Alert>

    <section className="space-y-3">
      <h2 className="text-lg font-semibold text-slate-800">IFIMP — facilities, maintenance and booking</h2>
      <ul className="space-y-2 text-sm">
        <li>
          <Link className="text-teal-700 underline" to={facilitiesPaths.audit}>
            Audit &amp; integrity
          </Link>
          <span className="text-slate-600">
            {' '}— replay the chain, search records, read authorisation denials. Needs
            {' '}<code>FACILITIES_AUDIT_INTEGRITY_CHECK</code> to run the verification, which
            compliance holds and the facilities director deliberately does not.
          </span>
        </li>
      </ul>
    </section>

    <section className="space-y-3">
      <h2 className="text-lg font-semibold text-slate-800">FTLMP — fleet, fuel and dispatch</h2>
      <ul className="space-y-2 text-sm">
        <li>
          <Link className="text-teal-700 underline" to={fleetPaths.governance}>
            Evidence &amp; audit
          </Link>
          <span className="text-slate-600">
            {' '}— governed evidence, export requests with a recorded justification and recipient,
            and the fleet chain.
          </span>
        </li>
      </ul>
    </section>

    <Alert variant="warning" title="Evidence export is a separate authorised act">
      Exporting evidence is not reading it. Each export records who asked, why, and who received it,
      and that record is itself auditable — so an export made to answer a question becomes part of
      the trail the next question is asked against.
    </Alert>
  </div>
);

export default AssurancePage;
