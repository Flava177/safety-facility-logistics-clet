# `tools/`

Two Python scripts that **write** documents. Neither is part of any build, and neither should be run
casually.

| Script | What it does | Reads | Writes |
|---|---|---|---|
| `build_sfl_srs.py` | Regenerates the SRS as a `.docx` — sections, tables, styling | its own embedded content | `docs/System Mappings and SRS/SFL_SRS.docx` |
| `extract_srs_sources.py` | Pulls text out of a source PDF/DOCX into an intermediate form | a PDF or DOCX given on the command line | an extract file |

## Why this file exists

`build_sfl_srs.py` **overwrites the SRS**, and the SRS is the contract. `solution.md` opens by naming
it as the specification every module, endpoint, event and test traces to, and the go-live readiness
pack names it as the baseline the Registrar's recommendation rests on. A script that regenerates it
is not obviously safe to run by accident from a file listing, and before this README nothing said so.

**If the SRS on disk has been edited by hand since it was last generated — and it has been — running
`build_sfl_srs.py` discards those edits.** Check `git status` and `git log` on
`docs/System Mappings and SRS/SFL_SRS.docx` before running it, and commit first.

## Dependencies

Not in any manifest, because nothing in CI runs these:

```bash
pip install python-docx pdfplumber
```

## Keeping or removing them

Reviewed on 1 August 2026 and **kept**. They are the only record of how the SRS document was
produced, and losing that would mean the next revision is hand-edited with no reproducible path back
to a generated baseline. If the SRS ever moves to a document management system, both scripts and this
directory should go with it.
