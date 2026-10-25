# The GlossHub input format, version 2

**Status: implemented.** This is the format the site reads and writes, and
the reference for what a text means: where the site and this document
disagree, the site has a bug. The site's Guide teaches the format topic
by topic, and [architecture.md](architecture.md) explains how the code
reads it.

## 1. Principles

- **The contract.** A text that parses without warnings means the same
  thing in every later version with the same format number (*Versions*).
- **One home for every fact.** Each piece of information is written in one
  place. Where interlinear alignment forces a repetition (the same words in
  several tiers, the same morphemes in several tiers), the repetition is
  checked, never silently merged or ignored.
- **`\text` is the text.** It is written as the text is written, so a source
  can be pasted in. Analysis (morphemes, stems, syllables, scansion, notes)
  lives in other tiers that point into it.
- **Parsing never fails.** Every problem is reported, as an **Error**
  (something can't be shown as written) or a **Warning** (it can, but looks
  wrong), and nothing typed is thrown away.
- **Reserve, don't guess.** Syntax that might be wanted later is reserved
  now: using it unescaped is flagged, so giving it a meaning later breaks
  no text that parsed cleanly.

## 2. Versions

`\glosshubformat 2` in the header gives the format version. A text without
it is version 1; the site writes it at the top of every text it saves or
exports. A higher number than the site reads is a warning (the text loads
as far as it can); anything but a whole number is an error.

The number is an integer, independent of any version of the site. It
changes only when keeping the contract is impossible: an existing command or
character changes meaning, segmentation rules change, a command's name stops
being accepted, or something accepted silently becomes an error. New
commands, new header lines, meanings for reserved syntax, and anything about
display need no new version. The site reads the earlier versions by their
rules, converts texts as it reads them, and writes the latest.

**Version 2** writes the transliteration and the transcriptions as text
tiers, `\text` with an option (§5.2, §5.9), and the text's own
segmentation tiers with none. It starts each IGT with `\igt` (§3).
Version 1's spellings are still read, with the same meaning, in a text of
either version; in a text of version 2 each is flagged with how version 2
writes it.

| version 1 | version 2 |
|---|---|
| `\translit`, `\tl` | `\text[translit]`, `\text[tl]` |
| `\phonemic`, `\phonetic`, `\ipa` | `\text[phonemic]`, `\text[phonetic]`, `\text[ipa]` |
| `\morph[text]`, `\syl[text]`, and `[text]` on any command but `\note` | `\morph`, `\syl`, no option |

`\ex`, which started an IGT in version 1, is `\igt`'s short form, read in
every version without a warning. Nothing else differs. Version 2's
spellings were reserved in version 1, so a text of version 1 may use them
too. The site also checks, in texts of either version, where the syllable
tiers divide the words (§5.4). What it finds are warnings: every text
means what it did.

## 3. Structure of a file

- A text is a **header**, then its **IGTs**, optionally grouped into
  **sections**, then optionally `\end` and the **back section**. An IGT,
  an **interlinear glossed text**, is one line of the text (a sentence, a
  verse) with everything added to it: its other tiers, its translation, its
  notes (§3.1).
  - The header is everything before the first IGT or `\section`. There is
    no `\begin`.
  - Each IGT starts with `\igt` (or its short form `\ex`), optionally
    followed by a label (`\igt 1.8`); unlabelled IGTs are numbered in
    order. `\igt` may be left out: a `\text` that comes before any IGT, or
    after one that already has its `\text`, starts a new one.
  - `\section[label] Heading` starts a group of IGTs (a poem, a chapter),
    shown under its heading, until the next `\section` or `\end`. IGTs
    before the first `\section` belong to no section.
  - IGT labels must be unique within their section; section labels must be
    unique in the text.
  - `\end` ends the IGTs. After it, only `\note`s may follow (notes on the
    text as a whole, or a commentary section); anything else there is
    reserved and flagged.
- Every other field starts with a **command** at the start of a line:
  `\text`, `\glmorph`, … Case doesn't matter, nor do spaces before the
  command: the site writes `\scan` above `\text`, and each `\morph` and
  `\syl` indented under the text tier it divides. A line that doesn't start
  with a command continues the previous field. Lines starting with `%` are
  comments.
