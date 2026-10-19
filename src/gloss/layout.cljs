(ns gloss.layout
  "Turns a line into display units. Morpheme, root, syllable and elision
  boundaries all cut a word into pieces; pieces are grouped by syllable
  inside each word, so scansion marks can sit over exact letters while the
  word stays contiguous. Words joined by a syllable that crosses the word
  boundary (elision) form one unbreakable unit. The text and the
  transliteration are laid out the same way, each from its own segments and
  syllables."
  (:require [gloss.formatting :as fmt]
            [gloss.model :as model]))

(defn- index-containing
  "The index of the [start end) range in `ranges` that contains `pos`."
  [ranges pos]
  (first (keep-indexed (fn [i [s e]] (when (and (<= s pos) (< pos e)) i)) ranges)))

(defn- seg-sep
  "What is drawn before a segment when breaks are shown: its separator, or
  the angle bracket opening an infix or closing it before the rest of the
  host."
  [seg]
  (cond
    (:infix? seg) "<"
    (:cont? seg) ">"
    :else (:sep seg)))

(defn segs-layout
  "One word of a row, as syllable groups of pieces. `segs` are its
  segments and the second argument their ranges (gloss.model/
  segment-ranges) in `letters`, the row's words run together; `prosody`
  and `rubies` (in word offsets) are optional, and so are `runs`, the
  word's formatting runs (in word offsets). A piece is {:text :start :morph
  :sep :joint? :zero? :root? :styles :syl :syl-start? :elided? :ruby}."
  [segs {:keys [start end morphs joints]} letters prosody rubies & [runs]]
  (let [{:keys [syllables feet caesurae elided]} prosody
        syl-ranges (mapv (juxt :start :end) syllables)
        rubies (mapv #(-> % (update :start + start) (update :end + start)) rubies)
        runs (mapv #(-> % (update :start + start) (update :end + start)) runs)
        ruby-ranges (mapv (juxt :start :end) rubies)
        roots (vec (for [[j seg] (map-indexed vector segs)
                         [a b] (:root seg)]
                     [(+ (first (morphs j)) a) (+ (first (morphs j)) b)]))
        cuts (->> (concat [start end]
                          (map first morphs)
                          (mapcat identity joints)
                          (mapcat identity roots)
                          (mapcat identity syl-ranges)
                          (mapcat identity elided)
                          (mapcat (juxt :start :end) rubies)
                          (mapcat (juxt :start :end) runs))
                  (filter #(<= start % end))
                  distinct sort)
        joint-of (fn [pos] (index-containing joints pos))
        ;; The morpheme a joint stands before.
        joint-morph (vec (for [[_ e] joints] (first (keep-indexed (fn [j [ms _]] (when (= ms e) j)) morphs))))
        pieces (for [[a b] (partition 2 1 cuts) :when (< a b)
                     :let [jt (joint-of a)
                           j (if jt (joint-morph jt) (index-containing morphs a))
                           k (index-containing syl-ranges a)
                           morph-start? (and j (not jt) (= a (first (get morphs j))))]]
                 {:text (subs letters a b)
                  :start a
                  :morph j
                  :joint? (boolean jt)
                  :root? (boolean (and (not jt) (index-containing roots a)))
                  :styles (fmt/styles-at runs a)
                  :syl k
                  :sep (when (and morph-start? (pos? j)) (seg-sep (get segs j)))
                  :syl-start? (boolean (and k (not jt) (= a (first (get syl-ranges k))) (> a start)))
                  :elided? (boolean (index-containing elided a))
                  :ruby (when-let [r (index-containing ruby-ranges a)]
                          {:index r
                           :text (get-in rubies [r :text])
                           :first? (= a (get-in rubies [r :start]))})})
        ;; Zero morphemes have no letters: a piece of their own, "Ø", where
        ;; they stand, in the syllable of the piece before.
        zeros (for [[j seg] (map-indexed vector segs) :when (:zero? seg)]
                {:text "Ø" :start (first (morphs j)) :morph j :zero? true
                 :sep (when (pos? j) (:sep seg))})
        pieces (if (seq zeros)
                 (let [ordered (sort-by (juxt :start #(if (:zero? %) 0 1) :morph) (concat pieces zeros))]
                   (rest (reductions (fn [prev p] (if (:zero? p) (assoc p :syl (:syl prev)) p))
                                     {:syl (:syl (first pieces))} ordered)))
                 pieces)
        groups (partition-by :syl pieces)]
    (vec (for [ps groups
               :let [k (:syl (first ps))
                     real (first (remove :zero? ps))
                     first-part? (and k real (= (:start real) (first (get syl-ranges k))))]]
           {:syl k
            :mark (when first-part? (get-in syllables [k :weight]))
            :cont? (and k (not first-part?))
            :foot? (boolean (and first-part? (feet k)))
            :caesura? (boolean (and first-part? (caesurae k)))
            :pieces (vec ps)}))))

(defn word-layout
  "A word's layout: on each surface it has (always the text), its pieces in
  syllable groups (:syls {surface [...]}). `ranges` and `letters` are the
  line's, by surface (gloss.model/surface-ranges, surface-letters)."
  [line i word ranges letters]
  {:index i
   :word word
   :start (:start (ranges "text"))
   :syls (into {} (for [s model/surfaces
                        :let [text? (= s "text"), segs (model/segs word s)]
                        :when (or text? (seq segs))]
                    [s (segs-layout segs (ranges s) (letters s) (model/prosody line s)
                                    (when text? (:ruby word)) (when text? (:fmt word)))]))})

(defn line-layout
  "Display units for a line: [{:words [word-layout ...]} ...]. Each word gets
  the note numbers it shows (:note-marks [{:key :n}]), from `marks`, the
  line's marks by word (gloss.notes/note-index)."
  [line marks]
  (let [ranges (into {} (for [s model/surfaces] [s (model/surface-ranges line s)]))
        letters (into {} (for [s model/surfaces] [s (model/surface-letters line s)]))
        ;; Words a syllable crosses into stay with the word before, by the
        ;; surface whose syllables are drawn (the first that has them).
        scanned (or (first (filter #(model/prosody line %) model/surfaces)) "text")
        syllables (:syllables (model/prosody line scanned))
        cross-ranges (ranges scanned)
        crosses? (fn [pos] (some #(< (:start %) pos (:end %)) syllables))
        words (vec (map-indexed
                    (fn [i w]
                      (assoc (word-layout line i w (into {} (for [[s rs] ranges] [s (get rs i)])) letters)
                             :note-marks (vec (sort-by :n (get marks i)))))
                    (:words line)))]
    (reduce (fn [units [w cr]]
              (if (and (seq units) cr (crosses? (:start cr)))
                (conj (pop units) (update (peek units) :words conj w))
                (conj units {:words [w]})))
            []
            (map vector words (concat cross-ranges (repeat nil))))))
