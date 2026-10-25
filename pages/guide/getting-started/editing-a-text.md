---
description: Changing a text bit by bit or all at once, and undoing what you changed.
---

# Editing a text

Every text page has a *Read | Edit* switch. In Edit mode you can change a text cell by cell, or edit its source; your changes are saved in this browser as you make them.

## Cell by cell

- Click a gloss, informal gloss, part of speech, transliteration, transcription, translation or note to edit it. *Enter* saves, *Tab* moves to the next word (*Shift+Tab* to the one before), *Escape* cancels.
- An [IGT](#guide-writing-a-text) without a translation shows *+ free translation*: click it to add one.
- A note's text is edited in place. The words it quotes are edited in the source: that of the IGT the note is written under, or the whole text's source for a note after `\end`.
- Click the title to rename the text.

## As source

Each IGT has three buttons beside its label (with a mouse, they appear when you point at the IGT):

- *Edit source* opens the IGT as source, with a preview of the result below it. *Apply* (or Ctrl/⌘+Enter) puts it in place, and *Cancel* (or *Escape*) leaves it as it was.
- *+ IGT* adds an IGT below this one, and *+ Add IGT*, after the last one, adds one at the end. Each opens a source editor for the new IGT, which starts with `\igt` and an empty `\text`.
- *Delete* removes the IGT. It takes two clicks, so that one click can't do it by accident.

*Document source*, beside *Undo* and *Redo*, opens the whole text as source, header included. In any source editor:

- Problems are listed under the text box. One that concerns a particular line of the source has a *Line N* button, which selects that line.
- The *Fill from the words* buttons add a `\morph` or `\syl` [tier](#guide-tiers-and-rows) (or the transliteration's) to the IGTs that lack one, with each word's letters ready for you to put the breaks in.

## Undo and redo

*Undo* and *Redo* (or Ctrl/⌘+Z and Ctrl/⌘+Shift+Z) step back and forward through your changes to the text.

## Opening texts in Edit mode

Texts open in Read mode. To have them open in Edit mode instead, set *Open texts in* on the Settings page.

Published texts can be edited too: your version is kept in this browser, and the published text doesn't change (see [Saving and exporting](#guide-saving-and-exporting)).
