(ns gloss.parse
  "Reads the plain-text input format (docs/format.md) into a document
  (gloss.model). Parsing never fails: every problem becomes a diagnostic
  {:level :msg :source-line …}, and tiers that can't be aligned are kept as
  written under the IGT's :raw key, so nothing typed is lost.

  The entry points are `parse-document`, for a whole text, and
  `parse-igts`, for IGT blocks alone (an IGT's source editor). The \\text tier
  and the syllable tiers have namespaces of their own (gloss.parse.text,
  gloss.parse.prosody)."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.english :as english]
            [gloss.formatting :as fmt]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.parse.prosody :as prosody]
            [gloss.parse.text :as text]
            [gloss.tiers :as tiers]))

;; ---------------------------------------------------------------------------
;; Source text and ids

(defn- normalize
  "Source in Unicode's composed form (NFC), so that a letter typed as a
  base and a combining mark compares equal to the precomposed one, and
  without carriage returns."
  [s]
  (-> (str s) (.normalize "NFC") (str/replace "\r" "")))

(defn new-id
  "A new id for an IGT or section: random, so that ids from separate
  parses don't collide."
  []
  (str "l" (subs (str (random-uuid)) 0 8)))

;; ---------------------------------------------------------------------------
;; Fields

(defn- command-name
  "A command as typed, by its standard name: a tier's (gloss.tiers), or
  \\igt for its short form \\ex. Other commands are kept, in lowercase."
  [s]
  (let [m (tiers/standard-name s)]
    (if (= "ex" m) "igt" m)))

