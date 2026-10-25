(ns gloss.app.storage
  "Saving the reader's data in the browser, and reading it back from any
  earlier format."
  (:require [cljs.reader :as reader]
            [clojure.set :as set]
            [clojure.string :as str]
            [gloss.app.state :as state]
            [gloss.english :as english]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

(def storage-key
  "The localStorage key that everything the site saves is under."
  "glosshub")

(defn- read-saved
  "The data saved under `k`: nil when there is none, or nothing worth
  keeping; ::unreadable when there is data but it can't be read."
  [k]
  (when-let [raw (try (.getItem js/localStorage k) (catch :default _ nil))]
    (let [saved (try (reader/read-string raw) (catch :default _ ::unreadable))]
      (cond
        (or (= ::unreadable saved) (not (map? saved))
            (some #(not (map? (get saved % {}))) [:docs :overrides]))
        ::unreadable
        ;; Saved settings count even without any texts of one's own.
        (or (seq (:docs saved)) (:view saved) (:prefs saved) (seq (:overrides saved)))
        saved))))

(def save-format
  "The version of the saved data. Texts are saved as their source text (the
  stable, public form), so changes to the internal model don't affect saved
  texts; this number covers the structure around them, and `migrate` reads
  the earlier formats. Format 2 renamed display settings (`from-format-1`)."
  2)

(def ^:private sources
  "Each text's source, by the text's value (weakly), so a save serializes
  only the texts that changed."
  (js/WeakMap.))

(defn- source-of
  "A document's source text, serialized again only when it has changed."
  [doc]
  (or (.get sources doc)
      (let [text (ser/document-source doc)]
        (.set sources doc text)
        text)))

(defn saved-form
  "What is written to storage for a state."
  [s]
  (let [as-source (fn [docs] (into {} (for [[k d] docs] [k (source-of d)])))]
    (-> (select-keys s [:order :view :text-views :prefs :override-bases])
        (assoc :format save-format
               :docs (as-source (:docs s))
               :overrides (as-source (:overrides s))))))

(def ^:private format-1-names
  "Display settings that format 1 saved under other names, by those names:
  each group's switch was a row's, and the reader's row orders were layer
  orders."
  {:scansion-row :scansion-group
   :orthography-row :orthography-group
   :glossing-row :glossing-group
   :marking-row :marking-group
   :commentary-row :commentary-group
   :layer-orders :row-orders})

(defn- from-format-1
  "Saved data of format 1 as format 2 has it: the display settings, the
  reader's defaults and each text's own, under their new names, and the
  morphemic gloss's \"tags only\" as \"grammatical only\"."
  [saved]
  (let [view (fn [v]
               (when (map? v)
                 (cond-> (set/rename-keys v format-1-names)
                   (= :tags (:gloss v)) (assoc :gloss :grammatical))))]
    (-> saved
        (update :view view)
        (update :text-views #(into {} (for [[k v] (when (map? %) %)] [k (view v)]))))))

(defn migrate
  "Saved data as the site uses it, from any earlier format: each text, saved
  as source, parsed again, and the display settings under their current
  names. Anything that isn't a text can't be shown, so it is left out."
  [saved]
  (let [revive-all (fn [docs] (into {} (for [[k d] docs :when (string? d)] [k (:doc (parse/parse-document d))])))]
    (-> (cond-> saved
          (= 1 (:format saved 1)) from-format-1)
        (update :docs revive-all)
        (update :overrides revive-all)
        (assoc :format save-format))))

(defn load-saved
  "The reader's saved state, or nil if there is none. Throws if there is
  saved data that can't be read, so that it isn't overwritten unawares."
  []
  (let [saved (read-saved storage-key)]
    (when (= ::unreadable saved)
      (throw (ex-info "The saved data can't be read." {:key storage-key})))
    (when saved
      (-> saved
          migrate
          (update :view state/clean-view)
          (update :text-views state/clean-text-views)
          (update :prefs state/clean-prefs)
          (update :override-bases #(if (map? %) % {}))
          (assoc :ui {})))))

;; ---------------------------------------------------------------------------
;; Export everything: all of a reader's texts in one file, as source text.
;; Each text follows a line `% ==== own <id>` (a text of their own) or
;; `% ==== edited <slug>` (their version of a published text). `%` lines are
;; comments in the format, so each part is an ordinary text.

(def export-header
  "The start of an export file's first line."
  "% GlossHub export")

(def ^:private part-pattern
  "The line that starts each text in an export file."
  #"^% ==== (own|edited) (\S+)\s*$")

(defn export-text
  "All of the reader's texts in one file, dated `date`."
  [s date]
  (let [own (for [id (:order s) :let [doc (get-in s [:docs id])] :when doc] ["own" id doc])
        edited (for [[slug doc] (sort-by key (:overrides s))] ["edited" slug doc])
        parts (concat own edited)]
    (str export-header ", " date ": " (english/counted (count parts) "text" "texts") ".\n"
         "% Import this file on GlossHub (Settings, or New text) to bring them back.\n"
         (apply str (for [[kind k doc] parts]
                      (str "\n% ==== " kind " " k "\n" (source-of doc)))))))

(defn read-export
  "The texts in an export file, [{:kind \"own\"|\"edited\" :key :source}], or
  nil when `text` isn't one."
  [text]
  (when (str/starts-with? (str/triml (str text)) export-header)
    (let [parts (reduce (fn [parts line]
                          (if-let [[_ kind k] (re-matches part-pattern line)]
                            (conj parts {:kind kind :key k :lines []})
                            (if (seq parts)
                              (update-in parts [(dec (count parts)) :lines] conj line)
                              parts)))
                        []
                        (str/split-lines text))]
      (mapv (fn [{:keys [kind key lines]}] {:kind kind :key key :source (str/trim (str/join "\n" lines))})
            parts))))

(defn- write!
  "Writes the state. A failure (storage full, or turned off) is shown to the
  reader until a later save succeeds."
  [s]
  (try
    (.setItem js/localStorage storage-key (pr-str (saved-form s)))
    (when (get-in @state/state [:status :save-failed?])
      (swap! state/state update :status dissoc :save-failed?))
    (catch :default e
      (js/console.error "Saving failed:" e)
      (swap! state/state assoc-in [:status :save-failed?] true))))

(defonce ^{:private true :doc "The state waiting to be written, while a save is scheduled."}
  pending
  (atom nil))

(defn flush!
  "Writes a save that is waiting, now (e.g. as the page is closed)."
  []
  (when-let [s @pending]
    (reset! pending nil)
    (write! s)))

(defn drop-pending!
  "Forgets a save that is waiting (when another tab's newer save is
  adopted instead)."
  []
  (reset! pending nil))

(defn back-up-saved!
  "Copies the saved data aside, under a dated key, before anything could
  overwrite it (used when it can't be read). Returns the key, if any."
  []
  (try
    (when-let [raw (.getItem js/localStorage storage-key)]
      (let [k (str storage-key "-backup-" (.toISOString (js/Date.)))]
        (.setItem js/localStorage k raw)
        k))
    (catch :default _ nil)))

(def ^:private save-delay-ms
  "How long a save waits for more changes, in milliseconds."
  300)

(defn save!
  "Saves the state shortly after: changes in quick succession (a burst of
  edits, undo held down) are written once."
  [s]
  (when-not @pending
    (js/setTimeout flush! save-delay-ms))
  (reset! pending s))
