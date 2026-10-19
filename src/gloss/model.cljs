(ns gloss.model
  "The document model: what a parsed document is, and what can be read off
  it. A document is plain data (docs/design.md shows its shape); the
  functions here answer questions about it: a word's segments on each
  surface, the items of the rows under the words, the formatting a row
  takes from the text, the order of the layers, and which kinds of content
  a document has."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.formatting :as fmt]
            [gloss.morph :as morph]
            [gloss.rows :as rows]
            [gloss.vocab :as vocab]))

(def format-version
  "The version of the input format this site reads and writes, declared in
  a text's header as `\\glosshubformat 1` (a text without the line is
  version 1). It changes only when something already in the format changes
  meaning; additions don't need a new version (docs/format.md §2)."
  1)

;; ---------------------------------------------------------------------------
;; Surfaces
;;
;; \text and \translit are the line's two surfaces: rows written as the
;; words are, which the other rows analyse or gloss. Each has the same
;; analysis rows, \morph and \syl (\morph[translit], \syl[translit]), each
;; checked against its own surface. A word keeps, per surface, its segments
;; (the word as written, divided by the surface's \morph item, or whole),
;; the \morph item as written and its problems; the line keeps each
;; surface's syllables:
;;
;;   word  :surfaces {"text" {:segs [...] :morph-src "Mūs-a" :morph-problems [...]}
;;                    "translit" {...}}
;;   line  :prosody {"text" {:syllables ...} "translit" {...}}
;;
;; Formatting, ruby, links and punctuation belong to \text alone.

(def surfaces
  "The surfaces, \\text first."
  ["text" "translit"])

(defn segs
  "A word's segments on surface `s`."
  [word s]
  (get-in word [:surfaces s :segs]))

(defn morph-src
  "A word's \\morph item on surface `s`, as written (nil without one)."
  [word s]
  (get-in word [:surfaces s :morph-src]))

(defn analysis-row
  "The name of an analysis row (`morph`, `syl`) for surface `s`:
  \"morph\" for the text, \"morph[translit]\" for the transliteration."
  [row s]
  (if (= s "text") row (str row "[" s "]")))

(defn surface-word
  "A word on surface `s` as written, without the text's edge punctuation."
  [word s]
  (morph/segments-text (segs word s)))

(defn word-letters
  "A word of \\text as written, without its edge punctuation."
  [word]
  (surface-word word "text"))

(defn word-text
  "A word of \\text as written, with its punctuation."
  [word]
  (str (:pre word) (word-letters word) (:post word)))

(defn surface-letters
  "A surface's words as written, run together (no spaces or edge
  punctuation). Its syllables, and the text's ruby and morpheme boundaries,
  are offsets into it."
  [line s]
  (apply str (map #(surface-word % s) (:words line))))

(defn segment-ranges
  "Ranges of a word's segments from `pos`: {:start :end :morphs [[s e] …]
  :joints [[s e] …]}, where a morpheme's range is its text and joints are
  the hyphens standing at boundaries."
  [segs pos]
  (let [[morphs joints end]
        (reduce (fn [[ms js p] m]
                  (let [jl (count (:joint m))
                        s (+ p jl)
                        e (+ s (count (:text m)))]
                    [(conj ms [s e]) (if (pos? jl) (conj js [p s]) js) e]))
                [[] [] pos]
                segs)]
    {:start pos :end end :morphs morphs :joints joints}))

(defn surface-ranges
  "Letter ranges of each word on surface `s`, with its morphemes and joints."
  [line s]
  (first
   (reduce (fn [[out pos] word]
             (let [r (segment-ranges (segs word s) pos)]
               [(conj out r) (:end r)]))
           [[] 0]
           (:words line))))

(defn prosody
  "A line's syllables on surface `s` (nil without a \\syl row for it)."
  [line s]
  (get-in line [:prosody s]))

(defn morph-structure
  "The segments that say what a word's morphemes are: those of the first
  surface with a \\morph item (they agree when both have one), else nil
  (the word is one morpheme)."
  [word]
  (some #(when (morph-src word %) (segs word %)) surfaces))

(defn morph-count
  "How many morphemes a word has (each needs a gloss)."
  [word]
  (if-let [ms (morph-structure word)] (count (morph/morphemes ms)) 1))

;; ---------------------------------------------------------------------------
;; Word items
;;
;; The rows with an item per word (gloss.rows/word-items) are stored in the
;; word under the row's name:
;;
;;   :glmorph   the gloss morphemes [{:text :sep :infix?}]
;;   :glword    the informal gloss, with its own formatting in :glword-fmt
;;   :pos       the part of speech as written
;;   :ipa, :phonemic, :phonetic   raw IPA
;;
;; `read-item` reads an item, for the parser and the cell editor alike;
;; gloss.serialize/item writes one out.

(defn read-item
  "Reads an item of row `row` (escapes hidden), ignoring the punctuation
  around it except in a transcription. Returns [fields problems]: the fields
  to set on the word (nil removes one, see `put-fields`) and messages."
  [row item]
  (case row
    "glmorph" (let [t (chars/strip-punct item)]
                [{:glmorph (when (not= t "") (morph/split-gloss t))} []])
    "glword" (let [{:keys [text problems] runs :fmt} (fmt/read-cell (chars/strip-punct item))]
               [{:glword (not-empty text) :glword-fmt (not-empty runs)} problems])
    "pos" [{:pos (not-empty (str/trim (chars/strip-punct item)))} []]
    [{(keyword row) (not-empty (str/trim item))} []]))

(defn put-fields
  "A word with `fields` set; a nil value removes the field."
  [word fields]
  (reduce-kv (fn [w k v] (if (nil? v) (dissoc w k) (assoc w k v))) word fields))

(defn has-row?
  "Whether a word has something in row `row`, a row under the words."
  [word row]
  (if (= row "translit")
    (boolean (seq (segs word "translit")))
    (let [v (get word (keyword row))]
      (if (string? v) (not (str/blank? v)) (boolean (seq v))))))

(defn line-rows
  "The rows under the words that a line has something in."
  [line]
  (set (for [row (remove #{"text"} rows/layers)
             :when (some #(has-row? % row) (:words line))]
         row)))

(defn doc-rows
  "The rows under the words that a document has something in."
  [doc]
  (apply set/union #{} (map line-rows (:lines doc))))

;; ---------------------------------------------------------------------------
;; Formatting carried from the text
;;
;; Which rows take the text's formatting is in gloss.rows (:formatting).

(defn word-styles
  "The formatting of a whole \\text word: the styles every letter of it has."
  [word]
  (let [core (word-letters word)]
    (fmt/common-styles (:fmt word) (map :start (chars/letters-of core)))))

(defn morph-styles
  "The formatting covering each whole morpheme of a word's \\morph (by
  position; empty without a \\morph): a vector of style sets."
  [word]
  (if-not (morph-src word "text")
    []
    (let [core (word-letters word)
          letters (map :start (chars/letters-of core))]
      (vec (for [[s e] (:morphs (segment-ranges (segs word "text") 0))]
             (fmt/common-styles (:fmt word) (filter #(and (<= s %) (< % e)) letters)))))))

(defn styles-from-text
  "The formatting row `row` takes from \\text at `word`, as gloss.rows says
  (:from-text, :own+from-text; otherwise none): the styles of the whole
  word, and, given `js`, the indices of the text's \\morph segments that a
  part of the row stands for (a gloss morpheme, a morpheme of
  \\morph[translit]), the styles common to them."
  [row word & [js]]
  (if-not (#{:from-text :own+from-text} (rows/formatting row))
    #{}
    (let [per (morph-styles word)
          part (when (seq js) (reduce (fn [a b] (set (filter b a))) (map #(get per % #{}) js)))]
      (into (word-styles word) part))))

;; ---------------------------------------------------------------------------
;; Color rules

(defn morph-color
  "The color rule applying to a morpheme gloss, if any (first rule wins)."
  [doc gloss-text]
  (when-let [elements (seq (vocab/gloss-elements gloss-text))]
    (let [tags (set (map str/upper-case elements))]
      (first (filter #(tags (str/upper-case (:tag %))) (:colors doc))))))

;; ---------------------------------------------------------------------------
;; Layer order
;;
;; The rows under each word are drawn in an order the document can set with
;; `\order` (by row name, any of its names; stored as standard names).
;; Layers it doesn't list keep their default place after the listed ones.

(defn layer-order
  "The order of a document's layers: the reader's own order for this text (a
  display setting) if they set one, else the document's \\order, then the
  built-in defaults for layers neither lists."
  ([doc] (layer-order doc nil))
  ([doc reader-order]
   (let [listed (filter (set rows/layers) (if (seq reader-order) reader-order (:order doc)))]
     (vec (distinct (concat listed rows/layers))))))

(defn present-layers
  "The document's layers that have content, in the order shown."
  [doc reader-order]
  (filterv (conj (doc-rows doc) "text") (layer-order doc reader-order)))

;; ---------------------------------------------------------------------------
;; What a document contains

(def row-setting
  "The display setting (gloss.app.state/default-view) that shows each row
  under the words."
  {"translit" :translit "ipa" :ipa "phonemic" :phonemic "phonetic" :phonetic
   "glmorph" :gloss "glword" :wgloss "pos" :pos})

(defn doc-features
  "The kinds of content a document has, so the display settings can offer
  only what applies: #{:scansion :ruby :translit :phonemic :phonetic :ipa
  :gloss :gram :pos :wgloss :morph-breaks :stems :roots :color :free :notes
  :fmt}."
  [doc]
  (let [lines (:lines doc)
        words (mapcat :words lines)]
    (cond-> (set (map row-setting (doc-rows doc)))
      (some #(or (seq (:prosody %)) (seq (:raw %))) lines) (conj :scansion)
      (some #(seq (:ruby %)) words) (conj :ruby)
      (some (fn [w] (some #(some vocab/grammatical? (vocab/gloss-elements (:text %))) (:glmorph w))) words)
      (conj :gram)
      (some (fn [w] (some #(> (count (segs w %)) 1) surfaces)) words) (conj :morph-breaks)
      (some (fn [w] (some #(some :stem? (segs w %)) surfaces)) words) (conj :stems)
      (some (fn [w] (some #(some :root (segs w %)) surfaces)) words) (conj :roots)
      (seq (:colors doc)) (conj :color)
      (some #(not (str/blank? (:free %))) lines) (conj :free)
      (or (seq (:notes doc)) (some #(seq (:notes %)) lines)) (conj :notes)
      (some #(seq (:fmt %)) words) (conj :fmt))))

;; ---------------------------------------------------------------------------
;; Lines

(defn line-index
  "The position of line `line-id` among the document's lines."
  [doc line-id]
  (first (keep-indexed (fn [i l] (when (= line-id (:id l)) i)) (:lines doc))))

(defn line-labels
  "Each line's label: its own, or its number within its section."
  [doc]
  (first (reduce (fn [[out counts] line]
                   (let [n (inc (get counts (:section line) 0))]
                     [(assoc out (:id line) (or (:label line) (str n)))
                      (assoc counts (:section line) n)]))
                 [{} {}]
                 (:lines doc))))

(defn section-starts
  "The sections, by the id of the line each starts with: {line-id
  section}."
  [doc]
  (let [by-id (into {} (map (juxt :id identity) (:sections doc)))]
    (into {} (for [[prev line] (map vector (cons nil (:lines doc)) (:lines doc))
                   :when (and (:section line) (not= (:section line) (:section prev)))]
               [(:id line) (by-id (:section line))]))))