(defn- field-lines
  "Splits source into fields {:command :written :value :source-line}. Lines
  that don't start with a command continue the previous field; `%` lines
  are comments. Commands are read by their standard name (`command-name`), so
  any accepted name works; :written is the name as typed, in lowercase."
  [src]
  (reduce
   (fn [fields [i raw]]
     (let [line (str/trimr raw)]
       (cond
         (str/blank? line) fields
         (str/starts-with? (str/triml line) "%") fields
         :else
         (if-let [[_ command opt value] (re-matches #"\s*\\([A-Za-z]+)(?:\[([^\]]*)\])?(?:\s+(.*))?" line)]
           (conj fields (cond-> {:command (command-name command) :written (str/lower-case command)
                                 :value (or value "") :source-line (inc i)}
                          opt (assoc :opt (str/trim opt))))
           (if (seq fields)
             (update-in fields [(dec (count fields)) :cont] (fnil conj []) (str/trim line))
             (conj fields {:command nil :value line :source-line (inc i)}))))))
   []
   (map-indexed vector (str/split-lines (normalize src)))))

(defn- field-value
  "A field's full value. Prose fields keep line breaks and are taken as
  written; other tiers join continuation lines with spaces, and their
  escapes are hidden until the document is read (gloss.chars/hide-escapes)."
  [{:keys [command value cont]}]
  (let [prose? (#{"ft" "note" "about"} command)
        v (str/trim (str/join (if prose? "\n" " ") (cons value cont)))]
    (if prose? v (chars/hide-escapes v))))

;; ---------------------------------------------------------------------------
;; Braced tokens: {the sailor} {} sings

(defn tokenize-braced
  "The entries of a tier: split at spaces, except inside braces, which make
  their contents one entry ({the sailor}); {} is an empty entry."
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

(defn- entries
  "A tier's entries in its value `v`, one per word: braces group, + (a
  subline break, which belongs to \\text) is skipped."
  [v]
  (remove #{"+"} (tokenize-braced v)))

;; ---------------------------------------------------------------------------
;; Notes

(defn- parse-group
  "A note's group, the text inside its braces (escapes hidden):
  `@section/label* words`, or `a … b` for a span; a quote may end with
  #n (`a#2`), its nth place in the IGT."
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
  "`\\note[tag] {group} {group} text`: the tag (from the command's option),
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
;; Reserved commands
;;
;; Commands the format reserves but the site doesn't support yet. Their fields
;; are kept as written (and written back out), so nothing typed is lost and
;; the commands can be supported later without changing what texts mean.

(def unsupported
  "Reserved commands: parallel tiers (\\row[code] …, named in the header with
  \\rowname[code] …)."
  #{"row" "rowname"})

(defn- keep-unsupported
  "The reserved-command fields among `fields`, kept verbatim, with a warning
  for each. Returns {:kept [{:command :opt :text}] :diagnostics}."
  [fields]
  (let [fs (filter #(unsupported (:command %)) fields)]
    {:kept (vec (for [{:keys [command opt value cont]} fs]
                  (cond-> {:command command :text (str/trim (str/join " " (cons value cont)))}
                    opt (assoc :opt opt))))
     :diagnostics (vec (for [f fs]
                         {:level :warning :source-line (:source-line f)
                          :msg (str "\\" (:command f) " isn't supported yet. It is kept as written "
                                    "but not shown.")}))}))

;; ---------------------------------------------------------------------------
;; Tiers: entries, characters and options

(defn- assign-by-word
  "Distributes a tier's entries over the words, one each: `put` takes a word
  and its entry and returns the word. `command` names the tier in problems.
  Returns [words diagnostics]."
  [words entries put command]
  (let [nw (count words), ne (count entries)
        has (str "The " command " tier has " (english/counted ne "entry" "entries")
                 " for " (english/counted nw "word" "words") "; ")]
    [(vec (map-indexed (fn [i w] (if (< i ne) (put w (nth entries i)) w)) words))
     (cond
       (< ne nw) (let [n (- nw ne)]
                   [{:level :warning
                     :msg (str has (if (= 1 n) "the last word is" (str "the last " n " words are"))
                               " left without one.")}])
       (> ne nw) (let [one? (= 1 (- ne nw))]
                   [{:level :error
                     :msg (str has (if one? "the extra entry “" "the extra entries “")
                               (str/join " " (drop nw entries)) (if one? "” is" "” are") " dropped. "
                               "Use {braces} for an entry of several words.")}])
       :else [])]))

(def ^:private header-prose
  "Header fields read as prose, which reserve nothing (but a stray backslash)."
  #{"about" "title"})

(defn- base-tier
  "The tier a tier key belongs to: \"morph[translit]\" is a \\morph tier."
  [k]
  (str/replace k "[translit]" ""))

(defn- checked?
  "Whether a tier's characters are checked here, against gloss.tiers: \\text
  and \\morph check their own as they are read."
  [k]
  (let [b (base-tier k)]
    (and (not (#{"text" "morph"} b))
         (or (tiers/by-name b) (header-prose b)))))

(defn- whole-value?
  "Whether a tier's value is checked whole rather than entry by entry."
  [b]
  (or (header-prose b) (#{"scan" "syl"} b) (:prose? (tiers/by-name b))))

(defn- tier-chars
  "The reserved characters and backslashes in a tier's value (escapes
  hidden): {[char command] [entry …]}."
  [k v]
  (let [b (base-tier k)
        entries (cond
                  (whole-value? b) [v]
                  (= b "glword") (map #(:text (fmt/read-glword (chars/strip-punct %))) (entries v))
                  :else (map chars/strip-punct (entries v)))
        reserved? (if (= b "scan")
                    (fn [c] (and (not= c "\\") (seq (:bad (prosody/parse-scan c)))))
                    (fn [c] (tiers/reserved? b c)))]
    (reduce (fn [acc entry]
              (reduce (fn [acc key] (update acc key (fnil conj []) entry)) acc
                      (distinct (concat
                                 (for [[_ cmd] (re-seq #"\\([0-9A-Za-z]*)" entry)] ["\\" cmd])
                                 (for [c (map str entry) :when (reserved? c)] [c nil])))))
            {} entries)))

(defn char-problems
  "Warnings for reserved characters and for backslashes that escape nothing
  in a tier's value (escapes hidden), one per character, naming the entries
  it is in."
  [k v]
  (let [found (tier-chars k v)
        name (str "\\" (tiers/command k))
        b (base-tier k)]
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

(defn- gloss-start-problems
  "Warnings for \\glmorph entries (escapes hidden) that start with a
  separator before their gloss: that is reserved for a word written apart
  but glossed with the word before it (Leipzig Rule 2A; docs/format.md
  §8)."
  [v]
  (for [[c in] (group-by #(subs % 0 1)
                         (distinct (filter #(re-find #"^[-=~]." %) (map chars/strip-punct (entries v)))))]
    {:level :warning
     :msg (str "A gloss can't start with “" c "” (" (str/join ", " (map #(str "“" % "”") in))
               "): that is reserved for a word written apart from the word it belongs to."
               " Write \\" c " for a literal “" c "”.")}))

(def ^:private tier-options
  "Commands that take an option, and the options they take, in the order
  they are listed (:any for a note's tag)."
  {"text" (filterv tiers/text-options tiers/rows)
   "morph" ["translit"] "syl" ["translit"] "note" :any})

(defn- option
  "A field's option as read (gloss.tiers/option). A note's option is its
  tag, taken as written."
  [{:keys [command opt]}]
  (if (= "note" command) opt (tiers/option opt)))

(defn- tier-key
  "The tier a field is: its command, with the option that names the text
  tier. \\text[translit] is the transliteration (\"translit\") and
  \\morph[translit] its morphemes (\"morph[translit]\"); version 1's
  commands (\\translit, \\morph[text]) are the same tiers."
  [{:keys [command] :as f}]
  (let [opt (option f)]
    (cond
      (and (= "text" command) (tiers/text-options opt)) opt
      (and (#{"morph" "syl"} command) (= "translit" opt)) (str command "[translit]")
      :else command)))

(defn- option-problems
  "Options that the fields' commands don't take."
  [fields]
  (for [{:keys [command opt source-line] :as f} fields
        :let [ok (get tier-options command)]
        :when (and (option f) (not (unsupported command)) (not= :any ok)
                   (not (some #{(option f)} ok)))]
    {:level :warning :source-line source-line
     :msg (str "\\" command "[" opt "]: "
               (if ok
                 (str "the option must be " (english/or-list ok) ".")
                 (str "\\" command " takes no options; options are reserved.")))}))

(defn- version-1-form
  "How format version 2 writes a field written with a command or option of
  version 1 (\\translit as \\text[translit], \\tl as \\text[tl], \\morph[text]
  as \\morph), or nil when it is written as version 2 writes it."
  [{:keys [command opt written]}]
  (cond
    ;; A text tier's command of version 1, by any of its names, is now an option.
    (tiers/text-options command) (str "text[" written "]")
    (and (= "text" (some-> opt str/lower-case)) (not= "note" command)) command))

(defn- version-1-problems
  "In a text of format version 2 or later, the fields written as version 1
  wrote them: read the same, but flagged with how they are written now."
  [fields version]
  (when (>= version 2)
    (for [{:keys [written opt source-line] :as f} fields
          :let [now (version-1-form f)]
          :when now]
      {:level :warning :source-line source-line
       :msg (str "\\" written (when opt (str "[" opt "]")) " is now written \\" now ".")})))

(def ^:private igt-commands
  "The commands an IGT block may contain."
  (conj (set (map :name tiers/tiers)) "igt"))

;; ---------------------------------------------------------------------------
;; An IGT block
;;
;; `parse-block` reads an IGT's tiers in steps: the text first (its words
;; are what every other tier aligns with), then the tiers with an entry per
;; word, the syllables, and finally the problems with the fields
;; themselves. Each step gets the block's context and returns what it read
;; with its problems, already pointing at their source lines.

(defn- block-context
  "What the steps need: each tier's value and source line, by tier key (the
  first field of each tier counts)."
  [fields]
  (let [firsts (reverse (filter :command fields))]
    {:fields fields
     :values (into {} (for [f firsts] [(tier-key f) (field-value f)]))
     :source-lines (merge {"igt" (:source-line (first fields))}
                          (into {} (for [f firsts] [(tier-key f) (:source-line f)])))}))

(defn- value
  "The value of tier `k` in the block, or nil if it has none."
  [ctx k]
  (get-in ctx [:values k]))

(defn- at-source-line
  "Problems of tier `k`, pointing at its source line (or the block's)."
  [ctx k ds]
  (let [lines (:source-lines ctx)]
    (map #(assoc % :source-line (or (lines k) (lines "igt"))) ds)))

(defn- read-translit
  "The transliteration, a surface: each word as written, without its
  punctuation, until \\morph[translit] divides it."
  [ctx words]
  (if (value ctx "translit")
    (let [[ws ds] (assign-by-word words (entries (value ctx "translit"))
                                  #(assoc-in %1 [:surfaces "translit" :segs] (morph/whole-word (chars/strip-punct %2)))
                                  (str "\\" (tiers/command "translit")))]
      [ws (at-source-line ctx "translit" ds)])
    [words []]))

(defn- segment-word
  "A word divided on surface `s` by its entry of the surface's \\morph tier."
  [word s entry]
  (let [result (morph/segment-word (model/surface-word word s) entry)]
    (update-in word [:surfaces s] assoc
               :segs (:segs result)
               :morph-src (chars/escapes-as-written entry)
               :morph-problems (morph/segment-problems result))))

(defn- read-morph-tiers
  "Each surface's \\morph tier (\\morph, \\morph[translit]), dividing its
  words into morphemes."
  [ctx words]
  (reduce (fn [[words ds] s]
            (let [k (model/segmentation-tier "morph" s)]
              (cond
                (not (value ctx k)) [words ds]
                (not (value ctx s))
                [words (into ds (at-source-line ctx k [{:level :error :msg (str "\\" k " segments the \\" (tiers/command s) " tier, but this IGT has none.")}]))]
                :else
                ;; Entries are split at spaces only: a \morph entry has no braces.
                (let [es (vec (remove #{"+"} (str/split (str/trim (value ctx k)) #"\s+")))
                      [ws d] (assign-by-word words es #(segment-word %1 s %2) (str "\\" k))]
                  [ws (into ds (at-source-line ctx k d))]))))
          [words []]
          model/surfaces))

(defn- read-entries
  "The tiers with an entry per word that the word stores
  (gloss.tiers/word-entries), each entry read by gloss.model/read-entry."
  [ctx words]
  (reduce (fn [[words ds] tier]
            (if-not (value ctx tier)
              [words ds]
              (let [es (vec (entries (value ctx tier)))
                    [ws d] (assign-by-word words es #(model/put-fields %1 (first (model/read-entry tier %2)))
                                           (str "\\" (tiers/command tier)))
                    problems (for [entry (take (count words) es)
                                   p (second (model/read-entry tier entry))]
                               {:level :warning :msg p})]
                [ws (into ds (at-source-line ctx tier (concat d problems)))])))
          [words []]
          tiers/word-entries))

(def ^:private word-steps
  "The tiers read word by word, in order: the transliteration before the
  \\morph tiers that divide it."
  [read-translit read-morph-tiers read-entries])

(defn- read-syllables
  "Each surface's \\syl tier, with the one \\scan, placed on the surface's
  letters. Returns {:prosody {surface p} :raw {tier kept-as-written}
  :diagnostics}. Whether the two surfaces' syllables agree is checked on
  the IGT afterwards (gloss.check), like their \\morph tiers."
  [ctx igt]
  (let [scan (value ctx "scan")
        pros (into {} (for [s model/surfaces
                            :let [k (model/segmentation-tier "syl" s)]
                            :when (value ctx k)]
                        [s (if (value ctx s)
                             (prosody/parse-prosody (model/surface-letters igt s) (map :start (model/surface-ranges igt s))
                                            scan (value ctx k) s)
                             {:diagnostics [{:level :error :msg (str "\\" k " syllabifies the \\" (tiers/command s) " tier, but this IGT has none.")}]})]))
        no-syl (when (and scan (empty? pros))
                 (let [msg "\\scan needs a \\syl (or \\syl[translit]) tier to say where the syllables are."]
                   {:raw {"scan" {:text scan :reason msg}}
                    :diagnostics [{:level :error :msg msg}]}))]
    {:prosody (into {} (for [[s p] pros :when (:prosody p)] [s (:prosody p)]))
     :raw (apply merge (concat (map :raw (vals pros)) [(:raw no-syl)]))
     :diagnostics (concat (mapcat (fn [[s p]] (at-source-line ctx (model/segmentation-tier "syl" s) (:diagnostics p))) pros)
                          (at-source-line ctx "scan" (:diagnostics no-syl)))}))

(defn- repeated-fields
  "The fields of a tier that an earlier field of the IGT already wrote
  (only the first is read: see `block-context`). Notes can be repeated."
  [fields]
  (second
   (reduce (fn [[seen out] f]
             (let [k (tier-key f)]
               [(conj seen k) (cond-> out (seen k) (conj f))]))
           [#{} []]
           (filter #(and (igt-commands (:command %)) (not= "note" (:command %))) fields))))

(defn- field-problems
  "Problems with the fields themselves: a + outside \\text, options a
  command doesn't take, reserved characters, tiers written twice, unknown
  commands."
  [ctx]
  (let [fields (:fields ctx)]
    (concat
     (for [f fields
           :when (and (:command f) (not (#{"text" "ft" "note"} (tier-key f)))
                      (some #{"+"} (str/split (str/trim (field-value f)) #"\s+")))]
       {:level :warning :source-line (:source-line f)
        :msg (str "Subline breaks (+) belong in \\text only; the + in \\" (tiers/command (tier-key f)) " is ignored.")})
     (option-problems fields)
     (for [f fields
           :let [k (tier-key f)]
           :when (checked? k)
           d (char-problems k (cond-> (field-value f) (#{"ft" "note"} k) chars/hide-escapes))]
       (assoc d :source-line (:source-line f)))
     (for [f fields
           :when (= "glmorph" (tier-key f))
           d (gloss-start-problems (field-value f))]
       (assoc d :source-line (:source-line f)))
     ;; Like an unknown command's, a repeated field isn't kept: an error.
     (for [f (repeated-fields fields)]
       {:level :error :source-line (:source-line f)
        :msg (str "This IGT already has \\" (tiers/command (tier-key f)) "; this one is dropped.")})
     (for [f fields
           :when (not (igt-commands (:command f)))
           :when (not (unsupported (:command f)))]
       ;; An unknown command's field isn't kept, so it is an error.
       {:level (if (:command f) :error :warning)
        :source-line (:source-line f)
        :msg (if (:command f)
               (str "Unknown command \\" (:command f) ".")
               "Text before any command.")}))))

(defn parse-block
  "Parses one IGT block (the fields from \\igt up to the next \\igt).
  Returns {:igt :diagnostics :source-lines}."
  [fields]
  (let [ctx (block-context fields)
        text (value ctx "text")
        {:keys [words breaks diagnostics]} (text/parse-tier (or text ""))
        text-problems (at-source-line ctx "text" (cond-> (vec diagnostics)
                                           (nil? text) (conj {:level :error :msg "This IGT has no \\text tier."})))
        [words word-problems] (reduce (fn [[ws ds] step]
                                        (let [[ws d] (step ctx ws)] [ws (into ds d)]))
                                      [words []]
                                      word-steps)
        start (first (filter #(= "igt" (:command %)) fields))
        kept (keep-unsupported fields)
        igt (cond-> {:id (new-id)
                     :label (when start (not-empty (field-value start)))
                     :words words}
              (seq breaks) (assoc :breaks breaks)
              (seq (:kept kept)) (assoc :unsupported (:kept kept)))
        ;; Syllables are placed on the words' letters, so they come last.
        {:keys [prosody raw] :as syllables} (read-syllables ctx igt)
        notes (mapv note-of (filter #(= "note" (:command %)) fields))
        igt (cond-> igt
              (seq prosody) (assoc :prosody prosody)
              (seq raw) (assoc :raw raw)
              (value ctx "ft") (assoc :free (value ctx "ft"))
              (seq notes) (assoc :notes notes))]
    {:igt igt
     :source-lines (:source-lines ctx)
     :diagnostics (vec (concat text-problems word-problems (:diagnostics kept)
                               (:diagnostics syllables) (field-problems ctx)))}))

;; ---------------------------------------------------------------------------
;; Documents

(defn- split-blocks
  "Splits fields into the header, IGT blocks and the back section. A block
  starts at \\igt, or at a \\text when the current block already has one
  (the text itself: \\text[translit] doesn't start a block); \\section
  starts a section, whose blocks carry its id; after \\end comes the back
  section. Returns {:header :blocks [{:fields :section}] :sections [{:id
  :label :title :source-line}] :back :end-line}."
  [fields]
  (reduce (fn [{:keys [blocks section back?] :as acc} f]
            (let [cur (peek blocks)]
              (cond
                back? (update acc :back conj f)
                (= "end" (:command f)) (assoc acc :back? true :end-line (:source-line f))
                (= "section" (:command f))
                (let [id (new-id)]
                  (-> acc
                      (update :sections conj (cond-> {:id id :title (field-value f) :source-line (:source-line f)}
                                               (not (str/blank? (:opt f))) (assoc :label (:opt f))))
                      (assoc :section id)))
                (or (= "igt" (:command f))
                    (and (= "text" (tier-key f))
                         (or (nil? cur) (some #(= "text" (tier-key %)) (:fields cur)))))
                (update acc :blocks conj (cond-> {:fields [f]} section (assoc :section section)))
                (and (nil? cur) (nil? section)) (update acc :header conj f)
                (nil? cur) (update acc :blocks conj (cond-> {:fields [f]} section (assoc :section section)))
                :else (update acc :blocks #(conj (pop %) (update cur :fields conj f))))))
          {:header [] :blocks [] :sections [] :back []}
          fields))

(defn- header-chars
  "Reserved characters and stray backslashes in a header field."
  [f]
  (map #(assoc % :source-line (:source-line f)) (char-problems (:command f) (chars/hide-escapes (field-value f)))))

(defn- parse-header
  "The header's fields read into the document's settings (title, \\abbr,
  \\color, …). Returns {:doc :diagnostics :version}, :version being the
  format version its \\glosshubformat gives (nil without one)."
  [fields]
  (reduce
   (fn [{:keys [doc] :as acc} f]
     (let [v (field-value f)]
       (case (:command f)
         "glosshubformat" (let [n (if (re-matches #"[1-9][0-9]*" v) (js/parseInt v 10) 0)]
                      (cond
                        (zero? n) (update acc :diagnostics conj
                                         {:level :error :source-line (:source-line f)
                                          :msg (str "\\glosshubformat takes the format version, a whole number "
                                                    "(\\glosshubformat " model/format-version "), not “" v "”.")})
                        (> n model/format-version) (-> acc
                                                       (assoc :version n)
                                                       (update :diagnostics conj
                                                               {:level :warning :source-line (:source-line f)
                                                                :msg (str "This text is written in version " n
                                                                          " of the GlossHub format, and this site reads versions up to "
                                                                          model/format-version ". Parts of it may not show.")}))
                        :else (assoc acc :version n)))
         "title" (-> acc (assoc-in [:doc :title] v) (update :diagnostics into (header-chars f)))
         "lang" (assoc-in acc [:doc :lang] v)
         "about" (-> acc (assoc-in [:doc :about] v) (update :diagnostics into (header-chars f)))
         "order" (assoc-in acc [:doc :order] (mapv tiers/row-key (str/split v #"\s+")))
         "dir" (if (#{"rtl" "ltr"} (str/lower-case v))
                 (assoc-in acc [:doc :dir] (str/lower-case v))
                 (update acc :diagnostics conj {:level :error :msg (str "\\dir must be rtl or ltr, not “" v "”.")}))
         "abbr" (if-let [[_ tag meaning] (re-matches #"(?s)(\S+)\s+(.+)" v)]
                  (assoc-in acc [:doc :abbrevs tag] meaning)
                  (update acc :diagnostics conj {:level :error :msg (str "\\abbr needs an abbreviation and its meaning: " v)}))
         "color" (if-let [[_ tag color label] (re-matches #"(\S+)\s+(\S+)(?:\s+(.+))?" v)]
                   (update-in acc [:doc :colors] (fnil conj [])
                              (cond-> {:tag tag :color color} label (assoc :label label)))
                   (update acc :diagnostics conj {:level :error :msg (str "\\color needs an abbreviation and a color: " v)}))
         "note" (-> acc
                    (update-in [:doc :notes] (fnil conj []) (note-of f))
                    (update :diagnostics conj
                            {:level :warning :source-line (:source-line f)
                             :msg "Notes belong under an IGT or after \\end, not in the header; this one will be written after \\end."}))
         (if (unsupported (:command f))
           (let [{:keys [kept diagnostics]} (keep-unsupported [f])]
             (-> acc
                 (update-in [:doc :unsupported] (fnil into []) kept)
                 (update :diagnostics into diagnostics)))
         (update acc :diagnostics conj
                 {:level (if (:command f) :error :warning)
                  :source-line (:source-line f)
                  :msg (if (:command f)
                         (str "Unknown header command \\" (:command f) ".")
                         "Text before any command.")})))))
   {:doc {} :diagnostics []}
   fields))

(defn- parse-blocks
  "Parses IGT blocks in order, each in its section, and checks that they
  are written as format `version` writes them. Returns {:igts :source-lines
  :diagnostics}, each diagnostic of an IGT with its id (:igt)."
  [blocks version]
  (let [parsed (mapv (fn [{:keys [fields section]}]
                       (cond-> (parse-block fields)
                         section (assoc-in [:igt :section] section)))
                     blocks)]
    {:igts (mapv :igt parsed)
     :source-lines (into {} (map (juxt (comp :id :igt) :source-lines) parsed))
     :diagnostics (concat (for [p parsed, d (:diagnostics p)]
                            (assoc d :igt (:id (:igt p))))
                          (version-1-problems (mapcat :fields blocks) version))}))

(defn- parse-back
  "The back section, after \\end: notes only."
  [fields]
  (reduce (fn [acc f]
            (if (= "note" (:command f))
              (update acc :notes conj (note-of f))
              (update acc :diagnostics conj
                      {:level :warning :source-line (:source-line f)
                       :msg (if (:command f)
                              (str "Only notes may follow \\end; \\" (:command f) " is ignored there.")
                              "Only notes may follow \\end; this text is ignored.")})))
          {:notes [] :diagnostics []}
          fields))

(defn- section-problems
  "Section labels unique in the text, IGT labels unique in their section."
  [sections igts]
  (concat
   (for [[label ss] (group-by :label sections)
         :when (and label (> (count ss) 1))]
     {:level :error :source-line (:source-line (second ss))
      :msg (str "Two sections are labelled “" label "”; section labels must be unique.")})
   (for [[_ ls] (group-by :section igts)
         [label same] (group-by :label ls)
         :when (and label (> (count same) 1))]
     {:level :error :igt (:id (second same))
      :msg (str "Two IGTs are labelled “" label "”" " in the same section; IGT labels must be unique there.")})))

(defn- parse-igts*
  "`parse-igts`, with escapes still hidden."
  [src]
  (let [{:keys [header blocks sections back end-line]} (split-blocks (field-lines src))
        ;; Notes before the first block, or after \end, go under the first IGT.
        loose (into (mapv note-of (filter #(= "note" (:command %)) header))
                    (:notes (parse-back back)))
        stray (concat
               (for [f header :when (not= "note" (:command f))]
                 {:level :warning :source-line (:source-line f)
                  :msg (str "Ignored " (if (:command f) (str "\\" (:command f)) "text")
                            " before the first IGT.")})
               (for [s sections]
                 {:level :warning :source-line (:source-line s)
                  :msg "Sections can't be added here; edit the document's source to add one."})
               (when end-line
                 [{:level :warning :source-line end-line
                   :msg "\\end can't be added here; edit the document's source."}]))
        ;; An IGT's source editor holds IGTs the site wrote, so it is read as
        ;; the version the site writes.
        {:keys [igts source-lines diagnostics]} (parse-blocks (map #(dissoc % :section) blocks)
                                                              model/format-version)]
    {:igts (if (and (seq loose) (seq igts))
             (update-in igts [0 :notes] (fnil into []) loose)
             igts)
     :source-lines source-lines
     :diagnostics (vec (concat stray diagnostics))}))

(defn- parse-document*
  "`parse-document`, with escapes still hidden."
  [src version]
  (let [{:keys [header blocks sections back]} (split-blocks (field-lines src))
        {:keys [doc diagnostics] :as head} (parse-header header)
        {:keys [igts source-lines] :as parsed} (parse-blocks blocks (or (:version head) version 1))
        back (parse-back back)]
    {:doc (cond-> (-> {:title "Untitled" :abbrevs {} :colors [] :notes []}
                      (merge doc)
                      (update :notes (fnil into []) (:notes back))
                      (assoc :igts igts))
            (seq sections) (assoc :sections (mapv #(dissoc % :source-line) sections)))
     :source-lines source-lines
     :diagnostics (vec (concat diagnostics
                               (:diagnostics parsed)
                               (:diagnostics back)
                               (section-problems sections igts)))}))

(defn parse-igts
  "Parses source containing only IGT blocks (as in an IGT's source editor),
  in the format version the site writes. Returns {:igts :source-lines
  :diagnostics}. See `parse-document` for :source-lines."
  [src]
  (chars/restore-escapes (parse-igts* src)))

(defn parse-document
  "Parses a whole text. Returns {:doc :source-lines :diagnostics}: the
  document, where each IGT's tiers are in the source ({igt-id {tier-key
  source-line}}, for problems found later, see gloss.check), and the
  problems found while reading. A text without \\glosshubformat is of
  format `version`, by default 1 (docs/format.md §2); the site's own
  sources, such as the Guide's examples, are of the version it writes."
  [src & [version]]
  (chars/restore-escapes (parse-document* src version)))

;; ---------------------------------------------------------------------------
;; Filling tiers from the text

(defn fillable
  "The tiers that can be filled in from the words of the surface they
  divide, for source `src`: \\morph and \\syl, and their [translit]
  versions when an IGT has a \\text[translit]."
  [src]
  (let [translit? (some #(= "translit" (tier-key %)) (field-lines src))]
    (for [s model/surfaces
          :when (or (= s "text") translit?)
          tier ["morph" "syl"]]
      (model/segmentation-tier tier s))))

(defn fill-tier
  "Adds the tier `k` (see `fillable`) to every IGT of `src` that has the
  tier it comes from but not this one: each word's letters, without
  punctuation, ready for morpheme or syllable breaks, indented under that
  tier as the site writes it, a \\syl after the tier's \\morph. The rest of
  the source is kept as written."
  [src k]
  (let [command (str "\\" k)
        from (if (str/ends-with? k "[translit]") "translit" "text")
        lines (str/split-lines (normalize src))
        {:keys [blocks]} (split-blocks (field-lines src))
        inserts (into {}
                      (for [{:keys [fields]} blocks
                            :let [src-field (first (filter #(= from (tier-key %)) fields))]
                            :when (and src-field (not (some #(= k (tier-key %)) fields)))
                            :let [igt (:igt (parse-block fields))
                                  word-of #(model/surface-word % from)
                                  entries (for [w (:words igt)
                                                :let [core (chars/restore-escapes (word-of w))]]
                                            (apply str (for [c (chars/letters-of core)] (subs core (:start c) (:end c)))))
                                  next-field (second (drop-while #(not= src-field %) fields))
                                  after (if (and (str/starts-with? k "syl") next-field
                                                 (= (model/segmentation-tier "morph" from) (tier-key next-field)))
                                          next-field
                                          src-field)]]
                        ;; After that tier and its continuation lines.
                        [(+ (dec (:source-line after)) (count (:cont after)))
                         (str tiers/segmentation-indent command " " (str/join " " entries))]))]
    (str/join "\n" (apply concat (map-indexed (fn [i l] (if-let [ins (inserts i)] [l ins] [l])) lines)))))
