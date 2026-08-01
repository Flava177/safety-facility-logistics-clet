import { FormEvent, useState } from 'react';
import logo from 'assets/sfl-logo.png';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import { TextInput } from 'shared/components/fields';
import { SEEDED_PASSWORD, seededAccounts } from 'shared/auth/accounts';
import { signIn } from 'shared/auth/signIn';
import type { SignInFailure } from 'shared/auth/signIn';
import { directorate } from 'shared/layout/navigation';

/**
 * Sign in.
 *
 * <h2>Why this is built on the dashboard's own kit</h2>
 *
 * The component this page was specified from is a shadcn block, and it is not used verbatim for two
 * concrete reasons rather than taste.
 *
 * Its classes are shadcn's CSS-variable tokens — `bg-background`, `text-muted-foreground`,
 * `border-input`, `ring-ring`. **This project defines none of them.** Its Tailwind theme is a bespoke
 * scale (`--text-theme-sm`, `--color-teal-*`, `--color-gray-*`), so pasting the block would have
 * produced an unstyled form: transparent surfaces, invisible borders, default type.
 *
 * And it ships its own `Button`, `Input`, `Label` and `cn`. All four already exist in
 * `shared/components/`, where `cn` is the same clsx + tailwind-merge helper and the field controls
 * carry the label/error/helper rhythm every other form in this application uses — including the one
 * line reserved under each field so a form never jumps when validation fires. Adding a parallel set
 * would fork the design system and pull in four Radix dependencies to do what is already here.
 *
 * The layout, the copy and the behaviour are as specified. Only the parts are this repository's.
 *
 * <h2>What signing in does</h2>
 *
 * Matches the email against the seeded accounts, then makes that account the actor for this browser
 * — username, display name, roles, site scopes. Those are what every API call carries, so the portal
 * that opens next is the one that account's roles entitle it to: a fleet manager lands on the fleet
 * dashboard, a driver on their driving day, a requester on their requests.
 *
 * It is a development sign-in and `accounts.ts` says so plainly. The services it talks to run with
 * `SFL_SECURITY_ENABLED=false`, where the actor is whatever the headers claim, so a form here can
 * only decide which headers to send. The token-issuing path exists beside it in `keycloak.ts` for
 * when a service runs with security on.
 */
const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [failure, setFailure] = useState<SignInFailure | null>(null);
  const [touched, setTouched] = useState(false);
  const [accountsOpen, setAccountsOpen] = useState(false);

  const emailMissing = email.trim().length === 0;
  const passwordMissing = password.length === 0;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setTouched(true);
    const result = signIn(email, password);
    if (!result.ok) {
      setFailure(result);
      if (result.reason === 'wrong-password') {
        // Clear only the password. Retyping an email that was probably right is a small insult
        // after a failed sign-in, and it is the field people get wrong least.
        setPassword('');
      }
      return;
    }
    /*
      A full navigation to the application root rather than a router push.

      Everything derived from the actor — programme entitlement, system entitlement, the merged
      permission set and the landing destination — is computed once at module scope, which is what
      makes the sidebar and the route guards synchronous. A client-side transition would leave all
      of it holding the pre-sign-in actor, so the signed-in user would land on somebody else's
      portal. The root then redirects to `landingPath()`, which is this account's first entitled
      destination.
    */
    window.location.assign(`${import.meta.env.BASE_URL.replace(/\/$/, '')}/`);
  };

  const fillFrom = (accountEmail: string) => {
    setEmail(accountEmail);
    setPassword(SEEDED_PASSWORD);
    setFailure(null);
    setAccountsOpen(false);
  };

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center px-4 py-10">
      {/*
        The campus at night, with a scrim over it. The scrim is not decoration: the wordmark sits on
        a photograph whose brightness varies across the frame, and without it the white heading falls
        to roughly 2:1 against the lit windows. `aria-hidden` because it carries nothing a screen
        reader needs.
      */}
      <div
        aria-hidden="true"
        className="absolute inset-0 bg-cover bg-center"
        style={{ backgroundImage: 'url(images/homepage-campus-night.jpg)' }}
      />
      <div aria-hidden="true" className="absolute inset-0 bg-gray-950/70" />

      <div className="relative w-full max-w-[26rem]">
        <div className="mb-6 flex flex-col items-center text-center">
          <img src={logo} alt="" aria-hidden="true" className="h-14 w-14 object-contain" />
          <h1 className="mt-3 text-title-sm font-bold tracking-tight text-white">
            {directorate.name}
          </h1>
          <p className="mt-1 text-theme-sm text-gray-300">{directorate.parentOrganisation}</p>
        </div>

        <div className="rounded-2xl bg-white p-7 shadow-theme-lg">
          <h2 className="text-theme-xl font-bold text-gray-900">Welcome Back</h2>
          <p className="mt-1 text-theme-sm text-gray-600">
            Sign in to your account to access CLET services securely from this browser.
          </p>

          <form onSubmit={submit} noValidate className="mt-6 space-y-4">
            <TextInput
              label="Email"
              type="email"
              name="username"
              autoComplete="username"
              value={email}
              onChange={(value) => {
                setEmail(value);
                setFailure(null);
              }}
              onBlur={() => setTouched(true)}
              required
              autoFocus
              placeholder="you@clet.gh"
              error={touched && emailMissing}
            />

            <TextInput
              label="Password"
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(value) => {
                setPassword(value);
                setFailure(null);
              }}
              onBlur={() => setTouched(true)}
              required
              placeholder="••••••••••"
              error={touched && passwordMissing}
            />

            {failure && (
              <Alert variant="error" title="Could not sign you in">
                <p className="text-theme-sm">{failure.message}</p>
              </Alert>
            )}

            <Button type="submit" className="w-full">
              Sign in
            </Button>
          </form>

          {/*
            The account list is on the page deliberately. This is a development sign-in against
            seeded accounts, and hiding the list would mean the only way to use the form is to read
            the source — while the accounts themselves are in the bundle either way.
          */}
          <div className="mt-6 border-t border-gray-200 pt-4">
            <button
              type="button"
              onClick={() => setAccountsOpen((open) => !open)}
              aria-expanded={accountsOpen}
              className="text-theme-sm font-medium text-teal-800 hover:underline"
            >
              {accountsOpen ? 'Hide accounts' : `Show the ${seededAccounts.length} seeded accounts`}
            </button>

            {accountsOpen && (
              <>
                <p className="mt-2 text-theme-xs text-gray-600">
                  Every account uses the password{' '}
                  <code className="rounded bg-gray-100 px-1 font-medium">{SEEDED_PASSWORD}</code>.
                  Choose one to fill the form.
                </p>
                <ul className="custom-scrollbar mt-3 max-h-64 space-y-1 overflow-y-auto pr-1">
                  {seededAccounts.map((account) => (
                    <li key={account.email}>
                      <button
                        type="button"
                        onClick={() => fillFrom(account.email)}
                        className="w-full rounded-md px-2 py-1.5 text-left transition-colors hover:bg-gray-50"
                      >
                        <span className="block text-theme-sm font-medium text-gray-900">
                          {account.email}
                        </span>
                        <span className="block text-theme-xs text-gray-500">
                          {account.description}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>

          <p className="mt-5 text-theme-xs text-gray-500">
            This is a CLET system. Activity is recorded against your account, and every action you
            take is attributed to it in the audit trail.
          </p>
        </div>

        <p className="mt-5 text-center text-theme-xs text-gray-400">
          {directorate.parentOrganisation} · Cluster 9
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
