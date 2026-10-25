---
description: Adding phonemic and phonetic transcriptions of each word.
---

# Phonemic and phonetic transcription

`\text[phonemic]` and `\text[phonetic]` add transcriptions of the text, with an [entry](#guide-tiers-and-rows) for each word. They are written as raw IPA: every character is kept as you type it (`.`, `|`, `ˈ`, …). Only `{braces}`, which group an entry, and a backslash, which escapes the character after it, have a meaning of their own.

```gloss
\lang ja

\igt
\text {古|ふる}{池|いけ} や + {蛙|かわづ} {飛|と}び{込|こ}む + {水|みず} の {音|おと}
  \morph 古-池 や 蛙 飛-び-込-む 水 の 音
\text[phonemic] huruike ja kawazu tobikomu mizu no oto
\text[phonetic] ɸɯɾɯike ja kawazɯ tobikomɯ mizɯ no oto
```

## IPA

When a text doesn't distinguish phonemic from phonetic transcription, use `\text[ipa]` instead. Using it with `\text[phonemic]` or `\text[phonetic]` in the same text is flagged.

## Display

Phonemic and IPA transcriptions are shown between slashes, phonetic ones between square brackets; you don't type these.

Each transcription has its own switch in *Display options* (the *Orthography* group). By default they come after the transliteration, phonemic before phonetic; *Row order* changes the order (see [Display options](#guide-display-options)). In your default display options (on the Settings page), an IPA transcription simply follows the phonemic and phonetic ones: it is shown when either of them is, in the place of the higher one.
