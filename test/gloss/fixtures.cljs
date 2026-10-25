(ns gloss.fixtures
  "Texts the tests share (test/fixtures/*.gloss, embedded at compile time):
  constructed examples that between them use every feature of the input
  format. The browser tests load the same files, as the reader's own texts,
  so that what they check doesn't depend on the Guide's pages."
  (:require-macros [gloss.inline :as inline])
  (:require [gloss.parse :as parse]))

(def latin
  "Leipzig glosses (one in Leipzig brackets), colors, notes on words and
  groups (and across IGTs, with a span and a starred group), emphasis,
  parts of speech, clitics, an infix, a link, and dactylic hexameter
  scansion with an elision."
  (inline/file "test/fixtures/latin.gloss"))

(def hebrew
  "Right-to-left text, transliteration, stems and roots, a gloss with a
  backslash, sections, sublines, a maqaf, emphasis, and notes on single
  words."
  (inline/file "test/fixtures/hebrew.gloss"))

(def japanese
  "Ruby readings, a word with two readings, a morpheme break after a reading,
  phonemic and phonetic transcriptions, and punctuation."
  (inline/file "test/fixtures/japanese.gloss"))

(def library
  "The three texts as a published library (the shape of
  gloss.app.state/library), for tests of published texts."
  (delay (vec (for [[slug source] [["latin" latin] ["hebrew" hebrew]
                                   ["japanese" japanese]]]
                {:slug slug :source source :doc (:doc (parse/parse-document source))}))))
