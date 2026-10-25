---
description: Writing a text one IGT at a time, with a translation, a title and a description.
---

# Writing a text

A text is written as a series of **IGTs**, short for *interlinear glossed text*: each is one line of the text, such as a sentence or a verse, with whatever you add to it. At its simplest, an IGT is a line and its translation:

```gloss "Indonesian (Sneddon 1996: 237). Leipzig Glossing Rules, example 1."
\igt
\text Mereka di Jakarta sekarang.
\ft They are in Jakarta now.
```

The example shows the source you write and, with it, what the page shows; the line under them says where it comes from. `\igt` starts the IGT. `\text` gives the line as it is written, punctuation and all, so a text can be pasted in. `\ft` gives its free translation. Only `\text` is required.

Everything else about the line goes under `\text` in the same way: how its words divide into morphemes, their glosses, its syllables and scansion, a transliteration, notes. The rest of this guide covers each of them, and [Tiers and rows](#guide-tiers-and-rows) shows how they line up.

In *Display options* (the *Commentary* group), the translation can be shown below or beside each IGT, or hidden (see [Free translations](#guide-free-translations)).

## Labels

An IGT can be given a **label** after `\igt`, such as a verse number: `\igt 1.1`. IGTs without one are numbered in order. Labels must be unique (within their section, in a text with [sections](#guide-sections)); they are also how a note refers to another IGT (see [Notes](#guide-notes)).

## The header

Everything before the first IGT (or the first `\section`) is the **header**, for what concerns the whole text:

- `\title` names the text: `\title Aeneid 1.8–11`.
- `\about` describes it in a sentence or two, shown under the title and in the list of texts. It can be formatted like the translation (see [Formatting and links](#guide-formatting-and-links)).

Other header lines are explained where they are used: `\abbr` in [Glossing abbreviations](#guide-glossing-abbreviations), `\color` in [Color coding](#guide-color-coding), `\dir` and `\lang` in [Text direction and fonts](#guide-text-direction-and-fonts), and `\order` in [Display options](#guide-display-options).

## How the source is written

- Each part of an IGT starts with a command, such as `\text` or `\ft`, at the start of a line. Everything up to the next command belongs to it, even over several lines: the translation, notes and description keep their line breaks, and the other parts join their lines with spaces.
- Spaces before a command don't matter. The site indents `\morph` and `\syl` under the `\text` they divide, and you can too.
- Some commands have a short form: `\ex` for `\igt` (as in LaTeX), `\tx` for `\text`, `\gl` for `\glmorph` and `\wg` for `\glword`; `[tl]` is short for `[translit]`.
- Lines that start with `%` are comments, which aren't shown. The site doesn't keep them when it rewrites a text (see [Special characters and other quirks](#guide-special-characters)).

[Commands](#guide-commands) lists every command, with the page that explains it, and *Input format*, under a text in Edit mode, sums up the format on one page.

## Problems

Problems are flagged as an *Error*, usually for something that can't be shown as written, or a *Warning*, for something that looks like a mistake or that the site sets aside. A source editor lists every problem in what you type, including those about how it is written, such as a missing entry or a reserved symbol, so fix them before you apply it. Afterwards, in Edit mode, the problems with an IGT's content, such as a gloss that doesn't match its word, stay listed under it. Reading a text doesn't show them. An example in this guide that shows a problem on purpose says so:

```gloss broken
\igt
\text Mereka di Jakarta sekarang.
\tranlation They are in Jakarta now.
```

## Sources

- Comrie, Bernard, Martin Haspelmath & Balthasar Bickel. 2008. [The Leipzig Glossing Rules: Conventions for interlinear morpheme-by-morpheme glosses](https://www.eva.mpg.de/lingua/resources/glossing-rules.php). Department of Linguistics of the Max Planck Institute for Evolutionary Anthropology & Department of Linguistics of the University of Leipzig. Last changed May 31, 2015.
- Sneddon, James Neil. 1996. *Indonesian: A comprehensive grammar.* London: Routledge.
