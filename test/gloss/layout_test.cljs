(ns gloss.layout-test
  (:require [cljs.test :refer [deftest is testing]]
            [gloss.fixtures :as fx]
            [gloss.layout :as layout]
            [gloss.model :as model]
            [gloss.notes :as notes]
            [gloss.parse :as parse]
            [gloss.render :as render]))

(def doc (:doc (parse/parse-document fx/latin)))

(defn- syl-texts [wl]
  (mapv (fn [s] (mapv :text (:pieces s))) (get-in wl [:syls "text"])))

(deftest morphemes-and-syllables-interleave
  (let [units (layout/line-layout (get-in doc [:lines 0]) (get-in (notes/note-index doc) [:marks (get-in doc [:lines 0 :id])]))
        words (mapcat :words units)
        nauta (nth words 0)]
    (is (= 7 (count units)))
    ;; Naut-a against Nau-ta
    (is (= [["Nau"] ["t" "a"]] (syl-texts nauta)))
    (is (= [nil "-"] (map :sep (:pieces (second (get-in nauta [:syls "text"]))))))
    (is (= [:long :short] (map :mark (get-in nauta [:syls "text"]))))
    (is (= [[] [] [] [] [] [] [{:key "coord" :n 1}]] (map :note-marks words)))))

(deftest elision-joins-words
  (let [line (get-in doc [:lines 2])
        units (layout/line-layout line (get-in (notes/note-index doc) [:marks (:id line)]))
        joined (first (filter #(> (count (:words %)) 1) units))
        [ille ubi] (:words joined)
        pueris (first (:words (nth units 3)))]
    (is (= 7 (count units)))
    (is (= [["Il"] ["l" "e"]] (syl-texts ille)))
    (is (:elided? (second (:pieces (last (get-in ille [:syls "text"]))))))
    (is (:cont? (first (get-in ubi [:syls "text"]))))
    (is (nil? (:mark (first (get-in ubi [:syls "text"])))))
    (is (:caesura? (first (get-in pueris [:syls "text"]))))))

(deftest no-prosody
  (let [line (first (:lines (:doc (parse/parse-document "\\tx puerōs\n\\morph puer-ōs"))))
        [unit] (layout/line-layout line {})]
    (is (= [["puer" "ōs"]] (syl-texts (first (:words unit)))))))

(deftest joints-zeros-and-roots
  (let [line (first (:lines (:doc (parse/parse-document "\\tx well-known puer\n\\morph well-know-n [(puer)]-\\0"))))
        [w1 w2] (mapcat :words (layout/line-layout line {}))
        pieces #(mapcat :pieces (get-in % [:syls "text"]))]
    (testing "a hyphen of the text at a morpheme boundary is a joint, not part of a morpheme"
      (is (= [["well" false] ["-" true] ["know" false] ["n" false]] (map (juxt :text :joint?) (pieces w1))))
      (is (= [nil nil "-" "-"] (map :sep (pieces w1)))))
    (testing "roots are marked letter by letter; a zero morpheme is a piece of its own"
      (is (= [["puer" true] ["Ø" false]] (map (juxt :text (comp boolean :root?)) (pieces w2))))
      (is (:zero? (last (pieces w2))))
      (is (= "-" (:sep (last (pieces w2))))))))

(deftest row-switches
  (let [v {:orthography-row true :translit true :ruby true
           :scansion-row false :marks true :syllables true
           :glossing-row true :morph-breaks true :gloss :tags :wgloss true :pos :class
           :marking-row true :stems :underline :color false
           :commentary-row false :free :side :notes :below}
        e (render/effective-view v)]
    (is (= [false false] [(:scansion e) (:syllables e)]))
    (is (= [:tags :class true :underline] [(:gloss e) (:pos e) (:wgloss e) (:stems e)]))
    (is (= [false false] [(:free e) (:notes e)]))
    (let [e (render/effective-view (assoc v :commentary-row true))]
      (is (= [true true true false] [(:free e) (:free-side? e) (:notes e) (:notes-side? e)])))
    (testing "each row's switch hides its own settings only"
      (is (= [:none :none false false true] (let [e (render/effective-view (assoc v :glossing-row false))]
                                              [(:gloss e) (:pos e) (:wgloss e) (:morph-breaks e) (:translit e)])))
      (is (= [false false :tags] (let [e (render/effective-view (assoc v :orthography-row false))]
                                   [(:translit e) (:ruby e) (:gloss e)])))
      (is (= [:none false true] (let [e (render/effective-view (assoc v :marking-row false :color true))]
                                  [(:stems e) (:color e) (:wgloss e)]))))))

(deftest subline-groups
  (let [line (first (:lines (:doc (parse/parse-document fx/hebrew))))
        units (layout/line-layout line {})]
    (is (= [["a" [0 1 2]] ["b" [3]]]
           (for [[label group] (render/sublines line units)]
             [label (mapv #(:index (first (:words %))) group)])))
    (is (= [[nil 7]] (for [[label group] (render/sublines (get-in doc [:lines 0]) (layout/line-layout (get-in doc [:lines 0]) {}))]
                       [label (count group)])))
    (is (= #{"translit" "glword"} (model/line-rows line)))))

(deftest ruby-pieces
  (let [line (first (:lines (:doc (parse/parse-document fx/japanese))))
        [w0 _ w2] (mapcat :words (layout/line-layout line {}))
        pieces (fn [wl] (mapv (juxt :text #(get-in % [:ruby :text])) (mapcat :pieces (get-in wl [:syls "text"]))))]
    (is (= [["青" "あお"] ["空" "ぞら"]] (pieces w0)))
    (is (= [["白" "しろ"] ["い" nil]] (pieces w2)))))
