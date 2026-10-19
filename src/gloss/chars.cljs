(ns gloss.chars
  "Characters as the rows read them: punctuation at the edges of a word,
  backslash escapes, and letters, which is what one row is compared by
  when it is checked against another."
  (:require [clojure.string :as str]
            [gloss.rows :as rows]))

;; ---------------------------------------------------------------------------
;; Punctuation
;;
;; Punctuation belongs to \text. The other rows ignore it at the edges of
;; their items.

(def lead-punct
  "Punctuation that can open a word: quotation marks, brackets, inverted
  marks, a dash."
  (set "\"“‘«¿¡(—「『（"))

(def trail-punct
  "Punctuation that can close a word, in several scripts."
  (set ",.;:!?…»”\")·׃—־、。」』）？！"))

(defn strip-punct
  "`s` trimmed, without the punctuation at its edges."
  [s]
  (let [s (str/trim (str s))
        s (apply str (drop-while lead-punct (map str s)))
        s (apply str (reverse (drop-while trail-punct (reverse (map str s)))))]
    s))

;; ---------------------------------------------------------------------------
;; Escapes
;;
;; A backslash makes the next punctuation character literal (`well\-known`,
;; `\*wódr̥`); before a letter or digit it starts a command. While a row is
;; parsed, each escaped character is replaced by a stand-in from Unicode's
;; private use area, so that no markup rule has to know about escapes, and
;; the parser puts the characters back once the document is read. When a
;; document is written out, `escape` adds a backslash wherever a character
;; would otherwise be read as markup.
;;
;; An apostrophe is always a letter, so `\'` is just an apostrophe.

(def ^:private escape-pattern
  "A backslash and the punctuation (any character but a letter, digit,
  combining mark or space) it escapes."
  (js/RegExp. "\\\\([^\\p{L}\\p{N}\\p{M}\\s])" "gu"))

;; The stand-ins are taken from this stretch of the private use area, which
;; no text has a reason to contain.
(def ^:private stand-in-first 0xE000)

(def ^:private stand-in-last 0xEFFF)

(def ^:private stand-in-pattern (js/RegExp. "[\\uE000-\\uEFFF]" "g"))

(def ^:private apostrophes #{"'" "’" "ʼ"})

;; Stand-ins are given out as characters are first escaped, and stay the same
;; for the session, so every row of a document agrees on them.
(defonce ^:private stand-ins (atom {}))

(defonce ^:private originals (atom {}))

(defn- stand-in
  "The stand-in for escaped character `c`, given out the first time it is
  escaped; nil once the stretch has run out (the escape is then left as
  written)."
  [c]
  (or (@stand-ins c)
      (let [code (+ stand-in-first (count @stand-ins))]
        (when (<= code stand-in-last)
          (let [s (js/String.fromCharCode code)]
            (swap! stand-ins assoc c s)
            (swap! originals assoc s c)
            s)))))

(defn hide-escapes
  "`s` with each escape (`\\*`) replaced by its character's stand-in. An
  escaped apostrophe is just an apostrophe."
  [s]
  (when s (.replace s escape-pattern (fn [m c] (if (apostrophes c) c (or (stand-in c) m))))))

(defn restore-escapes
  "`x` with the escaped characters put back in place of their stand-ins:
  in a string, or in every string of a collection."
  [x]
  (cond
    (string? x) (.replace x stand-in-pattern #(get @originals % %))
    (map? x) (into (empty x) (map (fn [[k v]] [k (restore-escapes v)])) x)
    (seq? x) (doall (map restore-escapes x))
    (coll? x) (into (empty x) (map restore-escapes) x)
    :else x))

(defn escapes-as-written
  "Item `s` (escapes hidden) with its escapes written back as backslashes,
  as it was typed, so that it can be stored and parsed again."
  [s]
  (when s
    (.replace s stand-in-pattern #(str "\\" (get @originals % %)))))

(def reserved
  "Characters of a standard keyboard that have to be escaped to be part of a
  word of \\text (gloss.rows): some are markup, the rest are kept for markup
  to come."
  (rows/escapes "text"))

(def unused-reserved
  "Reserved characters with no meaning in \\text yet."
  (set "`~@#$%^&=<>"))

(defn escape-char
  "Character `c`, at index `i` of an item `n` characters long, as written
  out: with a backslash when it is in `markup`, or when it is punctuation
  at the item's edge, which would otherwise be read as the item's own
  punctuation."
  [markup c i n]
  (if (or (markup c)
          (and (zero? i) (lead-punct c))
          (and (= i (dec n)) (trail-punct c)))
    (str "\\" c)
    c))

(defn escape
  "`s` written out for a row whose markup is `markup`: see `escape-char`."
  [markup s]
  (let [cs (mapv str (str s)), n (count cs)]
    (apply str (map-indexed #(escape-char markup %2 %1 n) cs))))

;; ---------------------------------------------------------------------------
;; Letters
;;
;; Rows are checked against \text (or \translit) letter by letter. A letter is
;; a base character with its combining marks; it is compared without its
;; diacritics and case, and apostrophes (' ’ ʼ) are all the same letter.
;; Anything else (punctuation, escaped characters) is skipped.

(defn- combining?
  "Whether `c` is a combining mark: Latin and Greek diacritics (macron,
  acute, grave, breve, circumflex, diaeresis, …) and Hebrew points."
  [c]
  (let [n (.charCodeAt c 0)]
    (or (<= 0x0300 n 0x036F) (<= 0x1AB0 n 0x1AFF) (<= 0x1DC0 n 0x1DFF)
        (<= 0x20D0 n 0x20FF) (<= 0xFE20 n 0xFE2F)
        (<= 0x0591 n 0x05BD) (= n 0x05BF) (<= 0x05C1 n 0x05C2) (<= 0x05C4 n 0x05C5) (= n 0x05C7))))

(def ^:private letter-pattern (js/RegExp. "^[\\p{L}\\p{N}]" "u"))

(defn letter-clusters
  "Splits text into clusters: a base character with its combining marks.
  Each is {:start :end :base :letter?}, where :base is the cluster without
  diacritics, lowercased (\"ū\" and \"u\" have the same base), and
  :letter? says whether it is a letter (or digit, or apostrophe) rather
  than punctuation."
  [s]
  (let [n (count s)]
    (loop [i 0, out []]
      (if (< i n)
        (let [j (loop [j (inc i)] (if (and (< j n) (combining? (subs s j (inc j)))) (recur (inc j)) j))
              text (subs s i j)
              base (->> (seq (.normalize text "NFD")) (remove combining?) (apply str) str/lower-case)
              base (if (apostrophes base) "'" base)]
          (recur j (conj out {:start i :end j :base base
                              :letter? (boolean (or (= base "'") (.test letter-pattern base)))})))
        out))))

(defn letters-of
  "The letter clusters of `s` (see `letter-clusters`)."
  [s]
  (filterv :letter? (letter-clusters s)))

(defn first-mismatch
  "Where two rows' letters (from `letters-of`) first differ: the index of
  the first letter that isn't the same, or where the shorter one ends; nil
  when they spell the same letters."
  [as bs]
  (or (first (keep-indexed (fn [k [a b]] (when (not= (:base a) (:base b)) k)) (map vector as bs)))
      (when (not= (count as) (count bs)) (min (count as) (count bs)))))
