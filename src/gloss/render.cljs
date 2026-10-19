(ns gloss.render
  "Hiccup for the interlinear view. Every row is derived from the document
  plus the view settings; nothing here holds state. The views are in order
  from the smallest up: gloss elements, the pieces of a word, a word's
  cells, a line, and the parts of a page around lines.

  A line is drawn from its layout (gloss.layout): each word cut into pieces
  wherever a morpheme, syllable, root, ruby reading or formatting boundary
  falls, grouped by syllable. `line-body` → `words-block` → `word-view` →
  the cells (`text-row`, `gloss-cell`, …) → `syls-view` → `piece-view`."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.formatting :as fmt]
            [gloss.layout :as layout]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.notes :as notes]
            [gloss.rows :as rows]
            [gloss.vocab :as vocab]))

(def nbsp
  "A no-break space: the content of an empty cell, which keeps the cell's
  row from collapsing."
  " ")

(def mark-glyph
  "The metrical mark drawn over a syllable, for each weight."
  {:long "–" :short "⏑" :anceps "×"})

;; ---------------------------------------------------------------------------
;; The render context
;;
;; Every view takes `ctx`, made once per render by `context`:
;;   :doc          the document
;;   :view         the display settings as the views read them (effective-view)
;;   :layer-order  the order of the rows under each word
;;   :ui           the edit state (:editing, :draft, …), when editing
;;   :vocab        the document's abbreviations
;;   :note-numbers note key → number
;; and the pages add:
;;   :read?        a reading page: nothing is editable
;;   :hl-keys      the notes whose words are highlighted
;;   :hl-words     this line's highlighted word indices
;; and, within a line, line-body and words-block add:
;;   :scan-row     the surface scansion is drawn over ("text", "translit", nil)
;;   :ruby-samples a reading from this line, to keep rows aligned

(defn effective-view
  "The settings the views read, after applying the row switches: a row that
  is off hides everything in it, while its own settings are kept for when it
  comes back on."
  [v]
  (let [orth? (:orthography-row v), gloss? (:glossing-row v), scan? (:scansion-row v)
        comm? (:commentary-row v), mark? (:marking-row v)
        place (fn [k] (if comm? (get v k) :none))]
    {:scansion (boolean (and scan? (:marks v)))
     :syllables (boolean (and scan? (:syllables v)))
     :ruby (boolean (and orth? (:ruby v)))
     :translit (boolean (and orth? (:translit v)))
     :phonemic (boolean (and orth? (:phonemic v)))
     :phonetic (boolean (and orth? (:phonetic v)))
     :ipa (boolean (and orth? (:ipa v)))
     :gloss (if gloss? (:gloss v) :none)
     :pos (if gloss? (:pos v) :none)
     :wgloss (boolean (and gloss? (:wgloss v)))
     :morph-breaks (boolean (and gloss? (:morph-breaks v)))
     :stems (if mark? (:stems v) :none)
     :roots (if mark? (:roots v) :none)
     :text-fmt (boolean (and mark? (:text-fmt v)))
     :color (boolean (and mark? (:color v)))
     :legend (boolean (and mark? (:color v)))
     :free (not= :none (place :free))
     :free-side? (= :side (place :free))
     :notes (not= :none (place :notes))
     :notes-side? (= :side (place :notes))
     :notes-end? (= :end (place :notes))}))

(defn context
  "Everything the views need, computed once per render. `view` holds the
  reader's stored settings (see gloss.app.state/default-view)."
  [doc view ui]
  {:doc doc
   :view (let [v (effective-view view)]
           ;; "Tags only" needs grammatical abbreviations to show.
           (cond-> v
             (and (= :tags (:gloss v)) (not ((model/doc-features doc) :gram))) (assoc :gloss :full)))
   ;; The reader's own order for this text, else the document's \order.
   :layer-order (model/layer-order doc (:reader-order view))
   :ui ui
   :vocab (vocab/document-vocab doc)
   :note-numbers (notes/note-numbers doc)})

;; ---------------------------------------------------------------------------
;; Gloss elements

