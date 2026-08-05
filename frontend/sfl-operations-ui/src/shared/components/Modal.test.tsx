import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Modal, { ModalCloseButton } from './Modal';

/**
 * Where focus lands when a dialog opens.
 *
 * <p>Worth its own test because the failure is silent and the blast radius is every dialog in the
 * dashboard. Before this, nothing moved focus on open, so it stayed on the button behind the modal:
 * a keyboard user was left outside a surface that had just declared `aria-modal`, and everyone else
 * had to click before they could type.
 *
 * <p>The three cases below are the ones that are easy to get wrong, and the reason the naive
 * "focus the first focusable element" is not what this does.
 */

const noop = () => {};

describe('Modal focus on open', () => {
  it('lands on the first form control, not the close button', () => {
    // The close × is the first focusable element in every FormDialog. Landing there would make
    // Enter discard the form the operator just opened.
    render(
      <Modal open onClose={noop}>
        <ModalCloseButton onClose={noop} />
        <input aria-label="Registration number" />
      </Modal>,
    );
    expect(document.activeElement).toBe(screen.getByLabelText('Registration number'));
  });

  it('respects a field that asked for focus explicitly', () => {
    // React sets autoFocus as a property and focuses during commit, leaving no `autofocus`
    // attribute to query — so the rule is "focus is already inside, do not second-guess it" rather
    // than a selector. Searching for `[autofocus]` matched nothing and stole focus from the
    // emergency dialogs that rely on this.
    render(
      <Modal open onClose={noop}>
        <input aria-label="First" />
        {/* eslint-disable-next-line jsx-a11y/no-autofocus */}
        <input aria-label="Chosen" autoFocus />
      </Modal>,
    );
    expect(document.activeElement).toBe(screen.getByLabelText('Chosen'));
  });

  it('skips a field hidden inside a collapsed disclosure', () => {
    // `querySelectorAll` finds inputs inside a closed <details>, but they cannot take focus — so a
    // naive implementation calls .focus() on them, nothing happens, and focus stays outside the
    // modal. This is exactly the shape "Register a vehicle" now has, with VIN behind More details.
    render(
      <Modal open onClose={noop}>
        <details>
          <summary>More details</summary>
          <input aria-label="VIN" />
        </details>
        <input aria-label="Operational owner" />
      </Modal>,
    );
    expect(document.activeElement).toBe(screen.getByLabelText('Operational owner'));
  });

  it('takes the field inside a disclosure that is already open', () => {
    render(
      <Modal open onClose={noop}>
        <details open>
          <summary>More details</summary>
          <input aria-label="VIN" />
        </details>
        <input aria-label="Operational owner" />
      </Modal>,
    );
    expect(document.activeElement).toBe(screen.getByLabelText('VIN'));
  });

  it('puts focus on the dialog itself when there is nothing to type into', () => {
    const { container } = render(
      <Modal open onClose={noop}>
        <p>Are you sure?</p>
      </Modal>,
    );
    expect(document.activeElement).toBe(container.querySelector('[role="dialog"]'));
  });

  it('does not touch focus while closed', () => {
    render(
      <Modal open={false} onClose={noop}>
        <input aria-label="Registration number" />
      </Modal>,
    );
    expect(document.activeElement).toBe(document.body);
  });

  it('still closes on Escape', () => {
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose}>
        <input aria-label="Registration number" />
      </Modal>,
    );
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('ignores Escape while a submit is in flight', () => {
    const onClose = vi.fn();
    render(
      <Modal open locked onClose={onClose}>
        <input aria-label="Registration number" />
      </Modal>,
    );
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(onClose).not.toHaveBeenCalled();
  });
});
