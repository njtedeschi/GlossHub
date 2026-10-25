---
description: Writing characters that have a special meaning, and details of the format that may change.
---

# Special characters and other quirks

How to write the characters that have a meaning in a text's source, and other details of the format that are easy to trip over or may still change.

## Escaping

A few characters have a meaning in the source, such as `*` for emphasis. To write one as it is, put a backslash before it: `\*wódr̥` is a word starting with an asterisk, `etc\.` keeps its period as part of the word, and `\\` is a backslash. A backslash works this way before any punctuation.

The translation, notes and description are taken as written: as in Markdown, a formatting character without a partner is just a character there, so `2 * 3` needs no escaping.

## Reserved symbols

Each [tier](#guide-tiers-and-rows) reserves the symbols it has no use for yet, and flags them when they aren't escaped, so that they can be given a meaning later without changing what your text says. In `\text`, the keyboard symbols other than `. , ; : " ' ! ? - / ( )` are either used for formatting, links, ruby readings and sublines, or kept for later, so to use one in a word, escape it: `\#`, `\&`, `\=`. An unescaped one is kept, with a warning.

## Apostrophes and quotation marks

An apostrophe counts as a letter (*don't*, glottal stops), even when it is escaped. So quote with double quotation marks (`“…”` or `"…"`), or with `‘` and an apostrophe after the word, which counts as part of it.

## How the site stores a text

The site keeps what it reads from a text, not the source as you typed it. A text you import, or write in a source editor, is stored that way, and written out in a standard form when you export it or open its source again: its tiers in a standard order, and `\glosshubformat 2` at the top. What the site doesn't read isn't kept:

- comments (`%` lines);
- what follows a command it doesn't know, such as a misspelled one, or an option a command doesn't take;
- the extra entries of a tier that has more entries than there are words;
- a tier written twice in one IGT: only the first is read;
- text before the first command, and anything after `\end` other than notes;
- a `+` outside `\text`.

So keep your own copy of a file whose comments you want, and fix the problems of an [IGT](#guide-writing-a-text) before you apply it.

## What may change

- `\glosshubformat` gives the version of the format a text is written in. A text with no warnings keeps its meaning for as long as that number stays the same, and the number goes up only if the format has to change in a way that would alter what a text means.
- Texts written in version 1, the first one, are still read. In a text that says `\glosshubformat 2`, version 1's commands are flagged with how version 2 writes them: `\translit` is now written `\text[translit]`, for instance.
- What is reserved now can be given a meaning later: the symbols each tier flags, options on `\igt` (such as `\igt[...]`), and anything after `\end` other than notes.
- How parts of speech combine may still change, so `\pos` reserves the symbols it doesn't use yet (see [Parts of speech](#guide-parts-of-speech)).
- The formatting conventions aren't settled either, and some of them look like the site's own marking (see [Formatting and links](#guide-formatting-and-links)).
