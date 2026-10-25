(ns gloss.app.markdown
  "Markdown pages (gloss.app.markdown-pages lists them): a page read from its
  source, and the small Markdown subset it is written in: headings (## and
  ###; # is the page's title), paragraphs, bullet lists (- ), fenced blocks
  (```lang … ```), and inline `code`, **bold**, *italics* and
  [links](#target). In a page, a ```gloss block is a live example; ```gloss
  broken marks one that shows a problem on purpose, and ```gloss grid shows
  an IGT's source on a grid instead, a row for each tier and a column for
  each word. A caption in double quotes can end the line that opens a
  block: ```gloss \"Lezgian (Haspelmath 1993: 207)\"."
  (:require [clojure.string :as str]))

(defn- front-matter
  "A page's front matter, the `key: value` lines between two `---` lines at
  its top, as a map with keyword keys, and the lines after it."
  [lines]
  (if (= "---" (first lines))
    (let [[fields [_ & more]] (split-with #(not= "---" %) (rest lines))]
      [(into {} (for [f fields
                      :let [[_ k v] (re-matches #"([a-z]+):\s*(.*)" f)]
                      :when k]
                  [(keyword k) (str/trim v)]))
       more])
    [{} lines]))

(defn read-page
  "A page read from its Markdown: {:front-matter :title :body}. A page
  starts with front matter (`---` lines, holding its description), then a
  `# Title` line (without one, :title is nil), then its body."
  [src]
  (let [[front lines] (front-matter (str/split-lines src))
        lines (drop-while str/blank? lines)
        title (second (re-matches #"# (.*)" (str (first lines))))]
    {:front-matter front
     :title title
     :body (str/join "\n" (cond-> lines title rest))}))

(defn blocks
  "Splits Markdown into blocks:
  {:type :heading :level n :text}, {:type :para :text}, {:type :list :items},
  {:type :fence :info \"gloss\" :text}, with :caption when the fence's line
  ends with one in double quotes."
  [src]
  (loop [lines (str/split-lines (str/replace (str src) "\r" "")), out [], para [], items []]
    (let [flush (fn [out]
                  (cond-> out
                    (seq para) (conj {:type :para :text (str/join " " para)})
                    (seq items) (conj {:type :list :items items})))]
      (if-let [line (first lines)]
        (cond
          (str/starts-with? line "```")
          (let [[_ info caption] (re-matches #"^(.*?)\s*(?:\"(.*)\")?$" (str/trim (subs line 3)))
                [body more] (split-with #(not (str/starts-with? % "```")) (rest lines))]
            (recur (rest more)
                   (conj (flush out) (cond-> {:type :fence :info info :text (str/join "\n" body)}
                                       caption (assoc :caption caption)))
                   [] []))

          (re-matches #"#{1,3} .*" line)
          (let [[_ hashes text] (re-matches #"(#{1,3}) (.*)" line)]
            (recur (rest lines) (conj (flush out) {:type :heading :level (count hashes) :text text}) [] []))

          (str/starts-with? line "- ")
          (recur (rest lines) (cond-> out (seq para) (conj {:type :para :text (str/join " " para)}))
                 [] (conj items (subs line 2)))

          (str/blank? line)
          (recur (rest lines) (flush out) [] [])

          ;; A line indented under a list item continues it.
          (and (seq items) (re-matches #"\s+\S.*" line))
          (recur (rest lines) out para (conj (pop items) (str (peek items) " " (str/trim line))))

          :else
          (recur (rest lines) (cond-> out (seq items) (conj {:type :list :items items}))
                 (conj para (str/trim line)) []))
        (flush out)))))

(def ^:private inline-pattern
  #"`([^`]+)`|\*\*([^*]+)\*\*|\*([^*]+)\*|\[([^\]]+)\]\(([^)]+)\)")

(defn inline
  "Hiccup for inline Markdown, as a seq (a fragment, not an element). A link
  out of the site (http…) opens in a new tab."
  [text]
  (let [re (js/RegExp. (.-source inline-pattern) "g")]
    (loop [pos 0, out []]
      (if-let [m (.exec re text)]
        (let [i (.-index m)
              [whole code strong em label href] (vec m)]
          (recur (+ i (count whole))
                 (conj out
                       (subs text pos i)
                       (cond
                         code [:code code]
                         strong [:strong strong]
                         em [:em em]
                         label [:a (cond-> {:href href}
                                     (str/starts-with? href "http") (assoc :target "_blank" :rel "noopener"))
                                label]))))
        (seq (conj out (subs text pos)))))))

(defn render
  "Hiccup for Markdown blocks, a heading at its own level (## is h2);
  `fence` renders a fenced block (given the block and its index among the
  document's fences)."
  [src fence]
  (let [bs (blocks src)
        ;; How many fences come before each block: a fence's index.
        fences-before (reductions + 0 (map #(if (= :fence (:type %)) 1 0) bs))]
    (map (fn [b n]
           (case (:type b)
             :heading [(keyword (str "h" (:level b))) (inline (:text b))]
             :para [:p (inline (:text b))]
             :list [:ul (for [item (:items b)] [:li (inline item)])]
             :fence (fence b n)))
         bs fences-before)))

(defn examples
  "A page's live examples: {:source :broken?}."
  [page]
  (for [b (blocks (:body page))
        :when (and (= :fence (:type b)) (str/starts-with? (:info b) "gloss"))]
    {:source (:text b) :broken? (str/includes? (:info b) "broken")}))
