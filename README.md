# GlossHub

[![Deploy](https://github.com/njtedeschi/GlossHub/actions/workflows/deploy.yml/badge.svg)](https://github.com/njtedeschi/GlossHub/actions/workflows/deploy.yml)

A web tool for annotating texts in linguistically interesting ways:
interlinear glosses, scansion, ruby readings, transliteration and notes,
shown at whatever level of detail the reader chooses, and editable on the
page.

**Try it at [njtedeschi.github.io/GlossHub](https://njtedeschi.github.io/GlossHub/).**

Texts are written in a plain-text format close to the LaTeX glossing
packages linguists already use, checked as you type. This is the first
line of the site's published text, *Aeneid* 1.8–11:

```text
\igt 1.8
\scan    _ u u ' _ _ ' _ u u ' _ || _ ' _ u u ' _ _
\text    Mūsa, mihī causās memorā, quō nūmine laesō
  \morph Mūs-a mihī caus-ās memor-ā quō nūmin-e laes-ō
  \syl   Mū-sa mi-hī cau-sās me-mo-rā quō nū-mi-ne lae-sō
\glmorph muse-VOC.SG 1SG.DAT cause-ACC.PL recount-2SG.PRS.ACT.IMP what.ABL.N.SG divinity-ABL.SG offended-ABL.N.SG
\glword  {O Muse} {to me} {the causes} recount what divinity offended
\ft      Muse, recount to me the causes: what divinity was offended,
```

`\igt` starts an interlinear glossed text (IGT), here labelled 1.8, and
each command under it adds to it:

- `\text`: the line as written;
- `\morph` and `\syl`, indented under `\text` because they divide it: its
  words into morphemes, and the line into syllables;
- `\scan`: the meter, a mark for each syllable (`_` long, `u` short), with
  `'` between feet and `||` at the caesura;
- `\glmorph`: a gloss for each morpheme, following the Leipzig Glossing
  Rules;
- `\glword`: an informal gloss for each word, in braces when it takes
  more than one word;
- `\ft`: the free translation.

The page draws the IGT in aligned columns, one for each word: the
metrical marks on top, then the word divided into its morphemes, the
gloss of each morpheme beneath it, and the informal gloss; the
translation runs below. Syllables and morphemes can divide a word
differently, as in *Mū-sa* and *Mūs-a*: the marks follow the syllables,
and the glosses the morphemes.

## Features

- **Interlinear glossing** following the Leipzig Glossing Rules: the text
  written as it is, its morphemes divided in a tier of their own,
  glossing abbreviations in small capitals with their full names on hover,
  and mismatched glosses flagged instead of silently misaligned.
- **Scansion** marked over the syllables of the text, with feet, caesurae
  and elision across words.
- **Right-to-left scripts** such as Hebrew, with a left-to-right
  transliteration; **phonemic, phonetic or IPA transcription**; **stems
  and roots**, including discontinuous roots, marked in the text, the
  transliteration and the glosses.
- **Ruby readings** (furigana and the like), drawn over their characters.
- **Notes** on single words or on spans that can cross lines, numbered
  automatically, with a click highlighting each note's words;
  **sublines** for long lines and **sections** for several texts on a
  page; **color coding** for grammatical categories; informal glosses and
  parts of speech.
- **Display options** per text: show, hide or reorder each kind of
  annotation, without changing the text.
- **Editing** cell by cell or as source, with undo and redo. Texts are
  saved in the browser, and export and import as plain-text files.
- **A Guide** of short pages, grouped by topic, whose examples can be edited live.

Everything runs in the browser: there is no server and no account, and a
reader's texts never leave their device.

## Documentation

- [docs/format.md](docs/format.md): the input format, the reference for
  what a text means. The site's Guide teaches it topic by topic.
- [docs/architecture.md](docs/architecture.md): how the code is
  organized, and the decisions behind it.
- [docs/development.md](docs/development.md): working on the code: setup,
  commands, tests, conventions, and the process for changes.
- [CONTRIBUTING.md](CONTRIBUTING.md): how to give feedback or contribute
  a change, and [CHANGELOG.md](CHANGELOG.md): what changed in each version.

## Development

You need Java 21 or later, the
[Clojure CLI](https://clojure.org/guides/install_clojure) and Node.js 22
or later. The site has no npm dependencies; npm runs the tests.

```sh
npm ci && npx playwright install chromium   # once: the test tools
npm run dev     # compile on every change (and, in another terminal:)
npm run serve   # the site at http://localhost:8000
npm test        # unit tests, the file check, the build, browser tests
```

[docs/development.md](docs/development.md) has the rest.

## License

The code is released under the [MIT License](LICENSE). The Guide quotes
examples from the
[Leipzig Glossing Rules](https://www.eva.mpg.de/lingua/resources/glossing-rules.php)
and the works they cite; each is credited under it and in the Sources of
its page, and they aren't covered by that license.