(defn- color-style
  "Color for a morpheme, from the rules matching its gloss."
  [ctx gloss-text]
  (when (get-in ctx [:view :color])
    (when-let [rule (model/morph-color (:doc ctx) gloss-text)]
      {:color (:color rule)})))

(defn- gram
  "A grammatical label: in small capitals, with its full name on hover."
  [ctx el]
  (let [full (vocab/lookup (:vocab ctx) el)]
    [:abbr.gram {:title (or full "not in the glossing vocabulary")
                 :class (when-not full "unknown")
                 ;; Only the tag a rule names is colored.
                 :style (color-style ctx el)}
     (str/lower-case el)]))

(defn- gloss-part
  "One morpheme's gloss: grammatical elements in small caps with their full
  name on hover, lexical elements as typed."
  [ctx text]
  (interpose "."
             (for [el (vocab/gloss-elements text)]
               (if (vocab/grammatical? el) (gram ctx el) el))))

(defn- style-classes
  "Classes for formatting styles."
  [styles]
  (for [st fmt/style-order :when (contains? styles st)] (str "fmt-" (name st))))

(defn- text-styles
  "The formatting row `row` takes from \\text at `word` (and its text
  morphemes `js`), when the reader shows formatting (gloss.rows says which
  rows take it, gloss.model/styles-from-text how)."
  [ctx row word & [js]]
  (if (get-in ctx [:view :text-fmt])
    (model/styles-from-text row word js)
    #{}))

(defn- stem-class
  "The class for marked stems in the reader's chosen style (:bold,
  :underline), or nil when stems aren't marked. Each style is one CSS rule."
  [ctx]
  (case (get-in ctx [:view :stems])
    :bold "stem-bold"
    :underline "stem-underline"
    nil))

(defn- root-class
  "The class for marked roots (:double underline, :bold), or nil."
  [ctx]
  (case (get-in ctx [:view :roots])
    :bold "root-bold"
    :double "root-double"
    nil))

(defn- entirely-root?
  "Whether every letter of a segment is root."
  [seg]
  (let [ranges (:root seg)]
    (and (seq ranges)
         (every? (fn [c] (some (fn [[a b]] (and (<= a (:start c)) (<= (:end c) b))) ranges))
                 (chars/letters-of (:text seg))))))

(defn- root-gloss
  "A morpheme's gloss, its root's meaning marked: up to a backslash when the
  gloss has one, else all of it when the morpheme is entirely root in every
  row that marks it, else up to the root's end (gloss.morph/root-gloss-end).
  `segs` are the morpheme in the text and the transliteration."
  [ctx segs text]
  (let [cls (root-class ctx)
        marked (filter :root segs)]
    (cond
      (or (nil? cls) (empty? marked)) (gloss-part ctx text)
      (and (not (str/includes? text "\\")) (every? entirely-root? marked))
      [:span {:class cls} (gloss-part ctx text)]
      :else (if-let [end (morph/root-gloss-end text)]
              (let [rest-text (subs text end)
                    lead (subs rest-text 0 1)]
                (list [:span {:class cls} (gloss-part ctx (subs text 0 end))]
                      lead
                      (gloss-part ctx (subs rest-text 1))))
              [:span {:class cls} (gloss-part ctx text)]))))

