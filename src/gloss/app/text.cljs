(ns gloss.app.text
  "A text's page: its actions, display options, and the text itself, read
  or edited."
  (:require [clojure.string :as str]
            [gloss.app.state :as state]
            [gloss.app.ui :as ui]
            [gloss.check :as check]
            [gloss.edit :as edit]
            [gloss.model :as model]
            [gloss.notes :as notes]
            [gloss.parse :as parse]
            [gloss.render :as render]
            [gloss.tiers :as tiers]))

;; ---------------------------------------------------------------------------
;; Source editors and IGTs in Edit mode

(defn- fill-buttons
  "Buttons that add a \\morph or \\syl tier, filled with the words'
  letters, to every IGT that lacks one. `on-fill` is the event, given the
  tier."
  [text on-fill]
  [:div.fill-tiers
   [:span.hint "Fill from the words:"]
   (for [k (parse/fillable text)
         :let [command (str "\\" k)
               filled (parse/fill-tier text k)]]
     [:button.small {:on {:click (conj on-fill k)}
                     :disabled (= filled text)
                     :title (str "Add a " command " tier with each word's letters, ready for breaks")}
      command])])

(defn- source-editor
  "A source text box with its problems and actions, and a preview of
  `preview-igts`. `on-draft`, `on-apply` and `on-cancel` are the events of
  the box and its buttons; with `on-fill`, the Fill buttons are offered."
  [ctx text on-draft on-apply on-cancel preview-igts diags & [on-fill]]
  [:div.source-editor
   [:textarea.source {:value text
                      :rows (max 6 (inc (count (str/split-lines text))))
                      :spellcheck "false"
                      :data-autofocus "true"
                      :on {:input on-draft :keydown [:source/key on-apply]}}]
   (when on-fill (fill-buttons text on-fill))
   (render/diagnostics-view (sort-by #(or (:source-line %) 0) diags) :goto? true)
   [:div.editor-actions
    [:button.primary {:on {:click on-apply}} "Apply"]
    [:button {:on {:click on-cancel}} "Cancel"]
    [:span.hint "Ctrl+Enter to apply, Esc to cancel"]]
   (when (seq preview-igts)
     [:div.preview
      [:div.preview-label "Preview"]
      (for [l preview-igts]
        [:div.igt (render/igt-body ctx l {})])])])

(defn- igt-source-editor
  "Editing IGTs as source: the source, its problems, and a preview of the
  IGTs as they will be, shown in `pdoc`, the document with them in place
  (so their notes, labels and numbers are right)."
  [ctx label {:keys [igts] :as parsed} pdoc text]
  (let [pctx (assoc ctx :doc pdoc :note-numbers (notes/note-numbers pdoc))]
    [:div.igt.editing
     [:div.igt-head [:span.label label]]
     (source-editor pctx text [:source/draft] [:source/apply] [:source/cancel]
                    igts (check/problems parsed (mapcat #(check/check-igt pdoc %) igts)) [:source/fill])]))

(defn- editing-igt
  "An IGT in Edit mode: its tools, the IGT, its problems; or its source
  editor while it is edited as source."
  [ctx doc igt idx label first-cited]
  (let [source (get-in ctx [:ui :source])]
    (if (= (:id igt) (:igt source))
      (let [parsed (parse/parse-igts (:text source))]
        (igt-source-editor ctx label parsed (edit/replace-igt doc (:id igt) (:igts parsed)) (:text source)))
      [:div.igt {:id (str "igt-" (:id igt)) :dir (:dir doc)}
       [:div.igt-head
        [:span.label label]
        [:span.igt-tools
         [:button.small {:on {:click [:igt/source (:id igt)]} :title "Edit this IGT as source"} "Edit source"]
         [:button.small {:on {:click [:igt/add idx]} :title "Add an IGT below"} "+ IGT"]
         (let [armed? (= [:igt (:id igt)] (get-in ctx [:ui :confirm]))]
           [:button.small.danger {:class (when armed? "armed")
                                  :on {:click [:igt/delete (:id igt)]}
                                  :title "Delete this IGT"}
            (if armed? "Click again to delete" "Delete")])]]
       (render/igt-body ctx igt {:first-cited first-cited})
       (render/diagnostics-view (check/check-igt doc igt))])))

(defn- new-igt-editor
  "The source editor for new IGTs, when they are being added after IGT
  `after` (nil: at the end)."
  [ctx after]
  (let [source (get-in ctx [:ui :source])]
    (when (and source (not (:igt source)) (= after (:after source)))
      (let [parsed (parse/parse-igts (:text source))]
        (igt-source-editor ctx "New IGT" parsed (edit/insert-igts (:doc ctx) after (:igts parsed)) (:text source))))))

;; ---------------------------------------------------------------------------
;; IGT cache
;;
;; An IGT's view is rebuilt only when something it shows changes: the IGT
;; itself, its place, its share of the edit state, or the surroundings (the
;; display options and the document apart from its IGTs). Otherwise the
;; previous hiccup is returned as is, and gloss.app.dom skips it, so typing in a
;; cell costs one IGT, not the whole text.

(defonce ^:private igt-cache (atom {:env nil :slots {}}))

(defn- igts-env
  "What every IGT's view depends on besides the IGT: the mode, the
  document without its IGTs, and the context without the edit state."
  [ctx mode]
  [mode (dissoc (:doc ctx) :igts) (dissoc ctx :doc :ui)])

(defn- begin-igts!
  "Starts rendering a text's IGTs. Returns the environment to cache them
  under: last render's, when equal, so that each IGT compares it by
  identity. A different environment empties the cache."
  [env]
  (let [old (:env @igt-cache)]
    (if (= old env)
      old
      (do (reset! igt-cache {:env env :slots {}}) env))))

(defn- cached-igt
  "The view of IGT `id`, from the cache when `key` is unchanged."
  [env id key f]
  (let [{cached-env :env slots :slots} @igt-cache
        [k h] (get slots id)]
    (if (and (identical? env cached-env) (= k key))
      h
      (let [h (f)]
        (swap! igt-cache assoc-in [:slots id] [key h])
        h))))

(defn- notes-key
  "What an IGT's view takes from notes anywhere in the text: its marks, the
  notes shown under it, and the problems of the notes written under it."
  [doc id cited]
  (let [index (notes/note-index doc)]
    [(get-in index [:marks id])
     (mapv #(first (notes/find-note doc %)) cited)
     (filter #(= id (:igt %)) (:problems index))]))

(defn- igt-ui
  "The part of the edit state that concerns IGT `id`."
  [ui id]
  (cond-> {}
    (= id (get-in ui [:editing :igt])) (assoc :editing (:editing ui) :draft (:draft ui))
    (= id (get-in ui [:source :igt])) (assoc :source (:source ui))
    (= [:igt id] (:confirm ui)) (assoc :confirm (:confirm ui))))

(defn- text-igts
  "Each IGT of the document, under its section heading when a section
  starts there, from the cache when nothing it shows has changed. `draw`
  makes an IGT's view: (draw ctx igt index label cited). `after`, if
  given, adds what follows IGT i, outside the cache: (after i)."
  [ctx doc mode draw & [after]]
  (let [env (begin-igts! (igts-env ctx mode))
        ui (:ui ctx)
        labels (model/igt-labels doc)
        starts (model/section-starts doc)
        cited (notes/first-cited doc)
        hl (notes/highlighted-words doc (:hl-keys ctx))]
    (for [[i igt] (map-indexed vector (:igts doc))
          :let [id (:id igt), c (cited id), lui (igt-ui ui id), h (hl id)
                ;; Everything this IGT's view shows, besides the environment.
                key [igt i (labels id) lui c h (notes-key doc id c)]]]
      (list (render/section-heading (starts id))
            (cached-igt env id key #(draw (assoc ctx :ui lui :hl-words h) igt i (labels id) c))
            (when after (after i))))))

(defn- reading-view
  "A document as a reader sees it: no editing, no diagnostics."
  [s doc view]
  (let [ctx (assoc (render/context doc view {}) :read? true :hl-keys (set (:note-highlight s)))]
    (list
     [:h1.title (:title doc)]
     (when-not (str/blank? (:about doc)) [:p.about (render/prose (:about doc))])
     (render/legend ctx)
     (text-igts ctx doc :read (fn [ctx igt _ label cited] (render/igt-view ctx igt label cited)))
     (render/end-notes ctx))))

(defn- editing-view
  "A document in edit mode: click-to-edit cells, IGT tools, diagnostics,
  source editors."
  [s doc]
  (let [ui (:ui s)
        ctx (assoc (render/context doc (state/page-view s) ui) :hl-keys (set (:note-highlight s)))
        title-target {:row "title"}]
    (list
     (if (= title-target (:editing ui))
       [:h1.title (render/cell-editor ctx)]
       [:h1.title.editable {:on {:click [:edit/start title-target]} :title "Click to rename"} (:title doc)])
     (render/legend ctx)
     (if-let [text (:doc-source ui)]
       (let [{pdoc :doc :as parsed} (parse/parse-document text)]
         (source-editor (render/context pdoc (state/page-view s) {}) text
                        [:doc-source/draft] [:doc-source/apply] [:source/cancel]
                        nil (check/problems parsed) [:doc-source/fill]))
       (list
        (text-igts ctx doc :edit
                   (fn [ctx igt i label cited] (editing-igt ctx doc igt i label cited))
                   ;; A new IGT's editor depends on the whole edit state.
                   #(new-igt-editor ctx %))
        (new-igt-editor ctx nil)
        (when-not (:source ui)
          [:button.add-igt {:on {:click [:igt/add nil]}} "+ Add IGT"])
        (render/end-notes ctx)))
     (ui/syntax-help))))

;; ---------------------------------------------------------------------------
;; Text pages

(defn- doc-box
  "The text's own actions, grouped with the Read | Edit switch. Actions that
  only exist in Edit mode get a row of their own, so the first row never
  shifts."
  [s doc]
  (let [{:keys [id]} (:route s)
        edit? (= :edit (:mode s))]
    [:div.box.doc-box
     [:div.doc-box-row
      [:div.mode-switch {:role "group" :aria-label "Mode"}
       [:button {:aria-pressed (if edit? "false" "true") :on {:click [:mode/set :read]}} "Read"]
       [:button {:aria-pressed (if edit? "true" "false") :on {:click [:mode/set :edit]}} "Edit"]]
      [:div.doc-actions
       [:button {:on {:click [:new/open]}} "New"]
       [:button {:on {:click [:doc/duplicate nil]}} "Duplicate"]
       [:button {:on {:click [:doc/export]}} "Export"]
       (when id (ui/confirm-button s [:doc id] [:doc/delete id] "Delete" "Click again to delete"))]]
     (when edit?
       [:div.doc-box-row.edit-row
        [:span.row-label "Editing"]
        [:div.doc-actions
         [:button {:on {:click [:history/undo]} :disabled (not (state/can? s :past))
                   :title "Undo (Ctrl+Z)"} "Undo"]
         [:button {:on {:click [:history/redo]} :disabled (not (state/can? s :future))
                   :title "Redo (Ctrl+Shift+Z)"} "Redo"]
         [:button {:on {:click [:doc/source]}} "Document source"]]])
     (when (get-in s [:ui :new?]) (ui/new-panel))]))

(defn- version-notice
  "For a published text: whether you're seeing your edited version, with the
  way back to the published one."
  [s]
  (let [slug (get-in s [:route :slug])]
    (cond
      (and slug (state/published-changed? s slug))
      [:div.box.notice-box.changed
       [:span "The published version of this text has changed since you started editing it. "
        "You’re still seeing your edited version; discarding your changes shows the new one."]
       [:button.small {:on {:click [:override/keep slug]}} "Keep my version"]
       (ui/confirm-button s [:override slug] [:override/discard slug]
                          "Discard my changes" "Click again to discard")]
      (and slug (get-in s [:overrides slug]))
      [:div.box.notice-box
       [:span "You’re seeing your edited version of this published text, saved in this browser."]
       (ui/confirm-button s [:override slug] [:override/discard slug]
                          "Discard my changes" "Click again to discard")]
      (and slug (= :edit (:mode s)))
      [:div.box.notice-box
       [:span "Your edits are saved in this browser as your version of this text; the published text doesn’t change."]])))

(defn- row-order-panel
  "Which row comes first under each word. A display setting: the reader's
  own order for this text. In Edit mode it can be saved as the document's."
  [doc view edit?]
  (let [order (model/present-rows doc (:reader-order view))]
    [:div.row-order-panel
     [:div.row-order-head
      [:strong "Row order"]
      [:span.hint "for you, on this text"]]
     [:ol.row-order
      (for [[i k] (map-indexed vector order)]
        [:li
         [:span.row-name (tiers/row-label k)]
         [:button.small {:on {:click [:row-order/move [k -1]]} :disabled (zero? i)
                         :aria-label (str "Move " (tiers/row-label k) " up")} "↑"]
         [:button.small {:on {:click [:row-order/move [k 1]]} :disabled (= i (dec (count order)))
                         :aria-label (str "Move " (tiers/row-label k) " down")} "↓"]])]
     [:div.editor-actions
      [:button.small {:on {:click [:row-order/reset]} :disabled (empty? (:reader-order view))}
       "Text’s own order"]
      (when edit?
        [:button.small {:on {:click [:row-order/save-doc]} :title "Write this order into the text (\\order)"}
         "Save as the text’s order"])
      [:button.small {:on {:click [:row-order/close]}} "Done"]]]))

(defn- display-box
  "Display options for this text: the row order, then the groups of
  settings. The box can be collapsed to its title."
  [s doc]
  (let [view (state/page-view s)
        open? (get-in s [:view :display-open?])
        row-order? (get-in s [:ui :row-order])]
    [:section.box.display-box {:aria-label "Display options"}
     [:div.display-title
      [:button.collapse {:aria-expanded (if open? "true" "false")
                         :on {:click [:view/toggle :display-open?]}}
       [:span.chevron {:aria-hidden "true"} (if open? "▾" "▸")]
       "Display options"]]
     (when open?
       (list
        [:div.display-head
         [:button {:on {:click [(if row-order? :row-order/close :row-order/open)]}
                   :aria-pressed (if row-order? "true" "false")} "Row order"]
         (when (seq (:reader-order view)) [:span.hint "your order"])
         [:button.reset-view {:on {:click [:view/reset-page]}
                              :disabled (not (state/page-view-changed? s))
                              :title "Undo this text's display changes and row order"}
          "Reset display options"]]
        (when row-order? (row-order-panel doc view (= :edit (:mode s))))
        (ui/controls view (model/doc-features doc))))]))

(defn text-page
  "A text's page: its actions, the notice about an edited published text,
  its display options, and the text, read or edited."
  [s]
  (let [doc (state/current-doc s)
        edit? (= :edit (:mode s))]
    (ui/shell s
     (doc-box s doc)
     (version-notice s)
     (display-box s doc)
     [:main
      (if edit? (editing-view s doc) (reading-view s doc (state/page-view s)))])))
