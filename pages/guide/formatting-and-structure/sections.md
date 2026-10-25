---
description: Grouping IGTs under headings, such as the poems of a collection.
---

# Sections

`\section[label] Heading` starts a group of [IGTs](#guide-writing-a-text) under a heading, such as one poem on a page with several. Its IGTs are numbered from 1, and their labels only need to be unique within the section.

```gloss
\section[ode] Odes 1.11
\igt 8
\text carpe diem
\section[epist] Epistles 1.2
\igt 40
\text sapere aude
```

- A section lasts until the next `\section`, or the end of the IGTs. IGTs before the first `\section` belong to no section.
- The label is optional. It is how a note refers to an IGT in another section: `{@epist/40 sapere aude}` (see [Notes](#guide-notes)). Section labels must be unique in the text.
