import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BlockerResponse } from 'modules/fleet/api/dto';
import BlockerList from './BlockerList';

/**
 * The heading states the count, and the count is the one the service sent.
 *
 * <p>Written because "1 blocking issue" was read as a hardcoded number. It is not - but nothing
 * proved that, and a heading that always said one would look identical on the single-blocker screen
 * where anyone would first notice it. These cases pin the arithmetic and the pluralisation so the
 * reading cannot come back.
 *
 * <p>Warnings are counted separately on purpose: only a BLOCKING severity causes a refusal, so a
 * heading that folded advisories into the same number would overstate what the service will reject.
 */

const blocker = (code: string, severity: BlockerResponse['severity']): BlockerResponse =>
  ({ code, severity, message: `${code} explained` }) as BlockerResponse;

describe('BlockerList', () => {
  it('states one blocking issue in the singular', () => {
    render(<BlockerList blockers={[blocker('SERVICE_OVERDUE', 'BLOCKING')]} />);

    expect(screen.getByText('1 blocking issue - the service will refuse this')).toBeInTheDocument();
  });

  it('states the real number when there is more than one', () => {
    render(
      <BlockerList
        blockers={[
          blocker('SERVICE_OVERDUE', 'BLOCKING'),
          blocker('COMPLIANCE_DOCUMENT_EXPIRED', 'BLOCKING'),
          blocker('INSPECTION_FAILED', 'BLOCKING'),
        ]}
      />,
    );

    expect(screen.getByText('3 blocking issues - the service will refuse this')).toBeInTheDocument();
    // Every one is listed, not just the one the heading is named after.
    expect(screen.getByText('SERVICE_OVERDUE explained')).toBeInTheDocument();
    expect(screen.getByText('COMPLIANCE_DOCUMENT_EXPIRED explained')).toBeInTheDocument();
    expect(screen.getByText('INSPECTION_FAILED explained')).toBeInTheDocument();
  });

  it('counts advisories apart from refusals', () => {
    render(
      <BlockerList
        blockers={[
          blocker('SERVICE_OVERDUE', 'BLOCKING'),
          blocker('SERVICE_DUE_SOON', 'WARNING'),
          blocker('COMPLIANCE_DOCUMENT_EXPIRING', 'WARNING'),
        ]}
      />,
    );

    expect(screen.getByText('1 blocking issue - the service will refuse this')).toBeInTheDocument();
    expect(screen.getByText('2 advisory warnings')).toBeInTheDocument();
  });

  it('says so when nothing is blocking, rather than saying nothing', () => {
    render(<BlockerList blockers={[]} clearMessage="No blockers. This assignment can proceed." />);

    expect(screen.getByText('No blockers. This assignment can proceed.')).toBeInTheDocument();
  });
});
