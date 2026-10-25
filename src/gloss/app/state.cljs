(ns gloss.app.state
  "The application state: the reader's defaults and preferences, the site's
  published texts and routes, the open text, and undo/redo."
  (:require [clojure.string :as str]
            [gloss.app.markdown-pages :as md-pages]
            [gloss.app.samples :as samples]
            [gloss.parse :as parse]))

;; ---------------------------------------------------------------------------
;; State

(def default-view
  "The reader's display settings. Each group of settings has a switch
  (`…-group`) that hides the whole group without touching the settings
  inside it."
  {:scansion-group true
   :marks true
   :syllables false
   :orthography-group true
   :glossing-group true
   :marking-group true
   :ruby true
   :translit true
   :phonemic true
   :phonetic true
   :ipa true
   :gloss :full                         ; :full | :grammatical | :none
   :pos :none                           ; :full | :class | :none
   :wgloss true
   :morph-breaks true
   :stems :underline                    ; :underline | :bold | :none
   :roots :double                       ; :double | :bold | :none
   :text-fmt true                       ; formatting from \\text, in every row it reaches
   :color true
   :commentary-group true
   :free :below                         ; :below | :side | :none
   :notes :below
   :row-orders {}                       ; the reader's own row order, per text
   :display-open? true})                ; Display options expanded

(def ^:private global-only
  "View keys that are never per text: the row orders are per text already,
  and whether Display options is open is the reader's habit, not the text's."
  #{:row-orders :display-open?})

(defn- same-kind?
  "Whether saved value `v` is of the same kind as default `d` (both
  keywords, or both booleans), so that it can stand in for it."
  [d v]
  (and (some? v) (= (keyword? d) (keyword? v)) (= (boolean? d) (boolean? v))))

(defn clean-view
  "Saved settings over the defaults, ignoring unknown keys and values of the
  wrong kind (e.g. from an older version)."
  [saved]
  (into {} (for [[k d] default-view
                 :let [v (get saved k)]]
             [k (if (same-kind? d v) v d)])))

(defn clean-text-views
  "Saved per-text display changes, keeping only valid ones."
  [saved]
  (into {} (for [[doc-key changes] (when (map? saved) saved)
                 :let [kept (into {} (for [[k v] (when (map? changes) changes)
                                           :when (and (contains? default-view k)
                                                      (not (global-only k))
                                                      (same-kind? (default-view k) v))]
                                       [k v]))]
                 :when (seq kept)]
             [doc-key kept])))

(def default-prefs
  "Site-wide preferences, set on the Settings page."
  {:theme :system                       ; :system | :light | :dark
   :text-size :normal                   ; :small | :normal | :large | :larger
   :open-mode :read})                   ; the mode texts open in

(def pref-choices
  "Each preference's values, with the labels the Settings page shows."
  {:theme [[:system "Same as system"] [:light "Light"] [:dark "Dark"]]
   :text-size [[:small "Small"] [:normal "Normal"] [:large "Large"] [:larger "Larger"]]
   :open-mode [[:read "Read"] [:edit "Edit"]]})

