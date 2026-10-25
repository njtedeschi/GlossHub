(ns gloss.app-test
  (:require [cljs.reader :as reader]
            [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gloss.app.actions :as actions]
            [gloss.app.state :as state]
            [gloss.app.storage :as storage]
            [gloss.check :as check]
            [gloss.edit :as edit]
            [gloss.fixtures :as fx]
            [gloss.model :as model]
            [gloss.parse :as parse]
            [gloss.app.samples :as samples]))

(deftest routes
  (is (= {:page :published} (state/route-of "")))
  (is (= {:page :published} (state/route-of "#")))
  (is (= {:page :mine} (state/route-of "#mine")))
  (is (= {:page :published} (state/route-of "#texts")))
  (is (= {:page :published} (state/route-of "#published")))
  (is (= {:page :guide} (state/route-of "#guide")))
  (with-redefs [state/library fx/library]
    (is (= {:page :text :slug "latin"} (state/route-of "#latin")))
    (is (= {:page :text :slug "hebrew"} (state/route-of "#/hebrew"))))
  (is (= {:page :own :id "d1234abcd"} (state/route-of "#d1234abcd"))))

(deftest reader-row-order-is-a-display-setting
  (with-redefs [state/library fx/library]
  (let [s {:route {:page :text :slug "hebrew"} :overrides {} :docs {} :view {:row-orders {}}}
        s2 (actions/handle s nil [:row-order/move ["translit" -1]])]
    (is (empty? (:overrides s2)))
    (is (= "translit" (first (get-in s2 [:view :row-orders "hebrew"]))))
    (let [s3 (actions/handle s2 nil [:row-order/save-doc])]
      (is (= "translit" (first (:order (get-in s3 [:overrides "hebrew"])))))
      (is (nil? (get-in s3 [:view :row-orders "hebrew"])))))))

(deftest editing-a-published-text-starts-a-local-version
  (with-redefs [state/library fx/library]
  (let [s {:route {:page :text :slug "latin"} :overrides {} :docs {}}
        published (state/current-doc s)
        target {:igt (get-in published [:igts 0 :id]) :row "glword" :word 0}
        s2 (-> s
               (actions/handle nil [:edit/start target])
               (actions/handle nil [:edit/commit]))]
    (is (= "Latin sentences" (:title published)))
    (is (contains? (:overrides s2) "latin"))
    (is (= (:igts published) (:igts (state/current-doc s2))))
    (is (= "Latin sentences" (get-in @state/library [0 :doc :title]))))))

