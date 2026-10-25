(ns gloss.model
  "The document model: what a parsed document is, and what can be read off
  it. A document is plain data (docs/architecture.md, section 4.1, shows
  its shape); the functions here answer questions about it: a word's
  segments on each surface, the entries of the tiers with one for each
  word, the formatting a tier takes from the text, the order of the rows,
  and which kinds of content a document has."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.formatting :as fmt]
            [gloss.morph :as morph]
            [gloss.tiers :as tiers]
            [gloss.vocab :as vocab]))

(def format-version
  "The version of the input format this site writes, declared in a text's
  header as `\\glosshubformat 2` (a text without the line is version 1).
  It changes only when something already in the format changes meaning or
  stops being written; additions don't need a new version (docs/format.md
  §2). Version 2 writes the text tiers as \\text[translit] and the like,
  and starts each IGT with \\igt; the site reads version 1 too, and flags
  its commands only in a text of version 2."
  2)

;; ---------------------------------------------------------------------------
;; Surfaces
;;
;; \text and \text[translit] are an IGT's two surfaces: the text tiers
;; that segmentation tiers divide. Each has the same segmentation tiers,
;; \morph and \syl (\morph[translit], \syl[translit]), each checked against
;; its own surface. A word keeps, per surface, its segments (the word as
;; written, divided by the surface's \morph entry, or whole), the \morph
;; entry as written and its problems; the IGT keeps each surface's
;; syllables:
;;
;;   word  :surfaces {"text" {:segs [...] :morph-src "Mūs-a" :morph-problems [...]}
;;                    "translit" {...}}
;;   igt   :prosody {"text" {:syllables ...} "translit" {...}}
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
  "A word's \\morph entry on surface `s`, as written (nil without one)."
  [word s]
  (get-in word [:surfaces s :morph-src]))

