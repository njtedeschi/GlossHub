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
            [gloss.rows :as rows]))

;; ---------------------------------------------------------------------------
;; Source editors and lines in Edit mode

(defn- fill-buttons
  "Buttons that add a \\morph or \\syl row, filled with the words'
  letters, to every line that lacks one. `on-fill` is the event, given the
  row."
  [text on-fill]
  [:div.fill-rows
   [:span.hint "Fill from the words:"]
   (for [k (parse/fillable text)
         :let [marker (str "\\" k)
               filled (parse/fill-row text k)]]
     [:button.small {:on {:click (conj on-fill k)}
                     :disabled (= filled text)
                     :title (str "Add a " marker " row with each word's letters, ready for breaks")}
      marker])])

(defn- source-editor
  "A source text box with its problems and actions, and a preview of
  `preview-lines`. `on-draft`, `on-apply` and `on-cancel` are the events of
  the box and its buttons; with `on-fill`, the Fill buttons are offered."
  [ctx text on-draft on-apply on-cancel preview-lines diags & [on-fill]]
  [:div.source-editor
   [:textarea.source {:value text
                      :rows (max 6 (inc (count (str/split-lines text))))
                      :spellcheck "false"
                      :data-autofocus "true"
                      :on {:input on-draft :keydown [:source/key]}}]
   (when on-fill (fill-buttons text on-fill))
   (render/diagnostics-view (sort-by #(or (:source-line %) 0) diags) :goto? true)
   [:div.editor-actions
    [:button.primary {:on {:click on-apply}} "Apply"]
    [:button {:on {:click on-cancel}} "Cancel"]
    [:span.hint "Ctrl+Enter to apply, Esc to cancel"]]
   (when (seq preview-lines)
     [:div.preview
      [:div.preview-label "Preview"]
      (for [l preview-lines]
        [:div.line (render/line-body ctx l {})])])])

(defn- line-source-editor
  "Editing lines as source: the source, its problems, and a preview of the
  lines as they will be, shown in `pdoc`, the document with them in place
  (so their notes, labels and numbers are right)."
  [ctx label {:keys [lines diagnostics source-lines]} pdoc text]
  (let [pctx (assoc ctx :doc pdoc :note-numbers (notes/note-numbers pdoc))
        problems (concat diagnostics (remove :raw? (mapcat #(check/check-line pdoc %) lines)))]
    [:div.line.editing
     [:div.line-head [:span.label label]]
     (source-editor pctx text [:source/draft] [:source/apply] [:source/cancel]
                    lines (check/with-source-lines problems source-lines) [:source/fill])]))

(defn- line-view
  "A line in Edit mode: its tools, the line, its problems; or its source
  editor while it is edited as source."
  [ctx doc line idx label first-cited]
  (let [source (get-in ctx [:ui :source])]
    (if (= (:id line) (:line source))
      (let [parsed (parse/parse-lines (:text source))]
        (line-source-editor ctx label parsed (edit/replace-line doc (:id line) (:lines parsed)) (:text source)))
      [:div.line {:id (str "line-" (:id line)) :dir (:dir doc)}
       [:div.line-head
        [:span.label label]
        [:span.line-tools
         [:button.small {:on {:click [:line/source (:id line)]} :title "Edit this line as text"} "Edit source"]
         [:button.small {:on {:click [:line/add idx]} :title "Add a line below"} "+ Line"]
         (let [armed? (= [:line (:id line)] (get-in ctx [:ui :confirm]))]
           [:button.small.danger {:class (when armed? "armed")
                                  :on {:click [:line/delete (:id line)]}
                                  :title "Delete this line"}
            (if armed? "Click again to delete" "Delete")])]]
       (render/line-body ctx line {:first-cited first-cited})
       (render/diagnostics-view (check/check-line doc line))])))

(defn- new-line-editor
  "The source editor for new lines, when they are being added after line
  `after` (nil: at the end)."
  [ctx after]
  (let [source (get-in ctx [:ui :source])]
    (when (and source (not (:line source)) (= after (:after source)))
      (let [parsed (parse/parse-lines (:text source))]
        (line-source-editor ctx "New line" parsed (edit/insert-lines (:doc ctx) after (:lines parsed)) (:text source))))))

;; ---------------------------------------------------------------------------
;; Line cache
;;
;; A line's view is rebuilt only when something it shows changes: the line
;; itself, its place, its share of the edit state, or the surroundings (the
;; display options and the document apart from its lines). Otherwise the
;; previous hiccup is returned as is, and gloss.app.dom skips it, so typing in a
;; cell costs one line, not the whole text.

(defonce ^:private line-cache (atom {:env nil :slots {}}))

(defn- lines-env
  "What every line's view depends on besides the line: the mode, the
  document without its lines, and the context without the edit state."
  [ctx mode]
  [mode (dissoc (:doc ctx) :lines) (dissoc ctx :doc :ui)])

(defn- begin-lines!
  "Starts rendering a text's lines. Returns the environment to cache them
  under: last render's, when equal, so that each line compares it by
  identity. A different environment empties the cache."
  [env]
  (let [old (:env @line-cache)]
    (if (= old env)
      old
      (do (reset! line-cache {:env env :slots {}}) env))))

(defn- cached-line
  "The view of line `id`, from the cache when `key` is unchanged."
  [env id key f]
  (let [{cached-env :env slots :slots} @line-cache
        [k h] (get slots id)]
    (if (and (identical? env cached-env) (= k key))
      h
      (let [h (f)]
        (swap! line-cache assoc-in [:slots id] [key h])
        h))))

(defn- notes-key
  "What a line's view takes from notes anywhere in the text: its marks, the
  notes shown under it, and the problems of the notes written under it."
  [doc id cited]
  (let [index (notes/note-index doc)]
    [(get-in index [:marks id])
     (mapv #(first (notes/find-note doc %)) cited)
     (filter #(= id (:line %)) (:problems index))]))

(defn- line-ui
  "The part of the edit state that concerns line `id`."
  [ui id]
  (cond-> {}
    (= id (get-in ui [:editing :line])) (assoc :editing (:editing ui) :draft (:draft ui))
    (= id (get-in ui [:source :line])) (assoc :source (:source ui))
    (= [:line id] (:confirm ui)) (assoc :confirm (:confirm ui))))

(defn- text-lines
  "Each line of the document, under its section heading when a section
  starts there, from the cache when nothing it shows has changed. `draw`
  makes a line's view: (draw ctx line index label cited). `after`, if
  given, adds what follows line i, outside the cache: (after i)."
  [ctx doc mode draw & [after]]
  (let [env (begin-lines! (lines-env ctx mode))
        ui (:ui ctx)
        labels (model/line-labels doc)
        starts (model/section-starts doc)
        cited (notes/first-cited doc)
        hl (notes/highlighted-words doc (:hl-keys ctx))]
    (for [[i line] (map-indexed vector (:lines doc))
          :let [id (:id line), c (cited id), lui (line-ui ui id), h (hl id)
                ;; Everything this line's view shows, besides the environment.
                key [line i (labels id) lui c h (notes-key doc id c)]]]
      (list (render/section-heading (starts id))
            (cached-line env id key #(draw (assoc ctx :ui lui :hl-words h) line i (labels id) c))
            (when after (after i))))))

(defn- reading-view
  "A document as a reader sees it: no editing, no diagnostics."
  [s doc view]
  (let [ctx (assoc (render/context doc view {}) :read? true :hl-keys (set (:note-highlight s)))]
    (list
     [:h1.title (:title doc)]
     (when-not (str/blank? (:about doc)) [:p.about (render/prose (:about doc))])
     (render/legend ctx)
     (text-lines ctx doc :read
                 (fn [ctx line _ label cited]
                   [:div.line {:dir (:dir doc)}
                    [:div.line-head [:span.label label]]
                    (render/line-body ctx line {:first-cited cited})]))
     (render/end-notes ctx))))

(defn- editing-view
  "A document in edit mode: click-to-edit cells, line tools, diagnostics,
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
       (let [{pdoc :doc diagnostics :diagnostics source-lines :source-lines} (parse/parse-document text)]
         (source-editor (render/context pdoc (state/page-view s) {}) text
                        [:doc-source/draft] [:doc-source/apply] [:source/cancel]
                        nil (check/with-source-lines (concat diagnostics (remove :raw? (check/check-doc pdoc))) source-lines)
                        [:doc-source/fill]))
       (list
        (text-lines ctx doc :edit
                    (fn [ctx line i label cited] (line-view ctx doc line i label cited))
                    ;; A new line's editor depends on the whole edit state.
                    #(new-line-editor ctx %))
        (new-line-editor ctx nil)
        (when-not (:source ui)
          [:button.add-line {:on {:click [:line/add nil]}} "+ Add line"])
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

(defn- layers-panel
  "Which row comes first under each word. A display setting: the reader's
  own order for this text. In Edit mode it can be saved as the document's."
  [doc view edit?]
  (let [order (model/present-layers doc (:reader-order view))]
    [:div.layers-panel
     [:div.layers-head
      [:strong "Layer order"]
      [:span.hint "for you, on this text"]]
     [:ol.layers
      (for [[i k] (map-indexed vector order)]
        [:li
         [:span.layer-name (rows/layer-label k)]
         [:button.small {:on {:click [:layers/move [k -1]]} :disabled (zero? i)
                         :aria-label (str "Move " (rows/layer-label k) " up")} "↑"]
         [:button.small {:on {:click [:layers/move [k 1]]} :disabled (= i (dec (count order)))
                         :aria-label (str "Move " (rows/layer-label k) " down")} "↓"]])]
     [:div.editor-actions
      [:button.small {:on {:click [:layers/reset]} :disabled (empty? (:reader-order view))}
       "Text’s own order"]
      (when edit?
        [:button.small {:on {:click [:layers/save-doc]} :title "Write this order into the text (\\order)"}
         "Save as the text’s order"])
      [:button.small {:on {:click [:layers/close]}} "Done"]]]))

(defn- display-box
  "Display options for this text: layer order, then the setting rows. The
  box can be collapsed to its title."
  [s doc]
  (let [view (state/page-view s)
        open? (get-in s [:view :display-open?])
        layers? (get-in s [:ui :layers])]
    [:section.box.display-box {:aria-label "Display options"}
     [:div.display-title
      [:button.collapse {:aria-expanded (if open? "true" "false")
                         :on {:click [:view/toggle :display-open?]}}
       [:span.chevron {:aria-hidden "true"} (if open? "▾" "▸")]
       "Display options"]]
     (when open?
       (list
        [:div.display-head
         [:button {:on {:click [(if layers? :layers/close :layers/open)]}
                   :aria-pressed (if layers? "true" "false")} "Layers"]
         (when (seq (:reader-order view)) [:span.hint "your order"])
         [:button.reset-view {:on {:click [:view/reset-page]}
                              :disabled (not (state/page-view-changed? s))
                              :title "Undo this text's display changes and layer order"}
          "Reset display options"]]
        (when layers? (layers-panel doc view (= :edit (:mode s))))
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
