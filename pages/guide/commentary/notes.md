---
description: Writing notes on single words, longer passages, or the text as a whole.
---

# Notes

A note is written under its [IGT](#guide-writing-a-text), with `\note`: first the words it is about, quoted in braces, then the note's text.

```gloss
\igt 1.8
\text Mūsa, mihī causās **memorā**, quō nūmine laesō
  \morph Mūs-a mihī caus-ās memor-ā quō nūmin-e laes-ō
\glmorph muse-VOC.SG 1SG.DAT cause-ACC.PL recount-2SG.PRS.ACT.IMP what.ABL.N.SG divinity-ABL.SG offended-ABL.N.SG
\note {Mūsa} The Muse of epic poetry, invoked at the start of the poem.
\note {quō nūmine laesō} Ablative absolute
```

- The note's number appears after the last word quoted. Click a number to highlight all of the note's words, and click again to turn the highlighting off. In Read mode, clicking the note itself does the same.
- Quotes are matched letter by letter: capitals, diacritics and punctuation don't matter, so `{musa}` would do as well.
- A quote is matched against `\text`, or against the [transliteration](#guide-transliteration) if it matches nothing in `\text`.
- A quote has to match exactly one place in its IGT. If a word occurs twice, quote one more word with it to say which, or add `#n` for its nth place: `{tot#2}` is the second *tot*.

## Longer stretches, and other IGTs

- `{a … b}` (or `{a ... b}`) quotes everything from `a` to the next `b`, which can be in a later IGT. With `#n` on the second quote, `{a … b#2}` runs to the second `b` after `a`.
- A note can quote several groups of words, `{…} {…}`; its number appears after each of them.
- A group starting with `@label` is matched in the IGT labelled `label` (`\igt 1.10`) rather than in the note's own. Only labels written after `\igt` can be used, not the automatic numbers, since those change when IGTs are added. In a text with [sections](#guide-sections), `@section/label` points into another section.
- A group starting with `*` (`{* …}`, or `{@1.10* …}`) belongs to the note without showing its number.

```gloss
\igt 1.9
\text quidve dolēns rēgīna deum tot **volvere** cāsūs
  \morph quid=ve dol-ēns rēgīn-a de-um tot volv-ere cās-ūs
\note {tot volvere cāsūs} {@1.10 tot adīre labōrēs} Complements of *impulerit*: what the queen drove the man to undergo.
\note {@1.10 īnsignem … virum} {* rēgīna} The object of *impulerit*. Its subject, *rēgīna*, is highlighted with it, without a number.
\igt 1.10
\text īnsignem pietāte virum tot **adīre** labōrēs
  \morph īnsign-em pietāt-e vir-um tot adīre labōr-ēs
```

## Numbering

- Notes are numbered automatically, in the order their numbers first appear in the text.
- `\note[tag]` gives a note a tag, which isn't shown and must be unique in the text. Nothing refers to notes by their tags yet.
- A note's text can be formatted, and can have links (see [Formatting and links](#guide-formatting-and-links)).

## Notes on the whole text

Notes about the text as a whole go at the end, after `\end`, which closes the IGTs: only notes may follow it. A note there that quotes words has to say which IGT each group is in, with `@label`.

```gloss
\igt 1.8
\text Mūsa, mihī causās memorā
\igt 1.9
\text quidve dolēns rēgīna deum
\end
\note The poem's opening invocation.
\note {@1.8 Mūsa} {@1.9 rēgīna} The two goddesses of the opening.
```

## Where notes appear

In *Display options* (the *Commentary* group), notes can be shown below each IGT, beside it, all together below the full text, or not at all.
