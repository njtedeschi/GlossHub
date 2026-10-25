---
description: Choosing what you see, for one text or for all of them.
---

# Display options

*Display options*, above each text, choose what you see: which [rows](#guide-tiers-and-rows) are shown and in what order, how glosses and parts of speech appear, and where the translation and notes go. They are your settings, saved in this browser; they never change the text.

The options come in groups, each with a switch that hides everything in it and brings it back as it was. A text offers only the options it has a use for: one without scansion has no *Scansion* group. Each option is described on the page about its feature:

- *Orthography*: [Transliteration](#guide-transliteration), [Phonemic and phonetic transcription](#guide-transcriptions), [Ruby readings](#guide-ruby).
- *Glossing*: [Informal glosses](#guide-informal-glosses), [Morphemes and morphemic glosses](#guide-morphemes-and-glosses), [Glossing abbreviations](#guide-glossing-abbreviations), [Parts of speech](#guide-parts-of-speech).
- *Scansion*: [Scansion](#guide-scansion).
- *Commentary*: [Free translations](#guide-free-translations) and [Notes](#guide-notes).
- *Marking*: [Stems and roots](#guide-stems-and-roots), [Formatting and links](#guide-formatting-and-links), [Color coding](#guide-color-coding).

## This text, and every text

Changes made on a text's page apply to that text only, and its *Reset display options* undoes them. To change what every text starts from, use *Default display options* on the Settings page. *Reset display options for every text*, also on the Settings page, returns every text, and your defaults, to the site's own.

## Row order

*Row order* changes the order of the rows under the words, for you, on this text; *Text’s own order* goes back to the text's. A text sets its own order with `\order` in the header, naming its rows as their tiers are written, without the backslash (`text`, `text[translit]`, `glmorph` and so on):

```gloss
\order text glword glmorph

\igt
\text causās
  \morph caus-ās
\glmorph cause-ACC.PL
\glword {the causes}
```

In Edit mode, *Save as the text’s order* writes your order into the text as its `\order`. Scansion marks and ruby readings aren't rows: they always stay directly above the text (or, for a scanned transliteration, above the transliteration).

## Appearance

The Settings page also sets the site's *Theme* (as your system, light or dark) and its *Text size*, which applies to the annotated text on text pages and in this guide.