(defn clean-prefs
  "Saved preferences over the defaults, keeping only known values."
  [saved]
  (into {} (for [[k d] default-prefs
                 :let [v (get saved k)]]
             [k (if (some #(= v (first %)) (pref-choices k)) v d)])))

(defn new-doc-id
  "A new id for one of the reader's own texts."
  []
  (str "d" (subs (str (random-uuid)) 0 8)))

(defn- narrow-screen?
  "Whether the page is as narrow as a phone's."
  []
  (boolean (and (exists? js/window) (.-matchMedia js/window)
                (.-matches (.matchMedia js/window "(max-width: 700px)")))))

(defn initial-state
  "A new reader's state: no texts of their own yet, no edits to published
  texts. On a phone, Display options starts closed, so that a text's first
  screen shows the text; opening it is remembered."
  []
  {:docs {}
   :order []
   :overrides {}
   :view (cond-> default-view            ; the reader's defaults for every text
           (narrow-screen?) (assoc :display-open? false))
   :text-views {}                       ; per text: display options changed there
   :prefs default-prefs
   :ui {}})

;; ---------------------------------------------------------------------------
;; Site: published texts and pages

(def site-name
  "The site's name, in one place."
  "GlossHub")

(def feedback-url
  "Where readers send feedback: the public repository's issues."
  "https://github.com/njtedeschi/GlossHub/issues")

(defn site-version
  "The version of the site: the commit it was built from, which the build
  writes into the page (scripts/build-site.cjs); nil in development."
  []
  (some-> (.querySelector js/document "meta[name=glosshub-version]") (.getAttribute "content")))

(def library
  "The published texts, parsed once."
  (delay (mapv #(assoc % :doc (:doc (parse/parse-document (:source %)))) samples/published)))

(defn text-by-slug
  "The published text with this slug, {:slug :source :doc}, or nil."
  [slug]
  (some #(when (= slug (:slug %)) %) @library))

(defn published-version
  "Which version of a published text this is: a hash of its source, to
  notice when it has changed since a reader started editing it."
  [slug]
  (some-> (text-by-slug slug) :source hash))

(defn published-changed?
  "Whether the published text `slug` has changed since the reader's edited
  version of it was started (unknown for versions saved before this was
  recorded: then not)."
  [s slug]
  (let [base (get-in s [:override-bases slug])]
    (boolean (and (get-in s [:overrides slug]) base (not= base (published-version slug))))))

(def text-tabs
  "The Texts page's two tabs: [address label]."
  [["published" "Published texts"]
   ["mine" "My texts"]])

(def sections
  "The navigation bar: [address label]. These addresses, and the Texts
  tabs', are reserved and can't be text slugs."
  [["texts" "Texts"]
   ["guide" "Guide"]
   ["about" "About"]
   ["settings" "Settings"]])

(defn route-of
  "The page for a location hash: a section of the site (# opens Published),
  #<slug> a published text, any other #<id> one of the reader's own texts
  (checked when rendering)."
  [hash]
  (let [h (str/replace (str hash) #"^#/?" "")
        guide-page (md-pages/by-address h)]
    (cond
      (#{"" "edit"} h) {:page :published}
      (= h "texts") {:page :published}
      guide-page {:page :guide :guide-page (:slug guide-page)}
      (some #(= h (first %)) (concat sections text-tabs)) {:page (keyword h)}
      (text-by-slug h) {:page :text :slug h}
      :else {:page :own :id h})))

(defn go!
  "Goes to the page at `hash`."
  [hash]
  (set! (.-hash js/location) hash))

(defonce ^{:doc "The application state, one map: see docs/architecture.md
  (section 5.1) for its keys."}
  state
  (atom nil))

(defn doc-key
  "The open text's key: its slug, or the id of the reader's own text."
  [s]
  (or (get-in s [:route :slug]) (get-in s [:route :id])))

(defn default-display
  "The reader's default display settings as a text starts from them. The
  defaults have no IPA switch: a text's IPA transcription is shown when the
  phonemic or phonetic one would be."
  [s]
  (let [v (:view s)]
    (assoc v :ipa (boolean (or (:phonemic v) (:phonetic v))))))

(defn page-view
  "The display settings for the open text: the reader's defaults, with the
  changes made on this text's page, and the reader's row order for it."
  [s]
  (-> (default-display s)
      (merge (get-in s [:text-views (doc-key s)]))
      (assoc :reader-order (get-in s [:view :row-orders (doc-key s)]))))

(defn set-display
  "Changes display option `k` with `f`. On a text page the change is that
  text's own; a value equal to the default is dropped rather than stored."
  [s k f]
  (let [dk (doc-key s)]
    (if (or (nil? dk) (global-only k))
      (update-in s [:view k] f)
      (let [v (f (get (page-view s) k))
            changes (if (= v (get (default-display s) k))
                      (dissoc (get-in s [:text-views dk]) k)
                      (assoc (get-in s [:text-views dk]) k v))]
        (if (seq changes)
          (assoc-in s [:text-views dk] changes)
          (update s :text-views dissoc dk))))))

(defn page-view-changed?
  "Whether the open text has display changes of its own (or its own row
  order) for Reset to undo."
  [s]
  (let [dk (doc-key s)]
    (boolean (or (seq (get-in s [:text-views dk])) (seq (get-in s [:view :row-orders dk]))))))

(defn- doc-path
  "Where the open page's document is stored: the reader's own text, or their
  edited version of a published text (kept apart from the published file)."
  [s]
  (case (get-in s [:route :page])
    :own [:docs (get-in s [:route :id])]
    :text [:overrides (get-in s [:route :slug])]
    nil))

(defn current-doc
  "The document on the open page; for a published text, the reader's edited
  version if they have one."
  [s]
  (case (get-in s [:route :page])
    :own (get-in s [:docs (get-in s [:route :id])])
    :text (let [slug (get-in s [:route :slug])]
            (or (get-in s [:overrides slug]) (:doc (text-by-slug slug))))
    nil))

;; ---------------------------------------------------------------------------
;; Undo and redo
;;
;; Each text has its own history for the visit (not saved). An entry is the
;; stored document before and after one change; nil means "no stored
;; document" (a published text without the reader's edits).

(def ^:private history-limit 100)

(defn- set-at
  "The state with the document at `path` set; nil removes it."
  [s path doc]
  (if (nil? doc)
    (update-in s (pop path) dissoc (peek path))
    (assoc-in s path doc)))

(defn record-change
  "Sets the document at `path`, recording the change for undo."
  [s path after]
  (let [before (get-in s path)]
    (if (= before after)
      s
      (-> s
          (set-at path after)
          (update-in [:history path :past] (fn [p] (vec (take-last history-limit (conj (or p []) {:before before :after after})))))
          (assoc-in [:history path :future] [])))))

(defn undo
  "Undoes the last change to the open text."
  [s]
  (let [path (doc-path s)]
    (if-let [{:keys [before] :as entry} (peek (get-in s [:history path :past]))]
      (-> s
          (set-at path before)
          (update-in [:history path :past] pop)
          (update-in [:history path :future] (fnil conj []) entry)
          (assoc :ui {}))
      s)))

(defn redo
  "Redoes the last change undone on the open text."
  [s]
  (let [path (doc-path s)]
    (if-let [{:keys [after] :as entry} (peek (get-in s [:history path :future]))]
      (-> s
          (set-at path after)
          (update-in [:history path :future] pop)
          (update-in [:history path :past] (fnil conj []) entry)
          (assoc :ui {}))
      s)))

(defn can?
  "Whether the open text has a change to undo (`k` :past) or to redo
  (:future)."
  [s k]
  (seq (get-in s [:history (doc-path s) k])))

(defn update-doc
  "Applies an edit to the open page's document, as one undoable step. The
  first edit to a published text starts the reader's own version of it."
  [s f & args]
  (if-let [path (doc-path s)]
    (let [[kind slug] path]
      (cond-> (record-change s path (apply f (current-doc s) args))
        ;; The first edit to a published text: remember which version it
        ;; started from.
        (and (= :overrides kind) (nil? (get-in s path)))
        (assoc-in [:override-bases slug] (published-version slug))))
    s))