(deftest applying-an-unchanged-source-is-no-edit
  (with-redefs [state/library fx/library]
    (let [s {:route {:page :text :slug "latin"} :overrides {} :docs {}}
          id (get-in (state/current-doc s) [:igts 0 :id])
          draft (fn [s f] (actions/handle s #js {:target #js {:value (f (get-in s [:ui :doc-source]))}} [:doc-source/draft]))]
      (testing "the whole text, or one IGT, applied as it was: no edited version, nothing to undo"
        (doseq [s2 [(-> s (actions/handle nil [:doc/source]) (actions/handle nil [:doc-source/apply]))
                    (-> s (actions/handle nil [:igt/source id]) (actions/handle nil [:source/apply]))]]
          (is (empty? (:overrides s2)))
          (is (empty? (:history s2)))
          (is (= {} (:ui s2)))))
      (testing "a change is an edit"
        (let [s2 (-> s
                     (actions/handle nil [:doc/source])
                     (draft #(str/replace % "\\title Latin sentences" "\\title Renamed"))
                     (actions/handle nil [:doc-source/apply]))]
          (is (= "Renamed" (:title (state/current-doc s2))))
          (is (contains? (:overrides s2) "latin")))))))

(deftest bundled-texts-parse-cleanly
  (testing "the published texts"
    (doseq [{:keys [slug source]} samples/published
            :let [{:keys [doc] :as parsed} (parse/parse-document source)]]
      (is (seq (:igts doc)) slug)
      (is (empty? (check/problems parsed)) slug)
      (is (not (clojure.string/blank? (:about doc))) slug)))
  (testing "the template for a new text"
    (let [{:keys [doc] :as parsed} (parse/parse-document samples/blank)]
      (is (= "Untitled" (:title doc)))
      (is (empty? (:igts doc)))
      (is (empty? (check/problems parsed))))))

(defn- edit-glword [s text]
  (let [igt-id (get-in (state/current-doc s) [:igts 0 :id])
        target {:igt igt-id :row "glword" :word 0}]
    (-> s
        (actions/handle nil [:edit/start target])
        (assoc-in [:ui :draft] text)
        (actions/handle nil [:edit/commit]))))

(deftest undo-and-redo
  (with-redefs [state/library fx/library]
  (let [s0 {:route {:page :text :slug "latin"} :overrides {} :docs {} :view {}}
        wg #(get-in (state/current-doc %) [:igts 0 :words 0 :glword])
        s1 (edit-glword s0 "A")
        s2 (edit-glword s1 "B")]
    (is (= ["the sailor" "A" "B"] (map wg [s0 s1 s2])))
    (let [u1 (actions/handle s2 nil [:history/undo])
          u2 (actions/handle u1 nil [:history/undo])
          r1 (actions/handle u2 nil [:history/redo])]
      (is (= "A" (wg u1)))
      (testing "undoing the first edit of a published text removes the reader's version"
        (is (= "the sailor" (wg u2)))
        (is (empty? (:overrides u2))))
      (is (= "A" (wg r1)))
      (testing "a new edit clears what could be redone"
        (is (empty? (get-in (edit-glword u1 "C") [:history [:overrides "latin"] :future])))))
    (testing "discarding one's changes can be undone"
      (let [d (-> s2 (actions/handle nil [:override/discard "latin"]) (actions/handle nil [:override/discard "latin"]))]
        (is (= "the sailor" (wg d)))
        (is (= "B" (wg (actions/handle d nil [:history/undo])))))))))

(deftest saved-texts-are-source
  (let [s {:docs {"d1" (:doc (parse/parse-document fx/hebrew))}
           :overrides {"latin" (:doc (parse/parse-document fx/latin))}
           :order ["d1"] :view {}}
        saved (storage/saved-form s)
        back (storage/migrate (reader/read-string (pr-str saved)))]
    (is (= 2 (:format saved)))
    (is (string? (get-in saved [:docs "d1"])))
    ;; Generated ids differ after a reload; sections are compared by label.
    (is (= (model/without-ids (get-in s [:docs "d1"])) (model/without-ids (get-in back [:docs "d1"]))))
    (is (= (model/without-ids (get-in s [:overrides "latin"])) (model/without-ids (get-in back [:overrides "latin"]))))))

(deftest display-settings-saved-in-format-1-carry-over
  ;; As the first public version saved them: the groups' switches named as
  ;; rows, the reader's row orders as layer orders, and "tags only" glosses.
  (let [saved {:format 1 :docs {} :overrides {}
               :view {:glossing-row false :scansion-row false :marks false
                      :layer-orders {"aeneid" ["glmorph" "text"]} :gloss :tags}
               :text-views {"aeneid" {:marking-row false :gloss :tags}}}
        back (storage/migrate (reader/read-string (pr-str saved)))
        view (state/clean-view (:view back))]
    (is (= 2 (:format back)))
    (is (false? (:glossing-group view)))
    (is (false? (:scansion-group view)))
    (is (false? (:marks view)))
    (is (true? (:commentary-group view)) "a switch that wasn't saved keeps its default")
    (is (= {"aeneid" ["glmorph" "text"]} (:row-orders view)))
    (is (= :grammatical (:gloss view)))
    (is (= {"aeneid" {:marking-group false :gloss :grammatical}}
           (state/clean-text-views (:text-views back)))))
  (testing "settings saved in format 2 are read as they are"
    (is (= {:glossing-group false :gloss :none}
           (:view (storage/migrate {:format 2 :view {:glossing-group false :gloss :none}}))))))

(deftest problems-point-at-source-lines
  (let [{:keys [diagnostics]} (parse/parse-document "\\title T\n\n\\ex\n\\tx a b\n% comment\n\\gl x\n\\wg p q r\n\\scan _ u\n\\syl ab")
        lines-by-msg (into {} (map (juxt :msg :source-line) diagnostics))]
    (is (= #{6 7 9} (set (vals lines-by-msg)))))
  (let [{:keys [igts source-lines]} (parse/parse-igts "\\ex\n\\tx a\n\\gl XYZ")
        d (first (check/check-igt {} (first igts)))]
    (is (= "glmorph" (:field d)))
    (is (= 3 (get-in source-lines [(:igt d) "glmorph"])))))

(deftest preferences
  (testing "unknown or malformed saved values fall back to the defaults"
    (is (= state/default-prefs (state/clean-prefs nil)))
    (is (= (assoc state/default-prefs :theme :dark)
           (state/clean-prefs {:theme :dark :text-size :huge :bogus 1}))))
  (let [s (actions/handle {:prefs state/default-prefs :docs {} :overrides {}} nil [:prefs/set [:text-size :large]])]
    (is (= :large (get-in s [:prefs :text-size])))
    (is (= :large (get-in (storage/saved-form s) [:prefs :text-size]))))
  (testing "erasing takes two clicks and keeps the page"
    (let [s {:route {:page :settings} :docs {"d1" {}} :overrides {} :prefs {:theme :dark} :ui {}}
          armed (actions/handle s nil [:settings/erase])
          erased (actions/handle armed nil [:settings/erase])]
      (is (= {"d1" {}} (:docs armed)))
      (is (empty? (:docs erased)))
      (is (= state/default-prefs (:prefs erased)))
      (is (= {:page :settings} (:route erased))))))

(deftest display-changes-are-per-text
  (let [on (fn [s slug] (assoc s :route {:page :text :slug slug}))
        s {:route {:page :text :slug "latin"} :text-views {}
           :view (assoc state/default-view :display-open? false
                        :row-orders {"latin" ["glmorph" "text"] "hebrew" ["translit" "text"]})}
        e (fn [v] #js {:target #js {:value v}})
        s1 (-> s
               (actions/handle (e "full") [:view/select :pos])
               (actions/handle nil [:view/toggle :wgloss]))]
    (testing "a change on a page is that text's own"
      (is (= {"latin" {:pos :full :wgloss false}} (:text-views s1)))
      (is (= :full (:pos (state/page-view s1))))
      (is (= :none (:pos (state/page-view (on s1 "hebrew")))))
      (is (= state/default-view (assoc (:view s1) :display-open? true :row-orders {}))))
    (testing "setting an option back to the default drops it"
      (is (= {"latin" {:pos :full}} (:text-views (actions/handle s1 nil [:view/toggle :wgloss])))))
    (testing "opening and closing the box stays global"
      (is (true? (get-in (actions/handle s1 nil [:view/toggle :display-open?]) [:view :display-open?]))))
    (testing "reset returns this text to the defaults and its own row order"
      (let [r (actions/handle s1 nil [:view/reset-page])]
        (is (not (state/page-view-changed? r)))
        (is (empty? (:text-views r)))
        (is (= {"hebrew" ["translit" "text"]} (get-in r [:view :row-orders])))
        (is (state/page-view-changed? (on r "hebrew")))))
    (testing "the Settings reset clears every text's changes"
      (is (empty? (:text-views (actions/handle s1 nil [:settings/reset-view])))))
    (testing "saved per-text changes are kept, invalid ones dropped"
      (is (= {"latin" {:pos :full :wgloss false}}
             (:text-views (reader/read-string (pr-str (storage/saved-form s1))))
             (state/clean-text-views {"latin" {:pos :full :wgloss false :bogus 1 :free "x"}
                                    "x" {:display-open? true}}))))))

(deftest default-display-options
  (testing "the IPA transcription follows the phonemic and phonetic defaults"
    (let [s {:route {:page :settings} :view (assoc state/default-view :phonemic false :phonetic false)}]
      (is (false? (:ipa (state/default-display s))))
      (is (true? (:ipa (state/default-display (assoc-in s [:view :phonetic] true)))))))
  (testing "a text's IPA switch is its own, compared with the derived default"
    (let [s {:route {:page :text :slug "x"} :text-views {} :view state/default-view}
          off (actions/handle s nil [:view/toggle :ipa])]
      (is (= {"x" {:ipa false}} (:text-views off)))
      (is (empty? (:text-views (actions/handle off nil [:view/toggle :ipa]))))))
  (testing "changes on the Settings page are the defaults"
    (let [s {:route {:page :settings} :text-views {} :view state/default-view}
          s2 (actions/handle s nil [:view/toggle :wgloss])]
      (is (false? (get-in s2 [:view :wgloss])))
      (is (empty? (:text-views s2)))
      (is (= state/default-view (:view (actions/handle s2 nil [:defaults/reset])))))))

(deftest editing-a-transliteration-keeps-its-morphemes
  (with-redefs [state/library fx/library]
  (let [s {:route {:page :text :slug "hebrew"} :overrides {} :docs {}}
        published (state/current-doc s)
        target {:igt (get-in published [:igts 0 :id]) :row "translit" :word 3}
        started (actions/handle s nil [:edit/start target])
        s2 (actions/handle started nil [:edit/commit])]
    (is (= "beveto" (get-in started [:ui :draft])))
    (is (= (model/segs (get-in published [:igts 0 :words 3]) "translit")
           (model/segs (get-in (state/current-doc s2) [:igts 0 :words 3]) "translit"))))))

(deftest exporting-everything
  (with-redefs [state/library fx/library]
    (let [own (:doc (parse/parse-document "\\title Mine\n\\ex\n\\text a"))
          edited (:doc (parse/parse-document fx/latin))
          s {:docs {"d1" own} :order ["d1"] :overrides {"latin" edited}}
          text (storage/export-text s "2026-10-19")
          parts (storage/read-export text)]
      (testing "one file: a header, then each text after its line"
        (is (re-find #"^% GlossHub export, 2026-10-19: 2 texts\." text))
        (is (= [["own" "d1"] ["edited" "latin"]] (map (juxt :kind :key) parts)))
        (is (= "Mine" (:title (:doc (parse/parse-document (:source (first parts))))))))
      (testing "importing it into an empty browser brings everything back"
        (let [s2 (actions/import-export {:docs {} :order [] :overrides {}} parts)]
          (is (= ["d1"] (:order s2)))
          (is (= "Mine" (get-in s2 [:docs "d1" :title])))
          (is (contains? (:overrides s2) "latin"))
          (is (= 2 (get-in s2 [:status :imported])))))
      (testing "a text already there under its id isn't added twice; a different one is kept beside it"
        (is (= ["d1"] (:order (actions/import-export s parts))))
        (let [changed {:docs {"d1" (:doc (parse/parse-document "\\title Other\n\\ex\n\\text b"))} :order ["d1"] :overrides {}}]
          (is (= 2 (count (:order (actions/import-export changed parts))))))))
    (testing "a single text is not an export"
      (is (nil? (storage/read-export "\\title A\n\\ex\n\\text a"))))))

(deftest a-published-text-changing-under-an-edit
  (let [s (with-redefs [state/library fx/library]
            (-> {:route {:page :text :slug "latin"} :overrides {} :docs {}}
                (state/update-doc edit/set-free (get-in @fx/library [0 :doc :igts 0 :id]) "Changed.")))
        changed (delay (update-in @fx/library [0 :source] str "\n\\ex\n\\text novum"))]
    (testing "the first edit remembers the published version it started from"
      (with-redefs [state/library fx/library]
        (is (number? (get-in s [:override-bases "latin"])))
        (is (false? (state/published-changed? s "latin")))))
    (testing "when the published text changes, the reader is told, and can keep their version"
      (with-redefs [state/library changed]
        (is (true? (state/published-changed? s "latin")))
        (is (false? (state/published-changed? (actions/handle s nil [:override/keep "latin"]) "latin")))))
    (testing "it is saved with the texts"
      (is (= (:override-bases s) (:override-bases (storage/saved-form s)))))))
