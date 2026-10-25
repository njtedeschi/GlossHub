(ns gloss.parse.prosody
  "Reading \\scan and \\syl, and placing the syllables on the letters of
  the surface they syllabify (docs/format.md §5.4 and §5.5)."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.model :as model]
            [gloss.tiers :as tiers]))

;; ---------------------------------------------------------------------------
;; \scan and \syl

(defn parse-scan
  "Reads a \\scan tier, mark by mark: long _ – — ¯ ; short u ˘ ∪ ; anceps
  x × ; a foot starts at ' or | ; a caesura is || or ‖. Returns {:weights
  [:long …] :feet :caesurae :bad}: feet and caesurae as the indices of the
  syllables they come before, and the characters that are no mark."
  [s]
  (loop [cs (seq s), weights [], feet #{}, caesurae #{}, bad []]
    (if-let [c (first cs)]
      (let [n (count weights)]
        (cond
          (re-matches #"\s" c) (recur (rest cs) weights feet caesurae bad)
          (and (= c "|") (= (second cs) "|")) (recur (nnext cs) weights feet (conj caesurae n) bad)
          (= c "‖") (recur (rest cs) weights feet (conj caesurae n) bad)
          (#{"'" "|"} c) (recur (rest cs) weights (conj feet n) caesurae bad)
          (#{"_" "–" "—" "¯" "-"} c) (recur (rest cs) (conj weights :long) feet caesurae bad)
          (#{"u" "U" "˘" "∪" "◡"} c) (recur (rest cs) (conj weights :short) feet caesurae bad)
          (#{"x" "X" "×"} c) (recur (rest cs) (conj weights :anceps) feet caesurae bad)
          :else (recur (rest cs) weights feet caesurae (conj bad c))))
      {:weights weights
       :feet (disj feet 0)
       :caesurae (disj caesurae 0)
       :bad bad})))

(def ^:private syl-skip
  "Characters \\syl skips: punctuation, and reserved characters (reported)."
  (into (into chars/lead-punct chars/trail-punct) (disj tiers/ascii-punctuation "'")))

(defn- end-syllable
  "Ends the syllable being read, if it has letters; the next one starts
  here."
  [st]
  (let [n (count (:letters st))]
    (cond-> (assoc st :start n :joined? false)
      (> n (:start st)) (update :syllables conj {:start (:start st) :end n}))))

(defn- syl-step
  "Reads one character of a \\syl tier into the state: the letters so far,
  the syllables (letter ranges) and where the current one starts, the
  elided ranges and where an open one starts, the syllables that begin a
  word, where words divide (the offsets of the spaces between syllables and
  of the ‿ inside them), and whether reading is inside a syllable that
  joins words (‿)."
  [st c]
  (cond
    (#{"|" "‖"} c) (-> st end-syllable (assoc :bars? true))
    (#{"‿" "_"} c) (-> st (assoc :joined? true) (update :joins conj (count (:letters st))))
    (re-matches #"\s" c) (if (:joined? st)
                           st
                           (let [st (end-syllable st)]
                             (-> st
                                 (update :word-starts conj (count (:syllables st)))
                                 (update :spaces conj (count (:letters st))))))
    (= c "-") (end-syllable st)
    (= c "(") (assoc st :elided-from (count (:letters st)) :joined? false)
    (= c ")") (let [n (count (:letters st))
                    from (:elided-from st)]
                (cond-> (assoc st :elided-from nil :joined? false)
                  (and from (> n from)) (update :elided conj [from n])))
    (syl-skip c) st
    :else (-> st (update :letters str c) (assoc :joined? false))))

(defn parse-syl
  "Reads a syllable tier: `-` between syllables, a space between words,
  `‿` (or `_`) joining words within a syllable, `(…)` around elided
  letters. Feet and caesurae belong in \\scan: a `|`, `||` or `‖` here only
  separates syllables, and is reported. Returns {:letters :syllables
  :elided :word-starts :spaces :joins :problems}, :word-starts being the
  indices of the syllables that begin a new word, and :spaces and :joins
  the offsets in :letters where a space or a ‿ divides words."
  [s]
  (let [st (end-syllable (reduce syl-step
                                 {:letters "" :start 0 :syllables [] :elided [] :elided-from nil
                                  :joined? false :word-starts #{} :spaces #{} :joins #{} :bars? false}
                                 (map str (str/trim s))))]
    {:letters (:letters st)
     :syllables (:syllables st)
     :elided (:elided st)
     :word-starts (disj (:word-starts st) 0)
     :spaces (:spaces st)
     :joins (:joins st)
     :problems (when (:bars? st)
                 [{:level :warning
                   :msg "Feet and caesurae (| and ||) belong in \\scan; in \\syl they only separate syllables."}])}))

;; ---------------------------------------------------------------------------
;; Placing syllables on the text
;;
;; \syl and its surface are compared letter by letter, ignoring diacritics
;; and case (gloss.chars).

(defn- mismatch-message
  "Where a \\syl tier and its surface first differ, at letter `k`: the \\syl
  syllable there with its neighbours (hyphens and word spaces included),
  and as many letters of the surface from the same place. `syl` is the
  parsed \\syl tier and `syl-cl` its letters; `letters` are the surface's,
  `surface-cl` its letters and `word-starts` the offsets where its words
  start."
  [syl syl-cl letters surface-cl word-starts k syl-tier surface]
  (let [sylls (:syllables syl)
        at (if (< k (count syl-cl)) (:start (syl-cl k)) (count (:letters syl)))
        si (or (first (keep-indexed (fn [i {:keys [start end]}] (when (and (<= start at) (< at end)) i)) sylls))
               (dec (count sylls)))
        from (max 0 (dec si)), to (min (dec (count sylls)) (inc si))
        sep (fn [i] (if ((:word-starts syl) i) " " "-"))
        syl-text (apply str (for [i (range from (inc to))
                                  :let [{:keys [start end]} (sylls i)]]
                              (str (when (> i from) (sep i)) (subs (:letters syl) start end))))
        ;; The same stretch of the surface: as many letters as the \syl quote.
        first-cl (count (filter #(< (:start %) (:start (sylls from))) syl-cl))
        n-cl (count (filter #(and (<= (:start (sylls from)) (:start %)) (< (:start %) (:end (sylls to)))) syl-cl))
        surface-part (subvec surface-cl (min first-cl (count surface-cl)) (min (+ first-cl n-cl) (count surface-cl)))
        surface-text (apply str (map-indexed (fn [i {:keys [start end]}]
                                               (str (when (and (pos? i) (word-starts start)) " ")
                                                    (subs letters start end)))
                                             surface-part))
        letter (fn [cl s] (if (< k (count cl)) (str "“" (subs s (:start (cl k)) (:end (cl k))) "”") "nothing"))
        syl-tier (str "\\" syl-tier)
        surface (str "\\" (tiers/command surface))]
    (str "The " syl-tier " and " surface " tiers spell different letters: " syl-tier " has “" syl-text "” where "
         surface " has “" surface-text "” (" (letter syl-cl (:letters syl)) " vs " (letter surface-cl letters) ").")))

;; ---------------------------------------------------------------------------
;; Where the words divide
;;
;; A syllable tier divides its surface's words where the surface does: with
;; a space between syllables, or ‿ inside a syllable two words share. Places
;; are compared as letter indices (gloss.chars/letter-index), so the two
;; tiers' spellings needn't be the same length. Whether two syllable tiers
;; agree with each other is checked on the IGT (gloss.check).

(defn- word-division-problem
  "Where a \\syl tier and its surface first disagree about where the words
  divide, as a warning, or nil. The two spell the same letters. `syl` is
  the parsed \\syl tier and `syl-cl` its letters; `letters` are the
  surface's, `surface-cl` its letters and `word-starts` the offsets where
  its words start, in order."
  [syl syl-cl letters surface-cl word-starts syl-tier surface]
  (let [n (count surface-cl)
        inner (fn [cl ps] (set (filter #(< 0 % n) (map #(chars/letter-index cl %) ps))))
        bounds (inner surface-cl word-starts)
        spaces (inner syl-cl (:spaces syl))
        joins (inner syl-cl (:joins syl))
        k (first (filter #(not= (contains? bounds %) (or (contains? spaces %) (contains? joins %)))
                         (sort (distinct (concat bounds spaces joins)))))]
    (when k
      (let [;; The surface's words and the syllables, as ranges of letters.
            words (->> (concat word-starts [(count letters)])
                       (map #(chars/letter-index surface-cl %))
                       (partition 2 1)
                       (remove (fn [[a b]] (= a b))))
            quoted (fn [[a b]] (str "“" (subs letters (:start (surface-cl a)) (:end (surface-cl (dec b)))) "”"))
            sl (:letters syl)
            sylls (for [{:keys [start end]} (:syllables syl)
                        :let [a (chars/letter-index syl-cl start), b (chars/letter-index syl-cl end)]
                        :when (< a b)]
                    [a b start end])
            syl-text (fn [[_ _ s e]] (str "“" (subs sl s e) "”"))
            syl-tier (str "\\" syl-tier)
            surface (str "\\" (tiers/command surface))]
        {:level :warning
         :msg (if-not (bounds k)
                (let [w (quoted (first (filter (fn [[a b]] (< a k b)) words)))]
                  (if (spaces k)
                    (str syl-tier " has a space inside " w ", which " surface
                         " writes as one word: write - between its syllables.")
                    (str syl-tier " has ‿ inside " w ", which " surface
                         " writes as one word; ‿ joins two words in a syllable they share.")))
                (let [[a _ s e :as here] (first (filter (fn [[a b]] (and (<= a k) (< k b))) sylls))]
                  (str syl-tier " doesn't separate " (quoted (last (filter (fn [[_ b]] (= b k)) words)))
                       " from " (quoted (first (filter (fn [[a _]] (= a k)) words))) ", which " surface
                       " writes as two words: "
                       (if (= a k)
                         (str "put a space between " (syl-text (last (filter (fn [[_ b]] (<= b k)) sylls)))
                              " and " (syl-text here) ".")
                         (let [at (:start (syl-cl k))]
                           (str "write the syllable they share as “" (subs sl s at) "‿" (subs sl at e) "”."))))))}))))

(defn parse-prosody
  "Reads a \\syl tier for surface `s` (\"text\" or \"translit\"), with the
  IGT's \\scan if it has one, and places the syllables on the surface's
  `letters` (its words run together, starting at the offsets
  `word-starts`). The tiers are compared letter by letter, ignoring
  diacritics and case. Returns {:prosody p :diagnostics ds}, or, when the
  tiers can't be aligned, {:raw r :diagnostics ds}: the tiers kept as
  written, by tier (\"scan\", and \"syl\" or \"syl[translit]\")."
  [letters word-starts scan-src syl-src s]
  (let [syl-key (model/segmentation-tier "syl" s)
        fail (fn [reason]
               {:raw (cond-> {syl-key {:text syl-src :reason reason}}
                       scan-src (assoc "scan" {:text scan-src :reason reason}))
                :diagnostics [{:level :error :msg reason}]})
        syl (parse-syl syl-src)
        scan (when scan-src (parse-scan scan-src))
        syl-cl (chars/letters-of (:letters syl))
        surface-cl (chars/letters-of letters)
        k (chars/first-mismatch syl-cl surface-cl)
        ;; \syl letter offsets -> the surface's letter offsets, letter by letter.
        to-surface (into {(count (:letters syl)) (count letters)}
                         (map-indexed (fn [i c] [(:start c) (:start (get surface-cl i))]) syl-cl))
        place (fn [p] (get to-surface p p))]
    (cond
      (and k (empty? (:syllables syl)))
      (fail "The \\syl tier has no syllables.")

      k
      (fail (mismatch-message syl syl-cl letters surface-cl (set word-starts) k syl-key s))

      (and scan (seq (:bad scan)))
      (fail (str "Unrecognized scansion marks: " (str/join " " (distinct (:bad scan))) "."))

      (and scan (not= (count (:weights scan)) (count (:syllables syl))))
      (fail (str "\\scan has " (count (:weights scan)) " marks but \\" syl-key " has "
                 (count (:syllables syl)) " syllables."))

      :else
      {:prosody {:syllables (vec (map-indexed
                                  (fn [i sy] (-> sy
                                                 (update :start place)
                                                 (update :end place)
                                                 (assoc :weight (get-in scan [:weights i]))))
                                  (:syllables syl)))
                 :feet (or (:feet scan) #{})
                 :caesurae (or (:caesurae scan) #{})
                 :elided (mapv (fn [[a b]] [(place a) (place b)]) (:elided syl))}
       :diagnostics (concat (:problems syl)
                            (some-> (word-division-problem syl syl-cl letters surface-cl word-starts syl-key s)
                                    vector))})))
