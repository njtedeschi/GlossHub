(ns gloss.app.ui
  "Views shared by several pages: the site chrome, display controls, small
  widgets and the input-format reference."
  (:require [gloss.app.state :as state]))

;; ---------------------------------------------------------------------------
;; Display controls

(defn- toggle
  "A checkbox for the display setting `k`."
  [view k label & {:keys [disabled?]}]
  [:label.toggle {:class (when disabled? "off")}
   [:input {:type "checkbox" :checked (boolean (get view k)) :disabled (boolean disabled?)
            :on {:change [:view/toggle k]}}]
   label])

(defn- choice
  "A menu for the display setting `k`; `options` are [value label] pairs."
  [view k label options & {:keys [disabled?]}]
  [:label.toggle {:class (when disabled? "off")}
   label
   [:select {:value (let [v (get view k)]
                      ;; A setting this document can't use shows as the first option.
                      (name (if (some #(= v (first %)) options) v (ffirst options))))
             :disabled (boolean disabled?)
             :on {:change [:view/select k]}}
    (for [[v text] options] [:option {:value (name v)} text])]])

(defn- control-row
  "A row of related settings. The row's switch hides all of them; turning it
  back on restores them as they were. `controls` are [feature control-fn]
  pairs; a control is offered only when the document has that feature, and a
  row with none of its features isn't shown."
  [view features row-key label & controls]
  (let [on? (get view row-key)
        present (filter #(features (first %)) controls)]
    (when (seq present)
      [:div.control-row {:role "group" :aria-label label}
       [:label.row-switch
        [:input {:type "checkbox" :checked (boolean on?) :on {:change [:view/toggle row-key]}}]
        label]
       [:div.row-controls
        (for [[_ c] present] (c (not on?)))]])))

(defn controls
  "The display settings rows. `features` is the content there is settings
  for (gloss.model/doc-features)."
  [view features]
  (let [place [[:below "below line"] [:side "beside line"] [:none "hidden"]]
        note-place [[:below "below line"] [:side "beside line"] [:end "below full text"] [:none "hidden"]]]
    [:div.controls
     ;; How the text is written and read: other scripts and readings.
     (control-row view features :orthography-row "Orthography"
                  [:translit #(toggle view :translit "Transliteration" :disabled? %)]
                  [:phonemic #(toggle view :phonemic "Phonemic transcription" :disabled? %)]
                  [:phonetic #(toggle view :phonetic "Phonetic transcription" :disabled? %)]
                  [:ipa #(toggle view :ipa "IPA transcription" :disabled? %)]
                  [:ruby #(toggle view :ruby "Ruby characters" :disabled? %)])
     (control-row view features :glossing-row "Glossing"
                  [:gloss #(choice view :gloss "Morphemic gloss"
                                   (if (features :gram)
                                     [[:full "full"] [:tags "tags only"] [:none "hidden"]]
                                     [[:full "shown"] [:none "hidden"]])
                                   :disabled? %)]
                  [:pos #(choice view :pos "Parts of speech" [[:full "with subclass"] [:class "class only"] [:none "hidden"]] :disabled? %)]
                  [:wgloss #(toggle view :wgloss "Informal gloss" :disabled? %)]
                  [:morph-breaks #(toggle view :morph-breaks "Morpheme breaks" :disabled? %)])
     (control-row view features :scansion-row "Scansion"
                  [:scansion #(toggle view :marks "Metrical marks" :disabled? %)]
                  [:scansion #(toggle view :syllables "Syllable breaks" :disabled? %)])
     ;; Content that isn't aligned with the text, so it can sit below each
     ;; line or beside it.
     (control-row view features :commentary-row "Commentary"
                  [:free #(choice view :free "Free translation" place :disabled? %)]
                  [:notes #(choice view :notes "Notes" note-place :disabled? %)])
     ;; How parts of the text are set off.
     (control-row view features :marking-row "Marking"
                  [:stems #(choice view :stems "Stem marking" [[:underline "underlined"] [:bold "bold"] [:none "unmarked"]] :disabled? %)]
                  [:roots #(choice view :roots "Root marking" [[:double "double underline"] [:bold "bold"] [:none "unmarked"]] :disabled? %)]
                  [:fmt #(toggle view :text-fmt "Text formatting" :disabled? %)]
                  [:color #(toggle view :color "Color coding" :disabled? %)])]))

(defn syntax-help
  "A summary of the input format, folded unless `open?`."
  [& [open?]]
  [:details.help {:open (boolean open?)}
   [:summary "Input format"]
   [:pre "\\ex 1.8                               start a line (label optional; unique in its section)
\\scan     _ u u ' _ _ ' _ u u ' _ || _ …   long _  short u  anceps x  foot '  caesura ||
\\syl      Mū-sa mi-hī cau-sās quō …        syllables: - between, ‿ joins words, (e) elided
\\text     Mūsa, mihī **causās** memorā, quō nūmine laesō   the text as written
          *italic* **bold** ~~struck~~ ==highlight== ++underline++   [words](address) a link
          … + …                          + starts a subline (a, b, …)
          {古|ふる}{池|いけ}                   ruby reading over characters: {base|reading}
\\morph    Mūs-a mihī [caus]-ās …          morphemes: - affix  = clitic  ~ reduplication  \\0 zero
                                        [stem]  (root) letters, e.g. (k)a(t)a(v)
\\translit bereshit bara …               transliteration, one item per word
\\morph[translit] be=[reshit] [(b)a(r)a] …   its morphemes; \\syl[translit] its syllables
\\phonemic huruike …                     phonemic transcription, raw IPA (likewise \\phonetic, \\ipa)
\\glmorph  muse-VOC.SG recount-2SG.IMP …   morphemic (Leipzig) gloss, one per morpheme
\\glword   {O Muse} recount …              informal glosses; {braces} group words
\\pos      N V.trans Pro=Conj …            part of speech, .subclass (any case, full or short names; = per clitic)
\\ft       Free translation (continues on following lines)
\\note     {quō nūmine laesō} Note text   quotes its words; the number goes after them
          {a … b} from a to the next b   {a#2} the second a   {@1.10 …} in line 1.10 (@sec/1.10)
          {* …} no number there   \\note[tag] {…} … a tag, to refer to the note

Short names: \\tx (\\text), \\tl (\\translit), \\gl (\\glmorph), \\wg (\\glword).
A backslash makes punctuation literal: \\*wódr̥, etc\\., \\{, \\\\. An apostrophe is a letter.
In \\text, keyboard symbols other than . , ; : \" ' ! ? - / ( ) are reserved: escape them
to write them in a word. Other rows reserve the symbols they don't use, and flag them too.

Header (before the first \\ex or \\section):
\\glosshubformat 1                 the format version (optional; the site writes it)
\\title Aeneid 1.8–11
\\dir   rtl                        right-to-left text (e.g. Hebrew)
\\lang  ja                         language of \\text (picks fonts)
\\order text translit glword glmorph pos   order of the rows under each word (or use Layers)
\\abbr  ACT active                 add or override a glossing abbreviation
\\color ACC #E5323B Acc            color morphemes glossed ACC; label for the legend

\\section[ode] Odes 1.11           a group of lines under a heading
\\end                              ends the lines; only notes may follow"]])

(defn confirm-button
  "A button that takes two clicks: the first arms it."
  [s confirm-key action label armed-label]
  (let [armed? (= confirm-key (get-in s [:ui :confirm]))]
    [:button.danger {:class (when armed? "armed") :on {:click action}}
     (if armed? armed-label label)]))

;; ---------------------------------------------------------------------------
;; Site chrome

(defn section-of
  "The navigation section a route belongs to."
  [route]
  (case (:page route)
    (:published :mine :text :own) "texts"
    (some-> (:page route) name)))

(defn- nav-bar
  "The navigation bar, with the current section marked."
  [route]
  (let [current (section-of route)]
    [:nav.nav-bar {:aria-label "Site"}
     [:div.nav-inner
      [:a.brand {:href "#published"} state/site-name]
      [:ul.nav-links
       (for [[href label] state/sections]
         [:li [:a {:href (str "#" href) :aria-current (when (= current href) "page")} label]])]]]))

(defn new-panel
  "Starting a text: blank, or from a file."
  []
  [:div.new-panel
   [:strong "New text"]
   [:button {:on {:click [:doc/new]}} "Blank"]
   [:label.button "Import a file…"
    [:input.hidden {:type "file" :accept ".txt,.gloss,text/plain" :on {:change [:doc/import]}}]]
   [:button.small {:on {:click [:new/cancel]}} "Cancel"]])

(defn- status-band
  "Messages about the site itself, above every page: saving failed, data
  came from another tab, an action failed."
  [s]
  (let [{:keys [save-failed? synced? action-failed load-backup imported]} (:status s)
        band (fn [kind k & content]
               [:div.status-band {:class kind :role (if (= kind "error") "alert" "status")}
                (into [:div.status-text] content)
                (when k [:button.small {:on {:click [:status/dismiss k]}} "Dismiss"])])]
    (when (or save-failed? synced? action-failed load-backup imported)
      [:div.status-bands
       (when save-failed?
         (band "error" nil
               [:strong "Your changes couldn’t be saved in this browser."]
               " Its storage may be full or turned off. Until this is fixed, export the texts you want to keep. "
               [:button.small {:on {:click [:status/retry-save]}} "Try again"]))
       (when load-backup
         (band "error" :load-backup
               [:strong "Your saved data couldn’t be read."]
               " A copy was kept in this browser’s storage under “" load-backup "”, so nothing is lost; "
               "this page started from the published texts."))
       (when action-failed
         (band "error" :action-failed "That didn’t work, and nothing was changed. (" action-failed ")"))
       (when imported
         (band "info" :imported "Imported " imported (if (= 1 imported) " text" " texts") " from the file."))
       (when synced?
         (band "info" :synced? "This page now shows changes saved in another tab or window."))])))

(defn- site-footer
  "Every page's foot: where to send feedback, and which version this is, so
  that feedback can say."
  []
  [:footer.site-footer
   [:a {:href state/feedback-url :target "_blank" :rel "noopener noreferrer"} "Send feedback"]
   [:span.version (if-let [v (state/site-version)] (str "Version " v) "Development version")]])

(defn shell
  "Every page: the navigation bar, the page's own content, the footer."
  [s & content]
  [:div.app
   (nav-bar (:route s))
   (into [:div.page (status-band s)] content)
   (site-footer)])

(defn crash-page
  "Shown instead of a page that failed to render. It depends on nothing in
  the state, so it can always be shown."
  [message]
  [:div.app
   [:nav.nav-bar {:aria-label "Site"}
    [:div.nav-inner [:a.brand {:href "#published"} state/site-name]]]
   [:div.page
    [:main.crash
     [:h1.title "Something went wrong"]
     [:p "This page couldn’t be shown. Your texts are safe: they’re saved in this browser, and nothing was changed."]
     [:p.crash-actions
      [:a.button {:href "#published"} "Go to Texts"]
      [:a.button {:href "#settings"} "Settings"]
      [:button {:on {:click #(.reload js/location)}} "Reload"]]
     [:details [:summary "Details"] [:pre message]]]]])

(defn texts-tabs
  "Switches the Texts page between published texts and the reader's own."
  [s]
  (let [page (get-in s [:route :page])
        current (if (#{:mine :own} page) "mine" "published")]
    [:nav.tabs {:aria-label "Texts"}
     (for [[href label] state/text-tabs]
       [:a {:href (str "#" href) :aria-current (when (= current href) "page")} label])]))
