(ns gloss.check
  "Problems found once a document exists, such as a gloss with more
  morphemes than its word. The parser reports the problems of each tier as
  written (gloss.parse); the checks here need the parsed document."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.english :as english]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.notes :as notes]
            [gloss.tiers :as tiers]
            [gloss.vocab :as vocab]))

;; ---------------------------------------------------------------------------
;; Checks of one word
;;
;; Each check takes the document's abbreviations and a word, and returns
;; problems {:level :msg :field}, :field naming the source tier (such as
;; "glmorph").

(defn- morph-problems
  "The problems found dividing the word with each surface's \\morph entry."
  [_ word]
  (for [s model/surfaces
        {:keys [level msg]} (get-in word [:surfaces s :morph-problems])]
    {:level level :msg msg :field (model/segmentation-tier "morph" s)}))

(defn- surface-agreement
  "Where both surfaces have a \\morph entry, they must divide the word alike."
  [_ word]
  (when (every? #(model/morph-src word %) model/surfaces)
    (let [[ms ts] (map #(model/segs word %) model/surfaces)
          shape (fn [ss] (mapv (juxt :sep :zero? :infix? :cont?) ss))
          ;; Marking in only one tier is fine; in both, it must agree.
          differ? (fn [k] (and (some k ms) (some k ts)
                               (not= (mapv (comp boolean k) ms) (mapv (comp boolean k) ts))))
          msg (cond
                (not= (shape ms) (shape ts))
                "\\morph and \\morph[translit] must have the same morphemes, with the same separators in the same order."
                (differ? :stem?) "\\morph and \\morph[translit] mark stems on different morphemes."
                (differ? :root) "\\morph and \\morph[translit] mark roots in different morphemes.")]
      (when msg
        [{:level :error :msg (str "“" (model/word-text word) "”: " msg) :field "morph[translit]"}]))))

(defn- gloss-count
  "A morphemic gloss has a gloss morpheme for each morpheme."
  [_ word]
  (let [n (model/morph-count word), gs (:glmorph word)]
    (when (and (seq gs) (not= n (count gs)))
      [{:level :warning :field "glmorph"
        :msg (if (model/morph-structure word)
               (str "“" (model/word-text word) "” has " (english/counted n "morpheme" "morphemes") " but its gloss “"
                    (morph/join-segments gs) "” has " (count gs) ".")
               (str "The gloss “" (morph/join-segments gs) "” has " (count gs) " morphemes, but “" (model/word-text word)
                    "” has no \\morph entry saying what they are. Add a \\morph tier."))}])))

(defn- gloss-separators
  "Each gloss morpheme has its morpheme's separator, and an infix's gloss
  goes with an infix."
  [_ word]
  (let [ms (model/morph-structure word), gs (:glmorph word)]
    (when (and ms (= (count (morph/morphemes ms)) (count gs)))
      (let [pairs (map vector gs (morph/gloss-segments ms gs))]
        (when (some (fn [[g js]] (or (nil? js) (and (not (:infix? g)) (not= (:sep (ms (first js))) (:sep g)))))
                    pairs)
          [{:level :warning :field "glmorph"
            :msg (str "Separator mismatch in “" (morph/join-segments ms) "” / “" (morph/join-segments gs) "”"
                      (when (some (comp nil? second) pairs)
                        ": an infix's gloss is written in angle brackets, <…>, and only for an infix")
                      ".")}])))))

(defn- unknown-abbreviations
  "Every glossing abbreviation in the gloss is in the document's vocabulary."
  [v word]
  (distinct
   (for [g (:glmorph word)
         el (vocab/gloss-elements (:text g))
         :when (and (vocab/grammatical? el) (nil? (vocab/lookup v el)))]
     {:level :warning :msg (str "Unknown abbreviation " el ".") :field "glmorph"})))

(defn- syntactic-words
  "Number of syntactic words in a word, its parts between clitic
  boundaries (rīdet=ne -> 2), by its \\morph; each has its own part of
  speech."
  [word]
  (inc (count (filter #(= "=" (:sep %)) (model/morph-structure word)))))

(defn- pos-count
  "A part of speech for each syntactic word."
  [_ word]
  (let [parts (vocab/pos-parts (:pos word))
        n (syntactic-words word)]
    (when (and (seq parts) (not= (count parts) n))
      [{:level :warning :field "pos"
        :msg (if (model/morph-structure word)
               (str "“" (model/word-text word) "” has " (english/counted n "syntactic word" "syntactic words")
                    " but its part of speech “" (:pos word) "” has " (count parts) "; separate them with =.")
               (str "The part of speech “" (:pos word) "” has clitics (=), but “" (model/word-text word)
                    "” has no \\morph entry saying where they are. Add a \\morph tier."))}])))

(defn- unknown-pos
  "Every part of speech and subclass is one the vocabulary knows."
  [_ word]
  (distinct
   (for [part (vocab/pos-parts (:pos word))
         {:keys [text entry]} (cons (:class part) (:subs part))
         :when (nil? entry)]
     {:level :warning :msg (str "Unknown part of speech “" text "”.") :field "pos"})))

(def ^:private word-checks
  "The checks run on every word, in the order their problems are listed."
  [morph-problems surface-agreement gloss-count gloss-separators
   unknown-abbreviations pos-count unknown-pos])

;; ---------------------------------------------------------------------------
;; Checks of an IGT's syllables
;;
;; Each surface's \syl tier is checked against its letters as it is read
;; (gloss.parse.prosody). Where both surfaces have syllables, the two tiers
;; must also agree with each other, as their \morph tiers must
;; (`surface-agreement`).

(defn- word-places
  "Where each word but the first begins among a surface's syllables:
  [:with j] when syllable j begins with it, [:inside j] when it begins
  inside syllable j (a syllable it shares with the word before), or nil
  for a word without letters. `letters` are the surface's, `ranges` its
  words' (gloss.model/surface-ranges), and the syllables are placed on
  `letters`."
  [letters ranges syllables]
  (let [cl (chars/letters-of letters)
        n (count cl)
        at #(chars/letter-index cl %)
        sylls (map-indexed (fn [j {:keys [start end]}] [j (at start) (at end)]) syllables)]
    (vec (for [{:keys [start end]} (rest ranges)
               :let [k (at start)]]
           (when (and (< 0 k n) (< k (at end)))
             (some (fn [[j a b]] (cond (and (= a k) (< a b)) [:with j] (< a k b) [:inside j])) sylls))))))

(defn- word-start-problem
  "Where an IGT's two syllable tiers, which have as many syllables, first
  begin a word at different syllables, as a warning, or nil."
  [igt]
  (let [places (for [s model/surfaces]
                 (word-places (model/surface-letters igt s) (model/surface-ranges igt s)
                              (:syllables (model/prosody igt s))))
        i (first (filter (fn [i] (let [[a b] (map #(nth % i) places)] (and a b (not= a b))))
                         (range (apply min (map count places)))))]
    (when i
      (let [[s1 s2] model/surfaces
            tier #(str "\\" (model/segmentation-tier "syl" %))
            word (fn [s] (let [{:keys [start end]} (nth (model/surface-ranges igt s) (inc i))]
                           (str "“" (subs (model/surface-letters igt s) start end) "”")))
            where (fn [[kind j]] (str (if (= :with kind) "with" "inside") " syllable " (inc j)))
            [a1 a2] (map #(nth % i) places)]
        {:level :warning
         :msg (str (tier s1) " and " (tier s2) " divide the words at different syllables: " (word s1)
                   " begins " (where a1) " in " (tier s1) ", but " (word s2) " begins " (where a2)
                   " in " (tier s2) ".")}))))

(defn- syllable-agreement
  "Where both surfaces have syllables, they must be as many, and begin each
  word at the same syllable. Problems point at \\syl[translit]."
  [igt]
  (when (every? #(model/prosody igt %) model/surfaces)
    (let [counts (map #(count (:syllables (model/prosody igt %))) model/surfaces)]
      (some-> (if (apply not= counts)
                {:level :error
                 :msg (str (str/join " but " (map (fn [s n] (str "\\" (model/segmentation-tier "syl" s) " has "
                                                                 (english/counted n "syllable" "syllables")))
                                                  model/surfaces counts))
                           ".")}
                (word-start-problem igt))
              (assoc :field (model/segmentation-tier "syl" "translit"))
              vector))))

;; ---------------------------------------------------------------------------
;; IGTs and documents

(defn check-igt
  "Persistent problems with an IGT: those of its words (`word-checks`), of
  its two syllable tiers together (`syllable-agreement`), its notes', and
  scansion that could not be placed."
  [doc igt]
  (let [v (vocab/document-vocab doc)]
    (concat
     (for [check word-checks
           [i word] (map-indexed vector (:words igt))
           d (check v word)]
       (assoc d :igt (:id igt) :word i))
     (for [d (syllable-agreement igt)]
       (assoc d :igt (:id igt)))
     (filter #(= (:id igt) (:igt %)) (:problems (notes/note-index doc)))
     ;; Scansion that can't be placed keeps \scan and its \syl as typed,
     ;; for one reason: it is listed once.
     (for [[reason raws] (group-by (comp :reason val) (:raw igt))]
       {:level :error :igt (:id igt) :raw? true :field (key (first raws))
        :msg (str "Scansion is kept as typed but not shown: " reason)}))))

(defn with-source-lines
  "`diags` with the source line each one concerns (:source-line). Parse
  problems carry theirs; a check names a tier of an IGT (:field), which is
  looked up in the parse's `source-lines`, or else the IGT's first source
  line."
  [diags source-lines]
  (for [d diags]
    (assoc d :source-line (or (:source-line d)
                              (get-in source-lines [(:igt d) (:field d)])
                              (get-in source-lines [(:igt d) "igt"])))))

(defn check-doc
  "Persistent problems with a whole document: its header's, every IGT's,
  and those of the notes written after \\end."
  [doc]
  (concat
   (for [k (:order doc) :when (not (some #{k} tiers/rows))]
     {:level :warning :msg (str "\\order lists “" k "”, which isn't a row of this document.")})
   (let [rs (model/doc-rows doc)]
     (when (and (rs "ipa") (or (rs "phonemic") (rs "phonetic")))
       [{:level :warning
         :msg "\\text[ipa] can't be used together with \\text[phonemic] or \\text[phonetic]: use \\text[ipa] alone, or the other two."}]))
   (mapcat #(check-igt doc %) (:igts doc))
   (remove :igt (:problems (notes/note-index doc)))))

(defn problems
  "Every problem with source text just read (`parsed`, as gloss.parse
  returns it): the parser's, then `checked`, the checks of what it read
  (by default the whole document's), each with its source line. Scansion
  that can't be placed is reported by the parser already, so its check
  isn't repeated."
  ([parsed] (problems parsed (check-doc (:doc parsed))))
  ([{:keys [diagnostics source-lines]} checked]
   (with-source-lines (concat diagnostics (remove :raw? checked)) source-lines)))
