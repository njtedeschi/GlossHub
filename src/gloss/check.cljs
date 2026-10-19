(ns gloss.check
  "Problems found once a document exists, such as a gloss with more
  morphemes than its word. The parser reports the problems of each row as
  written (gloss.parse); the checks here need the parsed document."
  (:require [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.notes :as notes]
            [gloss.rows :as rows]
            [gloss.vocab :as vocab]))

;; ---------------------------------------------------------------------------
;; Checks of one word
;;
;; Each check takes the document's abbreviations and a word, and returns
;; problems {:level :msg :field}, :field naming the source row (such as
;; "glmorph").

(defn- morph-problems
  "The problems found dividing the word with each surface's \\morph item."
  [_ word]
  (for [s model/surfaces
        {:keys [level msg]} (get-in word [:surfaces s :morph-problems])]
    {:level level :msg msg :field (model/analysis-row "morph" s)}))

(defn- surface-agreement
  "Where both surfaces have a \\morph item, they must divide the word alike."
  [_ word]
  (when (every? #(model/morph-src word %) model/surfaces)
    (let [[ms ts] (map #(model/segs word %) model/surfaces)
          shape (fn [ss] (mapv (juxt :sep :zero? :infix? :cont?) ss))
          ;; Marking in only one row is fine; in both, it must agree.
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
               (str "“" (model/word-text word) "” has " n " morpheme(s) but its gloss “"
                    (morph/join-segments gs) "” has " (count gs) ".")
               (str "The gloss “" (morph/join-segments gs) "” has " (count gs) " morphemes, but “" (model/word-text word)
                    "” has no \\morph item saying what they are. Add a \\morph row."))}])))

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
  "Every grammatical label in the gloss is in the document's abbreviations."
  [v word]
  (distinct
   (for [g (:glmorph word)
         el (vocab/gloss-elements (:text g))
         :when (and (vocab/grammatical? el) (nil? (vocab/lookup v el)))]
     {:level :warning :msg (str "Unknown abbreviation " el ".") :field "glmorph"})))

(defn- clitic-groups
  "Number of clitic-separated parts of a word (rīdet=ne -> 2), by its
  \\morph; each can carry its own part of speech."
  [word]
  (inc (count (filter #(= "=" (:sep %)) (model/morph-structure word)))))

(defn- pos-groups
  "A part of speech for each clitic group."
  [_ word]
  (let [parts (vocab/pos-parts (:pos word))
        groups (clitic-groups word)]
    (when (and (seq parts) (not= (count parts) groups))
      [{:level :warning :field "pos"
        :msg (if (model/morph-structure word)
               (str "“" (model/word-text word) "” has " groups " clitic group(s) but its part of speech “"
                    (:pos word) "” has " (count parts) "; separate them with =.")
               (str "The part of speech “" (:pos word) "” has clitics (=), but “" (model/word-text word)
                    "” has no \\morph item saying where they are. Add a \\morph row."))}])))

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
   unknown-abbreviations pos-groups unknown-pos])

;; ---------------------------------------------------------------------------
;; Lines and documents

(defn check-line
  "Persistent problems with a line: those of its words (`word-checks`), its
  notes', and scansion that could not be placed."
  [doc line]
  (let [v (vocab/document-vocab doc)]
    (concat
     (for [check word-checks
           [i word] (map-indexed vector (:words line))
           d (check v word)]
       (assoc d :line (:id line) :word i))
     (filter #(= (:id line) (:line %)) (:problems (notes/note-index doc)))
     (for [[k raw] (:raw line)]
       {:level :error :line (:id line) :raw? true :field k
        :msg (str "Scansion is kept as typed but not shown: " (:reason raw))}))))

(defn with-source-lines
  "`diags` with the source line each one concerns (:source-line). Parse
  problems carry theirs; a check names a row of a line (:field), which is
  looked up in the parse's `source-lines` (falling back to the line's
  \\ex)."
  [diags source-lines]
  (for [d diags]
    (assoc d :source-line (or (:source-line d)
                              (get-in source-lines [(:line d) (:field d)])
                              (get-in source-lines [(:line d) "ex"])))))

(defn check-doc
  "Persistent problems with a whole document: its header's, every line's,
  and those of the notes written after \\end."
  [doc]
  (concat
   (for [k (:order doc) :when (not (some #{k} rows/layers))]
     {:level :warning :msg (str "\\order lists “" k "”, which isn't a layer of this document.")})
   (let [rs (model/doc-rows doc)]
     (when (and (rs "ipa") (or (rs "phonemic") (rs "phonetic")))
       [{:level :warning
         :msg "\\ipa can't be used together with \\phonemic or \\phonetic: use \\ipa alone, or the other two."}]))
   (mapcat #(check-line doc %) (:lines doc))
   (remove :line (:problems (notes/note-index doc)))))