- **Once per IGT.** Each tier, and `\ft`, is written at most once in an
  IGT: a second field for one (two `\glmorph`s, or `\text[translit]` and
  `\text[tl]`) is an error, and only the first is read. A second `\text`
  starts a new IGT instead (above), and `\note` can be repeated.
- **Options** follow a command in square brackets: `\text[translit]`,
  `\note[tag]`. A command accepts only the options listed for it; any other
  option (including any option on `\igt`) is flagged as not supported yet.
  Unknown commands are flagged too, so every command not listed here
  (`\begin`, for instance) is reserved. The options of `\text`, `\morph`
  and `\syl` name a text tier, in any case; a note's is its tag.
- **Short names.** `\ex` (`\igt`), `\tx` (`\text`), `\gl` (`\glmorph`),
  `\wg` (`\glword`), and the option `[tl]` (`[translit]`): `\text[tl]`,
  `\morph[tl]`. Version 1's names are read too (§2).
- **Reserved commands.** `\row[code]` and `\rowname[code]` (parallel tiers)
  are kept as written and written back out, with a warning that they aren't
  supported yet.

### 3.1 Tiers, entries and rows

An IGT's fields are its **tiers**, one for each command, and its
commentary. There are three kinds of tier:

- **Text tiers** record the form of the words: `\text`, the text itself,
  and the transliteration and transcriptions, written `\text` with an
  option (`\text[translit]`, `\text[phonemic]`, `\text[phonetic]`,
  `\text[ipa]`).
- **Segmentation tiers** overlay analysis on that form: they are the form,
  divided. `\morph` divides the words into morphemes and `\syl` divides the
  line into syllables; each divides the text tier its option names
  (`\morph[translit]` divides `\text[translit]`).
- **Annotation tiers** are analysis alone, without repeating the form:
  `\glmorph`, `\glword`, `\pos` and `\scan`.

The free translation (`\ft`) and notes (`\note`) are **commentary**: they
are attached to the line or to its words, but not aligned with them.

**Words and entries.** The spaces of `\text` divide a line into **words**.
The tiers aligned with the words have an **entry** for each, separated by
spaces: the other text tiers, `\morph`, `\glmorph`, `\glword` and `\pos`.
`{…}` makes one entry of several words (`{the sailor}`), and `{}` is an
empty one. Too few entries leave the last words without one (a warning);
too many are dropped (an error). `\syl` and `\scan` have no entries:
syllables divide the line, and one syllable can span two words.

**Units.** What a tier's entries or marks go with is its unit: a word, a
**syntactic word**, a morpheme or a syllable. Units are marked rather than
given tiers of their own: words by the spaces of the text tiers, and the
syntactic words of a word with clitics (`rīdet=ne`) by `=` in `\morph`.

**Rows.** On the page, the text tiers and the annotation tiers with
entries are shown as **rows**: bands of **cells**, one per word. A word's
cells are its **column**. The segmentation tiers aren't rows of their own:
they show in the row of the text tier they divide, as morpheme and
syllable breaks; and `\scan`'s marks are drawn over a text row (§5.5). The
rows come in the text's order (`\order`, §5.12), which the reader can
change with *Row order*.

## 4. Characters

### 4.1 Letters and matching

A segmentation tier is checked against the text tier it divides, and a
note's quotes against the words (§5.11). They are compared on
**letters**:

- A letter is a base character with its combining marks, or a digit, or an
  **apostrophe**. `'`, `’` and `ʼ` are the same letter (contractions,
  glottal stops, ejectives), wherever they are in a word.
- Ignored: capitals, diacritics, punctuation.
- A token made only of the punctuation that can open or close a word
  (`—`, `«`, `,`) isn't a word; it goes with the word beside it. Other
  tokens without letters are words (`–`, `/`).

So `\morph Mus-a` matches `\text Mūsa,`. An apostrophe meant as a single
quote is escaped: `\’Run,\’ she said`.

### 4.2 Escapes and commands

- A backslash before any punctuation makes it plain punctuation with no
  role: `\*wódr̥`, `etc\.` (the period stays with the word), `\{`. `\\`
  is a backslash. A backslash is never literal on its own, in any field.
- An apostrophe (`'`, `’`, `ʼ`) is always a letter, escaped or not.
  Quotations use `“…”`, `"…"` or `‘…’` (where the closing `’` counts as
  part of the word before it).
