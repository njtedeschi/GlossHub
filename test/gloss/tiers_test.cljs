(ns gloss.tiers-test
  "The table of tiers (gloss.tiers), the two surfaces, and formatting carried
  from the text as the table says."
  (:require [cljs.test :refer [deftest is testing]]
            [gloss.model :as model]
            [gloss.parse :as parse]
            [gloss.tiers :as tiers]))

(defn- word [src] (get-in (parse/parse-document src) [:doc :igts 0 :words 0]))

(deftest names
  (is (= "glmorph" (tiers/standard-name "GL")))
  (is (= "translit" (tiers/standard-name "translit")))
  (is (= "ex" (tiers/standard-name "ex")) "other commands are kept")
  (is (= ["text" "translit" "ipa" "phonemic" "phonetic" "glmorph" "glword" "pos"] tiers/rows))
  (is (= "Morphemic gloss" (tiers/row-label "glmorph")))
  (testing "each tier is of one kind"
    (is (= {:text 5 :segmentation 2 :annotation 4 :commentary 2} (frequencies (map :kind tiers/tiers))))
    (is (= ["morph" "syl"] (map :name (filter #(= :segmentation (:kind %)) tiers/tiers)))))
  (testing "the text tiers other than \\text are written \\text with an option"
    (is (= #{"translit" "ipa" "phonemic" "phonetic"} tiers/text-options))
    (is (= "text[translit]" (tiers/command "translit")))
    (is (= "text[ipa]" (tiers/command "ipa")))
    (is (= "morph[translit]" (tiers/command "morph[translit]")))
    (is (= "glmorph" (tiers/command "glmorph"))))
  (testing "options are read the same in a field and in \\order"
    (is (= "translit" (tiers/option "TL")))
    (is (= "phonemic" (tiers/option "Phonemic")))
    (is (nil? (tiers/option "Text")) "[text] is the text itself: no option")
    (is (nil? (tiers/option nil)))
    (is (= "xyz" (tiers/option "XYZ")) "an option naming no text tier is kept")
    (is (= ["translit" "translit" "glmorph"] (map tiers/row-key ["text[translit]" "TEXT[TL]" "gl"])))
    (is (= ["text[text]" "text[xyz]"] (map tiers/row-key ["text[text]" "text[xyz]"])) "names of no row are kept")))

(deftest characters
  (testing "reserved: ASCII punctuation a tier reads neither as itself nor as markup"
    (is (tiers/reserved? "pos" "-"))
    (is (not (tiers/reserved? "pos" ".")))
    (is (tiers/reserved? "glmorph" ","))
    (is (not (tiers/reserved? "glmorph" "-")))
    (is (not (tiers/reserved? "glword" "=")))
    (is (not (tiers/reserved? "pos" "\\")) "a backslash is never reserved"))
  (testing "written out: markup is escaped unless the value keeps it"
    (is (contains? (tiers/escapes "glmorph") "-"))
    (is (not (contains? (tiers/escapes "pos") ".")))
    (is (contains? (tiers/escapes "pos") "-"))
    (is (= #{"\\" "{" "}"} (tiers/escapes "phonemic")))))

(deftest surfaces
  (let [w (word "\\ex\n\\text sumulat\n\\morph s<um>ulat\n\\translit sumulat\n\\morph[translit] s<um>ulat")]
    (is (= ["s" "um" "ulat"] (map :text (model/segs w "text"))))
    (is (= ["s" "um" "ulat"] (map :text (model/segs w "translit"))))
    (is (= "s<um>ulat" (model/morph-src w "translit"))))
  (is (= "syl[translit]" (model/segmentation-tier "syl" "translit")))
  (is (= "morph" (model/segmentation-tier "morph" "text"))))

(deftest formatting-from-text
  (let [w (word "\\ex\n\\text **Mūs**a\n\\morph Mūs-a")]
    (is (= #{:bold} (model/styles-from-text "glmorph" w [0])) "a morpheme the formatting covers")
    (is (= #{} (model/styles-from-text "glmorph" w [1])))
    (is (= #{} (model/styles-from-text "pos" w [0])) "\\pos takes none"))
  (let [w (word "\\ex\n\\text **Mūsa**")]
    (is (= #{:bold} (model/styles-from-text "glword" w)))
    (is (= #{:bold} (model/styles-from-text "translit" w)))
    (is (= #{} (model/styles-from-text "phonemic" w)))))
