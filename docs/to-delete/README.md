# Pending deletion

Everything under this folder is a candidate for deletion, not a working reference. Nothing here is
linked from any current doc, and nothing in the codebase reads a path under this folder. It exists so
a person can review before anything is actually removed - delete this whole folder once you agree.

## `release1-demo-cleanup-2026-08-01/`

Moved here as-is from `docs/archive/`. It was already self-identified as archived (the folder name
says so) - build prompts, phase plans and a demo script from the Release 1 cleanup pass on
2026-08-01. Superseded by the current codebase and by `solution.md`.

## `architecture-reviews-2026-09/`

Five dated, point-in-time review documents from a single September 2026 review cycle:

- `2026-09-08-code-quality-audit.md` - explicitly called "historical context only" by the review one
  day later.
- `2026-09-09-external-readiness-review.md`
- `2026-09-09-final-issues-review.md`
- `2026-09-10-final-issues-verification.md` - the final word in that chain.
- `2026-09-09-security-audit.md`

All five predate the S160, S163, S160a, S161, S162 and S162a builds, so any statement in them about
what is or isn't built is now stale. Their code-quality and security findings on the modules that
existed at the time may still hold, but nothing here should be read as current project status -
`solution.md` is the current source of truth for that, and the docs in `docs/facilities/`,
`docs/fleet/`, `docs/fuel/`, `docs/dispatch/` and `docs/emergency/` are the current per-system record.
If a specific finding from one of these reviews is still open, re-file it as its own tracked item
rather than keeping the whole snapshot around.
