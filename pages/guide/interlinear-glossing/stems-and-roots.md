---
description: Marking stems and roots, including roots whose letters are spread through a word.
---

# Stems and roots

In `\morph`, `[…]` marks a stem and `(…)` a root. Both are optional. The text and the morphemic gloss show stems underlined and roots underlined twice, or as you choose in *Display options*.

This page builds on [Morphemes and morphemic glosses](#guide-morphemes-and-glosses).

```gloss
\igt
\text overreacting blackbirds
  \morph [over-re-(act)]-ing [(black)]-[(bird)]-s
\glmorph over-again-act-PTCP black-bird-PL
```

- A stem is a run of whole morphemes: `[over-re-(act)]-ing`. A compound can have several: `[(black)]-[(bird)]-s`.
- Stems can nest: `[[(black)]-[(bird)]]-s` makes *blackbird* one stem made of two. The outermost stem is the one marked.
- A root is a morpheme's root letters. Several groups in one morpheme are one root, so a root can skip letters: `(k)a(t)a(v)` is the root k-t-v, with the pattern's vowels between.

```gloss
\igt
\text katav
  \morph (k)a(t)a(v)
\glmorph write\\PST.3SG.M
\ft he wrote
```

## In the gloss

The morphemic gloss marks the stems and roots of the morphemes it glosses. When a morpheme is only partly root, the root's meaning is the gloss up to `\\` (or, without one, up to the first glossing abbreviation): `write\\PST.3SG.M` marks *write*.

## With a transliteration

`\morph[translit]` marks the stems and roots of a [transliteration](#guide-transliteration) in the same way. The text and the transliteration mark their own: in unpointed Hebrew every letter of בָּרָא is root, in *bara* the vowels aren't. When both mark a word's stems and roots, they must be in the same morphemes.

## Display

In *Display options* (the *Marking* group), *Stem marking* underlines stems or makes them bold, and *Root marking* double-underlines roots or makes them bold.
