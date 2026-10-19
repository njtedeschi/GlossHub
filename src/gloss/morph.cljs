(ns gloss.morph
  "Morphemes: reading a \\morph item and placing its breaks on the word as
  written, and pairing the gloss morphemes of \\glmorph with the morphemes
  they gloss (docs/format.md §5.3 and §5.6)."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.vocab :as vocab]))

;; ---------------------------------------------------------------------------
;; Morpheme rows (\morph, \morph[translit])
;;
;; A \morph item segments one word: `-` `=` `~` between morphemes, `<…>`
;; around an infix, `\0` (or Ø) for a zero morpheme, `[…]` around a stem
;; (whole morphemes), `(…)` around root letters (within a morpheme; several
;; groups in one morpheme form one root). The item is matched letter by
;; letter against its word, and its boundaries are placed on the word as
;; written.
;;
;; Segments are in written order. An infix splits its host morpheme in two:
;; s<um>ulat is three segments, `s`, `um` (:infix?) and `ulat` (:cont?, the
;; rest of the host), and two morphemes. A root open across an infix
;; continues after it, without the infix: (s<um>ulat).

(def separators
  "The separators between morphemes: affix, clitic, reduplication."
  #{"-" "=" "~"})

(def ^:private morph-free
  "Punctuation that a \\morph item may contain and that is ignored, so text
  can be pasted in and broken up."
  (set ".,;:!?\""))

(defn- morph-tokens
  "The tokens of a \\morph item: [:sep c] for a separator, [:infix-open]
  and [:infix-close] for < >, [:stem-open] and [:stem-close] for [ ],
  [:root-open] and [:root-close] for ( ), [:zero] for \\0 (or Ø, ∅),
  [:command name] for any other backslash (name \"\" when no letters or
  digits follow it), and [:char c] for anything else."
  [item]
  (loop [i 0, out []]
    (if (>= i (count item))
      out
      (let [c (subs item i (inc i))]
        (if (= c "\\")
          (let [cmd (or (re-find #"^[0-9A-Za-z]+" (subs item (inc i))) "")]
            (recur (+ i 1 (count cmd)) (conj out (if (= cmd "0") [:zero] [:command cmd]))))
          (recur (inc i) (conj out (case c
                                     ("-" "=" "~") [:sep c]
                                     "<" [:infix-open]
                                     ">" [:infix-close]
                                     "[" [:stem-open]
                                     "]" [:stem-close]
                                     "(" [:root-open]
                                     ")" [:root-close]
                                     ("Ø" "∅") [:zero]
                                     [:char c]))))))))

;; An item is read token by token, into a state: the segments read so far
;; (:segs), the one being read (:cur, with its :root ranges and, while a
;; root is open, where it started, :root-open), how many stems are open
;; (:depth), and the problems found. A root open when an infix starts
;; continues after it (:reopen-root).

(def ^:private zero-alone
  "A zero morpheme \\0 is a morpheme of its own: separate it with - or =.")

(defn- new-seg [sep] (cond-> {:text "" :root [] :root-open nil} sep (assoc :sep sep)))

(defn- add-problem [st msg] (update st :problems conj msg))

(defn- letter-count [text] (count (chars/letters-of text)))

(defn- at-start?
  "Whether nothing (no letter, no zero) has been read into the current
  segment."
  [st]
  (and (= "" (get-in st [:cur :text])) (not (get-in st [:cur :zero?]))))

(defn- close-seg
  "Ends the current segment; `end?` when the item ends there."
  [st end?]
  (let [st (cond-> st
             (get-in st [:cur :root-open])
             (-> (add-problem "A root (…) is not closed within its morpheme.")
                 (assoc-in [:cur :root-open] nil)))
        seg (:cur st)]
    (cond-> st
      (and (= "" (:text seg)) (not (:zero? seg)) (or (:sep seg) (not end?)))
      (add-problem "An empty morpheme: two separators in a row, or one at the edge of the word.")
      true (update :segs conj seg))))

(defn- open-infix [st]
  (let [cur (:cur st)
        open (:root-open cur)
        start? (at-start? st)]
    (if (:infix? cur)
      (add-problem st "Infixes can't be nested.")
      (-> st
          (cond-> start? (add-problem "An infix goes inside a morpheme: letters of the morpheme must come before it, as in s<um>ulat."))
          ;; A root open here continues after the infix.
          (cond-> open (-> (update-in [:cur :root] conj [open (letter-count (:text cur))])
                           (assoc-in [:cur :root-open] nil)
                           (assoc :reopen-root true)))
          ;; (An empty start is reported above, not as an empty morpheme.)
          (cond-> (not start?) (close-seg false))
          (assoc :cur (cond-> (assoc (new-seg nil) :infix? true)
                        (pos? (:depth st)) (assoc :stem? true)))))))

(defn- close-infix [st]
  (if-not (get-in st [:cur :infix?])
    (add-problem st "A > closes no infix.")
    (-> st
        (close-seg false)
        ;; The rest of the host morpheme follows.
        (assoc :cur (cond-> (assoc (new-seg nil) :cont? true)
                      (:reopen-root st) (assoc :root-open 0)))
        (dissoc :reopen-root))))

(defn- close-root [st]
  (let [open (get-in st [:cur :root-open])]
    (-> st
        (cond-> (nil? open) (add-problem "A ) closes no root.")
                open (update-in [:cur :root] conj [open (letter-count (get-in st [:cur :text]))]))
        (assoc-in [:cur :root-open] nil))))

(defn- add-char [st c]
  (-> st
      (cond-> (get-in st [:cur :zero?]) (add-problem zero-alone))
      (cond-> (and (re-matches #"[!-/:-@\[-`{-~]" c) (not (morph-free c)) (not= c "'"))
        (add-problem (str "“" c "” is reserved in \\morph; write \\" c " for a literal “" c "”.")))
      (update-in [:cur :text] str c)
      (cond-> (pos? (:depth st)) (assoc-in [:cur :stem?] true))))

(defn- morph-step
  "Reads one token into the state. `next-token` is the one after it, if
  any: a stem must close at the end of a morpheme."
  [st [kind x] next-token]
  (case kind
    :sep (if (get-in st [:cur :infix?])
           (add-problem st "An infix <…> is one morpheme; it can't contain separators.")
           (-> st (close-seg false) (assoc :cur (new-seg x))))
    :infix-open (open-infix st)
    :infix-close (close-infix st)
    :stem-open (cond-> (update st :depth inc)
                 (not (at-start? st))
                 (add-problem "A stem [ … ] covers whole morphemes: open it at the start of one."))
    :stem-close (cond-> (update st :depth #(max 0 (dec %)))
                  (zero? (:depth st))
                  (add-problem "A ] closes no stem.")
                  (not (or (nil? next-token) (#{:sep :stem-close} (first next-token))))
                  (add-problem "A stem [ … ] covers whole morphemes: close it at the end of one."))
    :root-open (cond-> (assoc-in st [:cur :root-open] (letter-count (get-in st [:cur :text])))
                 (get-in st [:cur :root-open]) (add-problem "Roots ( … ) can't be nested."))
    :root-close (close-root st)
    :zero (cond-> (-> st
                      (assoc-in [:cur :zero?] true)
                      (assoc-in [:cur :stem?] (pos? (:depth st))))
            (not (at-start? st)) (add-problem zero-alone))
    :command (add-problem st (if (seq x)
                               (str "Unknown command \\" x ".")
                               "A backslash must be escaped: write \\\\."))
    :char (add-char st x)))

(defn parse-morph-item
  "Reads one \\morph item (escapes hidden). Returns {:segs [{:text :sep
  :zero? :stem? :root [[a b]] :infix? :cont?}] :problems [msg]}, where
  :root ranges count the segment's letters."
  [item]
  (let [tokens (morph-tokens item)
        st (reduce (fn [st [token next-token]] (morph-step st token next-token))
                   {:segs [] :cur (new-seg nil) :depth 0 :problems []}
                   (map vector tokens (concat (rest tokens) [nil])))
        cur (:cur st)
        st (-> st
               (cond-> (:infix? cur) (add-problem "An infix < is not closed with >.")
                       (and (:cont? cur) (= "" (:text cur)))
                       (add-problem "An infix goes inside a morpheme: letters of the morpheme must follow it, as in s<um>ulat."))
               (close-seg true)
               (cond-> (pos? (:depth st)) (add-problem "A stem [ is not closed.")))]
    {:segs (mapv (fn [seg] (cond-> (dissoc seg :root-open)
                             (empty? (:root seg)) (dissoc :root)))
                 (:segs st))
     :problems (vec (distinct (:problems st)))}))

(def ^:private hyphen?
  "The hyphens a word can contain. A morpheme break that falls on one takes
  its place, so the hyphen isn't drawn twice."
  #{"-" "‐" "‑"})

(defn project-morph
  "Places a \\morph item's segments (from `parse-morph-item`) on `core`,
  the word as written. The letters must match (docs/format.md §4.1).
  Returns {:segs [{:text :sep :joint :zero? :stem? :root}]} where each
  segment's :text is a stretch of `core` and :joint is a hyphen of `core`
  standing where the boundary falls (shown only when breaks are hidden), so
  that joints and texts together spell `core`; :root ranges are offsets in
  the segment's :text. Or {:problem msg} when the letters differ."
  [core segs]
  (let [tcl (chars/letters-of core)
        mcl (vec (for [[j seg] (map-indexed vector segs)
                       c (chars/letters-of (:text seg))]
                   (assoc c :seg j)))
        bad (chars/first-mismatch mcl tcl)]
    (if bad
      {:problem (let [show (fn [cls s k] (if (< k (count cls)) (str "“" (subs s (:start (cls k)) (:end (cls k))) "”") "nothing"))]
                  (str "\\morph doesn't spell the word “" core "”: it has "
                       (if (< bad (count mcl))
                         (let [c (mcl bad)] (str "“" (subs (:text (segs (:seg c))) (:start c) (:end c)) "”"))
                         "nothing")
                       " where the word has " (show tcl core bad) "."))}
      (let [;; Letter index where each segment starts.
            starts (vec (reductions + 0 (map #(count (chars/letters-of (:text %))) segs)))
            n (count core)
            ;; Character position where segment j's letters begin, and the
            ;; hyphen before it that serves as its joint, if any.
            place (fn [j]
                    (let [b (starts j)]
                      (cond
                        (zero? j) {:pos 0}
                        (>= b (count tcl)) {:pos n}
                        :else (let [pos (:start (tcl b))
                                    gap-start (if (pos? b) (:end (tcl (dec b))) 0)
                                    gap (subs core gap-start pos)]
                                (if (and (hyphen? gap) (:sep (segs j)) (not (:zero? (segs j))))
                                  {:pos pos :joint gap :joint-start gap-start}
                                  {:pos pos})))))
            places (mapv place (range (count segs)))
            ;; Zero morphemes take no letters; their place is where the next
            ;; letters begin.
            ends (vec (for [j (range (count segs))]
                        (if (:zero? (segs j))
                          (:pos (places j))
                          (let [nxt (first (filter #(not (:zero? (segs %))) (range (inc j) (count segs))))]
                            (if nxt
                              (let [p (places nxt)] (or (:joint-start p) (:pos p)))
                              n)))))]
        {:segs (vec (for [j (range (count segs))
                          :let [seg (segs j)
                                {:keys [pos joint]} (places j)
                                start (if (zero? j) 0 pos)
                                start (if (:zero? seg) (ends j) start)
                                text (subs core start (max start (ends j)))
                                b (starts j)
                                root (vec (for [[a e] (:root seg) :when (< a e)]
                                            [(- (:start (tcl (+ b a))) start) (- (:end (tcl (+ b (dec e)))) start)]))]]
                      (cond-> {:text text}
                        (:sep seg) (assoc :sep (:sep seg))
                        joint (assoc :joint joint)
                        (:zero? seg) (assoc :zero? true)
                        (:stem? seg) (assoc :stem? true)
                        (:infix? seg) (assoc :infix? true)
                        (:cont? seg) (assoc :cont? true)
                        (seq root) (assoc :root root))))}))))

(defn whole-word
  "The segments of a word with no \\morph item: one, the whole word."
  [core]
  (if (= core "") [] [{:text core}]))

(defn segments-text
  "The word that segments spell, as written: joints and texts."
  [segs]
  (apply str (map #(str (:joint %) (:text %)) segs)))

(defn segment-word
  "Segments for a word as written (`core`) from its \\morph item (or nil).
  Returns {:segs :problems :mismatch}: unsegmented, with the :mismatch,
  when the item doesn't spell the word."
  [core item]
  (if (str/blank? item)
    {:segs (whole-word core) :problems []}
    (let [{:keys [segs problems]} (parse-morph-item item)
          projected (project-morph core segs)]
      (if (:problem projected)
        {:segs (whole-word core) :problems problems :mismatch (:problem projected)}
        {:segs (:segs projected) :problems problems}))))

(defn segment-problems
  "Problems from segmenting a word, as diagnostics: a \\morph item that
  doesn't spell its word is an error, the rest are warnings."
  [{:keys [problems mismatch]}]
  (vec (concat (for [p problems] {:level :warning :msg (chars/restore-escapes p)})
               (when mismatch [{:level :error :msg (chars/restore-escapes mismatch)}]))))

;; ---------------------------------------------------------------------------
;; Morphemes and their glosses
;;
;; A \glmorph item is split into gloss morphemes the way a \morph item is
;; split into segments, and each gloss morpheme is paired with a morpheme:
;; infix glosses (<…>) with the infixes in order, the others with the other
;; morphemes in order.

(defn split-gloss
  "The gloss morphemes of a \\glmorph item, split at the Leipzig
  separators, each with the separator before it: \"rīdet=ne\" ->
  [{:text \"rīdet\"} {:text \"ne\" :sep \"=\"}]. `<…>` is an infix's gloss,
  {:text :infix? true}, wherever it stands (`<AV>write`); a lone < or > is
  part of the gloss (1>3)."
  [item]
  (let [n (count item)
        seg (fn [cur sep] (cond-> {:text cur} sep (assoc :sep sep)))]
    (loop [i 0, cur "", sep nil, out []]
      (if (>= i n)
        (if (or (not= cur "") sep (empty? out) (not (:infix? (peek out))))
          (conj out (seg cur sep))
          out)
        (let [c (subs item i (inc i))
              infix (when (= c "<") (re-find #"^<([^<>\s=~-]+)>" (subs item i)))]
          (cond
            infix
            (recur (+ i (count (first infix))) "" nil
                   (cond-> out
                     (or (not= cur "") sep) (conj (seg cur sep))
                     true (conj {:text (second infix) :infix? true})))
            (and (separators c) (or (not= cur "") (and (nil? sep) (:infix? (peek out)))))
            (recur (inc i) "" c (cond-> out (not= cur "") (conj (seg cur sep))))
            :else (recur (inc i) (str cur c) sep out)))))))

(defn morphemes
  "A word's morphemes as segment indices, in order: [[kind [j …]] …], kind
  :infix or :plain; a host morpheme split by an infix has two segments
  (s<um>ulat -> [[:plain [0 2]] [:infix [1]]])."
  [segs]
  (reduce (fn [out [j s]]
            (cond
              (:infix? s) (conj out [:infix [j]])
              (and (:cont? s) (some #(= :plain (first %)) out))
              (let [k (last (keep-indexed (fn [k [kind]] (when (= :plain kind) k)) out))]
                (update-in out [k 1] conj j))
              :else (conj out [:plain [j]])))
          []
          (map-indexed vector segs)))

(defn gloss-segments
  "For each gloss morpheme in `glosses`, the indices of the segments of
  `segs` it glosses, or nil when there is no morpheme for it: infix
  glosses (<…>) go with the infixes in order, the others with the other
  morphemes in order."
  [segs glosses]
  (let [ms (morphemes segs)
        of (fn [kind] (map second (filter #(= kind (first %)) ms)))]
    (first (reduce (fn [[out plain infix] g]
                     (if (:infix? g)
                       [(conj out (first infix)) plain (rest infix)]
                       [(conj out (first plain)) (rest plain) infix]))
                   [[] (of :plain) (of :infix)]
                   glosses))))

(defn segment-glosses
  "For each segment of `segs`, the index of its gloss in `glosses` (or nil)."
  [segs glosses]
  (reduce (fn [v [gi js]] (reduce #(assoc %1 %2 gi) v js))
          (vec (repeat (count segs) nil))
          (map-indexed vector (gloss-segments segs glosses))))

(defn merge-segments
  "The segments `js` of one morpheme as one: their text run together, with
  the root ranges shifted to match; a stem if any part is."
  [segs js]
  (reduce (fn [m j]
            (let [s (get segs j), off (count (:text m))]
              (cond-> (update m :text str (:text s))
                (:stem? s) (assoc :stem? true)
                (seq (:root s)) (update :root (fnil into []) (map (fn [[a b]] [(+ a off) (+ b off)]) (:root s))))))
          {:text ""}
          js))

(defn root-gloss-end
  "Where the root's meaning ends in the gloss of a morpheme that is only
  partly root (root and pattern): before the first backslash, or else
  before the first grammatical element. Returns a character offset, or nil
  when the whole gloss is the root's."
  [gloss-text]
  (let [bs (str/index-of gloss-text "\\")]
    (or bs
        (let [parts (str/split gloss-text #"\.")
              k (first (keep-indexed (fn [i el] (when (and (pos? i) (vocab/grammatical? el)) i)) parts))]
          (when k (dec (apply + (map #(inc (count %)) (take k parts)))))))))

(defn join-segments
  "Segments (or gloss morphemes) written out with their separators, an
  infix in angle brackets; with `markup`, escaped for a row whose markup
  it is (gloss.chars/escape)."
  ([segments] (apply str (map (fn [{:keys [text sep infix?]}] (if infix? (str "<" text ">") (str sep text))) segments)))
  ([markup segments] (join-segments (map #(update % :text (partial chars/escape markup)) segments))))
