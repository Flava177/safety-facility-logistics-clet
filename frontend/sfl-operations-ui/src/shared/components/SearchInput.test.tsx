import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import SearchInput from './SearchInput';

/**
 * The debounce, which is the whole reason this component exists.
 *
 * <p>The register text filters called back on every keystroke, and every callback was a query. The
 * assertion that matters is the **count**, not the value: a version that committed each character
 * would still arrive at the right string, having asked the server six times to get there.
 *
 * <h2>Real timers, deliberately</h2>
 *
 * <p>The first version of this file drove it with `vi.useFakeTimers()` and
 * `userEvent.setup({ advanceTimers })`, and every test that touched the keyboard hung until the
 * 5-second limit. user-event awaits its own zero-delay timers between keystrokes, so faking the
 * clock puts the typing and the thing that advances the clock on either side of the same await.
 * A short real delay is less clever and it terminates.
 */

/** Short enough not to slow the suite, long enough to span the keystrokes of one word. */
const DELAY = 60;

describe('SearchInput', () => {
  it('commits once for a word typed without pausing', async () => {
    const onChange = vi.fn();
    render(<SearchInput label="Registration number" value="" onChange={onChange} delay={DELAY} />);

    await userEvent.type(screen.getByRole('searchbox'), 'GT1234');

    // Six characters. A per-keystroke implementation never settles on one call, so this fails
    // by timeout rather than passing early - which is the correct outcome for that bug.
    await waitFor(() => expect(onChange).toHaveBeenCalledTimes(1));
    expect(onChange).toHaveBeenCalledWith('GT1234');
  });

  it('shows what is typed before it is committed', async () => {
    render(<SearchInput label="Registration number" value="" onChange={vi.fn()} delay={5_000} />);

    await userEvent.type(screen.getByRole('searchbox'), 'GT');

    // The field is never allowed to lag the operator; only the query is.
    expect(screen.getByRole('searchbox')).toHaveValue('GT');
  });

  it('commits immediately on Enter rather than making a finished typist wait', async () => {
    const onChange = vi.fn();
    render(<SearchInput label="Registration number" value="" onChange={onChange} delay={5_000} />);

    await userEvent.type(screen.getByRole('searchbox'), 'GT{Enter}');

    // A five-second debounce that has not elapsed: only the Enter path can have produced this.
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith('GT');
  });

  it('adopts a value changed from outside, which is how Reset and the filter chips work', () => {
    const { rerender } = render(
      <SearchInput label="Registration number" value="GT1234" onChange={vi.fn()} />,
    );
    expect(screen.getByRole('searchbox')).toHaveValue('GT1234');

    rerender(<SearchInput label="Registration number" value="" onChange={vi.fn()} />);
    expect(screen.getByRole('searchbox')).toHaveValue('');
  });

  it('clears to empty in one action', async () => {
    const onChange = vi.fn();
    render(
      <SearchInput
        label="Registration number"
        value="GT1234"
        onChange={onChange}
        delay={5_000}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: /clear registration number/i }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith('');
  });
});