(defn segmentation-tier
  "The key of a segmentation tier (`morph`, `syl`) for surface `s`:
  \"morph\" for the text, \"morph[translit]\" for the transliteration."
  [tier s]
  (if (= s "text") tier (str tier "[" s "]")))

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
  [igt s]
  (apply str (map #(surface-word % s) (:words igt))))

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
  [igt s]
  (first
   (reduce (fn [[out pos] word]
             (let [r (segment-ranges (segs word s) pos)]
               [(conj out r) (:end r)]))
           [[] 0]
           (:words igt))))

(defn prosody
  "An IGT's syllables on surface `s` (nil without a \\syl tier for it)."
  [igt s]
  (get-in igt [:prosody s]))

(defn morph-structure
  "The segments that say what a word's morphemes are: those of the first
  surface with a \\morph entry (they agree when both have one), else nil
  (the word is one morpheme)."
  [word]
  (some #(when (morph-src word %) (segs word %)) surfaces))

(defn morph-count
  "How many morphemes a word has (each needs a gloss)."
  [word]
  (if-let [ms (morph-structure word)] (count (morph/morphemes ms)) 1))

;; ---------------------------------------------------------------------------
;; Word entries
;;
;; The tiers with an entry per word (gloss.tiers/word-entries) are stored in
;; the word under the tier's name:
;;
;;   :glmorph   the gloss morphemes [{:text :sep :infix?}]
;;   :glword    the informal gloss, with its own formatting in :glword-fmt
;;   :pos       the part of speech as written
;;   :ipa, :phonemic, :phonetic   raw IPA
;;
;; `read-entry` reads an entry, for the parser and the cell editor alike;
;; gloss.serialize/entry writes one out.

(defn read-entry
  "Reads an entry of tier `tier` (escapes hidden), ignoring the punctuation
  around it except in a transcription. Returns [fields problems]: the fields
  to set on the word (nil removes one, see `put-fields`) and messages."
  [tier entry]
  (case tier
    "glmorph" (let [t (chars/strip-punct entry)]
                [{:glmorph (when (not= t "") (morph/split-gloss t))} []])
    "glword" (let [{:keys [text problems] runs :fmt} (fmt/read-glword (chars/strip-punct entry))]
               [{:glword (not-empty text) :glword-fmt (not-empty runs)} problems])
    "pos" [{:pos (not-empty (str/trim (chars/strip-punct entry)))} []]
    [{(keyword tier) (not-empty (str/trim entry))} []]))

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

(defn igt-rows
  "The rows under the words that an IGT has something in."
  [igt]
  (set (for [row (remove #{"text"} tiers/rows)
             :when (some #(has-row? % row) (:words igt))]
         row)))

(defn doc-rows
  "The rows under the words that a document has something in."
  [doc]
  (apply set/union #{} (map igt-rows (:igts doc))))

;; ---------------------------------------------------------------------------
;; Formatting carried from the text
;;
;; Which tiers take the text's formatting is in gloss.tiers (:formatting).

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
  "The formatting tier `tier` takes from \\text at `word`, as gloss.tiers
  says (:from-text, :own+from-text; otherwise none): the styles of the
  whole word, and, given `js`, the indices of the text's \\morph segments
  that a part of the tier stands for (a gloss morpheme, a morpheme of
  \\morph[translit]), the styles common to them."
  [tier word & [js]]
  (if-not (#{:from-text :own+from-text} (tiers/formatting tier))
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
;; Row order
;;
;; The rows under each word are drawn in an order the document can set with
;; `\order` (by tier name, any of its names; stored as standard names).
;; Rows it doesn't list keep their default place after the listed ones.

(defn row-order
  "The order of a document's rows: the reader's own order for this text (a
  display setting) if they set one, else the document's \\order, then the
  built-in defaults for rows neither lists."
  ([doc] (row-order doc nil))
  ([doc reader-order]
   (let [listed (filter (set tiers/rows) (if (seq reader-order) reader-order (:order doc)))]
     (vec (distinct (concat listed tiers/rows))))))

(defn present-rows
  "The document's rows that have content, in the order shown."
  [doc reader-order]
  (filterv (conj (doc-rows doc) "text") (row-order doc reader-order)))

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
  (let [igts (:igts doc)
        words (mapcat :words igts)]
    (cond-> (set (map row-setting (doc-rows doc)))
      (some #(or (seq (:prosody %)) (seq (:raw %))) igts) (conj :scansion)
      (some #(seq (:ruby %)) words) (conj :ruby)
      (some (fn [w] (some #(some vocab/grammatical? (vocab/gloss-elements (:text %))) (:glmorph w))) words)
      (conj :gram)
      (some (fn [w] (some #(> (count (segs w %)) 1) surfaces)) words) (conj :morph-breaks)
      (some (fn [w] (some #(some :stem? (segs w %)) surfaces)) words) (conj :stems)
      (some (fn [w] (some #(some :root (segs w %)) surfaces)) words) (conj :roots)
      (seq (:colors doc)) (conj :color)
      (some #(not (str/blank? (:free %))) igts) (conj :free)
      (or (seq (:notes doc)) (some #(seq (:notes %)) igts)) (conj :notes)
      (some #(seq (:fmt %)) words) (conj :fmt))))

;; ---------------------------------------------------------------------------
;; IGTs

(defn igt-index
  "The position of IGT `igt-id` among the document's IGTs."
  [doc igt-id]
  (first (keep-indexed (fn [i l] (when (= igt-id (:id l)) i)) (:igts doc))))

(defn igt-labels
  "Each IGT's label: its own, or its number within its section."
  [doc]
  (first (reduce (fn [[out counts] igt]
                   (let [n (inc (get counts (:section igt) 0))]
                     [(assoc out (:id igt) (or (:label igt) (str n)))
                      (assoc counts (:section igt) n)]))
                 [{} {}]
                 (:igts doc))))

(defn section-starts
  "The sections, by the id of the IGT each starts with: {igt-id
  section}."
  [doc]
  (let [by-id (into {} (map (juxt :id identity) (:sections doc)))]
    (into {} (for [[prev igt] (map vector (cons nil (:igts doc)) (:igts doc))
                   :when (and (:section igt) (not= (:section igt) (:section prev)))]
               [(:id igt) (by-id (:section igt))]))))

(defn without-ids
  "A document without its generated ids (an IGT's section given by the
  section's label instead), to compare two readings of the same text: the
  ids are new at every reading."
  [doc]
  (let [label (into {} (map (juxt :id :label) (:sections doc)))]
    (cond-> (update doc :igts (fn [igts] (mapv #(cond-> (dissoc % :id) (:section %) (update :section label)) igts)))
      (:sections doc) (update :sections (fn [ss] (mapv #(dissoc % :id) ss))))))
