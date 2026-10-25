# Development

How to work on GlossHub: setting up, the everyday commands, testing, the
conventions the code follows, the interfaces that must not break, and the
process for changes. [architecture.md](architecture.md) explains how the
code works, and [format.md](format.md) specifies the input format.

1. [Setup](#1-setup)
2. [Everyday commands](#2-everyday-commands)
3. [Testing](#3-testing)
4. [Conventions](#4-conventions)
5. [Interfaces that must not break](#5-interfaces-that-must-not-break)
6. [The process for changes](#6-the-process-for-changes)

---

## 1. Setup

You need:

- **Java 21** or later, which runs the ClojureScript compiler;
- the **[Clojure CLI](https://clojure.org/guides/install_clojure)**, which
  reads `deps.edn` and downloads the compiler (into `~/.m2`) the first time;
- **Node.js 22** or later, for the tests and the site's assembly script;
- **Python 3**, only for `npm run serve` (any static file server will do).

Then, once:

```sh
npm ci                                # the test tools (Playwright)
npx playwright install chromium       # the browser the browser tests drive
```

The site itself has no npm dependencies: `npm ci` only installs what the
browser tests need.

---

## 2. Everyday commands

Every command is an npm script (`package.json`), so they have the same
names everywhere, and CI runs the same scripts.

| command | what it does |
|---|---|
| `npm run dev` | compiles into `public/js` and recompiles a namespace when its file changes; stays running |
| `npm run serve` | serves `public/` at http://localhost:8000; stays running |
| `npm run test:unit` | compiles and runs the unit tests (seconds) |
| `npm run test:check` | the file checker over the published texts and the test texts: no problems, and each reads the same once written out |
| `npm run build` | the optimized build, `public/js/main.js` |
| `npm run test:browser` | the browser tests, against the last build (about a minute) |
| `npm test` | all of it: unit tests, the file check, the build, browser tests |
| `npm run check -- FILE.gloss …` | lists each file's problems (a folder stands for its `.gloss` files); with `--round-trip`, checks that each reads the same once written out. Fails if any file doesn't pass |
| `npm run build:site` | assembles the deployable site in `site/` (after `npm run build`) |

The scripts call `clojure`, not `clj`: they are the same program, but
`clj` wants `rlwrap` for line editing, which CI machines don't have.
Without the Clojure CLI, the standalone compiler jar from the
[ClojureScript releases](https://github.com/clojure/clojurescript/releases)
runs the same builds: `java -cp cljs.jar:src cljs.main -co release.edn -c`.

### The daily loop

Two terminals running `npm run dev` and `npm run serve`, and the site open
at http://localhost:8000. Save a file, wait for the compile message,
reload the page. Compile errors and warnings appear in the first terminal;
runtime errors appear in the browser's developer console (⌥⌘I or F12,
then *Console*). The development build keeps names readable in error
messages and stack traces (`gloss.render.igt_body`).

Three things to know:

- **Markdown pages and published texts are compiled in.** The compiler
  only recompiles a namespace when its own file changes, so
  `npm run build` and `npm run test:unit` clear their compile cache
  (`target/release`, `target/test`) first, and always build the files as
  they are. `npm run dev` keeps its cache, and only watches `src/`: after
  editing a Markdown page (`pages/`) or a published text (`texts/`), save
  `src/gloss/app/markdown_pages.cljs` or `src/gloss/app/samples.cljs`
  unchanged to trigger a rebuild.
- **Both builds write `public/js/main.js`.** Before running the browser
  tests, or to see what will be published, run `npm run build`.
- **Your saved data while developing** lives in the browser's storage for
  `localhost:8000`, apart from the deployed site's. To start afresh: the
  Settings page's *Erase everything*, or the developer tools'
  *Application → Storage → Clear site data*.

### The REPL

For trying the core without a browser:

```sh
clojure -M -m cljs.main -re node
```

then, at the prompt:

```clojure
(require '[gloss.parse :as parse] '[gloss.serialize :as ser])
(def r (parse/parse-document "\\igt\n\\text puerōs\n\\morph puer-ōs\n\\glmorph boy-ACC.PL\n"))
(:doc r)
(ser/document-source (:doc r))
```

The core namespaces load in Node; the website's (`gloss.app.*`) need a
browser.

---

## 3. Testing

Two suites, for two layers, and a check of the texts.

### Unit tests (`npm run test:unit`)

`cljs.test`, compiled for Node, in `test/gloss/`; fast, and no browser.
The runner runs every namespace ending in `-test` that
`test/gloss/test_runner.cljs` requires, so a new test namespace is added
there.

| file | covers |
|---|---|
| `parse_test.cljs` | the input format: each feature parses as expected, problems point at the right source line, documents round-trip |
| `morph_test.cljs`, `infix_test.cljs` | `\morph` entries, stems and roots, infixes, glosses paired with morphemes, `\syl`, sections and `\end` |
| `notes_test.cljs` | notes: quotes, spans, `#n`, other IGTs, numbering |
| `formatting_test.cljs` | formatting and links in `\text`, `\glword` and prose, and what the other tiers take from the text |
| `reserved_test.cljs`, `tiers_test.cljs` | reserved characters and escapes by tier; the tier table |
| `layout_test.cljs`, `vocab_test.cljs` | cutting words into pieces; abbreviations and parts of speech |
| `app_test.cljs` | the website's logic through `handle` and the state functions: routes, editing published texts, undo, display options, saved data |
| `markdown_test.cljs` | the Markdown subset, and reading a page; every Markdown page (the Guide's, About) has one title and a short description in plain words, its links lead to pages, and its examples parse cleanly (and every "broken" one with a problem) |
| `guide_test.cljs` | the Guide's groups: none empty, no two pages with the same slug, and each page found by its slug and in its group |

### Browser tests (`npm run test:browser`)

[Playwright](https://playwright.dev) drives Chromium through the built
site, with Node's own test runner, in `test/browser/`: pages, editing,
display options, notes, data safety, export and import. Within a file the
tests are steps of one visit (the undo test undoes the edit made by the
test before it), so run a file as a whole:
`node --test test/browser/editing.test.cjs`. To watch what a test does,
change `chromium.launch()` in `helpers.cjs` to
`chromium.launch({ headless: false, slowMo: 300 })`.

### The file check (`npm run test:check`)

The file checker (`npm run check`) over the published texts and the test
texts: each must have no problems and read the same once the site writes
it out. Running it in the tests also keeps the checker itself working.

### Test texts

Both suites use the constructed texts in `test/fixtures/` (Latin, Hebrew,
Japanese), which between them use every feature of the format. The unit
tests compile them in (`gloss.fixtures`); the browser tests load them as
the reader's own texts. A browser test of how something is drawn uses
these texts, not the Guide's examples, so rewriting a Guide page can't
break it; only the tests of the Guide itself (its navigation, its live
examples) open its pages. A feature none of the texts has is added to one
of them, with the unit tests' expectations of that text. Unit tests of
*published* texts swap them in as
the library with `with-redefs` (see `app_test.cljs`), so they don't
depend on what the site publishes.

### What to test where

- Anything about the **input format or the model**: a unit test, with a
  round-trip check. This is the most valuable suite: the format is what
  readers' texts are kept in.
- **Website logic** (an action, a setting, saving): a unit test calling
  `handle` or the state functions.
- What only a real browser shows (**focus, events, rendering, storage,
  small screens**): a browser test.
- **A bug**: first a test that fails because of it, then the fix.

---

## 4. Conventions

What the code already does; keeping to it keeps the code coherent.

**Structure.**

- Namespaces are `gloss.<area>`; the website's are `gloss.app.<area>`,
  in `src/gloss/app/`. The core (everything else under `src/gloss/`)
  never requires the website and never touches the browser, so it runs
  and is tested on Node. Code only the website uses goes under `app/`,
  even when, like `gloss.app.dom`, it is generic.
- A namespace holds one concern. When a file passes a few hundred lines,
  look for the seam (architecture.md §3 lists the current ones).
- Data, not objects: documents, settings and state are maps and vectors;
  event handlers in hiccup are vectors such as `[:igt/delete id]`.
- Pure where possible: the core and `handle` take values and return
  values. Effects (storage, navigation, files) stay in a few named
  functions ending in `!`.

**Names.** The project's terms are defined in architecture.md
(*Vocabulary*). An *IGT*, an interlinear glossed text, is one line of the
text with everything added to it (`\igt`). A *tier* is what one command
writes in an IGT's source (`\glmorph`), and a *row* is what the reader
sees (a band of cells, one per word). A *line* is a line of text, such as
the one an IGT holds; a *source line* is a line of the source text.
Tiers are named by their standard names everywhere: in code, in data
(`:glmorph`), in CSS classes (`.row.glmorph`, for the tier's row). IGTs
are `igt` in code (`:igts`, `check-igt`, `.igt`), and `line` is kept for
lines of text (`sublines`, `:source-line`). The site's user is "the
reader".

**Docstrings and comments.** Every public function has a docstring, and
so does every private one whose purpose isn't plain from its name. A
docstring says what the function returns or does, names its arguments in
backticks, and gives the shape of data it returns (`{:segs :problems}`).
Comments explain *why*; the code says *what*. Section headers (`;; ----`)
divide long namespaces, with a paragraph when the section has a concept
to explain.

**Formatting.** Two-space indentation, standard Clojure layout,
`:require` lists sorted; `.editorconfig` has the rest.

**User-facing text** is plain and specific ("Click again to delete",
"Your changes couldn’t be saved in this browser"), uses typographic
quotes and apostrophes (’ “ ”), and avoids jargon. Problems say what is
wrong and how to fix it.

**Commit messages.** A first line under about 70 characters that says
what the commit does, in the imperative ("Add LaTeX export for glosses");
then a body that says why, and anything a reviewer needs to know.
`Fixes #12` closes an issue when the change is merged.

---

## 5. Interfaces that must not break

Most code can change freely; these can't, because readers depend on them
and the site can't reach into their browsers to fix things afterwards.

- **The input format.** Texts are written by hand and kept for years.
  The contract (format.md §2): a text that parses without warnings means
  the same thing in every later version with the same format number. A
  change that keeps the contract (a new command, a meaning for reserved
  syntax) updates `format.md` in the same pull request; one that breaks
  it needs a new format version and a conversion from the old one.
- **Saved data.** Readers' browsers hold data in the current saved format
  (`gloss.app.storage/save-format`). Texts are saved as source text, so the
  internal model can change freely; a change to the structure around them
  bumps `save-format`, teaches `migrate` the old shape, and adds a test
  that an old save still loads.
- **Export files** (`% GlossHub export`, `gloss.app.storage/export-text`)
  are a reader's backup: an older export must always import.
- **Addresses.** `#<slug>` addresses of published texts and the section
  names (`#guide`, `#settings`, …) are bookmarked and linked; renaming one
  needs the old address to keep working.

---

## 6. The process for changes

A small open-source project's process, scaled to one main developer, with
GitHub's tools: issues, branches, pull requests, Actions, releases.

```
 issue ──▶ branch ──▶ commits ──▶ pull request ──▶ CI ──▶ review ──▶ merge ──▶ deploy
   ▲                                                                            │
   └───────────────────────── feedback, bugs ◀──────────────────────────────────┘
```

1. **Issue.** A change starts as an issue: a bug ("sublines lose their
   scansion"), a feature ("LaTeX export") or a task ("split style.css").
   It records the *why* and the decisions, and gives the work a number.
   Labels (`bug`, `feature`, `format`, `ui`, `docs`, `feedback`, and
   `needs decision` for what waits on a choice) and milestones keep
   issues sorted. Readers' feedback arrives as issues too (the site's
   *Send feedback* link, with a form for it).
2. **Design first, for the format.** A change to the input format is
   proposed in its issue (or a draft of `format.md`) and agreed before
   code: the format is the one interface that can't be fixed later.
3. **Branch.** One branch per issue, off an up-to-date `main`, named for
   what it does: `feature/latex-export`, `fix/subline-scansion`. `main`
   always works and is always deployable.
4. **Commits.** Small and self-contained: each builds and passes the
   tests (see *Commit messages* above).
5. **Pull request.** When the branch is ready (or early, as a draft), open
   a pull request into `main`; the template asks what changed, why, how it
   was tested, and whether the docs and the changelog are updated. Keep it
   focused: one feature or fix, a few hundred lines at most. A refactor
   and a feature are two pull requests.
6. **CI.** `.github/workflows/test.yml` runs the unit and browser tests on
   every pull request. A red check blocks merging (branch protection on
   `main`).
7. **Review.** With collaborators, someone else approves. Alone, read your
   own diff in GitHub's *Files changed* view before merging: it is a
   different view from the editor, and it catches leftovers.
8. **Merge.** *Squash and merge*, so that `main` has one commit per pull
   request, titled and described by it. Delete the branch.
9. **Deploy.** Merging into `main` runs `.github/workflows/deploy.yml`:
   the tests again, the build, and publishing to GitHub Pages. To see
   locally what will be published: `npm run build && npm run build:site`,
   then serve `site/`.

**Dependencies.** Dependabot opens a pull request each month for the
workflows' actions and for Playwright; CI tests it like any other. The
ClojureScript compiler (`deps.edn`) is updated by hand, with a full test
run and a look at the release build's size.

**Releases.** A site deployed on every merge has versions for people,
not for installation: a tag (`v0.2.0`) and a GitHub release at meaningful
points, with notes taken from `CHANGELOG.md`, where every change a reader
would notice gets a line under *Unreleased* in its pull request. Versions
follow [semantic versioning](https://semver.org), and stay below 1.0
while the site is young. Three version numbers are independent:
the site's (tags), the input format's (`\glosshubformat`, format.md §2)
and the saved data's (`save-format`).

To make a release:

1. In `CHANGELOG.md`, rename *Unreleased* to the version and the date
   (`## [0.3.0] - YYYY-MM-DD`), start a new, empty *Unreleased* above
   it, and update the comparison links at the end of the file. Merge
   this like any other change.
2. Tag the merge commit and publish a GitHub release from the tag
   (*Releases → Draft a new release*, tag `v0.3.0` on `main`), with the
   changelog's section as its notes.
