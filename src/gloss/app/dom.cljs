(ns gloss.app.dom
  "A minimal hiccup renderer that patches the DOM in place.

  Hiccup follows Replicant's conventions so the two can be swapped:
  `[:div.a#b {:class [...] :style {...} :on {:click action}} & children]`.
  An event handler is either a function of the event or data (any other
  value), which is passed to the dispatch function given to `render!`.
  Children are matched by position, which keeps focus and input state in
  elements whose position is stable. The previous render's hiccup is kept,
  and a subtree that is identical (the same value, not just equal) to last
  time is skipped."
  (:require [clojure.string :as str]
            [goog.object :as gobj]))

(def ^:private tag-cache
  "Parsed tags, by name: a page uses few distinct tags, many times. (Keyed
  by the string, as keywords aren't interned.)"
  (js/Map.))

(defn- parse-tag
  "A hiccup tag (:div.a#b) as [name id classes class-string]."
  [tag]
  (let [k (name tag)]
    (or (.get tag-cache k)
        (let [[_ tname rest] (re-matches #"([^.#]+)(.*)" k)
              id (second (re-find #"#([^.]+)" rest))
              classes (vec (map second (re-seq #"\.([^.#]+)" rest)))
              parsed [tname id classes (when (seq classes) (str/join " " classes))]]
          (.set tag-cache k parsed)
          parsed))))

(defn- class-list
  "The classes in a :class value: a string, a keyword, or nested
  collections of them (nils ignored)."
  [c]
  (cond
    (nil? c) []
    (string? c) [c]
    (keyword? c) [(name c)]
    :else (mapcat class-list c)))

(defn- element? [h] (vector? h))

(defn- text-of
  "The text of a non-element child."
  [h]
  (if (string? h) h (str h)))

(defn- flatten-children
  "An element's children, with nested seqs spliced in and nils dropped. The
  children themselves stay hiccup, so unchanged ones can be recognized."
  [children]
  (letfn [(add [out c]
            (cond
              (nil? c) out
              (vector? c) (conj! out c)
              (or (seq? c) (sequential? c)) (reduce add out c)
              :else (conj! out c)))]
    (persistent! (reduce add (transient []) children))))

(def ^:private nodes
  "Each element's `node`, by its hiccup (weakly): the new render's elements
  are the old ones next time, and needn't be taken apart again."
  (js/WeakMap.))

(declare node*)

(defn- node
  "One level of an element: {:tag :attrs :children}, children as hiccup."
  [h]
  (or (.get nodes h)
      (let [n (node* h)]
        (.set nodes h n)
        n)))

(defn- node* [h]
  (let [n (count h)
        second (when (> n 1) (nth h 1))
        attrs? (map? second)
        attrs (if attrs? second {})
        [tname id classes class-str] (parse-tag (nth h 0))
        extra (:class attrs)
        class (if (nil? extra)
                class-str
                (let [cls (distinct (concat classes (class-list extra)))]
                  (when (seq cls) (str/join " " cls))))]
    {:tag tname
     :attrs (cond-> (if attrs? (dissoc attrs :class) attrs)
              id (assoc :id id)
              class (assoc :class class))
     :children (flatten-children (subvec h (if attrs? 2 1)))}))

;; ---------------------------------------------------------------------------
;; Attributes

(def ^:private property-attrs
  "Attributes set as DOM properties rather than as attributes: an input's
  value, for one, is a property once the reader has typed in it."
  #{:value :checked :selected :disabled})

(defn- set-handler!
  "Sets `el`'s handler for `event` (nil removes it). One listener per event
  is added to the element, the first time; it looks up the current
  handler, so later renders only swap the handler, not the listener."
  [el event handler dispatch]
  (let [handlers (or (gobj/get el "__gg_on") (let [h #js {}] (gobj/set el "__gg_on" h) h))
        ename (name event)]
    (when-not (gobj/containsKey handlers ename)
      (.addEventListener el ename
                         (fn [e]
                           (let [h (gobj/get handlers ename)]
                             (cond
                               (nil? h) nil
                               (fn? h) (h e)
                               :else ((gobj/get el "__gg_dispatch") e h))))))
    (gobj/set el "__gg_dispatch" dispatch)
    (gobj/set handlers ename handler)))

(defn- set-attr!
  "Sets attribute `k` of `el` to `v`: nil or false removes it, true makes
  it a bare attribute, :style is a map of properties."
  [el k v]
  (cond
    (property-attrs k) (when (not= (gobj/get el (name k)) v)
                         (gobj/set el (name k) (if (= k :value) (str v) (boolean v))))
    (= k :style) (let [style (.-style el)]
                   (.setAttribute el "style" "")
                   (doseq [[sk sv] v :when (some? sv)]
                     (.setProperty style (name sk) (str sv))))
    (or (nil? v) (false? v)) (.removeAttribute el (name k))
    (true? v) (.setAttribute el (name k) "")
    :else (.setAttribute el (name k) (str v))))

(defn- update-attrs!
  "Brings `el`'s attributes and handlers from `old` to `new`."
  [el old new dispatch]
  (let [old-on (:on old), new-on (:on new)]
    (doseq [[ev h] new-on] (set-handler! el ev h dispatch))
    (doseq [ev (keys old-on) :when (not (contains? new-on ev))]
      (set-handler! el ev nil dispatch)))
  (let [old (dissoc old :on), new (dissoc new :on)]
    (doseq [k (keys old) :when (not (contains? new k))]
      (if (property-attrs k)
        (set-attr! el k (if (= k :value) "" false))
        (.removeAttribute el (name k))))
    (doseq [[k v] new :when (or (property-attrs k) (not= v (get old k)))]
      (set-attr! el k v))))

;; ---------------------------------------------------------------------------
;; Creating and patching

(defn- create
  "A new DOM node for hiccup `h`."
  [h dispatch]
  (if-not (element? h)
    (.createTextNode js/document (text-of h))
    (let [{:keys [tag attrs children]} (node h)
          el (.createElement js/document tag)]
      (doseq [c children]
        (.appendChild el (create c dispatch)))
      ;; After the children, so a <select>'s value finds its options.
      (update-attrs! el {} attrs dispatch)
      el)))

(defn- patch!
  "Brings `el`, rendered from hiccup `old`, up to date with `new`. A subtree
  whose hiccup is the very same value as last time is skipped: views that
  return a cached value for unchanged content (see gloss.app.text) cost
  nothing to re-render."
  [parent el old new dispatch]
  (cond
    (identical? old new) nil

    (and (not (element? old)) (not (element? new)))
    (let [t (text-of new)]
      (when (not= (text-of old) t) (set! (.-nodeValue el) t)))

    (or (not (element? old)) (not (element? new))
        (not= (first (parse-tag (first old))) (first (parse-tag (first new)))))
    (.replaceChild parent (create new dispatch) el)

    :else
    (let [o (node old), n (node new)]
      (update-attrs! el (:attrs o) (:attrs n) dispatch)
      (let [oc (:children o), nc (:children n)
            no (count oc), nn (count nc)]
        (dotimes [i (min no nn)]
          (patch! el (aget (.-childNodes el) i) (nth oc i) (nth nc i) dispatch))
        (doseq [c (drop no nc)]
          (.appendChild el (create c dispatch)))
        (doseq [_ (range nn no)]
          (.removeChild el (.-lastChild el))))
      ;; A <select>'s value can only be set once its options exist.
      (when (and (= "select" (:tag n)) (contains? (:attrs n) :value))
        (set-attr! el :value (get-in n [:attrs :value]))))))

(defn clear!
  "Empties a container and forgets its last render (after a failed render
  left it in an unknown state)."
  [container]
  (set! (.-innerHTML container) "")
  (gobj/remove container "__gg_hiccup"))

(defn render!
  "Renders hiccup into container, patching against the previous render."
  [container hiccup dispatch]
  (let [old (gobj/get container "__gg_hiccup")]
    (if (and old (.-firstChild container))
      (patch! container (.-firstChild container) old hiccup dispatch)
      (do (set! (.-innerHTML container) "")
          (.appendChild container (create hiccup dispatch))))
    (gobj/set container "__gg_hiccup" hiccup)))
