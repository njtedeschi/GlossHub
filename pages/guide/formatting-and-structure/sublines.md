---
description: Splitting a long line into shorter parts.
---

# Sublines

A long line can be split into sublines, labeled a, b, …, with a lone `+` in `\text`. The [tiers](#guide-tiers-and-rows) with an entry per word follow the text, so the `+` goes in `\text` only.

```gloss
\igt 1.8
\text Mūsa, mihī causās memorā, + quō nūmine laesō
  \morph Mūs-a mihī caus-ās memor-ā quō nūmin-e laes-ō
\glword {O Muse} {to me} {the causes} recount what divinity offended
\ft Muse, recount to me the causes: what divinity was offended,
```

## Sublines with scansion

When a line with sublines also has [scansion](#guide-scansion), the whole line is first shown unbroken with its metrical marks, so the meter isn't cut, and the glossed sublines follow below it.

```gloss
\igt 1.8
\scan _ u u ' _ _ ' _ u u ' _ || _ ' _ u u ' _ _
\text Mūsa, mihī causās memorā, + quō nūmine laesō
  \morph Mūs-a mihī caus-ās memor-ā quō nūmin-e laes-ō
  \syl Mū-sa mi-hī cau-sās me-mo-rā quō nū-mi-ne lae-sō
\glword {O Muse} {to me} {the causes} recount what divinity offended
```
