---
description: Adding a transliteration and analyzing it alongside the original script.
---

# Transliteration

`\text[translit]` adds a transliteration: the text again, in another script, with an [entry](#guide-tiers-and-rows) for each word. `\morph[translit]` divides it into morphemes, as `\morph` does the text. `[tl]` is short for `[translit]`.

```gloss
\dir rtl

\igt 1:1
\text בְּרֵאשִׁית, בָּרָא אֱלֹהִים, אֵת הַשָּׁמַיִם, וְאֵת הָאָרֶץ
  \morph בְּ=רֵאשִׁית [(ב)(ר)(א)] אֱלֹהִים אֵת הַ=שָּׁמַיִם וְ=אֵת הָ=אָרֶץ
\text[translit] bereshit bara elohim et hashamayim veet haarets
  \morph[translit] be=[reshit] [(b)a(r)a] [elohim] et ha=[shamayim] ve=et ha=[arets]
\glmorph in=beginning create\\PST.3SG.M God ACC the=heavens and=ACC the=earth
\glword {in the beginning} created God {} {the heavens} {and} {the earth}
\ft In the beginning God created the heavens and the earth.
```

- `\morph[translit]` is written like `\morph` (see [Morphemes and morphemic glosses](#guide-morphemes-and-glosses)). When an [IGT](#guide-writing-a-text) has both, they must divide each word into the same morphemes, with the same separators. The glosses follow either.
- Each marks its own [stems and roots](#guide-stems-and-roots): in unpointed Hebrew every letter of בָּרָא is root, in *bara* the vowels aren't.
- The transliteration is written plainly, without formatting of its own; a word formatted in the text is formatted in its transliteration too.
- A note's quote is matched against the transliteration when it matches nothing in the text (see [Notes](#guide-notes)).
- `\syl[translit]` divides the transliteration into syllables, so that a text in another script can be scanned (see [Scansion](#guide-scansion)).

In *Display options* (the *Orthography* group), *Transliteration* shows or hides it.
