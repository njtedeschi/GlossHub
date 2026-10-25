(ns gloss.app.pages
  "The section pages: Texts, Guide, About and Settings."
  (:require [clojure.string :as str]
            [gloss.app.markdown :as md]
            [gloss.app.markdown-pages :as md-pages]
            [gloss.app.state :as state]
            [gloss.app.ui :as ui]
            [gloss.check :as check]
            [gloss.english :as english]
            [gloss.model :as model]
            [gloss.parse :as parse]
            [gloss.render :as render]
            [gloss.serialize :as ser]))

(defn published-page
  "The Texts page, Published tab: the published texts."
  [s]
  (ui/shell s
   [:main
    [:h1.title "Texts"]
    (ui/texts-tabs s)
    (if (empty? @state/library)
      [:p.hint-block "No texts have been published yet. You can write your own under "
       [:a {:href "#mine"} "My texts"] ", and the " [:a {:href "#guide"} "Guide"]
       " shows how."]
      [:ul.library
       (for [{:keys [slug doc]} @state/library]
       [:li
        [:div.entry-head
         [:a.library-title {:href (str "#" slug)} (:title doc)]
         (when (get-in s [:overrides slug])
           [:span.badge {:title "You have edited this text in this browser"} "edited"])
         [:span.entry-actions
          [:button.small {:on {:click [:doc/duplicate [:slug slug]]}} "Duplicate"]]]
        (when-not (str/blank? (:about doc)) [:p (render/prose (:about doc))])])])]))

