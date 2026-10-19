(ns gloss.parse
  "Reads the plain-text input format (docs/format.md) into a document
  (gloss.model). Parsing never fails: every problem becomes a diagnostic
  {:level :msg :source-line …}, and rows that can't be aligned are kept as
  written under the line's :raw key, so nothing typed is lost.

  The entry points are `parse-document`, for a whole text, and
  `parse-lines`, for line blocks alone (the line editor). The \\text row and
  the prosody rows have namespaces of their own (gloss.parse.text,
  gloss.parse.prosody)."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.formatting :as fmt]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.parse.prosody :as prosody]
            [gloss.parse.text :as text]
            [gloss.rows :as rows]))

;; ---------------------------------------------------------------------------
;; Source text and ids

(defn- normalize
  "Source in Unicode's composed form (NFC), so that a letter typed as a
  base and a combining mark compares equal to the precomposed one, and
  without carriage returns."
  [s]
  (-> (str s) (.normalize "NFC") (str/replace "\r" "")))

(defn new-id
  "A new id for a line or section: random, so that ids from separate
  parses don't collide."
  []
  (str "l" (subs (str (random-uuid)) 0 8)))

;; ---------------------------------------------------------------------------
;; Fields

(defn- field-lines
  "Splits source into fields {:marker :value :source-line}. Lines that
  don't start with a marker continue the previous field; `%` lines are
  comments. Markers are read by their standard name (gloss.rows), so any
  accepted name works."
  [src]
  (reduce
   (fn [fields [i raw]]
     (let [line (str/trimr raw)]
       (cond
         (str/blank? line) fields
         (str/starts-with? (str/triml line) "%") fields
         :else
         (if-let [[_ marker opt value] (re-matches #"\s*\\([A-Za-z]+)(?:\[([^\]]*)\])?(?:\s+(.*))?" line)]
           (conj fields (cond-> {:marker (rows/row-name marker)
                                 :value (or value "") :source-line (inc i)}
                          opt (assoc :opt (str/trim opt))))
           (if (seq fields)
             (update-in fields [(dec (count fields)) :cont] (fnil conj []) (str/trim line))
             (conj fields {:marker nil :value line :source-line (inc i)}))))))
   []
   (map-indexed vector (str/split-lines (normalize src)))))

(defn- field-value
  "A field's full value. Prose fields keep line breaks and are taken as
  written; other rows join continuation lines with spaces, and their
  escapes are hidden until the document is read (gloss.chars/hide-escapes)."
  [{:keys [marker value cont]}]
  (let [prose? (#{"ft" "note" "about"} marker)
        v (str/trim (str/join (if prose? "\n" " ") (cons value cont)))]
    (if prose? v (chars/hide-escapes v))))

;; ---------------------------------------------------------------------------
;; Braced tokens: {the sailor} {} sings

(defn tokenize-braced
  "The items of a row: split at spaces, except inside braces, which make
  their contents one item ({the sailor}); {} is an empty item."
  [s]
  (loop [cs (seq s), cur nil, depth 0, out []]
    (if-let [c (first cs)]
      (cond
        (and (= c "{") (zero? depth)) (recur (rest cs) (or cur "") 1 out)
        (= c "{") (recur (rest cs) (str cur c) (inc depth) out)
        (and (= c "}") (= depth 1)) (recur (rest cs) cur 0 out)
        (= c "}") (recur (rest cs) (str cur c) (max 0 (dec depth)) out)
        (and (zero? depth) (re-matches #"\s" c))
        (recur (rest cs) nil 0 (if cur (conj out cur) out))
        :else (recur (rest cs) (str cur c) depth out))
      (if cur (conj out cur) out))))

;; ---------------------------------------------------------------------------
;; Notes

(defn- parse-group
  "A note's group, the text inside its braces (escapes hidden):
  `@section/label* words`, or `a … b` for a span; a quote may end with
  #n (`a#2`), its nth place in the line."
  [g]
  (let [[_ section label star body] (re-matches #"(?s)^\s*(?:@(?:([^\s/*]+)/)?([^\s*]+))?(\*)?\s*(.*?)\s*$" g)
        ws (str/split body #"\s+")
        span (first (keep-indexed (fn [i w] (when (#{"…" "..."} w) i)) ws))
        ;; A quote may end with #n: its nth place.
        with-place (fn [q] (if-let [[_ q n] (re-matches #"(?s)^(.*?)\s*#([1-9][0-9]*)$" q)]
                        [q (js/parseInt n 10)]
                        [q nil]))]
    (cond-> (if span
              (let [[from fnth] (with-place (str/join " " (take span ws)))
                    [to tnth] (with-place (str/join " " (drop (inc span) ws)))]
                (cond-> {:from from :to to}
                  fnth (assoc :from-nth fnth)
                  tnth (assoc :to-nth tnth)))
              (let [[words n] (with-place body)]
                (cond-> {:words words} n (assoc :nth n))))
      label (assoc :at (cond-> {:label label} section (assoc :section section)))
      star (assoc :star? true))))

(defn parse-note
  "`\\note[tag] {group} {group} text`: the tag (from the marker's option),
  the groups in braces, then the note's text (prose, as written)."
  [tag v]
  (loop [s (chars/hide-escapes v), groups []]
    (if-let [[m g] (re-find #"^\{([^{}]*)\}\s*" s)]
      (recur (subs s (count m)) (conj groups (parse-group g)))
      (cond-> {:groups groups :text (chars/escapes-as-written s)}
        (not (str/blank? tag)) (assoc :tag tag)))))

(defn- note-of
  "A \\note field, read."
  [f]
  (parse-note (:opt f) (field-value f)))

;; ---------------------------------------------------------------------------
;; Reserved markers
;;
;; Markers the format reserves but the site doesn't support yet. Their rows
;; are kept as written (and written back out), so nothing typed is lost and
;; the markers can be supported later without changing what texts mean.

(def unsupported
  "Reserved markers: parallel rows (\\row[code] …, named in the header with
  \\rowname[code] …)."
  #{"row" "rowname"})

(defn- keep-unsupported
  "The reserved-marker fields among `fields`, kept verbatim, with a warning
  for each. Returns {:kept [{:marker :opt :text}] :diagnostics}."
  [fields]
  (let [fs (filter #(unsupported (:marker %)) fields)]
    {:kept (vec (for [{:keys [marker opt value cont]} fs]
                  (cond-> {:marker marker :text (str/trim (str/join " " (cons value cont)))}
                    opt (assoc :opt opt))))
     :diagnostics (vec (for [f fs]
                         {:level :warning :source-line (:source-line f)
                          :msg (str "\\" (:marker f) " isn't supported yet. The row is kept as written "
                                    "but not shown.")}))}))

;; ---------------------------------------------------------------------------
;; Rows: items, characters and options

(defn- assign-by-word
  "Distributes a row's items over the words, one each: `put` takes a word
  and its item and returns the word. `marker` names the row in problems.
  Returns [words diagnostics]."
  [words tokens put marker]
  (let [nw (count words), nt (count tokens)]
    [(vec (map-indexed (fn [i w] (if (< i nt) (put w (nth tokens i)) w)) words))
     (cond
       (< nt nw) [{:level :warning
                   :msg (str "The " marker " row has " nt " item(s) for " nw " words; the last "
                             (- nw nt) " word(s) are left without one.")}]
       (> nt nw) [{:level :error
                   :msg (str "The " marker " row has " nt " item(s) for " nw " words; extra item(s) “"
                             (str/join " " (drop nw tokens)) "” are dropped. "
                             "Use {braces} for multi-word glosses.")}]
       :else [])]))

(def ^:private header-prose
  "Header rows read as prose, which reserve nothing (but a stray backslash)."
  #{"about" "title"})

(defn- base-row
  "The row a row key belongs to: \"morph[translit]\" is a \\morph row."
  [k]
  (str/replace k "[translit]" ""))

(defn- checked?
  "Whether a row's characters are checked here, against gloss.rows: \\text
  and \\morph check their own as they are read."
  [k]
  (let [b (base-row k)]
    (and (not (#{"text" "morph"} b))
         (or (rows/by-name b) (header-prose b)))))

(defn- whole-value?
  "Whether a row's value is checked whole rather than item by item."
  [b]
  (or (header-prose b) (#{"scan" "syl"} b) (:prose? (rows/by-name b))))

(defn- row-chars
  "The reserved characters and backslashes in a row's value (escapes
  hidden): {[char command] [item …]}."
  [k v]
  (let [b (base-row k)
        items (cond
                (whole-value? b) [v]
                (= b "glword") (map #(:text (fmt/read-cell (chars/strip-punct %))) (remove #{"+"} (tokenize-braced v)))
                :else (map chars/strip-punct (remove #{"+"} (tokenize-braced v))))
        reserved? (if (= b "scan")
                    (fn [c] (and (not= c "\\") (seq (:bad (prosody/parse-scan c)))))
                    (fn [c] (rows/reserved? b c)))]
    (reduce (fn [acc item]
              (reduce (fn [acc key] (update acc key (fnil conj []) item)) acc
                      (distinct (concat
                                 (for [[_ cmd] (re-seq #"\\([0-9A-Za-z]*)" item)] ["\\" cmd])
                                 (for [c (map str item) :when (reserved? c)] [c nil])))))
            {} items)))

(defn char-problems
  "Warnings for reserved characters and for backslashes that escape nothing
  in a row's value (escapes hidden), one per character, naming the items
  it is in."
  [k v]
  (let [found (row-chars k v)
        name (str "\\" k)
        b (base-row k)]
    (for [[[c cmd] in] (sort-by (comp str first) found)
          :let [where (when-not (whole-value? b)
                        (str " (in " (str/join ", " (map #(str "“" % "”") (distinct in))) ")"))]]
      {:level :warning
       :msg (cond
              (seq cmd) (str "Unknown command \\" cmd " in " name where "; write \\\\ for a backslash.")
              (= c "\\") (str "A backslash in " name where " escapes nothing; write \\\\ for a backslash.")
              (and (= k "pos") (= c "-")) (str "“-”" where " is reserved in \\pos: subclasses follow a period (V.trans).")
              (= k "scan") (str "“" c "” is reserved in \\scan, whose marks are _ (long), u (short), x (anceps), "
                                "' or | (a foot) and || (a caesura).")
              :else (str "“" c "”" where " is reserved in " name "; write \\" c " for a literal “" c "”."))})))

(def ^:private row-options
  "Markers that take an option, and the options they take (:any for a
  note's tag)."
  {"morph" #{"text" "translit"} "syl" #{"text" "translit"} "note" :any})

(defn- row-key
  "The row a field is: its marker, with the option for rows of the
  transliteration (\\morph[translit] is \"morph[translit]\")."
  [{:keys [marker opt]}]
  (if (= opt "translit") (str marker "[translit]") marker))

(defn- option-problems
  "Options that the fields' markers don't take."
  [fields]
  (for [{:keys [marker opt source-line]} fields
        :when (and opt (not (unsupported marker))
                   (not= :any (get row-options marker))
                   (not (contains? (get row-options marker) opt)))]
    {:level :warning :source-line source-line
     :msg (str "\\" marker "[" opt "]: "
               (if-let [ok (get row-options marker)]
                 (str "the option must be " (str/join " or " (sort ok)) ".")
                 (str "\\" marker " takes no options; options are reserved.")))}))

(def ^:private line-markers
  "The markers a line block may contain."
  (conj (set (map :name rows/rows)) "ex"))

;; ---------------------------------------------------------------------------
;; A line block
;;
;; `parse-block` reads a line's rows in steps: the text first (its words are
;; what every other row aligns with), then the rows with an item per word,
;; the syllables, and finally the problems with the fields themselves. Each
;; step gets the block's context and returns what it read with its
;; problems, already pointing at their source rows.

(defn- block-context
  "What the steps need: each row's value and source line, by row key (the
  first field of each row counts)."
  [fields]
  (let [firsts (reverse (filter :marker fields))]
    {:fields fields
     :values (into {} (for [f firsts] [(row-key f) (field-value f)]))
     :source-lines (merge {"ex" (:source-line (first fields))}
                          (into {} (for [f firsts] [(row-key f) (:source-line f)])))}))

(defn- value
  "The value of row `k` in the block, or nil if it has none."
  [ctx k]
  (get-in ctx [:values k]))

(defn- at-source-line
  "Problems of row `k`, pointing at its source line (or the block's)."
  [ctx k ds]
  (let [lines (:source-lines ctx)]
    (map #(assoc % :source-line (or (lines k) (lines "ex"))) ds)))

(defn- items
  "A row's items, one per word: braces group, + (a subline break, which
  belongs to \\text) is skipped."
  [ctx k]
  (remove #{"+"} (tokenize-braced (value ctx k))))

(defn- read-translit
  "The transliteration, a surface: each word as written, without its
  punctuation, until \\morph[translit] divides it."
  [ctx words]
  (if (value ctx "translit")
    (let [[ws ds] (assign-by-word words (items ctx "translit")
                                  #(assoc-in %1 [:surfaces "translit" :segs] (morph/whole-word (chars/strip-punct %2)))
                                  "\\translit")]
      [ws (at-source-line ctx "translit" ds)])
    [words []]))

(defn- segment-word
  "A word divided on surface `s` by its item of the surface's \\morph row."
  [word s item]
  (let [result (morph/segment-word (model/surface-word word s) item)]
    (update-in word [:surfaces s] assoc
               :segs (:segs result)
               :morph-src (chars/escapes-as-written item)
               :morph-problems (morph/segment-problems result))))

(defn- read-morph-rows
  "Each surface's \\morph row (\\morph, \\morph[translit]), dividing its
  words into morphemes."
  [ctx words]
  (reduce (fn [[words ds] s]
            (let [k (model/analysis-row "morph" s)]
              (cond
                (not (value ctx k)) [words ds]
                (not (value ctx s))
                [words (into ds (at-source-line ctx k [{:level :error :msg (str "\\" k " segments the \\" s " row, but this line has none.")}]))]
                :else
                ;; Items are split at spaces only: a \morph item has no braces.
                (let [its (vec (remove #{"+"} (str/split (str/trim (value ctx k)) #"\s+")))
                      [ws d] (assign-by-word words its #(segment-word %1 s %2) (str "\\" k))]
                  [ws (into ds (at-source-line ctx k d))]))))
          [words []]
          model/surfaces))

(defn- read-items
  "The rows with an item per word that the word stores
  (gloss.rows/word-items), each item read by gloss.model/read-item."
  [ctx words]
  (reduce (fn [[words ds] row]
            (if-not (value ctx row)
              [words ds]
              (let [its (vec (items ctx row))
                    [ws d] (assign-by-word words its #(model/put-fields %1 (first (model/read-item row %2)))
                                           (str "\\" row))
                    problems (for [item (take (count words) its)
                                   p (second (model/read-item row item))]
                               {:level :warning :msg p})]
                [ws (into ds (at-source-line ctx row (concat d problems)))])))
          [words []]
          rows/word-items))

(def ^:private word-steps
  "The rows read word by word, in order: the transliteration before the
  \\morph rows that divide it."
  [read-translit read-morph-rows read-items])

(defn- read-syllables
  "Each surface's \\syl row, with the one \\scan, placed on the surface's
  letters. Returns {:prosody {surface p} :raw {row kept-as-written}
  :diagnostics}."
  [ctx line]
  (let [scan (value ctx "scan")
        pros (into {} (for [s model/surfaces
                            :let [k (model/analysis-row "syl" s)]
                            :when (value ctx k)]
                        [s (if (value ctx s)
                             (prosody/parse-prosody (model/surface-letters line s) (map :start (model/surface-ranges line s))
                                            scan (value ctx k) s)
                             {:diagnostics [{:level :error :msg (str "\\" k " syllabifies the \\" s " row, but this line has none.")}]})]))
        no-syl (when (and scan (empty? pros))
                 (let [msg "\\scan needs a \\syl (or \\syl[translit]) row to say where the syllables are."]
                   {:raw {"scan" {:text scan :reason msg}}
                    :diagnostics [{:level :error :msg msg}]}))
        ;; Where both surfaces have syllables, they must be as many.
        counts (into {} (for [[s p] pros :when (:prosody p)] [s (count (get-in p [:prosody :syllables]))]))
        count-problem (when (> (count (distinct (vals counts))) 1)
                        [{:level :error
                          :msg (str (str/join " but " (for [[s n] counts] (str "\\" (model/analysis-row "syl" s) " has " n " syllables")))
                                    ".")}])]
    {:prosody (into {} (for [[s p] pros :when (:prosody p)] [s (:prosody p)]))
     :raw (apply merge (concat (map :raw (vals pros)) [(:raw no-syl)]))
     :diagnostics (concat (mapcat (fn [[s p]] (at-source-line ctx (model/analysis-row "syl" s) (:diagnostics p))) pros)
                          (at-source-line ctx "scan" (concat (:diagnostics no-syl) count-problem)))}))

(defn- field-problems
  "Problems with the fields themselves: a + outside \\text, options a
  marker doesn't take, reserved characters, unknown markers."
  [ctx]
  (let [fields (:fields ctx)]
    (concat
     (for [f fields
           :when (and (:marker f) (not (#{"text" "ft" "note"} (:marker f)))
                      (some #{"+"} (str/split (str/trim (field-value f)) #"\s+")))]
       {:level :warning :source-line (:source-line f)
        :msg (str "Subline breaks (+) belong in \\text only; the + in \\" (row-key f) " is ignored.")})
     (option-problems fields)
     (for [f fields
           :let [k (row-key f)]
           :when (checked? k)
           d (char-problems k (cond-> (field-value f) (#{"ft" "note"} k) chars/hide-escapes))]
       (assoc d :source-line (:source-line f)))
     (for [f fields
           :when (not (line-markers (:marker f)))
           :when (not (unsupported (:marker f)))]
       {:level :warning
        :source-line (:source-line f)
        :msg (if (:marker f)
               (str "Unknown marker \\" (:marker f) ".")
               "Text outside any marker.")}))))

(defn parse-block
  "Parses one line block (the fields from \\ex up to the next \\ex).
  Returns {:line :diagnostics :source-lines}."
  [fields]
  (let [fields (map #(cond-> % (= "text" (:opt %)) (dissoc :opt)) fields)
        ctx (block-context fields)
        text (value ctx "text")
        {:keys [words breaks diagnostics]} (text/parse-row (or text ""))
        text-problems (at-source-line ctx "text" (cond-> (vec diagnostics)
                                           (nil? text) (conj {:level :error :msg "This line has no \\text row."})))
        [words word-problems] (reduce (fn [[ws ds] step]
                                        (let [[ws d] (step ctx ws)] [ws (into ds d)]))
                                      [words []]
                                      word-steps)
        ex (first (filter #(= "ex" (:marker %)) fields))
        kept (keep-unsupported fields)
        line (cond-> {:id (new-id)
                      :label (when ex (not-empty (field-value ex)))
                      :words words}
               (seq breaks) (assoc :breaks breaks)
               (seq (:kept kept)) (assoc :unsupported (:kept kept)))
        ;; Syllables are placed on the words' letters, so they come last.
        {:keys [prosody raw] :as syllables} (read-syllables ctx line)
        notes (mapv note-of (filter #(= "note" (:marker %)) fields))
        line (cond-> line
               (seq prosody) (assoc :prosody prosody)
               (seq raw) (assoc :raw raw)
               (value ctx "ft") (assoc :free (value ctx "ft"))
               (seq notes) (assoc :notes notes))]
    {:line line
     :source-lines (:source-lines ctx)
     :diagnostics (vec (concat text-problems word-problems (:diagnostics kept)
                               (:diagnostics syllables) (field-problems ctx)))}))

;; ---------------------------------------------------------------------------
;; Documents

(defn- split-blocks
  "Splits fields into the header, line blocks and the back section. A block
  starts at \\ex, or at a \\text when the current block already has one;
  \\section starts a section, whose blocks carry its id; after \\end
  comes the back section. Returns {:header :blocks [{:fields :section}]
  :sections [{:id :label :title :source-line}] :back :end-line}."
  [fields]
  (reduce (fn [{:keys [blocks section back?] :as acc} f]
            (let [cur (peek blocks)]
              (cond
                back? (update acc :back conj f)
                (= "end" (:marker f)) (assoc acc :back? true :end-line (:source-line f))
                (= "section" (:marker f))
                (let [id (new-id)]
                  (-> acc
                      (update :sections conj (cond-> {:id id :title (field-value f) :source-line (:source-line f)}
                                               (not (str/blank? (:opt f))) (assoc :label (:opt f))))
                      (assoc :section id)))
                (or (= "ex" (:marker f))
                    (and (= "text" (:marker f))
                         (or (nil? cur) (some #(= "text" (:marker %)) (:fields cur)))))
                (update acc :blocks conj (cond-> {:fields [f]} section (assoc :section section)))
                (and (nil? cur) (nil? section)) (update acc :header conj f)
                (nil? cur) (update acc :blocks conj (cond-> {:fields [f]} section (assoc :section section)))
                :else (update acc :blocks #(conj (pop %) (update cur :fields conj f))))))
          {:header [] :blocks [] :sections [] :back []}
          fields))

(defn- header-chars
  "Reserved characters and stray backslashes in a header field."
  [f]
  (map #(assoc % :source-line (:source-line f)) (char-problems (:marker f) (chars/hide-escapes (field-value f)))))

(defn- parse-header
  "The header's fields read into the document's settings (title, \\abbr,
  \\color, …). Returns {:doc :diagnostics}."
  [fields]
  (reduce
   (fn [{:keys [doc] :as acc} f]
     (let [v (field-value f)]
       (case (:marker f)
         "glosshubformat" (let [n (if (re-matches #"[1-9][0-9]*" v) (js/parseInt v 10) 0)]
                      (cond
                        (zero? n) (update acc :diagnostics conj
                                         {:level :error :source-line (:source-line f)
                                          :msg (str "\\glosshubformat takes the format version, a whole number "
                                                    "(\\glosshubformat " model/format-version "), not “" v "”.")})
                        (> n model/format-version) (update acc :diagnostics conj
                                                           {:level :warning :source-line (:source-line f)
                                                            :msg (str "This text is written in version " n
                                                                      " of the GlossHub format, and this site reads version "
                                                                      model/format-version ". Parts of it may not show.")})
                        :else acc))
         "title" (-> acc (assoc-in [:doc :title] v) (update :diagnostics into (header-chars f)))
         "lang" (assoc-in acc [:doc :lang] v)
         "about" (-> acc (assoc-in [:doc :about] v) (update :diagnostics into (header-chars f)))
         "order" (assoc-in acc [:doc :order] (mapv rows/row-name (str/split v #"\s+")))
         "dir" (if (#{"rtl" "ltr"} (str/lower-case v))
                 (assoc-in acc [:doc :dir] (str/lower-case v))
                 (update acc :diagnostics conj {:level :error :msg (str "\\dir must be rtl or ltr, not “" v "”.")}))
         "abbr" (if-let [[_ tag meaning] (re-matches #"(?s)(\S+)\s+(.+)" v)]
                  (assoc-in acc [:doc :abbrevs tag] meaning)
                  (update acc :diagnostics conj {:level :error :msg (str "\\abbr needs a tag and a meaning: " v)}))
         "color" (if-let [[_ tag color label] (re-matches #"(\S+)\s+(\S+)(?:\s+(.+))?" v)]
                   (update-in acc [:doc :colors] (fnil conj [])
                              (cond-> {:tag tag :color color} label (assoc :label label)))
                   (update acc :diagnostics conj {:level :error :msg (str "\\color needs a tag and a color: " v)}))
         "note" (-> acc
                    (update-in [:doc :notes] (fnil conj []) (note-of f))
                    (update :diagnostics conj
                            {:level :warning :source-line (:source-line f)
                             :msg "Notes belong under a line or after \\end, not in the header; this one will be written after \\end."}))
         (if (unsupported (:marker f))
           (let [{:keys [kept diagnostics]} (keep-unsupported [f])]
             (-> acc
                 (update-in [:doc :unsupported] (fnil into []) kept)
                 (update :diagnostics into diagnostics)))
         (update acc :diagnostics conj
                 {:level :warning
                  :source-line (:source-line f)
                  :msg (if (:marker f)
                         (str "Unknown header marker \\" (:marker f) ".")
                         "Text outside any marker.")})))))
   {:doc {} :diagnostics []}
   fields))

(defn- parse-blocks
  "Parses line blocks in order, each in its section. Returns {:lines
  :source-lines :diagnostics}, each diagnostic with its block's index and
  line."
  [blocks]
  (let [parsed (mapv (fn [{:keys [fields section]}]
                       (cond-> (parse-block fields)
                         section (assoc-in [:line :section] section)))
                     blocks)]
    {:lines (mapv :line parsed)
     :source-lines (into {} (map (juxt (comp :id :line) :source-lines) parsed))
     :diagnostics (for [[i p] (map-indexed vector parsed)
                        d (:diagnostics p)]
                    (assoc d :block i :line (:id (:line p))))}))

(defn- parse-back
  "The back section, after \\end: notes only."
  [fields]
  (reduce (fn [acc f]
            (if (= "note" (:marker f))
              (update acc :notes conj (note-of f))
              (update acc :diagnostics conj
                      {:level :warning :source-line (:source-line f)
                       :msg (if (:marker f)
                              (str "Only notes may follow \\end; \\" (:marker f) " is ignored there.")
                              "Only notes may follow \\end; this text is ignored.")})))
          {:notes [] :diagnostics []}
          fields))

(defn- section-problems
  "Section labels unique in the text, line labels unique in their section."
  [sections lines]
  (concat
   (for [[label ss] (group-by :label sections)
         :when (and label (> (count ss) 1))]
     {:level :error :source-line (:source-line (second ss))
      :msg (str "Two sections are labelled “" label "”; section labels must be unique.")})
   (for [[_ ls] (group-by :section lines)
         [label same] (group-by :label ls)
         :when (and label (> (count same) 1))]
     {:level :error :line (:id (second same))
      :msg (str "Two lines are labelled “" label "”" " in the same section; line labels must be unique there.")})))

(defn- parse-lines*
  "`parse-lines`, with escapes still hidden."
  [src]
  (let [{:keys [header blocks sections back end-line]} (split-blocks (field-lines src))
        ;; Notes before the first block, or after \end, go under the first line.
        loose (into (mapv note-of (filter #(= "note" (:marker %)) header))
                    (:notes (parse-back back)))
        stray (concat
               (for [f header :when (not= "note" (:marker f))]
                 {:level :warning :block 0 :source-line (:source-line f)
                  :msg (str "Ignored " (if (:marker f) (str "\\" (:marker f)) "text")
                            " before the first line.")})
               (for [s sections]
                 {:level :warning :block 0 :source-line (:source-line s)
                  :msg "Sections can't be added here; edit the document's source to add one."})
               (when end-line
                 [{:level :warning :block 0 :source-line end-line
                   :msg "\\end can't be added here; edit the document's source."}]))
        {:keys [lines source-lines diagnostics]} (parse-blocks (map #(dissoc % :section) blocks))]
    {:lines (if (and (seq loose) (seq lines))
              (update-in lines [0 :notes] (fnil into []) loose)
              lines)
     :source-lines source-lines
     :diagnostics (vec (concat stray diagnostics))}))

(defn- parse-document*
  "`parse-document`, with escapes still hidden."
  [src]
  (let [{:keys [header blocks sections back]} (split-blocks (field-lines src))
        {:keys [doc diagnostics]} (parse-header header)
        {:keys [lines source-lines] :as parsed} (parse-blocks blocks)
        back (parse-back back)]
    {:doc (cond-> (-> {:title "Untitled" :abbrevs {} :colors [] :notes []}
                      (merge doc)
                      (update :notes (fnil into []) (:notes back))
                      (assoc :lines lines))
            (seq sections) (assoc :sections (mapv #(dissoc % :source-line) sections)))
     :source-lines source-lines
     :diagnostics (vec (concat diagnostics
                               (:diagnostics parsed)
                               (:diagnostics back)
                               (section-problems sections lines)))}))

(defn parse-lines
  "Parses source containing only line blocks (as in the line editor).
  Returns {:lines :source-lines :diagnostics}, each diagnostic with the
  index of its block (:block). See `parse-document` for :source-lines."
  [src]
  (chars/restore-escapes (parse-lines* src)))

(defn parse-document
  "Parses a whole text. Returns {:doc :source-lines :diagnostics}: the
  document, where each line's rows are in the source ({line-id {row-key
  source-line}}, for problems found later, see gloss.check), and the
  problems found while reading."
  [src]
  (chars/restore-escapes (parse-document* src)))

;; ---------------------------------------------------------------------------
;; Filling rows from the text

(defn fillable
  "The rows that can be filled in from the words of the surface they
  analyse, for source `src`: \\morph and \\syl, and their [translit]
  versions when a line has a \\translit."
  [src]
  (let [translit? (some #(= "translit" (:marker %)) (field-lines src))]
    (for [s model/surfaces
          :when (or (= s "text") translit?)
          row ["morph" "syl"]]
      (model/analysis-row row s))))

(defn fill-row
  "Adds the row `row-key` (see `fillable`) to every line of `src` that has
  the row it comes from but not this one: each word's letters, without
  punctuation, ready for morpheme or syllable breaks. The rest of the
  source is kept as written."
  [src k]
  (let [marker (str "\\" k)
        from (if (str/ends-with? k "[translit]") "translit" "text")
        lines (str/split-lines (normalize src))
        {:keys [blocks]} (split-blocks (field-lines src))
        inserts (into {}
                      (for [{:keys [fields]} blocks
                            :let [fields (map #(cond-> % (= "text" (:opt %)) (dissoc :opt)) fields)
                                  src-field (first (filter #(= from (row-key %)) fields))]
                            :when (and src-field (not (some #(= k (row-key %)) fields)))
                            :let [line (:line (parse-block fields))
                                  word-of #(model/surface-word % from)
                                  items (for [w (:words line)
                                              :let [core (chars/restore-escapes (word-of w))]]
                                          (apply str (for [c (chars/letters-of core)] (subs core (:start c) (:end c)))))]]
                        ;; After the source row and its continuation lines.
                        [(+ (dec (:source-line src-field)) (count (:cont src-field)))
                         (str marker " " (str/join " " items))]))]
    (str/join "\n" (apply concat (map-indexed (fn [i l] (if-let [ins (inserts i)] [l ins] [l])) lines)))))
