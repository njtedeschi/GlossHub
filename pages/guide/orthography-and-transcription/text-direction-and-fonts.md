---
description: Writing texts that run right to left, and showing each language in a suitable font.
---

# Text direction and fonts

Two header lines say how a text is written: `\dir rtl` makes it run right to left, and `\lang` says what language it is in, which chooses the fonts.

```gloss
\dir rtl

\igt 1:1
\text בְּרֵאשִׁית, בָּרָא אֱלֹהִים, אֵת הַשָּׁמַיִם, וְאֵת הָאָרֶץ
\glword {in the beginning} created God {} {the heavens} {and} {the earth}
\ft In the beginning God created the heavens and the earth.
```

## Right to left

`\dir rtl` is for scripts such as Hebrew and Arabic. The text runs right to left, and the [rows](#guide-tiers-and-rows) under it stay left to right.

## Language and fonts

`\lang` takes a language code. For Chinese, Japanese and Korean it chooses the font, so that the characters they share take each language's forms: `ja` for Japanese, `ko` for Korean, `zh-Hans` for Simplified and `zh-Hant` for Traditional Chinese, `yue` for Cantonese.

```gloss
\lang ja

\igt
\text 古池や
\ft The old pond
```
