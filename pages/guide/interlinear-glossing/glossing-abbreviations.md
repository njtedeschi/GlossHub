---
description: Using standard abbreviations for grammatical categories, and adding your own.
---

# Glossing abbreviations

The morphemic gloss writes grammatical categories as abbreviations in capitals, shown in small capitals; hover over one to see its full name. The abbreviations of the Leipzig Glossing Rules are built in, with a few common extras, and a text can add its own.

This page builds on [Morphemes and morphemic glosses](#guide-morphemes-and-glosses). Its examples are the Rules' own.

```gloss "Russian. Leipzig Glossing Rules, example 5."
\igt
\text My s Marko poexali avtobusom v Peredelkino.
  \morph My s Marko poexa-l-i avtobus-om v Peredelkino
\glmorph 1PL COM Marko go-PST-PL bus-INS ALL Peredelkino
\ft Marko and I went to Peredelkino by bus.
```

- An element of a gloss written entirely in capitals and digits is an abbreviation (`COM`, `1PL`); anything else is lexical, even a capitalized name such as `Marko`. So a lexical gloss in capitals, such as *I*, reads as an abbreviation: write `1SG`, or put *I* in the informal gloss.
- An abbreviation isn't always needed. The Rules give a second gloss of this example, with words instead: `we with Marko go-PST-PL bus-by to Peredelkino`.
- Person and number are written together, without a period: the Rules gloss Italian *and-iamo*, 'we go', as `go-PRS.1PL`, not `go-PRS.1.PL`. Hovering over `1PL` shows *first person plural*.
- The extras are `ACT` (active), `AOR` (aorist), `CSTR` (construct state), `GER` (gerund), `GERV` (gerundive), `IMPF` (imperfect), `MID` (middle), `OPT` (optative), `PLPRF` (pluperfect) and `SUP` (supine).
- An abbreviation that is in neither list is underlined with dots and flagged.

## Adding your own

`\abbr` in the header adds an abbreviation for one text, or changes the meaning of a built-in one. Here `UND` (undergoer) is new, and `ACT` means *actor* rather than the site's *active*. (The verb's stem, *na-…-xʔų* 'hear', comes in two parts, and each is glossed *hear*.)

```gloss "Lakhota. Leipzig Glossing Rules, example 24."
\abbr UND undergoer
\abbr ACT actor

\igt
\text nawíčhawaxʔų
  \morph na-wíčha-wa-xʔų
\glmorph hear-3PL.UND-1SG.ACT-hear
\ft I hear them
```

## The prefix non-

The Leipzig prefix `N-`, *non-*, can go before any abbreviation, written `N`, `NON` or `n`: `NSG`, `NONSG` and `nSG` all mean *non-singular*, and in `nSG` the *n* is shown in lowercase. An abbreviation that is already known as written comes first, so `NEG` is still negation, and a text's own `\abbr NSG` means what it says. Here `3NSG` is *third person non-singular* and `NPST` *non-past*:

```gloss "Belhare. Leipzig Glossing Rules, example 21."
\igt
\text nee akhimchi nyuNNa
  \morph ne-e a-khim-chi n-yuNNa
\glmorph DEM-LOC 1SG.POSS-house-PL 3NSG-be.NPST
\ft Here are my houses.
```

## Grammatical only

In *Display options* (the *Glossing* group), *Morphemic gloss* can be set to *grammatical only*, which hides the lexical glosses and keeps the abbreviations: `go-PST-PL` is shown as `PST-PL`, and `bus-INS` as `INS`. The choice is offered when a text uses glossing abbreviations.

## Sources

- Comrie, Bernard, Martin Haspelmath & Balthasar Bickel. 2008. [The Leipzig Glossing Rules: Conventions for interlinear morpheme-by-morpheme glosses](https://www.eva.mpg.de/lingua/resources/glossing-rules.php). Department of Linguistics of the Max Planck Institute for Evolutionary Anthropology & Department of Linguistics of the University of Leipzig. Last changed May 31, 2015.
