(ns gloss.app.samples
  "The site's texts in the plain-text input format: the published texts
  (texts/*.gloss, embedded at compile time) and the template for a new text.
  To publish a text, add texts/<slug>.gloss (with \\title and \\about lines)
  and an entry in `published`."
  (:require-macros [gloss.inline :as inline]))

(def blank
  "A new blank text: a title and no lines."
  "\\title Untitled\n")

(def published
  "The texts on the site, in library order: {:slug :source}. The slug is the
  page address (#aeneid) and the file name in texts/."
  [{:slug "aeneid" :source (inline/file "texts/aeneid.gloss")}])
