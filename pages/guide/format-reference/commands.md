---
description: Every command, with the page that explains it.
---

# Commands

Each part of a text's source starts with a command, such as `\text` or `\ft`, at the start of a line (see [Writing a text](#guide-writing-a-text)). Here they all are, each with the page that explains it. A short form, where there is one, follows in parentheses.

## An IGT

- `\igt` (`\ex`) starts an [IGT](#guide-writing-a-text), optionally with a label: `\igt 1.8`.
- `\text` (`\tx`) gives the line as it is written. Within it, `+` starts a [subline](#guide-sublines), `{古|ふる}` gives a [ruby reading](#guide-ruby), and words can be [formatted and linked](#guide-formatting-and-links).
- `\ft` gives the [free translation](#guide-free-translations).
- `\note` writes a [note](#guide-notes) on the IGT or some of its words; `\note[tag]` gives it a tag.

## Other forms of the line

- `\text[translit]` (`\text[tl]`) gives a [transliteration](#guide-transliteration).
- `\text[phonemic]`, `\text[phonetic]` and `\text[ipa]` give [transcriptions](#guide-transcriptions).

## Dividing the words

- `\morph` divides each word into morphemes ([Morphemes and morphemic glosses](#guide-morphemes-and-glosses)), and marks [infixes](#guide-infixes) and [stems and roots](#guide-stems-and-roots). In it, `\0` is a zero morpheme. `\morph[translit]` divides the transliteration.
- `\syl` divides the line into syllables, for [scansion](#guide-scansion); `\syl[translit]` divides the transliteration.

## Glossing

- `\glmorph` (`\gl`) glosses each morpheme ([Morphemes and morphemic glosses](#guide-morphemes-and-glosses), [Glossing abbreviations](#guide-glossing-abbreviations)).
- `\glword` (`\wg`) gives each word an [informal gloss](#guide-informal-glosses).
- `\pos` gives each word its [part of speech](#guide-parts-of-speech).
- `\scan` marks the meter over the syllables ([Scansion](#guide-scansion)).

## The header

These go before the first IGT:

- `\title` names the text, and `\about` describes it ([Writing a text](#guide-writing-a-text)).
- `\abbr` adds a [glossing abbreviation](#guide-glossing-abbreviations) of the text's own.
- `\color` colors an abbreviation ([Color coding](#guide-color-coding)).
- `\dir rtl` is for a text written right to left, and `\lang` gives its language, which picks its fonts ([Text direction and fonts](#guide-text-direction-and-fonts)).
- `\order` sets the order of the rows ([Display options](#guide-display-options)).
- `\glosshubformat` gives the version of the format; the site writes it ([Special characters and other quirks](#guide-special-characters)).

## Sections and the end

- `\section` starts a group of IGTs under a heading ([Sections](#guide-sections)).
- `\end` closes the IGTs; only notes on the whole text follow it ([Notes](#guide-notes)).

## Everything else

- A line starting with `%` is a comment, which isn't shown ([Writing a text](#guide-writing-a-text)).
- A backslash before punctuation writes it as it is: `\*` ([Special characters and other quirks](#guide-special-characters)).
- Version 1 of the format wrote `\translit`, `\phonemic`, `\phonetic` and `\ipa` for the forms that are now `\text[translit]` and so on. They are still read ([Special characters and other quirks](#guide-special-characters)).
- Any other command is flagged. A few are reserved for later, such as `\row`: they are kept as written, but not shown.
