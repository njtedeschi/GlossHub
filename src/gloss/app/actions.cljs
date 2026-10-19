(ns gloss.app.actions
  "What the page's events do: pure state transitions (`handle`), plus the
  few effects they need (navigation, files)."
  (:require [clojure.string :as str]
            [gloss.app.samples :as samples]
            [gloss.app.state :as state]
            [gloss.app.storage :as storage]
            [gloss.edit :as edit]
            [gloss.model :as model]
            [gloss.notes :as notes]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

;; ---------------------------------------------------------------------------
;; Cell editing

(defn- cell-value
  "The text to edit for `target` (see gloss.render/cell): an item under a
  word, a free translation, a note or the title."
  [doc {:keys [row line word note]}]
  (let [l (when line (get-in doc [:lines (model/line-index doc line)]))]
    (case row
      "ft" (:free l)
      "note" (:text (first (notes/find-note doc note)))
      "title" (:title doc)
      (ser/item row (get-in l [:words word])))))

(defn- apply-cell
  "The document with `value` typed into the cell `target` (see
  `cell-value`)."
  [doc {:keys [row line word note]} value]
  (case row
    "ft" (edit/set-free doc line value)
    "note" (edit/set-note doc note value)
    "title" (assoc doc :title (if (str/blank? value) "Untitled" (str/trim value)))
    (edit/set-item doc line word row value)))

(defn- start-edit
  "Starts editing the cell `target`, with its current value as the draft."
  [s target]
  (assoc s :ui (assoc (:ui s) :editing target :draft (or (cell-value (state/current-doc s) target) ""))))

(defn- commit-edit
  "Applies the draft of the cell being edited, if any, as one undoable
  change, and stops editing."
  [s]
  (if-let [target (get-in s [:ui :editing])]
    (-> s
        (state/update-doc apply-cell target (get-in s [:ui :draft]))
        (update :ui dissoc :editing :draft))
    s))

(defn- neighbour-target
  "The same tier on the next (or previous) word, wrapping to adjacent lines."
  [doc {:keys [line word] :as target} step]
  (when word
    (let [lines (:lines doc)
          li (model/line-index doc line)
          n (count (get-in lines [li :words]))
          w (+ word step)]
      (cond
        (< -1 w n) (assoc target :word w)
        (and (>= w n) (< (inc li) (count lines)))
        (assoc target :line (:id (get lines (inc li))) :word 0)
        (and (neg? w) (pos? li))
        (let [prev (get lines (dec li))]
          (assoc target :line (:id prev) :word (dec (count (:words prev)))))))))

;; ---------------------------------------------------------------------------
;; Files

(defn- download!
  "Offers `text` to the reader as a file named `filename`."
  [filename text]
  (let [blob (js/Blob. #js [text] #js {:type "text/plain;charset=utf-8"})
        url (.createObjectURL js/URL blob)
        a (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild (.-body js/document) a)
    (.click a)
    (.remove a)
    (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))

(defn- slug
  "A file name made from a title."
  [s]
  (let [x (-> (str s) str/lower-case (str/replace #"[\s/\\:*?\"<>|.,;'’]+" "-") (str/replace #"^-|-$" ""))]
    (if (str/blank? x) "document" x)))

(defn- select-source-line!
  "Selects line `n` (1-based) of the source text box and brings it into
  view."
  [n]
  (when-let [ta (.querySelector js/document "textarea.source")]
    (let [lines (str/split (.-value ta) #"\n" -1)
          start (reduce + (map #(inc (count %)) (take (dec n) lines)))
          end (+ start (count (nth lines (dec n) "")))]
      (.focus ta)
      (.setSelectionRange ta start end)
      (.scrollIntoView ta #js {:block "nearest"}))))

(defn- add-doc
  "Adds a text of the reader's own and opens its page, in `mode`."
  [s doc mode]
  (let [id (state/new-doc-id)]
    (js/setTimeout #(state/go! id) 0)
    (-> s
        (assoc-in [:docs id] doc)
        (update :order (fnil conj []) id)
        (assoc :pending-mode mode :ui {}))))

;; An export file brings back the reader's own texts (as new texts, unless
;; the same text is already there under its id) and their versions of
;; published texts (replacing the current ones).
(defn import-export
  "The state with the texts of an export file (gloss.app.storage/read-export)
  added, and a note of how many."
  [s parts]
  (let [s (reduce (fn [s {:keys [kind key source]}]
                    (let [doc (:doc (parse/parse-document source))]
                      (cond
                        (and (= kind "edited") (state/text-by-slug key))
                        (-> s
                            (assoc-in [:overrides key] doc)
                            (assoc-in [:override-bases key] (state/published-version key)))
                        (and (get-in s [:docs key])
                             (= source (str/trim (ser/document-source (get-in s [:docs key])))))
                        s
                        :else (let [id (if (or (= kind "edited") (contains? (:docs s) key)) (state/new-doc-id) key)]
                                (-> s
                                    (assoc-in [:docs id] doc)
                                    (update :order (fnil conj []) id))))))
                  s parts)]
    (assoc-in s [:status :imported] (count parts))))

;; ---------------------------------------------------------------------------
;; Actions

(defn- target-value
  "The value of the input an event came from."
  [e]
  (.. e -target -value))

(defn handle
  "Pure state transitions. Returns the new state."
  [s e [action arg]]
  (case action
    :view/toggle (state/set-display s arg not)
    :view/select (state/set-display s arg (constantly (keyword (target-value e))))

    :edit/start (start-edit (commit-edit s) arg)
    :edit/draft (assoc-in s [:ui :draft] (target-value e))
    :edit/commit (commit-edit s)
    :edit/blur (if (= arg (get-in s [:ui :editing])) (commit-edit s) s)
    :edit/cancel (update s :ui dissoc :editing :draft)
    :edit/key (let [k (.-key e)]
                (cond
                  (and (= k "Enter") (not (.-shiftKey e))) (do (.preventDefault e) (commit-edit s))
                  (= k "Escape") (update s :ui dissoc :editing :draft)
                  (= k "Tab")
                  (let [target (get-in s [:ui :editing])
                        s (commit-edit s)]
                    (if-let [next (neighbour-target (state/current-doc s) target (if (.-shiftKey e) -1 1))]
                      (do (.preventDefault e) (start-edit s next))
                      s))
                  :else s))

    :line/source (assoc s :ui {:source {:line arg
                                        :text (let [doc (state/current-doc s)]
                                                (ser/line-block (get-in doc [:lines (model/line-index doc arg)])))}})
    :line/add (assoc s :ui {:source {:after arg :text "\\ex\n\\text "}})
    ;; Deleting takes two clicks: the first arms the button.
    :line/delete (if (= [:line arg] (get-in s [:ui :confirm]))
                   (-> s (state/update-doc edit/delete-line arg) (assoc :ui {}))
                   (assoc-in s [:ui :confirm] [:line arg]))
    :source/draft (assoc-in s [:ui :source :text] (target-value e))
    :source/fill (update-in s [:ui :source :text] parse/fill-row arg)
    :source/key (cond
                  (= (.-key e) "Escape") (assoc s :ui {})
                  (and (= (.-key e) "Enter") (or (.-ctrlKey e) (.-metaKey e)))
                  (do (.preventDefault e) (handle s e [:source/apply]))
                  :else s)
    :source/cancel (assoc s :ui {})
    :source/goto (do (select-source-line! arg) s)
    :source/apply (let [{:keys [line after text]} (get-in s [:ui :source])
                        {:keys [lines]} (parse/parse-lines text)]
                    (-> s
                        (state/update-doc (fn [doc]
                                            (if line
                                              (edit/replace-line doc line lines)
                                              (edit/insert-lines doc after lines))))
                        (assoc :ui {})))

    :doc/source (assoc s :ui {:doc-source (ser/document-source (state/current-doc s))})
    :mode/set (assoc s :mode arg :ui {})
    :history/undo (state/undo s)
    :history/redo (state/redo s)
    :layers/open (assoc s :ui {:layers true})
    :layers/close (assoc s :ui {})
    ;; Reordering layers is a display setting: the reader's own order for
    ;; this text. "Save as the document's order" (Edit mode) writes \order.
    :layers/move (let [[k step] arg
                       v (state/page-view s)
                       order (model/present-layers (state/current-doc s) (:reader-order v))
                       i (.indexOf order k)
                       j (+ i step)]
                   (if (and (<= 0 i) (< -1 j (count order)))
                     (assoc-in s [:view :layer-orders (state/doc-key s)] (assoc order i (order j) j (order i)))
                     s))
    :layers/reset (update-in s [:view :layer-orders] dissoc (state/doc-key s))
    :layers/save-doc (let [v (state/page-view s)
                           order (model/present-layers (state/current-doc s) (:reader-order v))]
                       (-> s
                           (state/update-doc assoc :order order)
                           (update-in [:view :layer-orders] dissoc (state/doc-key s))))
    :new/open (assoc s :ui {:new? true})
    :new/cancel (assoc s :ui {})
    :doc/duplicate (let [doc (case (first arg)
                               :slug (or (get-in s [:overrides (second arg)]) (:doc (state/text-by-slug (second arg))))
                               :id (get-in s [:docs (second arg)])
                               (state/current-doc s))]
                     (add-doc s (update doc :title str " (copy)") :read))
    :status/dismiss (update s :status dissoc arg)
    ;; Highlighting a note's words: a set of note keys, for the visit.
    :note/highlight (update s :note-highlight #(let [h (set %)] (if (h arg) (disj h arg) (conj h arg))))
    :status/retry-save (do (js/setTimeout #(storage/save! @state/state) 0) s)
    :settings/reset-view (assoc s :view state/default-view :text-views {})
    ;; The reader's defaults (Settings): back to the site's, keeping the
    ;; per-text layer orders and whether boxes are open.
    :defaults/reset (assoc s :view (merge state/default-view (select-keys (:view s) [:layer-orders :display-open?])))
    :view/reset-page (-> s
                         (update :text-views dissoc (state/doc-key s))
                         (update-in [:view :layer-orders] dissoc (state/doc-key s)))
    :prefs/set (assoc-in s [:prefs (first arg)] (second arg))
    ;; Erasing everything saved in this browser takes two clicks.
    :settings/erase (if (= [:erase] (get-in s [:ui :confirm]))
                      (assoc (state/initial-state) :route (:route s) :ui {:erased? true})
                      (assoc-in s [:ui :confirm] [:erase]))
    :doc-source/draft (assoc-in s [:ui :doc-source] (target-value e))
    :doc-source/fill (update-in s [:ui :doc-source] parse/fill-row arg)
    :doc-source/apply (-> s
                          (state/update-doc (constantly (:doc (parse/parse-document (get-in s [:ui :doc-source])))))
                          (assoc :ui {}))
    :doc/new (add-doc s (:doc (parse/parse-document samples/blank)) :edit)
    ;; Deleting a text of one's own, or discarding one's edits to a published
    ;; text, takes two clicks: the first arms the button.
    :doc/delete (if (= [:doc arg] (get-in s [:ui :confirm]))
                  (do (when (= arg (get-in s [:route :id])) (js/setTimeout #(state/go! "mine") 0))
                      (-> s
                          (update :docs dissoc arg)
                          (update :order (fn [o] (vec (remove #{arg} o))))
                          (update-in [:view :layer-orders] dissoc arg)
                          (update :text-views dissoc arg)
                          (assoc :ui {})))
                  (assoc-in s [:ui :confirm] [:doc arg]))
    ;; Keeping one's version of a published text that has changed since.
    :override/keep (assoc-in s [:override-bases arg] (state/published-version arg))
    :override/discard (if (= [:override arg] (get-in s [:ui :confirm]))
                        (-> s
                            (state/record-change [:overrides arg] nil)
                            (update :override-bases dissoc arg)
                            (assoc :ui {}))
                        (assoc-in s [:ui :confirm] [:override arg]))
    :doc/export (do (download! (str (slug (:title (state/current-doc s))) ".gloss.txt")
                               (ser/document-source (state/current-doc s)))
                    s)
    :doc/import (let [file (aget (.. e -target -files) 0)]
                  (when file
                    (.then (.text file)
                           (fn [text] (swap! state/state handle nil [:doc/imported text]))))
                  (set! (.. e -target -value) "")
                  s)
    ;; One text, or a whole export file.
    :doc/imported (if-let [parts (storage/read-export arg)]
                    (do (js/setTimeout #(state/go! "mine") 0)
                        (import-export (assoc s :ui {}) parts))
                    (add-doc s (:doc (parse/parse-document arg)) :read))
    :data/export (let [date (subs (.toISOString (js/Date.)) 0 10)]
                   (download! (str "glosshub-texts-" date ".gloss.txt") (storage/export-text s date))
                   s)
    s))

(defn dispatch!
  "Runs an action. If it fails, the state is left as it was and the reader
  is told."
  [e action]
  (try
    (swap! state/state handle e action)
    (catch :default err
      (js/console.error "Action failed:" (pr-str action) err)
      (swap! state/state assoc-in [:status :action-failed] (str (or (.-message err) err))))))