- Inside a tier, a backslash before letters or digits is a command too
  (§3). One is defined there: `\0`, a zero morpheme, in `\morph` only
  (shown as Ø; a literal `Ø` or `∅` means the same). Any other is flagged
  as unknown.
- Commands typed for a planned LaTeX input converter (§8) are to be
  converted as they are typed and never stored, so they can't collide
  with format commands.

### 4.3 Reserved characters, by tier

Every ASCII symbol that a tier doesn't give a meaning to is **reserved** in
it: unescaped, it is flagged and kept as written. Non-ASCII punctuation is
always free.

| tier | free | markup | reserved |
|---|---|---|---|
| `\text` | letters, `. , ; : " ' ! ? - / ( )` | formatting and links (§6), ruby `{base\|reading}`, a lone `+` | everything else: `@ # $ % & ^ \| < > = ~ { } [ ]`, backtick, `*` `_` `+` outside formatting |
| `\text[translit]` | as `\text` | — | as `\text`, including the formatting characters |
| `\glword` | everything, as in prose | formatting within the entry; `{…}` grouping | — |
| `\morph` | letters | `- = ~ <…> [ ] ( )`, `\0` | everything else, including `#` |
| `\syl` | letters | `-`, `‿` (or `_`), `( )` | everything else, including `\|` and `‖` |
| `\scan` | spaces | its marks (§5.5) | everything else |
| `\glmorph` | letters, `. : ; ' [ ] ( ) _`, a lone `<` or `>`, `\\` | `- = ~ <…>` (must match `\morph`; not at the start of an entry, §8); `{…}` grouping | everything else, including `#` |
| `\pos` | letters | `.` (subclass), `=` (between syntactic words) | everything else, including `-` |
| transcriptions (`\text[phonemic]`, …) | everything (all of IPA) | `{…}` grouping | nothing (a backslash is still an escape) |
| prose (`\ft`, `\note` text, `\about`) | everything | formatting and links (§6) | — (as in Markdown, an unmatched delimiter is just a character) |

