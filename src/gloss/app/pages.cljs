(ns gloss.app.pages
  "The section pages: Texts, Guide, About and Settings."
  (:require [clojure.string :as str]
            [gloss.app.state :as state]
            [gloss.app.ui :as ui]
            [gloss.render :as render]))

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
                  (let [n (count (:lines doc))] (str n (if (= 1 n) " line" " lines")))
                  (render/prose (:about doc)))]])]
        [:p.hint-block (if (seq @state/library)
                         "None yet. Start one with New, or duplicate a published text."
                         "None yet. Start one with New.")])
      [:p.hint-block "My texts, and your edits to published texts, are saved in this browser
                      only. Use Export on a text’s page to keep a copy."]])))

(defn guide-page
  "The Guide: a placeholder for now."
  [s]
  (ui/shell s
   [:main.prose-page
    [:h1.title "Guide"]
    [:p.about "This page is forthcoming."]]))

(defn about-page
  "The About page: a placeholder for now, with the one thing readers need
  to know before typing."
  [s]
  (ui/shell s
   [:main.prose-page
    [:h1.title "About"]
    [:p.about "This page is forthcoming."]
    [:p "Your texts are saved only in this browser. To keep a copy, use "
     [:strong "Export everything"] " in " [:a {:href "#settings"} "Settings"] "."]]))

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
        site-defaults? (= (dissoc view :layer-orders :display-open?)
                          (dissoc state/default-view :layer-orders :display-open?))]
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
        n-edited (count (:overrides s))
        plural (fn [n one many] (str n " " (if (= 1 n) one many)))]
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
       [:p "Saved in this browser: " (plural n-own "text" "texts") " of your own, "
        (plural n-edited "edited published text" "edited published texts") ". "
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
