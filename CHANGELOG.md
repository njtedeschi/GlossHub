# Changelog

Changes to GlossHub that readers and writers of texts would notice, newest
first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [semantic versioning](https://semver.org). The input
format has a version of its own, independent of these
([docs/format.md](docs/format.md), §2).

## [Unreleased]

## [0.2.0] - 2026-10-24

### Added

- **The Guide**, in place of its placeholder: 25 short pages in seven
  groups, with live examples that can be edited. The glossing pages use
  the Leipzig Glossing Rules' own examples, with their sources. A
  *Commands* page lists every command with the page that explains it,
  and the *Input format* summary under a text in Edit mode links to it.
- **About**, in place of its placeholder: what the site does, how it
  works, and where a reader's texts are kept.
- **The input format, version 2** ([docs/format.md](docs/format.md)).
  The site writes version 2 and still reads version 1.
  - `\igt` starts an interlinear glossed text, an IGT (`\ex`, its short
    form, still works).
  - The other text tiers are `\text` with an option: the transliteration
    is `\text[translit]`, and the transcriptions are `\text[phonemic]`,
    `\text[phonetic]` and `\text[ipa]`, where version 1 wrote
    `\translit`, `\phonemic` and so on.
  - Each `\morph` and `\syl` is written indented under the text tier it
    divides, and `\order` names the rows as their tiers are written
    (`\order text text[translit] glmorph`).
- **Glossing**: the Leipzig prefix non- (`NSG`, `nSG`), and the
  underscore that joins the words of a lexical gloss (`come_out`).
- **Checks**: a syllable tier must divide the words where its text tier
  does, and `\syl` and `\syl[translit]` must begin each word at the same
  syllable.

### Changed

- The site and its messages say *IGT* where they said *line* (*+ IGT*,
  *New IGT*, a translation *below each IGT*), *command* where they said
  *marker*, *Row order* where they said *Layer order*, and *glossing
  abbreviation* where they said *grammatical label* or *tag*.
- The morphemic gloss's *tags only* is now *grammatical only*, and keeps
  the morphemes apart: `go-PST-PL` shows `PST-PL`, not `PST.PL`.
- Abbreviations are shown in small capitals, with their full names on
  hover, wherever they stand in a gloss: after a backslash (`write\PST`),
  in Leipzig brackets (`boy[NOM.SG]`, `eye(N)`) and around `>` (`1>3`).
  `:` and `;` are shown as typed, not as periods.
- Stricter reading: a command the site doesn't know, and a tier written
  twice in one IGT, are errors, since the site keeps neither; a
  separator at the start of a gloss (`-FUT`) is reserved for a planned
  feature (`\-FUT` is a gloss with a hyphen).
- When `\syl` and `\syl[translit]` have different numbers of syllables,
  the problem stays listed under the IGT in Edit mode, like the other
  problems with an IGT's content.
- Saved data is in a new format; display settings saved by 0.1.0 carry
  over.

### Fixed

- Ctrl/Cmd+Enter in *Document source* applies the document: it applied
  an open IGT editor instead, which discarded the draft.
- Highlighted text (`==text==`) takes the dark theme's color when *Dark*
  is chosen in Settings, not only when it is the system's.
- Applying a source editor without changing anything is no longer an
  edit (which, on a published text, made an edited version of it).
- A parenthesis opened inside a gloss item, as in `eye(N)`, keeps its
  closing parenthesis.
- Scansion that can't be placed is listed once, not once per tier.

## [0.1.0] - 2026-10-19

The first public version.

### Added

- **The input format, version 1**: a plain-text format close to the LaTeX
  glossing packages, checked as it is typed, with problems that point at
  their source lines.
  - The text as written (`\text`), with formatting, links, ruby readings
    and sublines; a transliteration (`\translit`); phonemic, phonetic or
    IPA transcriptions.
  - Morphemes, stems, roots and infixes (`\morph`), with Leipzig-style
    morphemic glosses (`\glmorph`), informal glosses (`\glword`) and parts
    of speech (`\pos`).
  - Syllables (`\syl`) and scansion (`\scan`), drawn over the text in the
    same line as the glosses.
  - Free translations; notes that quote the words they are about, across
    lines if need be; sections; a document's own abbreviations, colors and
    order of rows.
  - A format version (`\glosshubformat 1`) with a written contract, and
    reserved syntax for what is planned.
- **Reading**: display options per text, over each reader's defaults
  (every row can be shown, hidden or reordered); grammatical labels with
  their full names on hover; color coding; right-to-left scripts; notes
  highlighted on click; phones.
- **Editing**: cell by cell, a line or the whole text as source, with
  undo and redo; "Fill from the words" for new `\morph` and `\syl` rows.
- **Texts**: the reader's own texts and their edited versions of
  published texts, saved in the browser; export and import of one text or
  of everything; one published text, *Aeneid* 1.8–11.
- **Settings** (theme, text size, the mode texts open in, default display
  options, erasing saved data); a feedback link and the site's version on
  every page. The Guide and About were placeholders.

[Unreleased]: https://github.com/njtedeschi/GlossHub/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/njtedeschi/GlossHub/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/njtedeschi/GlossHub/releases/tag/v0.1.0