In `\text[translit]`, `\glmorph` and `\pos`, punctuation at the edges of an
entry is ignored, as §5 says of each; the table is about the characters
inside an entry. A backslash that escapes nothing (before a space, at the
end, or before a letter or digit that isn't a command) is flagged in every
field, prose and transcriptions included.

In `\morph` and `\syl`, sentence punctuation (`. , ; : ! ? "` and non-ASCII
punctuation) is ignored without a warning, so text can be pasted in and
broken up; the site never writes it there.

## 5. Tiers and commentary

### 5.1 `\text`

The object-language text as written, with its punctuation. Its spaces
divide the line into words, which every tier with entries follows. Besides
plain text it may contain formatting and links (§6), ruby readings
`{白|しろ}`, and a lone `+` between words to start a subline (sublines only
in `\text`). Nothing else: morpheme breaks, stems, syllables and notes live
elsewhere. Every IGT needs a `\text`.

### 5.2 `\text[translit]`

A transliteration, one entry per word of `\text` (the counts must match),
as plain text: no formatting, links, ruby or sublines (it receives
formatting from `\text`, §6.2). `[tl]` is short for `[translit]`. It is a
text tier, `\text` with an option, as the transcriptions are (§5.9). When an
IGT has `\text[translit]`, it is the tier that `\morph[translit]` and
`\syl[translit]` divide.

### 5.3 `\morph`, `\morph[translit]`: morphemes, stems and roots

`\morph` divides the words of `\text`; `\morph[translit]` those of
`\text[translit]`. Each has an entry per word, which must match its word's
letters (§4.1). Both are optional, and a text with `\text[translit]` may
have either or both.

- **Morphemes** are separated by `-` (affix), `=` (clitic), `~`
  (reduplication), or enclosed in `<…>` (infix: `s<um>ulat`). `\0` is a
  zero morpheme (`puer-\0`). The parts of a word between `=` are its
  syntactic words.
- **Stems** `[…]` and **roots** `(…)` are optional:
  - A stem is a run of whole morphemes and may cross separators:
    `[over-re-(act)]-ing`. Stems may nest (`[[(black)]-[(bird)]]-s`).
  - A root stays within one morpheme, except a root split by an infix:
    `(s<um>ulat)`, where the infix is not part of the root.
    `(s)<um>(ulat)` means the same.
  - **Parenthesized groups in the same morpheme form one root.** So
    `(k)a(t)a(v)` is one discontinuous root, k-t-v, in one morpheme (root
    and pattern share it); `[(black)]-[(bird)]` is two roots.
  - Brackets nest properly and never cross words; a root contains no stem;
    `\0` can't be a root. A letter is never split from its marks, so
    `(כָ)` includes its vowel point.
- **When both tiers exist,** they must have the same morphemes in each
  word, with the same separators (and `\0`s) in the same order; if both
  mark stems and roots, the stems must cover the same morphemes and the
  roots be in the same morphemes (which letters are root may differ:
  unpointed `(כ)(ת)(ב)` and `(k)a(t)a(v)`).

### 5.4 `\syl`, `\syl[translit]`: syllables

`\syl` divides `\text` into syllables; `\syl[translit]` divides
`\text[translit]`, matching letters as in §4.1. They have no entries: each
runs over the whole line. `-` separates syllables, a space separates words,
`‿` (or `_`) joins words within a syllable, and `(…)` encloses elided
letters. Feet and caesurae are not marked here (§5.5).

- **The words divide where their text tier's do.** Every space falls
  between two of its words, and every division between them is a space
  between syllables or a `‿` inside a syllable the two words share
  (`mul-t(um)‿il-le` for *multum ille*). The first place they disagree is
  flagged, as a warning: the syllables are still placed.
- **When both tiers exist** they must have the same number of syllables
  (an error otherwise), since one `\scan` scans both, and begin each word at
  the same syllable (a warning otherwise).

### 5.5 `\scan`: scansion

One mark per syllable, spacing free: `_` (or `–`, `¯`) long, `u` (or `˘`)
short, `x` anceps; `'` or `|` before a syllable starts a foot, `||` marks a
caesura. Feet and caesurae are written only here. The number of marks must
match the syllables of `\syl` (or `\syl[translit]`). It has no entries and
no row of its own.

**Where it is drawn:** over the text row when the IGT has `\syl` and
Scansion is shown; otherwise over the transliteration row when the IGT has
`\syl[translit]`.

### 5.6 `\glmorph`: the morphemic gloss

One entry per word, following the Leipzig Glossing Rules. `{…}` groups an
entry containing spaces; `{}` is empty.

- **Separators** `-`, `=`, `~` and infix glosses `<…>` must match the
  word's `\morph` (or `\morph[translit]`) in number and order: a gloss
  morpheme for each morpheme. An infix's gloss is the gloss morpheme in
  angle brackets, wherever it stands (`<AV>write`, `win<PRS>-1SG`); the
  others pair in order. A zero morpheme has an ordinary gloss (`puer-\0` /
  `boy-NOM.SG`).
- **The gloss's own symbols** (Leipzig) are free: `.` `:` `;`, `_`
  between the words of a lexical gloss for something with no parts
  (`come_out`, Rule 4A), `[…]` for a non-overt element (`boy[NOM]`), `(…)`
  for an inherent category (`oko(N)`), a lone `<` or `>` (`1>3`), and
  `\\` for a non-segmental change (`write\\PST`, shown as `write\PST`).
- An element written entirely in capitals and digits (`PST`, `3SG`, `A`)
  is a glossing abbreviation (small capitals, full name on hover); any other
  element is lexical, including capitalized words such as `John`.
- **Non-.** Any abbreviation can take the Leipzig prefix *non-*, written
  `NON`, `N` or a lowercase `n` (shown in lowercase): `NSG`, `NONSG` and
  `nSG` are non-singular, `3NSG` third person non-singular. An
  abbreviation of the vocabulary as written comes first (`NEG` is
  negation, and a text's own `\abbr NSG` means what it says), and the
  prefix is read once (`NNSG` is unknown).
- Without a `\morph` (or `\morph[translit]`) tier, each word is one
  morpheme: a word-by-word gloss (`1SG love 2SG`) needs no `\morph`. A
  separator in such a gloss is flagged, suggesting a `\morph` tier.

### 5.7 `\glword`: the informal gloss

One entry per word, each as free as `\ft`: any characters, and formatting
(§6). Only the braces are taken: `{…}` groups an entry containing spaces,
`{}` is empty (`\{` for a literal brace). It is an informal gloss, so it
records no morpheme boundaries and takes no part in stem and root marking.

### 5.8 `\pos`: parts of speech

One entry per word, with a part of speech for each of its syntactic words:
a class with optional subclasses after `.` (`V.trans`), and `=` between the
syntactic words of a word with clitics (`Pro=Conj`). Its `=` must
correspond one to one with the `=` of the word's `\morph` (and so of its
`\glmorph`); without `\morph`, `=` in `\pos` is flagged. Syntactic words
are the only units inside a word that it records: a part of speech spans
the word's affixes, but a clitic is enough like a word to have its own, so
`-` is reserved here rather than mirroring `\morph`.

**Less settled than `\glmorph`.** `\glmorph` follows the Leipzig Glossing
Rules; `\pos` has no such convention behind it, and how several categories
or subcategories combine may still change. Everything but letters, `.` and
`=` is reserved, so that it can.

### 5.9 `\text[phonemic]`, `\text[phonetic]`, `\text[ipa]`: transcriptions

Raw IPA, one entry per word, with no morphological content: text tiers, as
the transliteration is (§5.2). The page encloses each in `/…/` (phonemic,
IPA) or `[…]` (phonetic). `\text[ipa]` is for texts that don't distinguish
the two, so using it with either of the others is flagged.

### 5.10 `\ft`: free translation

Commentary on the whole line, in prose (§6), shown without quotation
marks; continuation lines keep their line breaks.

### 5.11 `\note`: notes

```
\note[abl-abs] {quō nūmine laesō} Ablative absolute
\note {dolēns} Present participle
\note[imp-comp] {tot volvere cāsūs} {@10 tot adīre labōrēs} Complement clauses of *impulerit*
```

- `\note[tag]`: the tag is optional and must be unique in the text; it
  names the note, though nothing refers to notes by their tags yet.
- One or more **groups** in braces say which words the note is about, by
  quoting them. A group is a word, consecutive words, or `a … b` (from `a`
  to the next `b`, possibly running onto the following IGTs; `...` works
  too). Quotes match `\text` as in §4.1: capitals, diacritics and
  punctuation don't matter.
- A group is matched in the IGT the note is written in, unless it starts
  with `@label`, the label of a labelled IGT (`\igt 10`):
  `{@10 tot adīre labōrēs}`. In another section, `@section/label`
  (`{@ode2/3 carpe diem}`); within the note's own section, `@label` is
  enough. Automatic numbers can't be used, since they change when IGTs
  are added.
- Notes are written under an IGT or after `\end`, never in the header. A
  note after `\end` needs `@label` on every group.
- A group is matched against `\text`; if it matches nothing there, against
  `\text[translit]`. Each group matches in one tier.
- A quote that occurs more than once in its IGT can end with `#n`, its
  nth place there: `{a#2}`, `{tot#2 adīre}`. In a span, `#n` on the
  second quote counts its places after the first (`{a … b#2}`, the second
  `b` after `a`). A literal `#` in a quote is written `\#`.
- The note's number appears after the last word of **every** group; a group
  starting with `*` (`{* …}`, `{@10* …}`) gets no number. Clicking a number
  or the note highlights all its groups.
- The text after the groups is prose (§6).
- **Flagged:** a group that matches nothing, or more than one place without
  `#n` (quote more words, or add `#n`), or fewer places than its `#n`; an
  unknown or duplicated label; a note whose groups are all
  starred (it has no number in the text).

### 5.12 The header

Before the first `\igt` or `\section`: `\glosshubformat`, `\title`,
`\about` (prose), `\dir rtl|ltr`, `\lang` (language of `\text`, for
fonts), `\order` (the order of the rows under each word; rows it doesn't
list keep their usual places after the ones it does), `\abbr ABBR
meaning`, `\color ABBR #hex [legend label]`.

`\order` names each row by its tier's command without the backslash, as
the site writes it (`\order text text[translit] glmorph`), or by any of
the tier's names (`translit`, `tl`, `phonemic`, `gl`), in any case.

## 6. Formatting and links

### 6.1 Syntax

In `\text`, `\glword` (entry by entry) and prose:

| written | shown |
|---|---|
| `*x*` or `_x_` | italic |
| `**x**` or `__x__` | bold |
| `~~x~~` | strikethrough |
| `==x==` | highlight |
| `++x++` | underline |
| `[words](url)` | a link: clicking any of the words, when reading, opens the address |

- They combine: `**x _y_**`.
- A delimiter at the start or end of a word may span words, within its
  line; formatting left open is flagged and closed at the line's end. One
  that opens inside a word must close inside the same word (formatting
  part of a word). `_` and `__` only work at the start and end of words.
- A delimiter touches the word it formats: one standing alone between
  spaces formats nothing and is flagged.
- A link stays within a line; it starts at the start of a word and ends
  at the end of one.
- In `\glword`, formatting stays within one entry. In prose it follows
  Markdown, so an unmatched delimiter is just a character; in the tiers it
  is flagged. (`\glword` doesn't follow the rules for `_` and for a lone
  delimiter yet; which rules it should follow is still to be decided.)

### 6.2 Formatting carried into other rows

One setting, *Text formatting* in the Marking group, turns formatting off
everywhere (the text, the rows it reaches and `\glword`'s own) or on,
everywhere it applies:

- **A whole word** formatted in `\text` is formatted in that word's cells
  of `\text[translit]`, `\glmorph` and `\glword` too.
- **Part of a word**, if it covers whole morphemes of `\morph`, is carried
  to those morphemes in `\glmorph`, and in `\text[translit]` when it has a
  `\morph[translit]` (by morpheme position). Formatting covering part of a
  morpheme stays in `\text`.
- A `\glword` entry with formatting of its own inherits none (all or
  nothing).
- Transcriptions, `\pos`, `\ft` and notes never receive it.

## 7. Display of stems and roots

Settings in the Marking group: **Stem marking** (none, underline, bold) and
**Root marking** (none, double underline, bold), defaulting to underline
and double underline, besides the formatting toggle (§6.2).

- `\text` and `\text[translit]` show the marking of their own `\morph`
  tier, letter by letter (so a discontinuous root marks several letters).
  Of nested stems, the outermost is marked.
- In `\glmorph`, a gloss morpheme is marked as stem when its morpheme is in
  the stem. For roots: a morpheme that is entirely root has its whole gloss
  marked; one that is partly root (a root with its pattern) has its gloss
  marked up to the first `\\`, or, without one, up to the first grammatical
  element: `write\\PST.3SG.M`, `write.PST.3SG.M` and `go.PST` all mark
  *write* / *go*; `come.out.PST` marks *come.out*.
- `\glword` is never marked.
- With morpheme breaks shown, an infix is drawn in angle brackets
  (`s<um>ulat`) and a zero morpheme as `-Ø`.
- **Hyphens already in the text.** Where a morpheme break falls exactly on a
  hyphen in `\text`, the break's separator takes the hyphen's place instead
  of being added beside it. With breaks hidden, the text shows as written.

  | `\text` | `\morph` | breaks shown | breaks hidden |
  |---|---|---|---|
  | `well-known` | `well-know-n` | `well-know-n` | `well-known` |
  | `donne-moi` | `donne=moi` | `donne=moi` | `donne-moi` |
  | `e-mail` | `email` | `e-mail` | `e-mail` |
  | `Mūsa,` | `Mūs-a` | `Mūs-a,` | `Mūsa,` |

## 8. Later, without breaking anything

Reserved now, to be given meaning later: parallel tiers (`\row`), options
on `\igt` (e.g. acceptability judgments), formatting, links and other
markup in `\text[translit]`, segmentation tiers for the transcriptions
(`\syl[phonemic]`), other content after `\end`, more commands, a separate
gloss for a templatic pattern, explicit stem or root marking in `\glmorph`
(with a delimiter nobody uses yet), boundary symbols such as `#` in
`\morph` and `\glmorph`, a separator at the start of a `\morph` or
`\glmorph` entry (a word written apart but glossed with the word before it,
Leipzig Rule 2A, which is planned; `\-FUT` is a gloss with a hyphen). Not
format at all: LaTeX/TIPA input conversion, "Fill from the words" (adding
a `\morph` or `\syl` filled from the text tier it divides), editing words
from the page.

## 9. Before version 1

Version 1 is the first published format. The format of the site's
private prototype is not read; the prototype's texts were converted
before the first public release.
