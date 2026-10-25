---
description: How what you write lines up with what you see: tiers, entries and rows.
---

# Tiers and rows

Each example in this guide shows an [IGT](#guide-writing-a-text) twice: its source, as you write it, and the result, as the page shows it. In the source, each command starts one part of the IGT. The parts that line up with the text are its **tiers**, and most tiers become a **row** in the result: a band under the words, with a cell for each word.

```gloss
\igt
\text Mūsa, mihī causās memorā
\glword {O Muse} {to me} {the causes} recount
\pos N Pro N V
```

Here `\text`, `\glword` and `\pos` are tiers, and each is a row: the text, the informal gloss and the parts of speech.

## One entry per word

The spaces of `\text` divide it into words. Most other tiers give something for each word, in the same order and separated by spaces: an **entry**. Each entry is shown under its word, so the source above lines up like this, with a row for each tier and a column for each word:

```gloss grid
\igt
\text Mūsa, mihī causās memorā
\glword {O Muse} {to me} {the causes} recount
\pos N Pro N V
```

- Braces group an entry of several words, `{O Muse}`, and `{}` is an empty entry, for a word with nothing in that tier.
- A word's punctuation belongs to the text: *Mūsa,* is one word, and its entries need no comma. Punctuation standing on its own, such as an em dash (—), isn't a word and takes no entry.

A tier with fewer entries than words leaves the last words without one, and a tier with more drops the extra entries; both are flagged. A missing pair of braces is the usual cause:

```gloss broken
\igt
\text Mūsa, mihī causās memorā
\glword {O Muse} to me {the causes} recount
```

## Tiers without rows of their own

`\morph` and `\syl` write the words again, divided into morphemes or syllables, without their punctuation. Lined up with the others, they would be rows that repeat the text:

```gloss grid
\igt
\text Arma virumque canō
  \morph Arma vir-um=que can-ō
  \syl Ar-ma vi-rum-que ca-nō
\glmorph arms.ACC.PL man-ACC.SG=and sing-1SG.PRS.ACT.IND
```

The page doesn't give them rows of their own. It draws their breaks into the text itself: those of `\morph` as it writes them (`-`, `=`), when *Morpheme breaks* is on in *Display options*, and those of `\syl` as dots, when *Syllable breaks* is on. This guide's examples show the morpheme breaks only:

```gloss
\igt
\text Arma virumque canō
  \morph Arma vir-um=que can-ō
  \syl Ar-ma vi-rum-que ca-nō
\glmorph arms.ACC.PL man-ACC.SG=and sing-1SG.PRS.ACT.IND
\ft I sing of arms and the man
```

Here the breaks of `\morph` show in the text, *vir-um=que*, and `\glmorph` glosses each morpheme in the row below (see [Morphemes and morphemic glosses](#guide-morphemes-and-glosses), and [Scansion](#guide-scansion) for `\syl`).

`\scan` doesn't get a row either: it puts its metrical marks above the text, the way ruby readings sit above their characters.

The [free translation](#guide-free-translations), `\ft`, and [notes](#guide-notes) aren't tiers at all. They aren't lined up with the words, so they go below the IGT or beside it.

## Spelling the same letters

`\morph` and `\syl` are compared with the text letter by letter, and so are the words a note quotes. Capitals, diacritics and punctuation don't count, so `mus-a` matches *Mūsa,*. An apostrophe counts as a letter (*don't*, glottal stops), wherever it is in a word.

## The text written other ways

A transliteration or a transcription is the text again, written another way, so it is written as `\text` with an option: `\text[translit]`, `\text[phonemic]`, `\text[phonetic]` or `\text[ipa]`. Each has an entry per word and a row of its own (see [Transliteration](#guide-transliteration) and [Phonemic and phonetic transcription](#guide-transcriptions)).

## Row order

Which rows are shown, and in what order, is yours to choose in [Display options](#guide-display-options): *Row order* changes the order on one text, and a text can set its own with `\order`.
