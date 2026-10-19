(ns gloss.rows-test
  "The table of rows (gloss.rows), the two surfaces, and formatting carried
  from the text as the table says."
  (:require [cljs.test :refer [deftest is testing]]
            [gloss.model :as model]
            [gloss.parse :as parse]
            [gloss.rows :as rows]))

(defn- word [src] (get-in (parse/parse-document src) [:doc :lines 0 :words 0]))

(deftest names
  (is (= "glmorph" (rows/row-name "GL")))
  (is (= "translit" (rows/row-name "translit")))
  (is (= "ex" (rows/row-name "ex")) "other markers are kept")
  (is (= ["text" "translit" "ipa" "phonemic" "phonetic" "glmorph" "glword" "pos"] rows/layers))
  (is (= "Morphemic gloss" (rows/layer-label "glmorph"))))

(deftest characters
  (testing "reserved: ASCII punctuation a row reads neither as itself nor as markup"
    (is (rows/reserved? "pos" "-"))
    (is (not (rows/reserved? "pos" ".")))
    (is (rows/reserved? "glmorph" ","))
    (is (not (rows/reserved? "glmorph" "-")))
    (is (not (rows/reserved? "glword" "=")))
    (is (not (rows/reserved? "pos" "\\")) "a backslash is never reserved"))
  (testing "written out: markup is escaped unless the value keeps it"
    (is (contains? (rows/escapes "glmorph") "-"))
    (is (not (contains? (rows/escapes "pos") ".")))
    (is (contains? (rows/escapes "pos") "-"))
    (is (= #{"\\" "{" "}"} (rows/escapes "phonemic")))))

(deftest surfaces
  (let [w (word "\\ex\n\\text sumulat\n\\morph s<um>ulat\n\\translit sumulat\n\\morph[translit] s<um>ulat")]
    (is (= ["s" "um" "ulat"] (map :text (model/segs w "text"))))
    (is (= ["s" "um" "ulat"] (map :text (model/segs w "translit"))))
    (is (= "s<um>ulat" (model/morph-src w "translit"))))
  (is (= "syl[translit]" (model/analysis-row "syl" "translit")))
  (is (= "morph" (model/analysis-row "morph" "text"))))

(deftest formatting-from-text
  (let [w (word "\\ex\n\\text **Mūs**a\n\\morph Mūs-a")]
    (is (= #{:bold} (model/styles-from-text "glmorph" w [0])) "a morpheme the formatting covers")
    (is (= #{} (model/styles-from-text "glmorph" w [1])))
    (is (= #{} (model/styles-from-text "pos" w [0])) "\\pos takes none"))
  (let [w (word "\\ex\n\\text **Mūsa**")]
    (is (= #{:bold} (model/styles-from-text "glword" w)))
    (is (= #{:bold} (model/styles-from-text "translit" w)))
    (is (= #{} (model/styles-from-text "phonemic" w)))))
