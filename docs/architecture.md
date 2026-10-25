# Architecture

How GlossHub's code is organized: the big picture, where things are, what
each part is responsible for, and the decisions behind them. It is for
anyone reading or changing the code, and assumes you know Clojure and the
basics of the web. [format.md](format.md) specifies the input format the
code reads and writes, and [development.md](development.md) covers working
on it: setup, commands, tests and the process for changes.

1. [The big picture](#1-the-big-picture)
2. [Builds, and the site in the browser](#2-builds-and-the-site-in-the-browser)
3. [A map of the repository](#3-a-map-of-the-repository)
4. [The core: from text to hiccup](#4-the-core-from-text-to-hiccup)
5. [The website: state, actions, rendering](#5-the-website-state-actions-rendering)
6. [Design decisions](#6-design-decisions)
7. [Where to change things](#7-where-to-change-things)
8. [Vocabulary](#8-vocabulary)

---

## 1. The big picture

GlossHub is a **static site**: there is no server-side program. A web
server only hands out four kinds of file, and everything else happens in
the reader's browser:

```
public/index.html      the page skeleton: an empty <div id="app">
public/css/style.css   how everything looks
public/js/main.js      the whole program, compiled from ClojureScript
(fonts, from Google Fonts)
```

`main.js` contains the parser, the renderer, the site's pages, the
published texts and the Markdown pages, the pages written in prose (the
Guide's and About); the last two are embedded at build time. When it
runs, it reads the reader's saved data from the browser's storage, works
out which page the address asks for, and builds that page inside
`<div id="app">`. From then on, every click and keystroke
is handled inside the browser, and the reader's texts and settings are
saved back to the browser's storage. Nothing is ever sent anywhere.

The code is in two layers:

```
 ┌──────────────────────────── the website (gloss.app.*) ─────────────────────────────┐
 │  pages, navigation, state, actions, saving, the render loop, the DOM               │
 │                                                                                    │
 │   ┌──────────────────── the core tool (no browser needed) ─────────────────────┐   │
 │   │  input text ──parse──▶ document ──layout/render──▶ hiccup                  │   │
 │   │               ◀──serialize──                                               │   │
 │   │  (gloss.parse, gloss.serialize, gloss.model and the namespaces             │   │
 │   │   around it, gloss.layout, gloss.render: section 3 lists them all)         │   │
 │   └────────────────────────────────────────────────────────────────────────────┘   │
 └────────────────────────────────────────────────────────────────────────────────────┘
```

The **core** is pure functions over plain data: text in, a document map
out; a document and display settings in, hiccup (HTML as Clojure vectors)
out. It knows nothing about pages, saving or the DOM. That is why it is
easy to test, and why it could later run elsewhere (on the JVM, to produce
LaTeX, say) with little change: it uses JavaScript directly only for
Unicode (normalization, and regular expressions with Unicode classes, in
`gloss.parse` and `gloss.chars`) and for a few caches keyed by object
(`WeakMap`, in `gloss.notes` and `gloss.render`).

The **website** wraps the core in an application: one state atom, actions
that change it, and a render loop that turns it into a page.

---

## 2. Builds, and the site in the browser

### Builds

The code is ClojureScript, compiled by the official compiler, `cljs.main`,
which the Clojure CLI runs on Java with the options in an `.edn` file:

| config | used by | optimizations | output |
|---|---|---|---|
| `dev.edn` | `npm run dev` (`clojure -M:dev`) | `:none` | `public/js/main.js`, a small loader, plus one file per namespace in `public/js/out/` |
| `release.edn` | `npm run build` (`clojure -M:release`) | `:advanced` | `public/js/main.js`, one optimized file |
| `test.edn` | `npm run test:unit` (`clojure -M:test`) | `:none`, target Node | `target/test/test.js`, run with `node` |
| `check.edn` | `npm run check`, `npm run test:check` (`clojure -M:check`) | `:none`, target Node | `target/check/check.js`, the file checker |

The release build goes through the Google Closure Compiler's advanced
optimizations, which drop dead code and shorten names. Code that touches
browser objects therefore uses only standard properties (`.-value`,
`.addEventListener`), whose names the compiler knows and keeps.

### Files compiled in

The published texts and the Markdown pages are part of `main.js`.
`gloss.inline` has three macros, which run inside the compiler: `file`
puts a file's contents into the code as a string (`(inline/file
"texts/aeneid.gloss")` in `gloss.app.samples`), `markdown-files` does the
same for a folder's Markdown pages, in the order given (a group of the
Guide), and `listing` gives the files under a folder (a test checks that
every Markdown page is listed). Publishing a text or changing a page
therefore needs a rebuild.

The compiler recompiles a namespace only when its own source file
changes, so a build from its cache would keep an old page. That is why
`npm run build` and `npm run test:unit` delete their caches
(`target/release`, `target/test`) first. The dev watcher keeps its cache
and watches only `src/` (development.md, *The daily loop*).

### In the browser

- **One page.** `index.html` holds an empty `<div id="app">` and the
  script; going to another page means replacing what is inside the
  `div`.
- **Addresses** are the URL's fragment (`…/#guide`, `…/#aeneid`), which
  is never sent to the server, so the site works on any static host.
  Changing it fires `hashchange`, and `gloss.app.state/route-of` turns it
  into a route map such as `{:page :guide :guide-page "scansion"}`.
- **Listeners** are set on elements as they are created
  (`gloss.app.dom`); the global ones (`hashchange`, `keydown` for undo,
  `storage`, `pagehide`) are set up once, in `gloss.app/init`.
- **Storage.** What a reader saves is one EDN string in `localStorage`,
  under the key `glosshub` (section 5.5). Storage belongs to an origin, so
  a local server and the deployed site keep separate data. When one tab
  saves, the site's other tabs get a `storage` event.
- **Fonts** come from Google Fonts (`index.html`): Gentium for Latin and
  Greek, Noto Serif for Hebrew and East Asian scripts. For Chinese,
  Japanese and Korean, `style.css` picks the font by the text's language
  (`\lang`), so shared characters take each language's forms.

### Tests

The unit tests are compiled for Node (`test.edn`; `gloss.test-runner`
runs `cljs.test`), so the core and the website's logic are tested without
a browser. The browser tests (`test/browser/`) are JavaScript: they drive
Chromium through Playwright against the built site. The file checker
runs in the tests too, over the published texts and the test texts.
development.md says what to test where.

---

## 3. A map of the repository

```
deps.edn, *.edn          build configuration (section 2)
package.json             the commands (npm scripts) and the test tools
.github/                 CI and deployment workflows, issue and pull request templates

src/gloss/               the program
  tiers.cljs             the table of tiers: names, kinds, units, formatting ┐
  vocab.cljs             glossing abbreviations and parts of speech          │
  english.cljs           counts and lists in the site's messages             │
  chars.cljs             punctuation, escapes, letters                       │
  formatting.cljs        bold, italics, links: runs, entries, prose          │
  morph.cljs             \morph entries; gloss morphemes paired with them    │
  model.cljs             the document: surfaces, word entries, derived views │
  notes.cljs             which words notes quote; their numbers              │
  check.cljs             problems found once a document exists               │ the core
  edit.cljs              the editors' document-to-document edits             │
  parse.cljs             input text → document, with problems                │
  parse/text.cljs        the \text tier                                      │
  parse/prosody.cljs     \scan and \syl; where \syl divides the words        │
  serialize.cljs         document → input text (round-trips with parse)      │
  layout.cljs            cuts words into syllable and morpheme pieces        │
  render.cljs            document + display settings → hiccup                ┘
  inline.clj             the compile-time macros that embed files
  check_cli.cljs         the file checker (npm run check)
  app.cljs               start-up, the page for each route, the render loop  ┐
  app/                                                                       │
    state.cljs           defaults, preferences, routes, open text, undo/redo │
    storage.cljs         saving in localStorage, reading it back; exports    │
    actions.cljs         what each event does (handle, dispatch!)            │
    ui.cljs              site chrome, display controls, shared widgets       │ the website
    text.cljs            a text's page, read and edited; the IGT cache       │
    pages.cljs           Texts, Guide, About, Settings                       │
    markdown_pages.cljs  the Markdown pages (embedded): the Guide's, About   │
    markdown.cljs        reading a Markdown page; the small Markdown subset  │
    samples.cljs         the published texts and the template for a new text │
    dom.cljs             hiccup → DOM, patching in place                     ┘

Everything under `src/gloss/app/` (and `gloss.app` itself) is the website
and needs a browser; everything else runs on Node too.

scripts/build-site.cjs   assembles the deployable site (content-named files, version stamp)
pages/                   the Markdown pages: the Guide's (guide/<group>/*.md) and About
texts/*.gloss            published texts, in the input format (the Aeneid)
public/                  what is served: index.html, css/style.css (js/ is built)
test/gloss/              unit tests (cljs.test, on Node)
test/browser/            browser tests (Playwright, Node's test runner)
test/fixtures/           constructed texts the two test suites share
docs/                    the input format, this document, and development
```

How the namespaces depend on each other. The core is in layers: each
namespace requires only namespaces in the layers below it (and
`gloss.parse` its own two helpers, `gloss.parse.text` and
`gloss.parse.prosody`).

```
 the website   gloss.app ──▶ app.pages, app.text, app.actions, app.storage
                             ──▶ app.ui ──▶ app.state;   app.dom (a generic library)
 ───────────────────────────────────────────────────────────────────────────────
 the core      render                    document + display settings → hiccup
               layout                    words cut into pieces
               parse, serialize          input text ⇄ document (parse.text, parse.prosody)
               check, edit               problems found later; the editors' edits
               notes                     the words notes quote, their numbers
               model                     the document
               morph, formatting         morphemes and glosses; bold, italics, links
               chars                     punctuation, escapes, letters
               tiers, vocab, english     the tier table; abbreviations, parts of speech;
                                         the words of messages (counts, lists)

 (app.markdown-pages ──▶ app.markdown; app.markdown-pages and app.samples embed
  files with gloss.inline)
```

Nothing in the core requires anything from the website, and `gloss.app.dom`
requires nothing at all.

---

## 4. The core: from text to hiccup

### 4.1 The document model

Everything is plain maps and vectors. A text is made of IGTs, interlinear
glossed texts: each is one line of the text (a sentence, a verse) with
everything added to it. A parsed document (abridged, from one IGT of two
words):

```clojure
{:title "Demo"
 :abbrevs {}                                ; \abbr additions to the vocabulary
 :colors [{:tag "NOM" :color "#929084" :label "Nom"}]
 :notes []                                  ; notes after \end (others are under their IGT)
 :igts
 [{:id "l760c4f2f"                          ; stable id, generated
   :label "1"
   :words [{:pre "" :post ""                ; punctuation outside the word
            :surfaces {"text" {:segs [{:text "Naut"} {:text "a" :sep "-"}]}}
            :glmorph [{:text "sailor"} {:text "NOM.SG" :sep "-"}]  ; by tier name
            :glword "the sailor"}
           {:pre "" :post "."
            :surfaces {"text" {:segs [{:text "can"} {:text "it" :sep "-"}]}}
            :glmorph [{:text "sing"} {:text "3SG.PRS" :sep "-"}]
            :glword "sings"}]
   :free "The sailor sings."
   :notes [{:tag "sailor"                   ; \note[sailor] {nauta} The sailor.
            :groups [{:words "nauta"}]       ; or {:from :to}, :at {:label}, :star?
            :text "The sailor."}]}]}
```

Words are the backbone: each tier's entry for a word (gloss, informal
gloss, part of speech, transliteration, transcriptions) hangs off the
word, as does its ruby, and the gloss morphemes line up with its segments
by position. A word has its segments per surface (`\text`, and
`\text[translit]` when there is one; `gloss.model/segs`). Scansion is
different: it lives on the IGT (`:prosody`, by surface), positioned by
letter offsets, because syllables and morphemes cut words differently. Optional keys appear only when used (`:pos`,
`:ruby`, `:breaks` for sublines, `:prosody`, `:unsupported`
for reserved tiers kept as written, …).

### 4.2 Parsing never fails (`gloss.parse`)

```clojure
(parse/parse-document src)
;; => {:doc {...} :diagnostics [...] :source-lines {...}}
```

- Source is split into **fields** (`\text …`, `\glmorph …`), with
  continuation lines and `%` comments handled, and each command read by
  its standard name (an alias as its tier's name, `\ex` as `\igt`);
  fields are grouped into the header, IGT blocks (at each `\igt`),
  sections, and what follows `\end`.
- An IGT block is read in steps (`parse-block`): the `\text` tier first
  (`gloss.parse.text`), since its words are what every other tier aligns
  with; then the tiers with an entry per word, aligned word by word
  (braces group words; `\morph` entries are read by `gloss.morph`); then
  the syllable tiers (`gloss.parse.prosody`), each placed on the letters
  of its text tier and checked to divide the words where that tier does
  (`word-division-problem`). Whether an IGT's two syllable tiers agree (as
  many syllables, each word beginning at the same one) is checked on the
  stored IGT instead (`gloss.check`), like its two `\morph` tiers, so the
  problem stays listed under it. A note keeps its groups as written; which
  words they quote is worked out later by `gloss.notes`.
- **Nothing is rejected.** A problem becomes a diagnostic map (`{:level
  :warning :msg "…" :source-line 7}`), and a tier that can't be aligned is
  kept verbatim under the IGT's `:raw`, so no input is ever lost.
  `:source-line` is how the editor's "Line 7" buttons find a problem;
  `:source-lines` maps each IGT's tiers to their source lines (and
  `"igt"` to its first), for problems found later.

Some problems are only visible once a document exists, such as a gloss
with a different number of parts than its word. Those come from
`check/check-igt` and `check/check-doc`, which the editor shows under
each IGT. Wherever source text is shown with its problems (the source
editors, the Guide's live examples, the check tool), `check/problems`
puts the parser's and the checks' together, each with its source line.

### 4.3 Serializing round-trips (`gloss.serialize`)

`ser/document-source` writes a document back in the input format. The
tests check the round trip: parse, write back, parse again, and the two
documents are equal.
This matters because **source text is the storage format** (section 5.5)
and because editing a cell is "change the model, show the new source".

### 4.4 The document and its helpers

The rest of the core, below the parser and the serializer, from the
bottom up:

- **`gloss.tiers`**: the table of tiers. Each tier's kind (`:kind`: text,
  segmentation, annotation or commentary) and unit (`:unit`: what its
  entries or marks go with) are values here, as is what is arbitrary
  about it (its names, the characters it reads, how it takes formatting,
  its label as a row), and the parser, the serializer, the checks and the
  display ask the table.
- **`gloss.chars`**: characters as the tiers read them. Backslash escapes
  are hidden while a tier is parsed (each escaped character replaced by a
  private-use stand-in, so no markup rule needs to know about escapes) and
  restored afterwards; letters are compared without diacritics or case
  (`letter-clusters`), which is how `\morph`, `\syl` and notes' quotes are
  matched against the text tiers.
- **`gloss.formatting`**: bold, italics and links, kept as runs over a
  text; `\glword` entries and prose.
- **`gloss.morph`**: a `\morph` entry read (`parse-morph-entry`) and placed
  on its word as written (`project-morph`); the gloss morphemes of
  `\glmorph` paired with the morphemes (`gloss-segments`, infixes
  included).
- **`gloss.model`**: the document itself. A word's segments on each
  surface (`segs`); the entries a word stores under their tiers' names
  (`read-entry` reads one, for the parser and the cell editor alike;
  `serialize/entry` writes one out); the formatting a tier takes from the
  text (`styles-from-text`); which rows an IGT or document has
  (`igt-rows`, `doc-features`); the row order (`row-order`).
- **`gloss.notes`**: `note-index`, the words each note's groups quote, the
  notes' numbers in reading order, where each note's text is shown, and
  their problems; computed over the whole document and cached per
  document.
- **`gloss.check`**: problems found once a document exists (`check-igt`,
  `check-doc`), each naming the tier it concerns; `problems`, every
  problem of a parse with its source line.
- **`gloss.edit`**: `set-entry`, `set-free`, `replace-igt`, … each a pure
  function from a document to a document.

### 4.5 Layout (`gloss.layout`)

A word is drawn in pieces, cut two ways at once: by morpheme (for
glosses) and by syllable (for scansion marks above).
`layout/igt-layout` computes, for each word, its **pieces**: the
intersections of syllables and morphemes, with flags for syllable starts,
elision, ruby, and the note numbers each word shows. This is the most
algorithmic part of the code, and it is display-independent.

### 4.6 Rendering (`gloss.render`)

```clojure
(render/context doc view ui)          ; computed once per page render
(render/igt-body ctx igt opts)        ; hiccup for one IGT
```

`context` bundles what the IGT views need: the **effective view** (the
reader's settings after applying the group switches, where switching off
"Glossing" or any other group hides everything in it without forgetting
the settings inside), the row order, the document's vocabulary and note
numbers, and the edit state. `igt-body` returns hiccup:

```clojure
[:div.igt-grid
 [:div.igt-main
  [:div.words
   [:span.unit
    [:span.word
     [:span.row.text    [:span.syl [:span.pc "Naut"] [:span.pc [:span.sep "-"] "a"]]]
     [:span.row.glmorph [:span.gp "sailor"] [:span.gp [:span.sep "-"] [:abbr.gram {:title "nominative"} "nom"] "." …]]
     [:span.row.glword  "the sailor"]]]
   …]
  [:div.free "The sailor sings."]]]
```

Each word is a column, a little vertical stack of its cells, one for each
row shown; CSS lays the columns out side by side and wraps them.
Glossing abbreviations become `<abbr>` elements, so hovering shows the full
name. In edit mode the same functions add click targets
(`:on {:click [:edit/start target]}`), and in read mode (`:read? true`)
they don't.

---

## 5. The website: state, actions, rendering

The website follows one loop:

```
        ┌──────────────────────────────────────────────────────┐
        ▼                                                      │
   state (one atom) ──page──▶ hiccup ──gloss.app.dom──▶ DOM ──event──▶ action ──handle──▶ new state
                                                          (data like [:edit/draft])
```

This is the architecture of re-frame, Elm and Redux, here without a
library.

### 5.1 State (`gloss.app.state`)

One atom, `state`, holds everything:

| key | what | saved? |
|---|---|---|
| `:docs`, `:order` | the reader's own texts (id → document) and their order | yes |
| `:overrides` | the reader's edited versions of published texts (slug → document) | yes |
| `:view` | the reader's default display options | yes |
| `:text-views` | display options changed on a particular text (key → changed options) | yes |
| `:prefs` | theme, text size, opening mode | yes |
| `:route` | the current address, parsed | no |
| `:mode` | `:read` or `:edit` | no |
| `:ui` | transient edit state: the cell being edited and its draft, an open source editor, an armed delete button | no |
| `:history` | undo/redo stacks per text, for the visit | no |
| `:status` | messages about the site (a failed save, …) | no |
| `:example-drafts` | edits to the live examples on Markdown pages, by page address and example (`guide-notes:1`), for the visit | no |

The published texts are not in the state: they are constants
(`state/library`, parsed once, lazily). `current-doc` answers "what is on
this page": the reader's edited version of a published text if there is
one, else the published one, or the reader's own text. `update-doc`
changes it: the first edit to a published text creates the reader's
version under `:overrides`, and every change is recorded for undo
(`record-change` keeps `{:before :after}` pairs).

### 5.2 Actions (`gloss.app.actions`)

Views never change state themselves. An event handler in hiccup is
**data**:

```clojure
[:button {:on {:click [:history/undo]}} "Undo"]
[:input  {:on {:input [:edit/draft] :keydown [:edit/key]}}]
```

`gloss.app.dom` passes the event and that vector to `dispatch!`, which runs

```clojure
(swap! state handle event action)
```

`handle` is one big `case` over the action name, from state to new state.
There are about forty actions (`:edit/start`, `:igt/delete`,
`:view/toggle`, `:doc/export`, `:prefs/set`, …). Keeping them as data
means a view is just a function from state to hiccup, actions can be
tested by calling `handle` directly (the unit tests do), and the renderer
could be swapped for Replicant without touching the views.

`handle` is *mostly* pure. A few actions need an effect (navigating to a
new text's address, downloading a file, reading an imported file,
`preventDefault` on a key), and do it in place, usually deferred with
`setTimeout` so it happens after the state change. A stricter design
would return effects as data, as re-frame does; at this size it isn't
worth it.

`dispatch!` also catches failures: an action that throws leaves the state
as it was and shows a message.

### 5.3 The DOM patcher (`gloss.app.dom`)

`dom/render!` makes the DOM match a hiccup tree. The first time it creates
elements; after that it **patches**: it walks the new hiccup alongside the
previous render's, child by child, and only touches what differs (a text,
an attribute, a class), creating or removing elements where the shape
changed. Patching instead of rebuilding matters for more than speed: an
`<input>` that survives keeps its focus and cursor while you type.

Two details make it fast:

- **Identity skipping.** If a subtree is the *same object* as last time
  (`identical?`, a pointer comparison), it is skipped without being looked
  at. Persistent data makes this sound: a value that is identical can't
  have changed.
- **Caches keyed by value.** Taking an element apart (tag, attributes,
  flattened children) is remembered per hiccup vector in a JavaScript
  `WeakMap` (a map whose keys don't keep objects alive), so last render's
  tree never needs re-analysis.

### 5.4 The render loop (`gloss.app`)

`init` loads the saved state, parses the address, registers the global
listeners, and adds a **watch** to the state atom. On every change the
watch:

1. starts a save, if anything saved has changed (section 5.5);
2. calls `render!`, which computes `(page @state)`, patches the DOM, sets
   the window title, and applies the theme and text size as attributes on
   `<html>` for the CSS.

Rendering is synchronous, so the page never lags behind typing. If an
event fires *during* a render (removing a focused input fires `blur`),
the loop runs once more rather than nesting. If rendering throws, a
fallback page that depends on nothing is shown instead
(`ui/crash-page`).

`page` dispatches on the route to `pages/published-page`,
`text/text-page`, `pages/guide-page`, and so on. Each page is wrapped by
`ui/shell` (navigation bar, status messages).

#### The IGT cache (`gloss.app.text`)

Building hiccup for every IGT of a long text on every keystroke was the
main cost, so a text's IGTs are cached. An IGT's view is reused, as the
very same object, unless one of these changed:

- the IGT itself, or its position;
- its share of the edit state (`igt-ui`: only the IGT being edited sees
  the draft);
- its surroundings: the mode, the display options, the document without
  its IGTs, note numbering (`igts-env`; when this changes, the whole
  cache is dropped).

Together with identity skipping in `gloss.app.dom`, typing in one cell costs
one IGT's work whatever the length of the text.

### 5.5 Saving (`gloss.app.storage`)

- **What is saved:** `saved-form` picks the saved keys (table above),
  converts every document to its **source text** with the serializer, and
  writes the whole map as one EDN string under `glosshub`, with a
  `:format` number. Source text is the public, stable form: changing the
  internal model never breaks saved texts, because they are parsed again
  on load. Converting is cached per document value, so only changed texts
  are re-serialized.
- **When:** `save!` waits 300 ms and coalesces, so a burst of edits is
  written once; leaving or hiding the page writes immediately (`flush!`).
- **Loading:** `load-saved` reads, checks the shape, `migrate`s older
  formats, and cleans settings against the defaults (unknown keys and
  wrongly typed values are dropped, so a newer or older version's settings
  can't break the page).
- **Safety:** a failed write shows a warning; unreadable data is copied
  aside before anything can overwrite it; another tab's save is adopted
  rather than overwritten (the `storage` event).

### 5.6 Pages (`gloss.app.pages`, `gloss.app.text`, `gloss.app.ui`)

Plain functions from state to hiccup. Every Markdown page (About, and each
Guide page) is drawn by one function, `markdown-page`: its title, then its
body, rendered with `gloss.app.markdown`; a Guide page adds its group above
the title and links to the rest of its group below the body. Each
` ```gloss ` block in a page is a live example, parsed and rendered by the
same core functions as a text page (`render/igts-view`, without its
cache), with a fixed display (`pages/example-view`) so examples look the
same for everyone. A reader's
edits to an example are kept for the visit under `:example-drafts`, by the
page's address and the example's number (`guide-notes:1`, `about:0`).

---

## 6. Design decisions

Each with what it buys, what was set aside, and what it costs.

| decision | why | alternative | cost |
|---|---|---|---|
| **Plain text is the source of truth and the storage format** | Readable, diffable, editable by hand, like LaTeX glossing source; survives changes to the internal model | Store the model (JSON/EDN) | Every feature needs parser *and* serializer support, kept in sync by round-trip tests |
| **The parser never fails** | Half-typed input is the normal state while editing; nothing typed is lost | Reject invalid input | Diagnostics and a `:raw` fallback have to be carried everywhere |
| **One model for all tiers** (words → morphemes; prosody on letters) | Parallel arrays per tier (an earlier approach) break on one typo; one structure keeps glosses and scansion aligned | Each tier its own array | Layout must reconcile two segmentations (`gloss.layout`) |
| **Display settings apart from data** | Readers choose detail without touching the text; one text serves many audiences | Settings in the document | The effective-view logic (group switches over the settings inside them) |
| **No UI framework; a small hiccup patcher** | No npm, no build plugins; ~200 lines we understand fully; Replicant-compatible hiccup and data handlers keep the door open | Reagent/React, Replicant, re-frame | We maintain the patcher, and optimize it ourselves (section 5.3) |
| **One state atom, data actions** | All state in one place: easy to save, test and reason about | State inside components | `handle` grows large; effects are handled ad hoc (section 5.2) |
| **Hash routing** | Works on any static host with no configuration | The History API (`/my-text` paths) | `#` in addresses; needs server rewrites to change later |
| **Saved in the browser only** | No server, no accounts, nothing to secure or pay for; data stays with the reader | A backend with accounts | Clearing site data loses texts; no sync between devices; export is the backup |
| **Texts and Markdown pages embedded at build time** | One file; tests and site read the same files; works offline | Fetch them at runtime | A rebuild to publish; the bundle grows with every text (fine for dozens, not thousands) |
| **`cljs.main` and `deps.edn`, no shadow-cljs or npm** | Minimal tooling: Java plus one dependency | shadow-cljs (hot reload, npm libraries) | No hot reload; npm libraries are awkward to use |
| **Caching by identity** | Typing cost independent of text length | Rebuild everything (fine below ~20 IGTs) | Cache keys must name everything an IGT's view depends on; a missed dependency shows a stale IGT (tests cover the known ones) |

---

## 7. Where to change things

**Add a tier**, for example another annotation tier with an entry per
word:

1. `tiers.cljs`: add it to the table, `tiers` (name, aliases, kind, unit,
   row label, characters, formatting). Much else follows from it: its
   command is recognized, its characters checked and escaped, its row
   offered in Row order. If it has an entry per word, add it to
   `word-entries` there: the parser then reads it and assigns its entries
   to the words, the word stores it under its name, and IGTs and
   documents know when they have it.
2. `model.cljs`: how an entry is read (`read-entry`) and the display
   setting that shows its row (`row-setting`); `check.cljs`: its checks,
   in `word-checks`.
3. `serialize.cljs`: how an entry is written (`entry`); extend the
   round-trip tests.
4. `render.cljs`: its cell (`row-cell`), and a class for it in
   `style.css` (`.row.<name>`).
5. `app/state.cljs` `default-view` and `app/ui.cljs` `controls`: a display
   option, if it can be hidden.
6. `format.md` and the Guide (`pages/guide/`): a section, a page or a
   paragraph. Tests in `test/gloss/`.

**Add a display option:** `default-view` (state), the control in
`ui/controls`, its effect in `render/effective-view` and the views. Saved
settings are cleaned against `default-view`, so old saves pick up the
default automatically.

**Add an action:** a clause in `actions/handle`, and `[:my/action arg]` in
a view's `:on`. Test it by calling `handle`.

**Add a page:** a function in `app/pages.cljs`, a case in `route-of`
(state), `page` (app) and `sections` (state) if it belongs in the
navigation bar. A page written in prose is a Markdown page: a file in
`pages/` (written like a Guide page, below), an entry in
`gloss.app.markdown-pages`, and a function that draws it with
`markdown-page`, as `about-page` does, with a case in `page-title` (app)
that takes its window title from its title line.

**Publish a text:** add `texts/<slug>.gloss`, with `\title` and `\about`
lines, and an entry in `gloss.app.samples/published`, then rebuild. The text
appears on the Texts page and has its own page at `#<slug>`. The unit
tests check that every published text parses without problems.

**Add a Guide page:** write `pages/guide/<group>/<slug>.md`: front matter
with its description for the Guide's index (`---`, `description: …`,
`---`; at most 100 characters, in plain words), a `# Title` line, then the
page, with `##` headings for its sections. Examples go in ```` ```gloss ````
blocks (```` ```gloss broken ```` for one that shows a problem on
purpose, ```` ```gloss grid ```` for an IGT's source on a grid of tiers and
words), and links to other pages are written `[Title](#guide-<slug>)`. An
example quoted from elsewhere ends its opening line with its source, in
double quotes (```` ```gloss "Lezgian (Haspelmath 1993: 207). Leipzig
Glossing Rules, example 2." ````), shown under it, and the page lists the
full references under `## Sources`, at its end.
Add its slug to its group in `guide-sources` in `gloss.app.markdown-pages`
(a new group is a new entry there, with its folder); the tests check every
Markdown page's title, description and examples, that every link leads to
a page, and that every file under `pages/` is listed.

**Change what is saved:** bump `save-format`, teach `migrate` the old
shape, and add a test that an old save still loads. Saved data lives in
readers' browsers: it can't be fixed afterwards (development.md,
*Interfaces that must not break*).

---

## 8. Vocabulary

The words this code and its documentation use in a fixed sense.

| term | meaning |
|---|---|
| **text** | a whole document (a poem, a set of sentences); also plain `\text`, "the text": the text tier with the words as written |
| **source** | a text in the input format, as typed or saved |
| **source line** | one line of the source (problems point at them: "Line 7") |
| **field** | a command and its value in the source (`\glmorph boy-NOM.PL`), with any continuation lines |
| **command** | what starts a field, as in LaTeX: a backslash and a name (`\glword`, `\title`, `\igt`; a field's `:command` in the parser). Inside a tier, `\0` (a zero morpheme, in `\morph`) is the only command |
| **tier** | what one command writes in an IGT's source (`\glmorph boy-NOM.PL`), known by its standard name (`glmorph`); `gloss.tiers` has them all. There are three kinds (`:kind`). **Text tiers** record the form of the words: `\text`, the transliteration (`\text[translit]`) and the transcriptions (`\text[phonemic]`, `\text[phonetic]`, `\text[ipa]`). **Segmentation tiers** are that form, divided: the morpheme tiers (`\morph`, `\morph[translit]`) and the syllable tiers (`\syl`, `\syl[translit]`), each dividing the text tier its option names. **Annotation tiers** are analysis alone, without repeating the form: `\glmorph`, `\glword`, `\pos`, `\scan` |
| **commentary** | the free translation (`\ft`) and notes (`\note`): attached to lines or words, not aligned with them, and not tiers (`gloss.tiers` lists them as `:commentary`) |
| **IGT** | an interlinear glossed text: one line of the text (a sentence, a verse) with whatever is added to it, its other tiers and its commentary. `\igt` starts one, optionally with a label (`\igt 1.8`); `\ex` is its short form. It is the unit the parser, the serializer, the checks and the editors work in (a document's `:igts`), and what the site's buttons and labels call it ("+ IGT", "New IGT") |
| **line** | a line of text: the line an IGT holds (sublines divide it), a line of the source (**source line**), a line of a Markdown page or an export file |
| **section** | a group of IGTs under a heading (`\section`) |
| **surface** | the code's name for `\text` and `\text[translit]`, the text tiers that segmentation tiers divide |
| **word** | a word of `\text`, with everything aligned to it |
| **entry** | a tier's piece for one word (`{the sailor}` in `\glword`): entries are separated by spaces, braces make one entry of several words, and `{}` is an empty one. `\text` has none (its spaces define the words), nor do `\syl` and `\scan` (a syllable can span two words) |
| **unit** | what a tier's entries or marks go with (`:unit`): a word, a syntactic word, a morpheme or a syllable. Units are marked, not given tiers of their own: words by the text tiers' spaces, syntactic words by `=` in `\morph` (`rīdet=ne` is two, each with its own part of speech). (`gloss.layout`'s display units, words that elision keeps together, are another thing) |
| **segment** | a stretch of a word between morpheme breaks; usually a morpheme, but an infix splits its host morpheme into two segments |
| **piece** | a stretch of a word between any two boundaries (morpheme, syllable, ruby, formatting), the smallest part the page draws |
| **row** | what the reader sees: a band of cells, one per word, such as the text row, the transliteration row or the morphemic gloss (a tier's `:row` is its label). The text tiers and most annotation tiers are shown as rows; `\scan`'s marks are drawn over a text row, like ruby, and segmentation tiers show in their text tier's row. The reader can reorder the rows (Row order), and a text can set their order (`\order`) |
| **cell** | one word's part of a row |
| **column** | one word's cells, one above the other |
| **diagnostic**, **problem** | an error or warning, `{:level :msg …}` |
| **reader** | the person using the site, reading or editing |
| **view**, **display options** | the reader's settings for what is shown, kept apart from the text; they come in groups (Orthography, Glossing, Scansion, Commentary, Marking), each with a switch that hides the whole group |
| **Markdown page** | a page of the site written in prose, as a Markdown file in `pages/`: the Guide's pages and About |
