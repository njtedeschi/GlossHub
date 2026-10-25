---
description: Dividing words into morphemes and glossing each one, following the Leipzig Glossing Rules.
---

# Morphemes and morphemic glosses

`\morph` writes each word of the text again, with breaks between its morphemes: `-` where an affix joins, `=` where a clitic joins, `~` for reduplication. The page draws the breaks into the text. `\glmorph`, the **morphemic gloss**, then glosses each morpheme, following the Leipzig Glossing Rules: one gloss per morpheme, with the same separators in the same places. The examples on this page are the Rules' own.

```gloss "Lezgian (Haspelmath 1993: 207). Leipzig Glossing Rules, example 2."
\igt
\text Gila aburun ferma hamišaluǧ güǧüna amuq'dač.
  \morph Gila abur-u-n ferma hamišaluǧ güǧüna amuq'-da-č
\glmorph now they-OBL-GEN farm forever behind stay-FUT-NEG
\ft Now their farm will not stay behind forever.
```

- `\morph` must spell the same letters as each word, but capitals, diacritics and punctuation don't count: `amuq'-da-č` matches *amuq'dač.*, and `hamisalug` would match *hamišaluǧ*.
- A hyphen that is already in the text stands for the break where one falls on it: *well-known* with `well-know-n`.
- Grammatical categories are written as abbreviations in capitals (`OBL`, `FUT`) and shown in small capitals; hover over one to see its full name (see [Glossing abbreviations](#guide-glossing-abbreviations)). Lexical glosses are written in lowercase.
- Punctuation belongs to the text: it is ignored in glosses.

`=` joins a clitic, in `\morph` and in the gloss alike:

```gloss "West Greenlandic (Fortescue 1984: 127). Leipzig Glossing Rules, example 3."
\igt
\text palasilu niuirturlu
  \morph palasi=lu niuirtur=lu
\glmorph priest=and shopkeeper=and
\ft both the priest and the shopkeeper
```

`~` joins a copied part to its stem:

```gloss "Tagalog. Leipzig Glossing Rules, example 30."
\igt
\text bibili
  \morph bi~bili
\glmorph IPFV~buy
\ft is buying
```

## Word by word

A text glossed word by word, with no morphemes to divide, needs no `\morph`: each word is one morpheme. When a morpheme's gloss takes several words or abbreviations, `.` joins them:

```gloss "French. Leipzig Glossing Rules, example 8."
\igt
\text aux chevaux
\glmorph to.ART.PL horse.PL
\ft to the horses
```

When you edit an IGT's source, the *Fill from the words* buttons add a `\morph` [tier](#guide-tiers-and-rows) with each word's letters, ready for you to put the breaks in.

## When the counts don't match

A gloss with a different number of morphemes than its word is kept as typed and flagged, so a missing hyphen never shifts the rest of the line. Here *-arum* is one morpheme, genitive and plural at once, so its gloss should be `GEN.PL`:

```gloss broken "Latin. Leipzig Glossing Rules, example 7, glossed wrongly on purpose."
\igt
\text insularum
  \morph insul-arum
\glmorph island-GEN-PL
\ft of the islands
```

## Other Leipzig conventions

Besides `.`, a gloss can use the Rules' other symbols:

- `_` between the words of a gloss when the word glossed has no parts, and only English lacks a single word for it: Turkish *çık-mak*, `come_out-INF`.
- `;` between the meanings of a form that can't be divided (`island-GEN;PL`), and `:` where the word could be divided but the gloss doesn't show it (`eat:they:shall`).
- `[…]` for an element that isn't there in the word (`boy[NOM.SG]`). Or write a zero morpheme in `\morph`, `\0`, shown as Ø, and gloss it like any other.
- `(…)` for an inherent category, such as a noun's gender (`tree(G4)`).
- `>` between the agent-like and the patient-like argument, where one affix marks both (`2DU>3SG`).
- `\\` between a category shown by a change inside the word and the rest of the gloss, such as the vowel of a plural: `father\\PL`, shown as `father\PL`.

```gloss "Latin. Leipzig Glossing Rules, example 22."
\igt
\text puer
\glmorph boy[NOM.SG]
\ft boy
\igt
\text puer
  \morph puer-\0
\glmorph boy-NOM.SG
\ft boy
```

```gloss "German. Leipzig Glossing Rules, example 16."
\igt
\text unsern Vätern
  \morph unser-n Väter-n
\glmorph our-DAT.PL father\\PL-DAT.PL
\ft to our fathers
```

## Display

In *Display options* (the *Glossing* group), *Morphemic gloss* shows or hides the gloss, and *Morpheme breaks* shows or hides the breaks in the text.

## Sources

- Comrie, Bernard, Martin Haspelmath & Balthasar Bickel. 2008. [The Leipzig Glossing Rules: Conventions for interlinear morpheme-by-morpheme glosses](https://www.eva.mpg.de/lingua/resources/glossing-rules.php). Department of Linguistics of the Max Planck Institute for Evolutionary Anthropology & Department of Linguistics of the University of Leipzig. Last changed May 31, 2015.
- Fortescue, Michael. 1984. *West Greenlandic.* (Croom Helm Descriptive Grammars.) London: Croom Helm.
- Haspelmath, Martin. 1993. *A grammar of Lezgian.* (Mouton Grammar Library 9.) Berlin: Mouton de Gruyter.