(defn mine-page
  "The Texts page, My texts tab: the reader's own texts."
  [s]
  (let [own (keep (fn [id] (when-let [d (get-in s [:docs id])] [id d])) (:order s))]
    (ui/shell s
     [:main
      (when (= :own (get-in s [:route :page]))
        [:p.notice "That text isn’t here. It may have been deleted, or saved in another browser."])
      [:h1.title "Texts"]
      (ui/texts-tabs s)
      [:div.section-head
       [:button {:on {:click [:new/open]}} "New"]]
      (when (get-in s [:ui :new?]) (ui/new-panel))
      (if (seq own)
        [:ul.library
         (for [[id doc] own]
           [:li
            [:div.entry-head
             [:a.library-title {:href (str "#" id)} (:title doc)]
             [:span.entry-actions
              [:button.small {:on {:click [:doc/duplicate [:id id]]}} "Duplicate"]
              (ui/confirm-button s [:doc id] [:doc/delete id] "Delete" "Click again to delete")]]
            [:p (if (str/blank? (:about doc))
                  (english/counted (count (:igts doc)) "IGT" "IGTs")
                  (render/prose (:about doc)))]])]
        [:p.hint-block (if (seq @state/library)
                         "None yet. Start one with New, or duplicate a published text."
                         "None yet. Start one with New.")])
      [:p.hint-block "My texts, and your edits to published texts, are saved in this browser
                      only. Use Export on a text’s page to keep a copy."]])))

(def example-view
  "Display settings for live examples: fixed, so every example shows what
  it is about whatever the reader's own settings are."
  (merge state/default-view {:pos :full :stems :underline}))

(defn- live-example
  "A live example on a Markdown page: its source beside the result, and its
  caption (where it comes from) below them. The source can be edited to try
  changes; the draft is kept under the page's `address` and the example's
  number `i` (guide-notes:1)."
  [s address {:keys [text info caption]} i]
  (let [id (str address ":" i)
        draft (get-in s [:example-drafts id])
        src (or draft text)
        {:keys [doc] :as parsed} (parse/parse-document src model/format-version)
        problems (check/problems parsed)
        ctx (assoc (render/context doc example-view {}) :read? true :hl-keys (set (:note-highlight s)))]
    [:figure.example
     [:div.example-source
      (if draft
        [:textarea.source.example-input {:value draft
                                         :rows (inc (count (str/split-lines draft)))
                                         :spellcheck "false"
                                         :aria-label "Example source"
                                         :on {:input [:example/draft id]}}]
        [:pre.example-code [:code src]])
      [:div.example-actions
       (if draft
         [:button.small {:on {:click [:example/reset id]}} "Reset"]
         [:button.small {:on {:click [:example/edit [id text]]}} "Edit this example"])
       (when (str/includes? info "broken") [:span.hint "This example shows a problem on purpose."])]]
     [:div.example-result
      (render/legend ctx)
      (render/igts-view ctx)
      (render/end-notes ctx)
      (render/diagnostics-view problems)]
     (when caption [:figcaption.example-caption (md/inline caption)])]))

(defn- tier-grid
  "A figure on a Markdown page (```gloss grid): an IGT's source laid out
  with a row for each tier and a column for each word
  (gloss.serialize/igt-grid)."
  [text]
  (let [doc (:doc (parse/parse-document text model/format-version))]
    [:figure.tier-grid
     [:table {:dir (:dir doc)}
      [:tbody
       (for [[command cells] (ser/igt-grid (first (:igts doc)))]
         [:tr
          [:th {:scope "row"} (str "\\" command)]
          (for [{:keys [text span]} cells]
            [:td {:colspan (when (> span 1) span)} text])])]]]))

(defn- markdown-page
  "A Markdown page (gloss.app.markdown-pages): its title, then its body,
  with its live examples. `before` goes above the title, `after` below the
  body."
  ([s page] (markdown-page s page nil nil))
  ([s {:keys [address title body]} before after]
   (ui/shell s
    [:main
     before
     [:h1.title title]
     [:div.markdown-body
      (md/render body (fn [{:keys [info text] :as b} n]
                        (cond
                          (= info "gloss grid") (tier-grid text)
                          (str/starts-with? info "gloss") (live-example s address b n)
                          :else [:pre [:code text]])))]
     after])))

(defn- guide-index
  "The Guide's first page: its pages, group by group."
  [s]
  (ui/shell s
   [:main.guide
    [:h1.title "Guide"]
    [:p.about "Short pages, grouped by topic. Start with " [:em "Getting started"] "; after that,
               each page stands on its own. Every example is live: it shows its source with the
               result, and you can edit the source to try things out."]
    (for [{:keys [title pages]} md-pages/groups]
      [:section.guide-group
       [:h2 title]
       [:ul.guide-pages
        (for [{:keys [address title description]} pages]
          [:li
           [:a.library-title {:href (str "#" address)} title]
           (when description [:p (md/inline description)])])]])]))

(defn- guide-article
  "One page of the Guide: the Markdown page, with its group above it and
  links to the other pages in its group below."
  [s slug]
  (let [page (md-pages/by-slug slug)
        group (md-pages/group-of page)
        others (remove #(= slug (:slug %)) (:pages group))]
    (markdown-page
     s page
     [:p.guide-crumb [:a {:href "#guide"} "Guide"] (str " · " (:title group))]
     (when (seq others)
       [:nav.guide-more {:aria-label (str "More in " (:title group))}
        [:span.guide-more-label (str "More in " (:title group) ":")]
        [:ul
         (for [{:keys [address title]} others]
           [:li [:a {:href (str "#" address)} title]])]]))))

(defn guide-page
  "The Guide: its index, or the page the route names."
  [s]
  (if-let [slug (get-in s [:route :guide-page])]
    (guide-article s slug)
    (guide-index s)))

(defn about-page
  "The About page: the Markdown page pages/about.md."
  [s]
  (markdown-page s md-pages/about))

(def ^:private all-features
  "Every kind of content a text can have, as far as display defaults go (the
  IPA transcription follows the phonemic and phonetic ones)."
  #{:scansion :ruby :translit :phonemic :phonetic :gloss :gram :pos
    :wgloss :morph-breaks :stems :roots :fmt :color :free :notes})

(defn- default-display-box
  "The reader's default display options: the same settings as a text's
  Display options box, for every kind of content."
  [s]
  (let [view (:view s)
        site-defaults? (= (dissoc view :row-orders :display-open?)
                          (dissoc state/default-view :row-orders :display-open?))]
    [:section.box.display-box.defaults-box {:aria-label "Default display options"}
     [:div.display-title [:strong "Default display options"]]
     [:div.display-head
      [:button.reset-view {:on {:click [:defaults/reset]}
                           :disabled site-defaults?
                           :title "Go back to the site's default display options"}
       "Reset to the site defaults"]]
     (ui/controls view all-features)]))

(defn- choice-group
  "A row of buttons choosing one value of a preference."
  [s k label]
  (let [current (get-in s [:prefs k])]
    [:div.setting
     [:span.setting-label label]
     [:div.mode-switch {:role "group" :aria-label label}
      (for [[v text] (state/pref-choices k)]
        [:button {:aria-pressed (if (= v current) "true" "false")
                  :on {:click [:prefs/set [k v]]}} text])]]))

(defn settings-page
  "The Settings page: appearance, texts, display defaults, saved data."
  [s]
  (let [n-own (count (:docs s))
        n-edited (count (:overrides s))]
    (ui/shell s
     [:main
      [:h1.title "Settings"]
      [:p.about "Settings are saved in this browser."]
      [:section.settings-section
       [:h2 "Appearance"]
       (choice-group s :theme "Theme")
       (choice-group s :text-size "Text size")
       [:p.hint "Text size applies to the annotated text on text pages and in the Guide."]]
      [:section.settings-section
       [:h2 "Texts"]
       (choice-group s :open-mode "Open texts in")]
      [:section.settings-section
       [:h2 "Display"]
       [:p "Every text starts from these defaults. Display options changed on a text's page
            apply to that text only, and its Reset returns it to the defaults."]
       (default-display-box s)
       [:button {:on {:click [:settings/reset-view]}} "Reset display options for every text"]]
      [:section.settings-section
       [:h2 "Your data"]
       [:p "Saved in this browser: " (english/counted n-own "text" "texts") " of your own, "
        (english/counted n-edited "edited published text" "edited published texts") ". "
        "Each text can be exported from its page, or all of them at once, as a backup."]
       [:div.editor-actions
        [:button {:on {:click [:data/export]}
                  :disabled (and (empty? (:docs s)) (empty? (:overrides s)))}
         "Export everything"]
        [:label.button "Import a file…"
         [:input.hidden {:type "file" :accept ".txt,.gloss,text/plain" :on {:change [:doc/import]}}]]]
       [:p.hint "An export holds your texts and your versions of published texts; importing it brings them back."]
       (if (get-in s [:ui :erased?])
         [:p.hint "Everything saved in this browser has been erased."]
         (ui/confirm-button s [:erase] [:settings/erase]
                            "Erase everything saved in this browser"
                            "Click again to erase your texts, edits and settings"))]])))
