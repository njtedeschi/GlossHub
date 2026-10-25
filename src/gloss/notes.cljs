(ns gloss.notes
  "Notes: which words each note's groups quote, and the notes' numbers in
  reading order. A note is written `\\note[tag] {group} … text`
  (docs/format.md §5.11); its groups are matched here, over the whole
  document, and the result is cached per document (`note-index`)."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.model :as model]))

;; ---------------------------------------------------------------------------
;; Notes and their groups
;;
;; A note is {:tag :text :groups}, kept under the IGT it is written under
;; (the IGT's :notes) or, after \end, in the document's :notes. Each group
;; quotes the words the note is about:
;;   {:words "tot adīre"}            consecutive words
;;   {:from "quō" :to "laesō"}       from one quote to the next of another,
;;                                   possibly in the IGTs that follow
;; with :at {:label :section} for another IGT (\note {@10 …}), and :star?
;; when the group shows no number. Quotes are matched like \morph entries:
;; capitals, diacritics and punctuation don't matter.

(defn note-key
  "How a note is referred to while reading (highlighting, editing): its tag,
  or else its place among the document's notes."
  [note i]
  (or (:tag note) i))

(defn all-notes
  "The document's notes in order, each [key note home-igt-id path]: those
  under IGTs, then those after \\end (home nil)."
  [doc]
  (let [under (for [[li igt] (map-indexed vector (:igts doc))
                    [ni note] (map-indexed vector (:notes igt))]
                [note (:id igt) [:igts li :notes ni]])
        back (for [[ni note] (map-indexed vector (:notes doc))]
               [note nil [:notes ni]])]
    (vec (map-indexed (fn [i [note home path]] [(note-key note i) note home path])
                      (concat under back)))))

(defn group-text
  "A group as written inside its braces, escaped so that it reads back the
  same."
  [{:keys [at star? words from to from-nth to-nth] n :nth}]
  (let [esc (fn [s] (let [s (chars/escape (set "\\{}#") s)]
                      (if (#{"@" "*"} (subs s 0 1)) (str "\\" s) s)))
        q (fn [s n] (str (esc (str s)) (when n (str "#" n))))
        head (str (when at (str "@" (when (:section at) (str (:section at) "/")) (:label at)))
                  (when star? "*"))
        body (if from (str (q from from-nth) " … " (q to to-nth)) (q words n))]
    (str head (when (seq head) " ") body)))

;; ---------------------------------------------------------------------------
;; Matching groups

(defn- match-key
  "A word or quoted word, for matching: its letters without diacritics or case."
  [s]
  (apply str (map :base (chars/letters-of s))))

(defn quote-keys
  "A quote's words, for matching (words of punctuation only are left out)."
  [s]
  (vec (remove #{""} (map match-key (str/split (str/trim (str s)) #"\s+")))))

(defn- word-keys
  "An IGT's words on surface `s`, for matching."
  [igt s]
  (mapv #(match-key (model/surface-word % s)) (:words igt)))

(defn- occurrences
  "Where `ks` occur as consecutive words in `words` (from index `from`)."
  [words ks & [from]]
  (let [n (count ks)]
    (if (zero? n)
      []
      (vec (for [i (range (or from 0) (inc (- (count words) n)))
                 :when (= ks (subvec words i (+ i n)))]
             i)))))

(defn- locate
  "The IGT a group is matched in: {:igt} or {:problem}."
  [{:keys [at]} home igts-by-label sections home-section]
  (cond
    (nil? at) (if home
                {:igt home}
                {:problem "A note after \\end must say which IGT each group is in: {@label …}."})
    (and (:section at) (not (contains? sections (:section at))))
    {:problem (str "@" (:section at) "/" (:label at) ": no section is labelled “" (:section at) "”.")}
    :else
    (let [section (if (:section at) (sections (:section at)) home-section)
          found (igts-by-label [section (:label at)])]
      (cond
        (empty? found)
        {:problem (str "@" (when (:section at) (str (:section at) "/")) (:label at)
                       ": no IGT is labelled “" (:label at) "”"
                       (if (:section at) " in that section" (when home-section " in this section"))
                       ". Notes refer to IGTs by their own labels (\\igt " (:label at) "), not by their numbers.")}
        (> (count found) 1)
        {:problem (str "@" (:label at) ": more than one IGT is labelled “" (:label at) "”.")}
        :else {:igt (:id (first found))}))))

(defn- quoted [s n] (str "“" (str/trim s) (when n (str "#" n)) "”"))

(defn- times [n] (if (= 1 n) "once" (str n " times")))

(defn- place-problem
  "Why a quote `q` (with #`n`, if given) found at `hits` doesn't name one
  place, or nil."
  [group q n hits where]
  (cond
    (empty? (quote-keys q)) (str "{" (group-text group) "} quotes no words.")
    (empty? hits) (str (quoted q nil) " matches no words " where ".")
    (and n (> n (count hits)))
    (str (quoted q n) ": “" (str/trim q) "” occurs only " (times (count hits)) " " where ".")
    (and (nil? n) (> (count hits) 1))
    (str (quoted q nil) " matches " (count hits) " places " where
         "; quote more words, or say which with #: “" (str/trim q) "#2” is the second.")))

(defn- span-end
  "Where a span's second quote ends: its first place after the first quote
  (or its #nth), in IGT `li` from word `from` or in the IGTs after, on
  surface `s`. [igt-index word] or nil, and how many places there were."
  [group igts li from s]
  (let [ks (quote-keys (:to group))
        places (when (seq ks)
                 (for [k (range li (count igts))
                       hit (occurrences (word-keys (igts k) s) ks (when (= k li) from))]
                   [k (+ hit (count ks) -1)]))]
    [(first (drop (dec (or (:to-nth group) 1)) places)) (count places)]))

(defn- match-group
  "Matches a group in its IGT (and, for a span, the IGTs after it).
  Returns {:members [[igt-id i]] :mark [igt-id i]} or {:problem}. A quote
  with #n stands for its nth place (for a span's end, after its start)."
  [group igt-id igts pos]
  (let [li (pos igt-id)
        span? (some? (:from group))
        [q n] (if span? [(:from group) (:from-nth group)] [(:words group) (:nth group)])
        ks (quote-keys q)
        where (if (:at group) "in that IGT" "in this IGT")
        ;; The first surface the quote occurs on: \text, else \text[translit].
        [s hits] (or (some (fn [s] (let [h (occurrences (word-keys (igts li) s) ks)] (when (seq h) [s h]))) model/surfaces)
                     ["text" []])]
    (if-let [problem (place-problem group q n hits where)]
      {:problem problem}
      (let [start (hits (dec (or n 1)))]
        (if-not span?
          ;; Consecutive words: the number goes after the last.
          (let [end (+ start (count ks) -1)]
            {:members (mapv #(vector igt-id %) (range start (inc end))) :mark [igt-id end]})
          ;; A span: every word from the first quote to the end of the second.
          (let [[end found] (span-end group igts li (+ start (count ks)) s)]
            (if-let [[ek ej] end]
              {:members (vec (for [k (range li (inc ek))
                                   :let [l (igts k)]
                                   i (range (if (= k li) start 0) (if (= k ek) (inc ej) (count (:words l))))]
                               [(:id l) i]))
               :mark [(:id (igts ek)) ej]}
              {:problem (if (and (:to-nth group) (pos? found))
                          (str (quoted (:to group) (:to-nth group)) ": “" (str/trim (:to group)) "” follows "
                               (quoted q n) " only " (times found) ".")
                          (str (quoted (:to group) nil) " doesn't follow " (quoted q n) " " where
                               (when (seq (quote-keys (:to group))) " or the ones after it") "."))})))))))

(defn- note-problem
  "A problem with a note, on the IGT it is written under (if any)."
  [home msg]
  (cond-> {:level :warning :msg msg :field "note"} home (assoc :igt home)))

(defn- doc-places
  "Where things are in a document, for matching notes: IGTs in order, each
  IGT's position, IGTs by [section label], sections by label."
  [doc]
  (let [igts (vec (:igts doc))]
    {:igts igts
     :pos (into {} (map-indexed (fn [i l] [(:id l) i]) igts))
     :by-id (into {} (map (juxt :id identity) igts))
     :igts-by-label (group-by (juxt :section :label) (filter :label igts))
     :sections (into {} (for [s (:sections doc) :when (:label s)] [(:label s) (:id s)]))}))

(defn- match-note
  "One note matched: the words its groups quote (:members), the words that
  show its number (:marks), and its problems."
  [{:keys [igts pos by-id igts-by-label sections]} [k note home]]
  (let [results (for [g (:groups note)]
                  (let [{:keys [igt problem]} (locate g home igts-by-label sections (:section (by-id home)))]
                    (if igt
                      (assoc (match-group g igt igts pos) :star? (:star? g))
                      {:problem problem})))
        groups (:groups note)]
    {:key k :home home
     :members (set (mapcat :members results))
     :marks (vec (for [r results :when (and (:mark r) (not (:star? r)))] (:mark r)))
     :problems (concat
                (for [r results :when (:problem r)] (note-problem home (:problem r)))
                (cond
                  ;; After \end, a note on the whole text quotes nothing.
                  (and home (empty? groups))
                  [(note-problem home "This note quotes no words: start it with the words it is about, in braces ({words}).")]
                  (and (seq groups) (every? :star? groups))
                  [(note-problem home "Every group of this note is starred, so its number appears nowhere in the text.")]))}))

;; ---------------------------------------------------------------------------
;; The note index

(defn- note-index*
  "`note-index`, uncached."
  [doc]
  (let [{:keys [pos] :as places} (doc-places doc)
        notes (all-notes doc)
        matched (map #(match-note places %) notes)
        ;; A word's reading order; a note stands at its first mark, else at
        ;; the end of the IGT it is written under, else after the text.
        at (fn [[igt-id i]] [(pos igt-id) i])
        order (fn [{:keys [marks home]}]
                (cond
                  (seq marks) (first (sort (map at marks)))
                  home [(pos home) js/Infinity]
                  :else [js/Infinity 0]))
        numbers (into {} (map-indexed (fn [n [_ m]] [(:key m) (inc n)])
                                      (sort-by (fn [[i m]] [(order m) i]) (map-indexed vector matched))))
        tags (frequencies (keep (comp :tag second) notes))]
    {:numbers numbers
     :members (into {} (map (juxt :key :members) matched))
     :marks (reduce (fn [acc {:keys [key marks]}]
                      (reduce (fn [acc [igt-id i]]
                                (update-in acc [igt-id i] (fnil conj []) {:key key :n (numbers key)}))
                              acc (distinct marks)))
                    {} matched)
     ;; The IGT under which each note's text is shown.
     :shown-under (into {} (for [m matched]
                             [(:key m) (if (seq (:marks m))
                                         (first (first (sort-by at (:marks m))))
                                         (:home m))]))
     :problems (concat (mapcat :problems matched)
                       (for [[tag n] tags :when (> n 1)]
                         (note-problem nil (str "Two notes are tagged “" tag "”; tags must be unique."))))}))

(def ^:private note-indexes
  "`note-index`'s results by document, held weakly. It is asked for once
  per IGT of a page, and an edited document is a new key."
  (js/WeakMap.))

(defn note-index
  "Where a document's notes are: {:numbers {key n} :members {key #{[igt-id
  word]}} :marks {igt-id {word [{:key :n}]}} :shown-under {key igt-id}
  :problems [diagnostic]}."
  [doc]
  (if (nil? doc)
    (note-index* {})
    (or (.get note-indexes doc)
        (let [index (note-index* doc)]
          (.set note-indexes doc index)
          index))))

(defn note-numbers
  "Each note's number, by key."
  [doc]
  (:numbers (note-index doc)))

(defn find-note
  "The note with key `k`: [note path] or nil."
  [doc k]
  (some (fn [[key note _ path]] (when (= key k) [note path])) (all-notes doc)))

(defn highlighted-words
  "The words belonging to the notes `keys`, by IGT: {igt-id #{word-index}}."
  [doc keys]
  (let [members (:members (note-index doc))]
    (reduce (fn [m [igt-id i]] (update m igt-id (fnil conj #{}) i))
            {}
            (mapcat members keys))))

(defn first-cited
  "The notes whose text is shown under each IGT: {igt-id #{key}}."
  [doc]
  (reduce (fn [m [k igt-id]] (if igt-id (update m igt-id (fnil conj #{}) k) m))
          {}
          (:shown-under (note-index doc))))

(defn uncited-notes
  "Keys of the notes shown under no IGT (they are shown after the text)."
  [doc]
  (let [{:keys [shown-under numbers]} (note-index doc)]
    (sort-by numbers (for [[k igt-id] shown-under :when (nil? igt-id)] k))))
