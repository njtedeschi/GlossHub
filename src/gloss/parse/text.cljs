(ns gloss.parse.text
  "Reading the \\text row into words: each word as written, with its
  punctuation, formatting, link and ruby readings, and the subline breaks
  (docs/format.md §5.1 and §6)."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.formatting :as fmt]
            [gloss.model :as model]
            [gloss.morph :as morph]))

;; ---------------------------------------------------------------------------
;; Tokens
;;
;; The row is split at spaces. Punctuation, formatting delimiters and a
;; link's brackets are taken off each end of a token; what is left is the
;; word.

(defn- strip-lead
  "Takes punctuation, formatting delimiters and a link's `[` off the start
  of a token; returns [rest items]."
  [token]
  (loop [t token, items []]
    (let [c (subs t 0 1)]
      (cond
        (= t "") [t items]
        (= c "[") (recur (subs t 1) (conj items [:link-open]))
        (fmt/delimiter-at t 0 true) (let [[d style] (fmt/delimiter-at t 0 true)]
                                       (recur (subs t (count d)) (conj items [:fmt style d])))
        (chars/lead-punct c) (recur (subs t 1) (conj items [:punct c]))
        :else [t items]))))

(def ^:private link-close-pattern
  "The end of a token that closes a link: `](address)`."
  #"\]\(([^()\s]*)\)$")

(defn- trail-delimiter
  "The formatting delimiter that token `t` ends with, as [delim style], or
  nil."
  [t]
  (some (fn [[d style]] (when (str/ends-with? t d) [d style])) fmt/delimiters))

(defn- strip-trail
  "Takes punctuation, formatting delimiters and a link's `](address)` off
  the end of a token; returns [core items-in-order]."
  [token]
  (loop [t token, items '()]
    (let [n (count t)
          c (when (pos? n) (subs t (dec n)))]
      (if-let [[m url] (re-find link-close-pattern t)]
        (recur (subs t 0 (- n (count m))) (conj items [:link-close url]))
        (cond
          (zero? n) [t (vec items)]
          (= c "]") (recur (subs t 0 (dec n)) (conj items [:link-close nil]))
          (trail-delimiter t) (let [[d style] (trail-delimiter t)]
                                (recur (subs t 0 (- n (count d))) (conj items [:fmt style d])))
          (chars/trail-punct c) (recur (subs t 0 (dec n)) (conj items [:punct c]))
          :else [t (vec items)])))))

(defn- markup-only?
  "Whether a token is only markup (no word), and which side it belongs to:
  :opener (to the next word) or :closer (to the word before)."
  [t]
  (let [[rest-t lead] (strip-lead t)]
    (cond
      (and (= "" rest-t) (seq lead) (every? #(= :link-open (first %)) lead))
      :opener
      :else
      (let [[core trail] (strip-trail t)]
        (when (and (= "" core) (some #(= :link-close (first %)) trail)
                   (every? #(#{:link-close :punct} (first %)) trail))
          :closer)))))

(defn- attach-markup-tokens
  "Glues tokens that are only markup to their word: `[ puerī` is read as
  `[puerī`, and `puerī ](address)` as `puerī](address)`. Formatting
  delimiters aren't glued: they must touch their word, as in Markdown."
  [tokens]
  (reduce (fn [out t]
            (cond
              (= t "+") (conj out t)
              (and (seq out) (not= "+" (peek out)) (= :opener (markup-only? (peek out))))
              (conj (pop out) (str (peek out) t))
              (and (seq out) (not= "+" (peek out)) (= :closer (markup-only? t)))
              (conj (pop out) (str (peek out) t))
              :else (conj out t)))
          []
          tokens))

(defn read-core
  "A word's core (escapes hidden): ruby markup `{base|reading}` and
  formatting delimiters inside the word taken out. Returns {:text :rubies
  :toggles [[offset style delimiter]]}; underscores inside a word are
  letters, not delimiters."
  [core]
  (let [n (count core)]
    (loop [i 0, plain "", mode :text, start 0, reading "", rubies [], toggles []]
      (if (>= i n)
        {:text plain :rubies rubies :toggles toggles}
        (let [c (subs core i (inc i))]
          (cond
            (= mode :reading)
            (if (= c "}")
              (recur (inc i) plain :text start "" (conj rubies {:start start :end (count plain) :text reading}) toggles)
              (recur (inc i) plain :reading start (str reading c) rubies toggles))
            (and (= mode :text) (= c "{")) (recur (inc i) plain :base (count plain) "" rubies toggles)
            (and (= mode :base) (= c "|")) (recur (inc i) plain :reading start "" rubies toggles)
            (fmt/delimiter-at core i false)
            (let [[d style] (fmt/delimiter-at core i false)]
              (recur (+ i (count d)) plain mode start reading rubies (conj toggles [(count plain) style d])))
            :else (recur (inc i) (str plain c) mode start reading rubies toggles)))))))

(defn- reserved-diagnostics
  "Warnings for reserved characters left in words once markup is read
  (gloss.chars/reserved): one per character, naming the words."
  [words]
  (let [found (reduce (fn [acc w]
                        (let [core (str (model/word-letters w) (apply str (map :text (:ruby w))))]
                          (reduce #(update %1 (str %2) (fnil conj []) core) acc
                                  (distinct (filter chars/reserved (map str core))))))
                      (sorted-map) words)]
    (for [[c cores] found
          :let [where (str/join ", " (map #(str "“" % "”") (distinct cores)))]]
      {:level :warning
       :msg (cond
              (= c "\\") (str "A backslash before a letter starts a command, and \\text has none (in " where "); "
                               "write \\\\ for a backslash.")
              (chars/unused-reserved c) (str "“" c "” (in " where ") is reserved in \\text for future use; "
                                             "write \\" c " for a literal “" c "”.")
              :else (str "“" c "” (in " where ") isn't read as markup there; "
                         "write \\" c " for a literal “" c "”."))})))

;; ---------------------------------------------------------------------------
;; Reading the row
;;
;; The row is read token by token, with a state: the words so far, the
;; formatting styles open (:on), where an open link started (:link-from),
;; punctuation waiting for the next word (:pre), the subline breaks, and the
;; problems found (:diags).

(defn- warn [st msg] (update st :diags conj {:level :warning :msg msg}))

(defn- punct-of
  "The punctuation among the items taken off a token's end."
  [items]
  (apply str (keep (fn [[k v]] (when (= k :punct) v)) items)))

(defn- add-break
  "A `+`: a subline starts at the next word."
  [st]
  (let [n (count (:words st))]
    (cond-> st (and (pos? n) (not= (peek (:breaks st)) n)) (update :breaks conj n))))

(defn- open-styles
  "The styles the delimiters before a word open."
  [st lead core]
  (reduce (fn [st [k style d]]
            (cond
              (not= k :fmt) st
              ((:on st) style) (warn st (str d " before “" core "” opens " (name style) " that is already open."))
              :else (update st :on conj style)))
          st lead))

(defn- open-link
  "A `[` before a word opens a link there."
  [st lead core]
  (if (some #(= :link-open (first %)) lead)
    (cond-> (assoc st :link-from (count (:words st)))
      (:link-from st) (warn (str "A link opens at “" core "” before the one before it is closed.")))
    st))

(defn- word-formatting
  "The formatting runs of a word `n` letters long: the styles open before
  it, toggled by the delimiters inside it (a style opened there must close
  there), then closed by the delimiters after it. Returns [st runs]."
  [st toggles trail core n]
  (let [[on changes inside]
        (reduce (fn [[on changes inside] [at style _]]
                  (let [on' (if (on style) (disj on style) (conj on style))]
                    [on' (conj changes [at on']) (if (on style) (disj inside style) (conj inside style))]))
                [(:on st) [[0 (:on st)]] #{}]
                toggles)
        [st inside]
        (reduce (fn [[st inside] [k style d]]
                  (cond
                    (not= k :fmt) [st inside]
                    ((:on st) style) [(update st :on disj style) (disj inside style)]
                    :else [(warn st (str d " after “" core "” closes " (name style) ", which isn't open.")) inside]))
                [(assoc st :on on) inside]
                trail)]
    [(cond-> (update st :on #(apply disj % inside))
       (seq inside)
       (warn (str "Formatting opened inside “" core "” must close in it; it is closed at the word's end.")))
     (fmt/runs changes n)]))

(defn- close-link
  "A link's `](address)` after word `i` puts the address on the words since
  the link opened. Returns [st address], the address if the link closed."
  [st trail core i]
  (let [closes? (some #(= :link-close (first %)) trail)
        url (some #(when (= :link-close (first %)) (second %)) trail)
        from (:link-from st)]
    (cond
      (not closes?) [st nil]
      (nil? url) [(warn st (str "A link needs its address: [words](address), at “" core "”.")) nil]
      (nil? from) [(warn st (str "](" url ") after “" core "” closes no link; start it with [.")) nil]
      :else [(-> st
                 (update :words (fn [ws] (reduce #(assoc-in %1 [%2 :link] url) ws (range from i))))
                 (assoc :link-from nil))
             url])))

(defn- read-word
  "A token with a word in it: the word, its punctuation, formatting, link
  and ruby."
  [st lead core trail]
  (let [{:keys [text rubies toggles]} (read-core core)
        [st runs] (word-formatting st toggles trail core (count text))
        [st link] (close-link st trail core (count (:words st)))
        word (cond-> {:pre (str (:pre st) (punct-of lead)) :post (punct-of trail)
                      :surfaces {"text" {:segs (morph/whole-word text)}}}
               (seq runs) (assoc :fmt runs)
               link (assoc :link link)
               (seq rubies) (assoc :ruby rubies))]
    (cond-> (-> st (update :words conj word) (assoc :pre ""))
      (not= (count (re-seq #"\{" core)) (count (re-seq #"\}" core)))
      (warn (str "Unfinished ruby markup in “" core "”; write {base|reading}.")))))

(defn- attach-punctuation
  "A token of punctuation only joins the word before it (or, at the start,
  the next one); a delimiter in it formats nothing."
  [st token lead trail]
  (let [text (str (punct-of lead) (punct-of trail))
        st (cond-> st
             (some #(= :fmt (first %)) (concat lead trail))
             (warn (str "“" token "”: a formatting delimiter must touch the word it formats.")))]
    (if-let [n (not-empty (:words st))]
      (update-in st [:words (dec (count n)) :post] str " " text)
      (update st :pre str text " "))))

(defn- read-token [st token]
  (if (= token "+")
    (add-break st)
    (let [[t lead] (strip-lead token)
          [core trail] (strip-trail t)
          opened (-> st (open-styles lead core) (open-link lead core))]
      (if (= core "")
        (attach-punctuation (assoc opened :on (:on st)) token lead trail)
        (read-word opened lead core trail)))))

(defn parse-row
  "Parses a \\text row into {:words :breaks :diagnostics}. A `+`
  token starts a new subline.
  Formatting is kept on each word as runs (:fmt); a delimiter at the start
  or end of a word may span words, and closes within the line; one inside
  a word must close in it. A link ([words](address)) sets :link on its
  words."
  [s]
  (let [{:keys [words breaks diags on link-from]}
        (reduce read-token
                {:words [] :on #{} :link-from nil :pre "" :breaks [] :diags []}
                (attach-markup-tokens (remove str/blank? (str/split (str/trim s) #"\s+"))))]
    {:words words
     :breaks (vec (filter #(< % (count words)) breaks))
     :diagnostics (cond-> (into diags (reserved-diagnostics words))
                    (seq on)
                    (conj {:level :warning
                           :msg "Formatting opened in this line isn't closed in it; it is closed at the line's end."})
                    link-from
                    (conj {:level :warning
                           :msg (str "A link opened at “" (model/word-letters (get words link-from))
                                     "” isn't closed in this line: [words](address).")}))}))
