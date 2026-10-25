(ns gloss.serialize
  "Writes a document back in the plain-text input format: `document-source`
  for a whole text, `igt-block` for one IGT. Parsing what is written
  gives the same document back (apart from IGT ids), which the tests
  check; that is what lets texts be saved and edited as source."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.formatting :as fmt]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.notes :as notes]
            [gloss.tiers :as tiers]))

(defn- escape-glword
  "A \\glword entry's plain text written out: backslashes before grouping
  braces and the formatting delimiters, and edge punctuation (as
  gloss.chars/escape);
  =, ~ and + only where doubled, since only == ~~ ++ are delimiters."
  [s]
  (-> (chars/escape (set "\\{}*_") s)
      (str/replace #"==+|~~+|\+\++" (fn [m] (apply str (map #(str "\\" %) m))))))

(defn- braced
  "An entry, already escaped, braced when it holds spaces or is empty."
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
;; Tiers

(defn- join-words
  "Joins \\text's words, with `+` where a subline starts."
  [igt words]
  (let [breaks (set (:breaks igt))]
    (str/join " " (map-indexed (fn [i w] (if (breaks i) (str "+ " w) w)) words))))

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

(defn text-tier
  "An IGT's \\text tier: its words with their punctuation, formatting,
  links and ruby readings, and `+` where a subline starts."
  [igt]
  (let [words (:words igt)
        n (count words)
        last-styles #(fmt/styles-at (:fmt %) (dec (count (model/word-letters %))))
        first-styles #(fmt/styles-at (:fmt %) 0)
        ;; Styles that run on from word i to word i+1, across the space.
        carry (vec (for [i (range n)]
                     (if (< (inc i) n)
                       (set (filter (first-styles (words (inc i))) (last-styles (words i))))
                       #{})))]
    (join-words
     igt
     (for [[i w] (map-indexed vector words)
           :let [link (:link w)
                 link-start? (and link (not= link (:link (get words (dec i)))))
                 link-end? (and link (not= link (:link (get words (inc i)))))
                 ;; Delimiters at the word's edges go inside its punctuation.
                 core (word-source w (if (pos? i) (carry (dec i)) #{}) (carry i))]]
       (str (when link-start? "[")
            (:pre w) core (:post w)
            (when link-end? (str "](" link ")")))))))

(defn- unsupported-field
  "A reserved command's field, written back as it was read (gloss.parse/unsupported)."
  [{:keys [command opt text]}]
  (str "\\" command (when opt (str "[" opt "]")) " " text))

(defn- letters-only
  "A word's letters, without its punctuation: what a \\morph entry for a
  word without one says."
  [core]
  (apply str (for [c (chars/letters-of core)] (subs core (:start c) (:end c)))))

(defn- morph-entries
  "An IGT's \\morph entries for surface `s`, one per word (a word without
  one as its letters), or nil when no word has one."
  [igt s]
  (let [words (:words igt)]
    (when (some #(model/morph-src % s) words)
      (for [w words] (or (model/morph-src w s) (letters-only (model/surface-word w s)))))))

(defn entry
  "A word's entry in tier `tier` (\\text[translit] or a
  gloss.tiers/word-entries tier), written out with the escapes the tier
  needs."
  [tier word]
  (case tier
    "translit" (chars/escape (tiers/escapes tier) (model/surface-word word "translit"))
    "glmorph" (morph/join-segments (tiers/escapes tier) (:glmorph word))
    "glword" (fmt/with-delimiters (str (:glword word)) (:glword-fmt word) escape-glword)
    (chars/escape (tiers/escapes tier) (str (get word (keyword tier))))))

(defn- tier-field
  "Tier `tier` of an IGT as a field: its command, then an entry per word."
  [igt tier]
  (str "\\" (tiers/command tier) " " (str/join " " (map #(braced (entry tier %)) (:words igt)))))

(def ^:private weight-mark
  "The mark the site writes for each syllable weight."
  {:long "_" :short "u" :anceps "x"})

(defn scan-tier
  "A \\scan tier from an IGT's prosody on one surface."
  [{:keys [syllables feet caesurae]}]
  (str/join " " (for [[i s] (map-indexed vector syllables)]
                  (str (cond (caesurae i) "|| " (feet i) "' " :else "")
                       (weight-mark (:weight s) "?")))))

(defn syl-tier
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

(defn- syllables
  "An IGT's \\syl for surface `s` as the site writes it, or nil without one."
  [igt s]
  (when-let [p (model/prosody igt s)]
    (syl-tier (model/surface-letters igt s) (model/surface-ranges igt s) p)))

;; ---------------------------------------------------------------------------
;; Blocks and documents

(defn note-source
  "A note as written: `\\note[tag] {group} … text`."
  [{:keys [tag groups text]}]
  (str "\\note" (when tag (str "[" tag "]"))
       (apply str (for [g groups] (str " {" (notes/group-text g) "}")))
       (when-not (str/blank? text) (str " " (prose text)))))

(def ^:private written-order
  "The order an IGT's source writes its tiers in, after \\igt and \\scan,
  each as [kind arg]: \\text and \\text[translit], each followed by the
  \\morph and \\syl that divide it (:divided, `arg` the text tier), then the
  reserved fields, kept as written, the transcriptions and the other tiers
  with an entry per word (:entries, `arg` the tier), and \\ft."
  (concat [[:text] [:divided "text"] [:entries "translit"] [:divided "translit"] [:unsupported]]
          (for [k (concat tiers/transcriptions ["glmorph" "glword" "pos"])] [:entries k])
          [[:ft]]))

(defn- block-lines
  "An IGT's source lines for one of the `written-order`, none for a tier it
  doesn't have: the \\morph and \\syl that divide a text tier are indented
  under it. `has` is the rows the IGT has something in."
  [igt has [kind arg]]
  (case kind
    :text [(str "\\text " (text-tier igt))]
    :divided (map #(str tiers/segmentation-indent %)
                  (concat (when-let [es (morph-entries igt arg)]
                            [(str "\\" (model/segmentation-tier "morph" arg) " " (str/join " " es))])
                          (let [k (model/segmentation-tier "syl" arg)]
                            (when-let [v (or (get-in igt [:raw k :text]) (syllables igt arg))]
                              [(str "\\" k " " v)]))))
    :entries (when (has arg) [(tier-field igt arg)])
    :unsupported (map unsupported-field (:unsupported igt))
    :ft (when-not (str/blank? (:free igt)) [(str "\\ft " (prose (:free igt)))])))

(defn igt-block
  "The source block for one IGT, with the notes written under it: \\scan
  first, as the marks are drawn above the text, then its tiers in
  `written-order`."
  [igt]
  (let [has (model/igt-rows igt)
        scan-of #(when (and (seq (:syllables %)) (every? :weight (:syllables %))) [(str "\\scan " (scan-tier %))])]
    (str/join
     "\n"
     (concat
      [(str "\\igt" (when (:label igt) (str " " (:label igt))))]
      (if-let [r (get-in igt [:raw "scan"])]
        [(str "\\scan " (:text r))]
        (scan-of (some #(model/prosody igt %) model/surfaces)))
      (mapcat #(block-lines igt has %) written-order)
      (map note-source (:notes igt))))))

(defn- grid-rows
  "An IGT's rows on a grid (`igt-grid`) for one of the `written-order`."
  [igt has [kind arg]]
  (let [words (:words igt)
        cells (fn [texts] (for [t texts] {:text t :span 1}))]
    (case kind
      :text [["text" (cells (map model/word-text words))]]
      :divided (concat (when-let [es (morph-entries igt arg)]
                         [[(model/segmentation-tier "morph" arg) (cells es)]])
                       (when-let [v (syllables igt arg)]
                         [[(model/segmentation-tier "syl" arg)
                           (for [t (str/split v #" ")] {:text t :span (inc (count (re-seq #"‿" t)))})]]))
      :entries (when (has arg) [[(tiers/command arg) (cells (map #(entry arg %) words))]])
      :unsupported nil
      :ft (when-not (str/blank? (:free igt)) [["ft" [{:text (:free igt) :span (count words)}]]]))))

(defn igt-grid
  "An IGT's tiers as its source writes them (`written-order`), word by word,
  for a figure with a row for each tier and a column for each word:
  [[command cells] …], each cell {:text :span}, `span` being how many words
  it covers. A \\syl syllable that joins two words (‿) covers both, and \\ft
  covers them all. \\scan, the reserved fields and the notes, which aren't
  written word by word, are left out."
  [igt]
  (let [has (model/igt-rows igt)]
    (mapcat #(grid-rows igt has %) written-order)))

(defn- section-heading
  "A section's \\section line."
  [{:keys [label title]}]
  (str "\\section" (when label (str "[" label "]")) (when-not (str/blank? title) (str " " title))))

(defn document-source
  "A whole document as source text: the header, then the IGTs under their
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
                (when (seq (:order doc)) [(str "\\order " (str/join " " (map tiers/command (:order doc))))])
                (map unsupported-field (:unsupported doc))
                (for [[tag meaning] (sort (:abbrevs doc))]
                  (str "\\abbr " tag " " meaning))
                (for [{:keys [tag color label]} (:colors doc)]
                  (str "\\color " tag " " color (when label (str " " label)))))
        ;; IGTs in order, each section's heading before its first IGT; a
        ;; section without IGTs keeps its place.
        [blocks next-section]
        (reduce (fn [[out k] igt]
                  (let [to (when-let [s (:section igt)] (position s))
                        [out k] (if (and to (>= to k))
                                  [(into out (map section-heading (subvec sections k (inc to)))) (inc to)]
                                  [out k])]
                    [(conj out (igt-block igt)) k]))
                [[] 0]
                (:igts doc))
        blocks (into blocks (map section-heading (drop next-section sections)))
        back (when (seq (:notes doc))
               (cons "\\end" (map note-source (:notes doc))))]
    (str (str/join "\n" header) "\n\n" (str/join "\n\n" blocks)
         (when back (str "\n\n" (str/join "\n" back)))
         "\n")))
