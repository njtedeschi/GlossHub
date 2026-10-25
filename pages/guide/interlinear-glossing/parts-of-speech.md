---
description: Labeling each word with its part of speech.
---

# Parts of speech

`\pos` gives each word a part of speech. Write it in full or abbreviated, in any case (`N`, `noun` or `Noun`); it is shown in a standard abbreviation.

```gloss
\igt
\text quidve dolēns
  \morph quid=ve dol-ēns
\pos pronoun=conjunction participle
```

- A subclass follows a period: `V.trans`, `det.quantifier`.
- A word with a clitic is more than one **syntactic word**: the clitic and its host each count as a word of their own in the syntax, so each gets its own part of speech. Write them in order, joined by `=`, as in `pronoun=conjunction` for *quid=ve*. The word's `\morph` says where they divide, so it needs the same `=` (see [Morphemes and morphemic glosses](#guide-morphemes-and-glosses)).
- The classes are noun (`N`), pronoun (`Pro`), verb (`V`), participle (`Ptcp`), adjective (`Adj`), adverb (`Adv`), preposition (`Prep`), conjunction (`Conj`), interjection (`Int`), determiner (`Det`), article (`Art`), complementizer (`Comp`), numeral (`Num`) and particle (`Part`).
- The subclasses are transitive (`trans`), intransitive (`intr`), ditransitive (`ditr`), auxiliary (`aux`), copula (`cop`), quantifier (`quant`), demonstrative (`dem`), possessive (`poss`), personal (`pers`), relative (`rel`), interrogative (`interr`), reflexive (`refl`), indefinite (`indef`), definite (`def`), cardinal (`card`), ordinal (`ord`), coordinating (`coord`), subordinating (`subord`) and proper (`prop`).
- A few other common spellings work too, such as `pron` and `vb`; anything else is flagged.

How parts of speech combine may still change, so `\pos` reserves the symbols it doesn't use yet.

Parts of speech are hidden by default; turn them on in *Display options* (the *Glossing* group), with or without subclasses.
