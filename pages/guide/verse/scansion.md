---
description: Marking the meter of verse over its syllables, elision included.
---

# Scansion

Scansion takes two [tiers](#guide-tiers-and-rows): `\syl` divides the text into syllables, and `\scan` gives each syllable its metrical mark. The marks are drawn over the syllables of the text.

```gloss
\igt 1.8
\scan _ u u ' _ _ ' _ u u ' _ || _ ' _ u u ' _ _
\text Mūsa, mihī causās memorā, quō nūmine laesō
  \morph Mūs-a mihī caus-ās memor-ā quō nūmin-e laes-ō
  \syl Mū-sa mi-hī cau-sās me-mo-rā quō nū-mi-ne lae-sō
```

- In `\syl`: the words' letters, with `-` between syllables and a space between words. Punctuation can be left out.
- In `\scan`: `_` long, `u` short, `x` anceps, `'` or `|` where a foot starts, `||` at the caesura. Spaces don't matter. Feet and caesurae are written only here. A long syllable can also be written `–` or `¯`, and a short one `˘`.

The marks sit over the syllables even where syllables and morphemes divide a word differently (`cau-sās` against `caus-ās`).

## Elision

Join the words of a syllable that crosses a word boundary with `‿` (or `_`), and put elided letters in `(parentheses)`:

```gloss
\igt 1.11
\scan _ u u ' _ || _ ' _ u u ' _ _ ' _ u u ' _ _
\text impulerit. Tantaene animīs caelestibus īrae?
  \morph impul-erit Tant-ae=ne anim-īs caelest-ibus īr-ae
  \syl im-pu-le-rit Tan-tae-n(e)‿a-ni-mīs cae-les-ti-bus ī-rae
```

## How \syl is checked

- `\syl` must spell the same letters as `\text`, but diacritics, capitals and punctuation don't count: `Mu-sa` matches *Mūsa,*. If the letters differ, the message quotes the syllable where they first differ and the same stretch of `\text`.
- Its spaces must fall between the words of `\text`, and every two words must be divided by a space between syllables, or by `‿` inside a syllable they share. The first place where they disagree is flagged.
- `\scan` must have one mark for each syllable.

## Scanning a transliteration

For a text in another script, `\syl[translit]` divides the [transliteration](#guide-transliteration) instead, and the marks are drawn over it. When an [IGT](#guide-writing-a-text) has both, they must have the same number of syllables and begin each word at the same one, and the marks go over the text.

## Display

In *Display options*, the *Scansion* group has *Metrical marks*, shown by default, and *Syllable breaks*, which puts a dot between the syllables of the text. A line split into sublines is first shown whole, with its marks (see [Sublines](#guide-sublines)).
