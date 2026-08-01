import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router';
import logo from 'assets/sfl-logo.png';
import Alert from 'shared/components/Alert';
import Button from 'shared/components/Button';
import { TextInput } from 'shared/components/fields';
import { signIn } from 'shared/auth/keycloak';
import type { SignInFailure } from 'shared/auth/keycloak';
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
 * would fork the design system and add four Radix dependencies to do what is already here.
 *
 * The layout, the copy and the behaviour are as specified. Only the parts are this repository's.
 *
 * <h2>What signing in actually does</h2>
 *
 * Exchanges the email and password for a real token from the realm, and stores it for the tab. Every
 * subsequent API call carries it as `Authorization: Bearer`, and the roles driving the sidebar are
 * read from the token's own claims rather than from anything this page holds — see
 * `shared/auth/session.ts`.
 */
const LoginPage = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState<SignInFailure | null>(null);
  const [touched, setTouched] = useState(false);

  const emailMissing = email.trim().length === 0;
  const passwordMissing = password.length === 0;
  const incomplete = emailMissing || passwordMissing;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setTouched(true);
    if (incomplete) {
      return;
    }
    setSubmitting(true);
    setFailure(null);
    const result = await signIn(email, password);
    setSubmitting(false);
    if (!result.ok) {
      setFailure(result);
      // Clear only the password. Retyping an email that was probably right is a small insult after
      // a failed sign-in, and it is the field people get wrong least.
      setPassword('');
      return;
    }
    /*
      A full navigation rather than a router push. Everything derived from the actor — programme
      entitlement, system entitlement, the merged permission set, the landing destination — is
      computed once at module scope, which is what makes the sidebar and route guards synchronous.
      A client-side transition would leave all of it holding the pre-sign-in actor.
    */
    window.location.assign(`${import.meta.env.BASE_URL.replace(/\/$/, '')}/`);
    void navigate;
  };

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center px-4 py-10">
      {/*
        The campus at night, with a scrim over it. The scrim is not decoration: the card and the
        wordmark sit on a photograph whose brightness varies across the frame, and without it the
        white heading falls to roughly 2:1 against the lit windows. `aria-hidden` because it carries
        no information a screen reader needs.
      */}
      <div
        aria-hidden="true"
        className="absolute inset-0 bg-cover bg-center"
        style={{ backgroundImage: 'url(/images/homepage-campus-night.jpg)' }}
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

        <div className="rounded-2xl border border-white/10 bg-white p-7 shadow-theme-lg">
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
              onChange={setEmail}
              onBlur={() => setTouched(true)}
              required
              autoFocus
              placeholder="you@clet.gh"
              error={touched && emailMissing}
              helperText={touched && emailMissing ? 'Enter your email address.' : undefined}
            />

            <TextInput
              label="Password"
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={setPassword}
              onBlur={() => setTouched(true)}
              required
              placeholder="••••••••••"
              error={touched && passwordMissing}
              helperText={touched && passwordMissing ? 'Enter your password.' : undefined}
            />

            {failure && (
              <Alert
                variant={failure.reason === 'credentials' ? 'error' : 'warning'}
                title={
                  failure.reason === 'credentials'
                    ? 'Those details did not match'
                    : failure.reason === 'unreachable'
                      ? 'The identity provider is not reachable'
                      : 'Sign-in could not complete'
                }
              >
                <p className="text-theme-sm">{failure.message}</p>
              </Alert>
            )}

            <Button type="submit" loading={submitting} className="w-full">
              {submitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

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
