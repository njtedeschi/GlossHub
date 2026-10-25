---
description: Showing small readings above characters, like Japanese furigana.
---

# Ruby readings

Ruby puts a small reading over characters, as furigana does in Japanese. Write it in `\text` as `{base|reading}`; the reading always sits directly above the text.

```gloss
\lang ja

\igt
\text {古|ふる}{池|いけ} や + {蛙|かわづ} {飛|と}び{込|こ}む + {水|みず} の {音|おと}
  \morph 古-池 や 蛙 飛-び-込-む 水 の 音
\text[translit] furuike ya kawazu tobikomu mizu no oto
  \morph[translit] furu-ike ya kawazu to-bi-ko-mu mizu no oto
\glword {old pond} {} frog {jumps in} water of sound
\ft The old pond —
    a frog jumps in,
    the sound of water.
\note {や} *ya*: a cutting word (*kireji*), which marks a pause.
```

- A reading can cover one character or several, and a word can have several readings (`{飛|と}び{込|こ}む`).
- `\lang ja` in the header says that the text is Japanese, which chooses the fonts (see [Text direction and fonts](#guide-text-direction-and-fonts)).

In *Display options* (the *Orthography* group), *Ruby characters* shows or hides the readings.
