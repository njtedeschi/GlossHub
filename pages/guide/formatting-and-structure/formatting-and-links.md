---
description: Adding italics, bold and other emphasis, and linking words to other pages.
---

# Formatting and links

Formatting in `\text` is written as in Markdown: `*italic*` (or `_italic_`), `**bold**` (or `__bold__`), `~~struck through~~`, `==highlighted==` and `++underlined++`. They can be combined: `**a _b_**`.

```gloss
\igt 1.8
\text Mūsa, mihī **causās** memorā, quō nūmine ==laesō==
  \morph Mūs-a mihī caus-ās memor-ā quō nūmin-e laes-ō
\glmorph muse-VOC.SG 1SG.DAT cause-ACC.PL recount-2SG.PRS.ACT.IMP what.ABL.N.SG divinity-ABL.SG offended-ABL.N.SG
\glword {O Muse} {to me} {the causes} recount what divinity offended
```

- A delimiter at the start or end of a word can cover several words of its line.
- One inside a word formats part of it, and closes within the word: `caus**ās**`.
- A delimiter touches the word it formats; on its own between spaces, it formats nothing.

## In the other rows

A word formatted as a whole is formatted in its transliteration, morphemic gloss and informal gloss too. When part of a word covers whole morphemes of its `\morph`, their glosses are formatted as well:

```gloss
\igt
\text caus**ās**
  \morph caus-ās
\glmorph cause-ACC.PL
\glword {the causes}
```

A `\glword` [entry](#guide-tiers-and-rows) can also have formatting of its own, `{**the** causes}`; an entry with its own takes nothing from the text.

*Text formatting*, in the *Marking* group of *Display options*, turns all of this formatting off, in the text and in the other [rows](#guide-tiers-and-rows), or back on.

## Links

`[words](address)` makes words a link: when reading, clicking any of them opens the address. A link stays within a line.

```gloss
\igt
\text [Arma virumque](https://en.wikisource.org/wiki/Aeneid) canō
\ft I sing of arms and the man
```

## Translations and notes

The free translation, notes and a text's description use the same formatting and links, the way Markdown does: a delimiter without a partner is just a character, so `2 * 3` needs no escaping there.

## Not settled yet

These conventions, and the options for showing them, may still change. For now, some of them look like the site's own marking:

- `**bold**` looks like a stem or root marked in bold, and `++underlined++` like an underlined stem (see [Stems and roots](#guide-stems-and-roots)).
- `==highlighted==` words are shaded, a little like the words of a note you have clicked (see [Notes](#guide-notes)).

Where the two could be confused, choose a different stem or root marking in *Display options*, or turn *Text formatting* off.
