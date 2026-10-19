(ns gloss.serialize
  "Writes a document back in the plain-text input format: `document-source`
  for a whole text, `line-block` for one line. Parsing what is written
  gives the same document back (apart from line ids), which the tests
  check; that is what lets texts be saved and edited as source."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.formatting :as fmt]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.notes :as notes]
            [gloss.rows :as rows]))

(defn- escape-cell
  "A \\glword cell's plain text written out: backslashes before grouping
  braces and the formatting delimiters, and edge punctuation (as
  gloss.chars/escape);
  =, ~ and + only where doubled, since only == ~~ ++ are delimiters."
  [s]
  (-> (chars/escape (set "\\{}*_") s)
      (str/replace #"==+|~~+|\+\++" (fn [m] (apply str (map #(str "\\" %) m))))))

(defn- braced
  "An item, already escaped, braced when it holds spaces or is empty."
  [s]
  (cond
    (str/blank? s) "{}"
    (re-find #"\s" s) (str "{" s "}")
    :else s))

(defn- prose
  "Prose values keep line breaks as indented continuation lines."
  [s]
  (str/replace s "\n" "\n    "))

;; ---------------------------------------------------------------------------
;; Rows

(defn- join-row
  "Joins \\text's words, with `+` where a subline starts."
  [line items]
  (let [breaks (set (:breaks line))]
    (str/join " " (map-indexed (fn [i item] (if (breaks i) (str "+ " item) item)) items))))

(defn- word-source
  "A \\text word written out: its letters escaped, with ruby markup and the
  formatting delimiters inside it. `before` and `after` are the styles that
  continue from the word before and to the word after (no delimiter there)."
  [w before after]
  (let [text (model/word-letters w)
        n (count text)
        runs (:fmt w)
        at #(fmt/styles-at runs %)
        opens (into {} (map (juxt :start identity) (:ruby w)))
        closes (into {} (map (juxt :end identity) (:ruby w)))
        diff (fn [a b] (fmt/delims (set (remove b a))))]
    (str
     (diff (at 0) before)
     (apply str
            (for [k (range n)]
              (str (when (pos? k) (str (diff (at (dec k)) (at k)) (diff (at k) (at (dec k)))))
                   (when (opens k) "{")
                   (chars/escape-char chars/reserved (subs text k (inc k)) k n)
                   (when-let [r (closes (inc k))]
                     (str "|" (chars/escape (set "\\{}|") (:text r)) "}")))))
     (diff (at (dec n)) after))))

(defn text-row
  "A line's \\text row: its words with their punctuation, formatting,
  links and ruby readings, and `+` where a subline starts."
  [line]
  (let [words (:words line)
        n (count words)
        last-styles #(fmt/styles-at (:fmt %) (dec (count (model/word-letters %))))
        first-styles #(fmt/styles-at (:fmt %) 0)
        ;; Styles that run on from word i to word i+1, across the space.
        carry (vec (for [i (range n)]
                     (if (< (inc i) n)
                       (set (filter (first-styles (words (inc i))) (last-styles (words i))))
                       #{})))]
    (join-row
     line
     (for [[i w] (map-indexed vector words)
           :let [link (:link w)
                 link-start? (and link (not= link (:link (get words (dec i)))))
                 link-end? (and link (not= link (:link (get words (inc i)))))
                 ;; Delimiters at the word's edges go inside its punctuation.
                 core (word-source w (if (pos? i) (carry (dec i)) #{}) (carry i))]]
       (str (when link-start? "[")
            (:pre w) core (:post w)
            (when link-end? (str "](" link ")")))))))

(defn- unsupported-row
  "A reserved marker's row, written back as it was read (gloss.parse/unsupported)."
  [{:keys [marker opt text]}]
  (str "\\" marker (when opt (str "[" opt "]")) " " text))

(defn- letters-only
  "A word's letters, without its punctuation: what a \\morph item for a word
  without one says."
  [core]
  (apply str (for [c (chars/letters-of core)] (subs core (:start c) (:end c)))))

(defn morph-row
  "The \\morph items of a line for surface `s` (the text by default)."
  ([line] (morph-row line "text"))
  ([line s]
   (str/join " " (for [w (:words line)]
                   (or (model/morph-src w s) (letters-only (model/surface-word w s)))))))

(defn item
  "A word's item in row `row` (\\translit or a gloss.rows/word-items row),
  written out with the escapes the row needs."
  [row word]
  (case row
    "translit" (chars/escape (rows/escapes row) (model/surface-word word "translit"))
    "glmorph" (morph/join-segments (rows/escapes row) (:glmorph word))
    "glword" (fmt/with-delimiters (str (:glword word)) (:glword-fmt word) escape-cell)
    (chars/escape (rows/escapes row) (str (get word (keyword row))))))

(defn- item-row
  "Row `row` of a line, an item per word."
  [line row]
  (str "\\" row " " (str/join " " (map #(braced (item row %)) (:words line)))))

(def ^:private weight-mark
  "The mark the site writes for each syllable weight."
  {:long "_" :short "u" :anceps "x"})

(defn scan-row
  "A \\scan row from a line's prosody on one surface."
  [{:keys [syllables feet caesurae]}]
  (str/join " " (for [[i s] (map-indexed vector syllables)]
                  (str (cond (caesurae i) "|| " (feet i) "' " :else "")
                       (weight-mark (:weight s) "?")))))

(defn syl-row
  "\\syl from a prosody over `letters` (the words run together, in `ranges`):
  letters only, `-` between syllables, `‿` where a syllable joins words,
  `(…)` around elided letters."
  [letters ranges {:keys [syllables elided]}]
  (let [starts (set (map :start syllables))
        el-starts (set (map first elided))
        el-ends (set (map second elided))
        crosses? (fn [pos] (some #(< (:start %) pos (:end %)) syllables))]
    (first
     (reduce
      (fn [[out open?] [i r]]
        (let [out (str out (when (pos? i) (if (crosses? (:start r)) "‿" " ")))
              [out open?]
              (reduce (fn [[out open?] cl]
                        (let [p (+ (:start r) (:start cl))
                              close? (and open? (el-ends p))
                              out (str out
                                       (when close? ")")
                                       (when (and (> p (:start r)) (starts p)) "-")
                                       (when (el-starts p) "(")
                                       (subs letters p (+ (:start r) (:end cl))))]
                          [out (or (el-starts p) (and open? (not close?)))]))
                      [out open?]
                      (filter :letter? (chars/letter-clusters (subs letters (:start r) (:end r)))))
              close? (and open? (el-ends (:end r)))]
          [(str out (when close? ")")) (and open? (not close?))]))
      ["" false]
      (map-indexed vector ranges)))))

;; ---------------------------------------------------------------------------
;; Blocks and documents

(defn note-source
  "A note as written: `\\note[tag] {group} … text`."
  [{:keys [tag groups text]}]
  (str "\\note" (when tag (str "[" tag "]"))
       (apply str (for [g groups] (str " {" (notes/group-text g) "}")))
       (when-not (str/blank? text) (str " " (prose text)))))

(defn line-block
  "The source block for one line, with the notes written under it."
  [line]
  (let [words (:words line)
        raw (:raw line)
        has (model/line-rows line)
        items (fn [rows] (for [row rows :when (has row)] (item-row line row)))
        morph-of? (fn [s] (some #(model/morph-src % s) words))
        syl-of (fn [s]
                 (let [k (model/analysis-row "syl" s)]
                   (cond
                     (get raw k) [(str "\\" k " " (get-in raw [k :text]))]
                     (model/prosody line s)
                     [(str "\\" k " " (syl-row (model/surface-letters line s) (model/surface-ranges line s)
                                                (model/prosody line s)))])))
        scan-of #(when (and (seq (:syllables %)) (every? :weight (:syllables %))) [(str "\\scan " (scan-row %))])]
    (str/join
     "\n"
     (concat
      [(str "\\ex" (when (:label line) (str " " (:label line))))]
      (if-let [r (get raw "scan")]
        [(str "\\scan " (:text r))]
        (scan-of (some #(model/prosody line %) model/surfaces)))
      (mapcat syl-of model/surfaces)
      [(str "\\text " (text-row line))]
      (when (morph-of? "text") [(str "\\morph " (morph-row line))])
      (items ["translit"])
      (when (morph-of? "translit") [(str "\\morph[translit] " (morph-row line "translit"))])
      (map unsupported-row (:unsupported line))
      (items rows/transcriptions)
      (items ["glmorph" "glword" "pos"])
      (when-not (str/blank? (:free line)) [(str "\\ft " (prose (:free line)))])
      (map note-source (:notes line))))))

(defn- section-heading
  "A section's \\section line."
  [{:keys [label title]}]
  (str "\\section" (when label (str "[" label "]")) (when-not (str/blank? title) (str " " title))))

(defn document-source
  "A whole document as source text: the header, then the lines under their
  section headings, then \\end and the notes after it."
  [doc]
  (let [sections (:sections doc)
        position (into {} (map-indexed (fn [i s] [(:id s) i]) sections))
        header (concat
                [(str "\\glosshubformat " model/format-version)
                 (str "\\title " (:title doc))]
                (when-not (str/blank? (:about doc)) [(str "\\about " (prose (:about doc)))])
                (when (:dir doc) [(str "\\dir " (:dir doc))])
                (when (:lang doc) [(str "\\lang " (:lang doc))])
                (when (seq (:order doc)) [(str "\\order " (str/join " " (:order doc)))])
                (map unsupported-row (:unsupported doc))
                (for [[tag meaning] (sort (:abbrevs doc))]
                  (str "\\abbr " tag " " meaning))
                (for [{:keys [tag color label]} (:colors doc)]
                  (str "\\color " tag " " color (when label (str " " label)))))
        ;; Lines in order, each section's heading before its first line; a
        ;; section without lines keeps its place.
        [blocks next-section]
        (reduce (fn [[out k] line]
                  (let [to (when-let [s (:section line)] (position s))
                        [out k] (if (and to (>= to k))
                                  [(into out (map section-heading (subvec sections k (inc to)))) (inc to)]
                                  [out k])]
                    [(conj out (line-block line)) k]))
                [[] 0]
                (:lines doc))
        blocks (into blocks (map section-heading (drop next-section sections)))
        back (when (seq (:notes doc))
               (cons "\\end" (map note-source (:notes doc))))]
    (str (str/join "\n" header) "\n\n" (str/join "\n\n" blocks)
         (when back (str "\n\n" (str/join "\n" back)))
         "\n")))
