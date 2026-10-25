(ns gloss.formatting
  "Formatting: *italic* (or _italic_), **bold** (or __bold__), ~~struck~~,
  ==highlighted==, ++underlined++, and [links](address). Shared by \\text,
  \\glword entries and prose (\\ft, notes, \\about); see docs/format.md §6.

  Formatting is kept as runs over a text: [{:start :end :styles #{…}}],
  offsets into the plain text, only where some style applies."
  (:require [clojure.string :as str]))

(def delimiters
  "Delimiter -> style, longest first so `**` is read before `*`."
  [["**" :bold] ["__" :bold] ["~~" :strike] ["==" :highlight] ["++" :underline]
   ["*" :italic] ["_" :italic]])

(def style-delimiter
  "The delimiter the site writes for each style."
  {:italic "*" :bold "**" :strike "~~" :highlight "==" :underline "++"})

(def style-order
  "The order in which a run's styles are written out and given CSS classes."
  [:bold :italic :strike :highlight :underline])

(defn delimiter-at
  "The delimiter starting at `i` of `s`, as [delim style], or nil.
  Underscores count only when `underscores?`."
  [s i underscores?]
  (some (fn [[d style]]
          (when (and (str/starts-with? (subs s i) d)
                     (or underscores? (not (str/starts-with? d "_"))))
            [d style]))
        delimiters))

(defn runs
  "Runs from style changes: `changes` is [[offset styles-from-here] …] in
  order, over a text of length `n`; empty stretches are left out, and
  neighbours with the same styles merged."
  [changes n]
  (let [pts (concat changes [[n nil]])]
    (reduce (fn [out [[a styles] [b _]]]
              (if (or (empty? styles) (>= a b))
                out
                (let [prev (peek out)]
                  (if (and prev (= (:end prev) a) (= (:styles prev) styles))
                    (conj (pop out) (assoc prev :end b))
                    (conj out {:start a :end b :styles styles})))))
            []
            (partition 2 1 pts))))

(defn styles-at
  "The styles at offset `i` of a text with `runs`."
  [runs i]
  (or (some #(when (and (<= (:start %) i) (< i (:end %))) (:styles %)) runs) #{}))

(defn common-styles
  "The styles every one of `positions` has (none for no positions)."
  [runs positions]
  (if (empty? positions)
    #{}
    (reduce (fn [acc p] (set (filter acc (styles-at runs p)))) (styles-at runs (first positions)) (rest positions))))

(defn delims
  "The delimiters for `styles`, in the site's order."
  [styles]
  (apply str (for [s style-order :when (contains? styles s)] (style-delimiter s))))

(defn with-delimiters
  "A text written out with its `runs` as delimiters, one where a style
  starts or stops (`escape` escapes the plain stretches)."
  [text runs escape]
  (let [n (count text)
        cuts (sort (distinct (concat [0 n] (mapcat (juxt :start :end) runs))))]
    (apply str
           (concat
            (for [[a b] (partition 2 1 cuts) :when (< a b)
                  :let [before (if (pos? a) (styles-at runs (dec a)) #{})
                        here (styles-at runs a)]]
              (str (delims (set (remove here before)))
                   (delims (set (remove before here)))
                   (escape (subs text a b))))
            [(delims (styles-at runs (dec n)))]))))

;; ---------------------------------------------------------------------------
;; Entries: a \glword entry, where formatting must close within the entry

(defn read-glword
  "Reads formatting in one \\glword entry (escapes already hidden). Returns {:text
  :fmt :problems}; a delimiter left open is reported and closed at the end."
  [s]
  (let [n (count s)]
    (loop [i 0, text "", on #{}, changes [], problems []]
      (if (>= i n)
        {:text text
         :fmt (runs changes (count text))
         :problems (cond-> problems
                     (seq on) (conj (str "Formatting opened in “" s "” isn't closed in it.")))}
        (if-let [[d style] (delimiter-at s i true)]
          (let [on (if (on style) (disj on style) (conj on style))]
            (recur (+ i (count d)) text on (conj changes [(count text) on]) problems))
          (recur (inc i) (str text (subs s i (inc i))) on changes problems))))))

;; ---------------------------------------------------------------------------
;; Prose: Markdown's way, where an unmatched delimiter is just a character

(defn- flanking
  "Whether the delimiter at [i, j) of `s` can open (followed by a
  non-space) and close (preceded by a non-space). An underscore inside a
  word does neither."
  [s i j d]
  (let [before (when (pos? i) (subs s (dec i) i))
        after (when (< j (count s)) (subs s j (inc j)))
        space? #(or (nil? %) (re-matches #"\s" %))
        word? #(and % (re-matches #"[\p{L}\p{N}]" %))]
    (if (str/starts-with? d "_")
      {:open? (and (not (space? after)) (not (word? before)))
       :close? (and (not (space? before)) (not (word? after)))}
      {:open? (not (space? after)) :close? (not (space? before))})))

(defn prose
  "Prose as pieces: [{:text :styles #{} :link url-or-nil} …]. A backslash
  escapes the next character; delimiters pair Markdown's way, and one
  without a partner is shown as it is; [text](address) is a link."
  [s]
  (let [s (str s)
        n (count s)
        ;; Tokens: {:text} for plain characters, {:delim :style :open? :close?},
        ;; {:link-open}, {:link-close url}.
        tokens (loop [i 0, out []]
                 (if (>= i n)
                   out
                   (let [c (subs s i (inc i))]
                     (cond
                       (and (= c "\\") (< (inc i) n) (not (re-matches #"[\p{L}\p{N}\s]" (subs s (inc i) (+ i 2)))))
                       (recur (+ i 2) (conj out {:text (subs s (inc i) (+ i 2))}))
                       (= c "[")
                       (recur (inc i) (conj out {:link-open true :raw "["}))
                       (and (= c "]") (re-find #"^\]\(([^()\s]*)\)" (subs s i)))
                       (let [[m url] (re-find #"^\]\(([^()\s]*)\)" (subs s i))]
                         (recur (+ i (count m)) (conj out {:link-close url :raw m})))
                       :else
                       (if-let [[d style] (delimiter-at s i true)]
                         (recur (+ i (count d)) (conj out (merge {:delim d :style style :raw d}
                                                                 (flanking s i (+ i (count d)) d))))
                         (recur (inc i) (conj out {:text c})))))))
        ;; Pair delimiters: each closer with the latest open one of its kind.
        paired (loop [k 0, stack {}, pairs #{}]
                 (if (>= k (count tokens))
                   pairs
                   (let [{:keys [delim open? close?]} (tokens k)]
                     (cond
                       (and delim close? (seq (get stack delim)))
                       (recur (inc k) (update stack delim pop) (conj pairs (peek (get stack delim)) k))
                       (and delim open?) (recur (inc k) (update stack delim (fnil conj []) k) pairs)
                       :else (recur (inc k) stack pairs)))))
        links (loop [k 0, open nil, pairs {}]
                (if (>= k (count tokens))
                  pairs
                  (let [t (tokens k)]
                    (cond
                      (:link-open t) (recur (inc k) k pairs)
                      (and (:link-close t) open) (recur (inc k) nil (assoc pairs open (:link-close t) k :close))
                      :else (recur (inc k) open pairs)))))]
    (loop [k 0, on #{}, link nil, out []]
      (if (>= k (count tokens))
        out
        (let [t (tokens k)]
          (cond
            (and (:delim t) (paired k))
            (recur (inc k) (if (on (:style t)) (disj on (:style t)) (conj on (:style t))) link out)
            (and (:link-open t) (string? (links k))) (recur (inc k) on (links k) out)
            (and (:link-close t) (= :close (links k))) (recur (inc k) on nil out)
            :else
            (let [text (or (:text t) (:raw t))
                  prev (peek out)]
              (recur (inc k) on link
                     (if (and prev (= (:styles prev) on) (= (:link prev) link))
                       (conj (pop out) (update prev :text str text))
                       (conj out {:text text :styles on :link link}))))))))))