(defn- full-gloss
  "The morphemic gloss as written, each gloss morpheme marked as its
  morpheme is (an infix's host, in two segments, as one)."
  [ctx word]
  (let [structure (or (model/morph-structure word) (model/segs word "text"))
        glosses (:glmorph word)
        of (morph/gloss-segments structure glosses)
        surface-segs (for [s model/surfaces :when (model/morph-src word s)] (model/segs word s))]
    (for [[gi g] (map-indexed vector glosses)
          :let [js (get of gi)
                seg (when js (morph/merge-segments structure js))]]
      [:span.gp {:class [(when (nil? js) "mismatch") (when (and (stem-class ctx) (:stem? seg)) (stem-class ctx))]}
       (cond
         (:infix? g) [:span.sep "<"]
         (:sep g) [:span.sep (:sep g)])
       [:span {:class (style-classes (text-styles ctx "glmorph" word js))}
        (root-gloss ctx (when js (map #(morph/merge-segments % js) surface-segs)) (:text g))]
       (when (:infix? g) [:span.sep ">"])])))

(defn- tag-gloss
  "Only the grammatical elements of the word's gloss, e.g. ACC.PL."
  [ctx word]
  (interpose "."
             (for [g (:glmorph word)
                   el (vocab/gloss-elements (:text g))
                   :when (vocab/grammatical? el)]
               (gram ctx el))))

;; ---------------------------------------------------------------------------
;; Words

(defn formatted
  "Text with formatting runs, as spans."
  [text runs]
  (let [n (count text)
        cuts (sort (distinct (concat [0 n] (mapcat (juxt :start :end) runs))))]
    (for [[a b] (partition 2 1 cuts) :when (< a b)
          :let [styles (fmt/styles-at runs a)]]
      (if (seq styles)
        [:span {:class (style-classes styles)} (subs text a b)]
        (subs text a b)))))

(defn prose
  "Prose (free translation, notes, \\about) with its formatting and links."
  [s]
  (for [{:keys [text styles link]} (fmt/prose s)
        :let [t (if (seq styles) [:span {:class (style-classes styles)} text] text)]]
    (if link
      [:a.link {:href link :target "_blank" :rel "noopener noreferrer"} t]
      t)))

(defn- note-mark
  "A note's number on a word. Clicking it highlights the note's words."
  [ctx {:keys [key n]}]
  [:sup.note-mark {:class (when (contains? (:hl-keys ctx) key) "active")
                   :title (str (:text (first (notes/find-note (:doc ctx) key)))
                               " (click to highlight its words)")
                   :on {:click [:note/highlight key]}}
   (or n "?")])

(defn cell-editor
  "An input for the cell being edited. Blur commits only its own target, so a
  blur from an input that is being replaced can't end the next edit."
  [ctx]
  (let [draft (get-in ctx [:ui :draft])
        target (get-in ctx [:ui :editing])]
    [:input.cell-input {:value draft
                        :size (max 4 (+ 2 (count draft)))
                        :data-autofocus "true"
                        :on {:input [:edit/draft]
                             :keydown [:edit/key]
                             :blur [:edit/blur target]}}]))

(defn- editing?
  "Whether `target` is the cell being edited."
  [ctx target]
  (= target (get-in ctx [:ui :editing])))

(defn- with-ruby-strut
  "A cell without readings in a row that has them gets an invisible reading
  over its own text, so every cell in the row has the same height. The
  reading copies a character of a real reading in the row, so it is set in
  the same font."
  [content sample]
  [:ruby.rb.strut content [:rt.rt (subs (str sample "\u00a0") 0 1)]])

(defn- ruby-text
  "A ruby reading; trailing tone numbers (jyutping hok6) as superscripts."
  [s]
  (if-let [[_ base tone] (re-matches #"(.*?[^\d])(\d+)" s)]
    (list base [:sup.tone tone])
    s))

(defn- piece-view
  "One piece of a word: its letters, with the morpheme separator or
  syllable dot before it. A joint (a hyphen of the text standing at a
  morpheme boundary) shows only while breaks are hidden, a zero morpheme
  only while they are shown."
  [ctx word segs p scan? extra-styles]
  (let [view (:view ctx)
        breaks? (:morph-breaks view)
        seg (get segs (:morph p))]
    [:span.pc {:class [(when (and scan? (:elided? p)) "elided")
                       (when (and (stem-class ctx) (:stem? seg)) (stem-class ctx))
                       (when (and (root-class ctx) (:root? p)) (root-class ctx))]
               :style (color-style ctx (:text (get (:glmorph word) (get (morph/segment-glosses segs (:glmorph word)) (:morph p)))))}
     (cond
       (:zero? p) (when breaks? (list (when (:sep p) [:span.sep (:sep p)]) "Ø"))
       (:joint? p) (when-not breaks? (:text p))
       :else (let [styles (if (:text-fmt view) (into (or (:styles p) #{}) (extra-styles (:morph p))) #{})]
               (list
                (cond
                  (and (:sep p) breaks?) [:span.sep (:sep p)]
                  (and (:syl-start? p) (:syllables view)) [:span.ssep "·"])
                ;; Formatting covers the letters, not the separator before them.
                (if (seq styles)
                  [:span {:class (style-classes styles)} (:text p)]
                  (:text p)))))]))

(defn- syls-view
  "A word's pieces in syllable groups, with the metrical marks over them when
  `scan?`. Pieces under one ruby reading are grouped so the reading can be
  centered over them."
  [ctx word segs syls scan? & [extra-styles]]
  (let [view (:view ctx)
        extra-styles (or extra-styles (constantly #{}))]
    (for [{:keys [mark cont? foot? caesura? pieces syl]} syls]
      [:span.syl {:class [(when foot? "foot") (when caesura? "caesura")
                          (when (and scan? syl (not mark) cont?) "tie-in")]}
       (when (and scan? mark) [:span.mark (mark-glyph mark)])
       (when (and scan? foot?) [:span.foot-bar])
       (when (and scan? caesura?) [:span.caesura-bar "‖"])
       (for [ps (partition-by #(get-in % [:ruby :index]) pieces)
             :let [ruby (:ruby (first ps))
                   piece-views (for [p ps] (piece-view ctx word segs p scan? extra-styles))]]
         (if (and ruby (:ruby view))
           [:ruby.rb piece-views
            (when (:first? ruby) [:rt.rt (ruby-text (:text ruby))])]
           piece-views))])))

(defn- text-row
  "A word's \\text cell: its pieces (in syllable groups, with the metrical
  marks when the scansion is drawn over the text), its punctuation, its
  link, and the numbers of the notes that end on it."
  [ctx line wl]
  (let [{:keys [view]} ctx
        word (:word wl)
        scan? (= "text" (:scan-row ctx))]
    [:span.row.text {:lang (get-in ctx [:doc :lang])}
     (when-not (str/blank? (:pre word)) [:span.punct (:pre word)])
     (let [syls (syls-view ctx word (model/segs word "text") (get-in wl [:syls "text"]) scan?)
           sample (get-in ctx [:ruby-samples "text"])
           content (if (and (:ruby view) sample (empty? (:ruby word)))
                     (with-ruby-strut syls sample)
                     syls)]
       ;; A link opens when reading; when editing it only shows where it goes.
       (if-let [url (:link word)]
         (if (:read? ctx)
           [:a.link {:href url :target "_blank" :rel "noopener noreferrer"} content]
           [:span.link {:title url} content])
         content))
     (when-not (str/blank? (:post word)) [:span.punct (:post word)])
     (when (:notes view)
       (for [m (:note-marks wl)] (note-mark ctx m)))]))

(defn- translit-pieces
  "A word's transliteration, laid out like the text, with the formatting it
  takes from the text."
  [ctx wl]
  (let [word (:word wl)
        segs (model/segs word "translit")
        ;; Morpheme by morpheme when both surfaces have a \morph (they agree).
        by-morph? (and (model/morph-src word "translit") (model/morph-src word "text")
                       (= (count segs) (count (model/segs word "text"))))]
    (syls-view ctx word segs (get-in wl [:syls "translit"]) (= "translit" (:scan-row ctx))
               (fn [j] (text-styles ctx "translit" word (when by-morph? [j]))))))

(defn- pos-tag
  "One part-of-speech tag in its standard abbreviation; with `full?` its
  subclasses too (\"V.trans\"). Unknown spellings show as typed, flagged."
  [{:keys [class subs]} full?]
  (let [shown (cons class (when full? subs))]
    [:abbr.pos-tag {:title (str/join ", " (map #(or (get-in % [:entry :name]) (str "unknown: " (:text %))) shown))}
     (interpose "."
                (for [{:keys [text entry]} shown]
                  (if entry
                    (:abbr entry)
                    [:span.unknown text])))]))

(defn- transcription-view
  "A word's transcription in row `row`, raw IPA, in its delimiters."
  [word row]
  (when-let [t (not-empty (get word (keyword row)))]
    (let [[open close] (get-in rows/by-name [row :delimiters])]
      (list [:span.delim open] t [:span.delim close]))))

(defn- editable
  "Attributes for content that can be clicked to edit, or `attrs` unchanged
  on a reading page."
  [ctx target title attrs]
  (if (:read? ctx)
    attrs
    (-> attrs
        (update :class (fn [c] [c "editable"]))
        (assoc :title title :on {:click [:edit/start target]}))))

(defn- cell
  "Word `i`'s cell in row `row`; editable in the editor. An edit's target
  is {:row :line :word}, or for content not under a word {:row \"ft\"
  :line}, {:row \"note\" :note key} or {:row \"title\"}."
  [ctx line i row row-class title content]
  (let [target {:line (:id line) :row row :word i}]
    (if (editing? ctx target)
      [:span.row {:class row-class} (cell-editor ctx)]
      [:span.row (editable ctx target title {:class row-class})
       (if (seq content) content nbsp)])))

(defn- translit-cell
  "A word's transliteration cell."
  [ctx line wl]
  (cell ctx line (:index wl) "translit" "translit" "Click to edit the transliteration"
        (when (get-in wl [:syls "translit"]) (translit-pieces ctx wl))))

(defn- transcription-cell
  "A word's cell in transcription row `row`."
  [ctx line wl row]
  (cell ctx line (:index wl) row ["transcription" row]
        (str "Click to edit the " (str/lower-case (rows/layer-label row)))
        (transcription-view (:word wl) row)))

(defn- gloss-cell
  "The morphemic gloss: in full, or only its grammatical tags; flagged when
  it has more or fewer morphemes than the word."
  [ctx line wl]
  (let [word (:word wl)
        mismatch? (and (seq (:glmorph word)) (not= (count (:glmorph word)) (model/morph-count word)))]
    (cell ctx line (:index wl) "glmorph" ["glmorph" (when mismatch? "mismatch")] "Click to edit the morphemic gloss"
          (if (= :tags (get-in ctx [:view :gloss])) (tag-gloss ctx word) (full-gloss ctx word)))))

(defn- pos-cell
  "Parts of speech, one tag per clitic group."
  [ctx line wl]
  (cell ctx line (:index wl) "pos" "pos" "Click to edit the part of speech"
        (let [parts (vocab/pos-parts (:pos (:word wl)))]
          (when (seq parts)
            (interpose [:span.sep "="] (map #(pos-tag % (= :full (get-in ctx [:view :pos]))) parts))))))

(defn- glword-cell
  "The informal gloss: with its own formatting if it has any, else the
  text's (gloss.rows :own+from-text)."
  [ctx line wl]
  (let [word (:word wl)]
    (cell ctx line (:index wl) "glword" "glword" "Click to edit the informal gloss"
          (when-not (str/blank? (:glword word))
            (if (seq (:glword-fmt word))
              (formatted (:glword word) (when (get-in ctx [:view :text-fmt]) (:glword-fmt word)))
              [:span {:class (style-classes (text-styles ctx "glword" word))} (:glword word)])))))

(defn- row-cell
  "A word's cell in row `row`."
  [ctx line wl row]
  (case row
    "text" (text-row ctx line wl)
    "translit" (translit-cell ctx line wl)
    "glmorph" (gloss-cell ctx line wl)
    "glword" (glword-cell ctx line wl)
    "pos" (pos-cell ctx line wl)
    (transcription-cell ctx line wl row)))

(defn- shown?
  "Whether the reader shows row `row` (gloss.model/row-setting)."
  [view row]
  (or (= row "text")
      (let [v (get view (model/row-setting row))]
        (boolean (and v (not= :none v))))))

(defn- word-view
  "A word's column: its rows that are shown, in the layer order. `filled`
  are the rows this line has content for, so a row that is empty on every
  word of the line isn't drawn. With `text-only?`, just the text (for the
  unbroken scansion line above sublines)."
  [ctx line wl filled & {:keys [text-only?]}]
  [:span.word {:class [(when (contains? (:hl-words ctx) (:index wl)) "note-hl")]}
   (if text-only?
     (if (= "translit" (:scan-row ctx))
       [:span.row.translit (translit-pieces ctx wl)]
       (text-row ctx line wl))
     (for [row (:layer-order ctx)
           :when (and (or (= row "text") (filled row)) (shown? (:view ctx) row))]
       (row-cell ctx line wl row)))])

;; ---------------------------------------------------------------------------
;; Lines

(defn- note-item
  "One note: its number and text. Clicking the number (or, when reading,
  the note) highlights the note's words; when editing, the text is edited."
  [ctx k target]
  (let [read? (:read? ctx)
        active? (contains? (:hl-keys ctx) k)]
    [:li {:class [(when active? "active") (when read? "clickable")]
          :on (when read? {:click [:note/highlight k]})
          :title (when read? "Click to highlight the note's words")}
     [:span.note-n {:on (when-not read? {:click [:note/highlight k]})
                    :title (when-not read? "Click to highlight the note's words")}
      ((:note-numbers ctx) k) "."]
     (if (editing? ctx target)
       (cell-editor ctx)
       [:span.note-text (editable ctx target "Click to edit the note" {})
        (let [t (:text (first (notes/find-note (:doc ctx) k)))]
          (cond
            (not (str/blank? t)) (prose t)
            read? nil
            :else [:em.missing "No text for this note"]))])]))

(defn- notes-view
  "The notes shown under a line, in number order."
  [ctx line first-cited]
  (when-let [ks (seq (sort-by (:note-numbers ctx) first-cited))]
    [:ol.notes {:dir "ltr"}
     (for [k ks]
       (note-item ctx k {:line (:id line) :row "note" :note k}))]))

(defn end-notes
  "Notes after the text: all of them, in order, when notes are placed below
  the full text; otherwise those shown under no line."
  [ctx]
  (let [numbers (:note-numbers ctx)
        ks (when (get-in ctx [:view :notes])
             (if (get-in ctx [:view :notes-end?])
               (sort-by numbers (keys numbers))
               (notes/uncited-notes (:doc ctx))))]
    (when (seq ks)
      [:section.end-notes {:dir "ltr"}
       [:h2 "Notes"]
       [:ol.notes
        (for [k ks]
          (note-item ctx k {:row "note" :note k}))]])))

(def subline-labels
  "The labels of a line's sublines, in order."
  "abcdefghijklmnopqrstuvwxyz")

(defn sublines
  "Splits a line's display units at its subline breaks:
  [[label units] ...], or one unlabelled group when there are no breaks."
  [line units]
  (let [breaks (:breaks line)
        sub-of (fn [unit] (count (filter #(<= % (:index (first (:words unit)))) breaks)))]
    (if (empty? breaks)
      [[nil units]]
      (for [group (partition-by sub-of units)]
        [(str (get subline-labels (sub-of (first group)))) group]))))

(defn- words-block
  "The words of `units`, each a column of its cells. With `scanned?`, room
  is left above the text for metrical marks; with `text-only?`, only the
  text is drawn."
  [ctx line units filled & {:keys [scanned? text-only?]}]
  (let [ctx (assoc ctx :ruby-samples
                   ;; A reading from this line, if it has any, for the
                   ;; invisible readings of cells without one (with-ruby-strut).
                   (into {} (for [w (:words line), r (:ruby w)] ["text" (:text r)])))]
    [:div.words {:class [(when scanned? (if (= "translit" (:scan-row ctx)) "scanned-translit" "scanned"))
                         (when (and (get-in ctx [:view :ruby]) (some :ruby (:words line))) "has-ruby")]}
     (for [unit units]
       [:span.unit
        (for [wl (:words unit)] (word-view ctx line wl filled :text-only? text-only?))])]))

(defn- free-view
  "A line's free translation: editable in Edit mode, where a line without
  one offers to add it."
  [ctx line]
  (let [free-target {:line (:id line) :row "ft"}]
    (cond
      (editing? ctx free-target)
      [:div.free {:dir "ltr"} (cell-editor ctx)]
      (not (str/blank? (:free line)))
      [:div.free (editable ctx free-target "Click to edit the free translation" {:dir "ltr"})
       (prose (:free line))]
      (:read? ctx) nil
      :else
      [:div.free.editable.empty {:dir "ltr" :on {:click [:edit/start free-target]}}
       "+ free translation"])))

(def ^:private layouts
  "Each line's layout, by the line (weakly), with the note marks it was
  made with: it doesn't depend on the display options, so changing them
  doesn't redo it."
  (js/WeakMap.))

(defn- line-layout
  "A line's layout (gloss.layout/line-layout), from the cache when the line
  and its note marks are unchanged."
  [line marks]
  (let [[made-with units] (.get layouts line)]
    (if (and units (= made-with marks))
      units
      (let [units (layout/line-layout line marks)]
        (.set layouts line [marks units])
        units))))

(defn line-body
  "The interlinear block for a line (without the line's toolbar).

  A line split into sublines shows each subline under its letter. If the
  line also has scansion, the whole line is first shown unbroken with its
  metrical marks, so the meter isn't interrupted, and the sublines below
  carry the glosses."
  [ctx line {:keys [first-cited]}]
  (let [{:keys [view]} ctx
        units (line-layout line (get-in (notes/note-index (:doc ctx)) [:marks (:id line)]))
        filled (model/line-rows line)
        ;; The surface the scansion is drawn over: the text when it has
        ;; syllables, else the transliteration when it is shown.
        scan-row (when (:scansion view)
                   (cond (model/prosody line "text") "text"
                         (and (model/prosody line "translit") (:translit view)) "translit"))
        ctx (assoc ctx :scan-row scan-row)
        scan? (some? scan-row)
        subs (sublines line units)
        split? (some first subs)
        main (list
              (when (and scan? split?)
                [:div.scan-line
                 (words-block (update ctx :view assoc :notes false) line units filled
                              :scanned? true :text-only? true)])
              (if split?
                (let [ctx (if scan? (-> ctx (update :view assoc :scansion false) (assoc :scan-row nil)) ctx)]
                  (for [[label group] subs]
                    [:div.subline
                     [:span.sub-label label]
                     (words-block ctx line group filled)]))
                (words-block ctx line units filled :scanned? scan?)))
        free (when (:free view) (free-view ctx line))
        notes (when (and (:notes view) (not (:notes-end? view)))
                (notes-view ctx line (if (some? first-cited)
                                       first-cited
                                       (get (notes/first-cited (:doc ctx)) (:id line)))))
        side (filter some? [(when (:free-side? view) free) (when (:notes-side? view) notes)])
        below (filter some? [(when-not (:free-side? view) free) (when-not (:notes-side? view) notes)])]
    [:div.line-grid {:class (when (seq side) "with-side")}
     [:div.line-main main below]
     (when (seq side) [:aside.line-side {:dir "ltr"} side])]))

(defn section-heading
  "The heading a section's lines are shown under."
  [section]
  (when section
    [:h2.section-heading (or (not-empty (:title section)) (:label section))]))

(defn diagnostics-view
  "Problems, most specific first, each labelled Error or Warning. With
  `goto?` (in a source editor), each one that knows its source line links
  to it."
  [diags & {:keys [goto?]}]
  (when (seq diags)
    [:ul.diags
     (for [d diags]
       [:li {:class (name (:level d))}
        [:span.diag-level (if (= :error (:level d)) "Error" "Warning")]
        (when (and goto? (:source-line d))
          [:button.line-link {:on {:click [:source/goto (:source-line d)]}
                              :title "Select this line in the source"}
           (str "Line " (:source-line d))])
        (:msg d)])]))

(defn legend
  "The color rules' legend, when colors are shown."
  [ctx]
  (let [colors (get-in ctx [:doc :colors])]
    (when (and (seq colors) (get-in ctx [:view :color]) (get-in ctx [:view :legend]))
      [:div.legend
       (for [{:keys [tag color label]} colors]
         [:span.legend-item {:style {:color color}
                             :title (vocab/lookup (:vocab ctx) tag)}
          (or label tag)])])))
