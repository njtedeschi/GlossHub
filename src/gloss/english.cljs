(ns gloss.english
  "English for the messages the site writes: counts with their nouns, and
  lists."
  (:require [clojure.string :as str]))

(defn counted
  "`n` with its noun, singular or plural: \"1 entry\", \"3 entries\"."
  [n one many]
  (str n " " (if (= 1 n) one many)))

(defn or-list
  "Words listed with commas, and `or` before the last: \"a, b or c\"."
  [ws]
  (if (next ws)
    (str (str/join ", " (butlast ws)) " or " (last ws))
    (first ws)))
