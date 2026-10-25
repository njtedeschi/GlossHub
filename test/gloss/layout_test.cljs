(ns gloss.layout-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.walk]
            [gloss.fixtures :as fx]
            [gloss.layout :as layout]
            [gloss.model :as model]
            [gloss.notes :as notes]
            [gloss.app.state :as state]
            [gloss.parse :as parse]
            [gloss.render :as render]))

(def doc (:doc (parse/parse-document fx/latin)))

(defn- syl-texts [wl]
  (mapv (fn [s] (mapv :text (:pieces s))) (get-in wl [:syls "text"])))

(deftest morphemes-and-syllables-interleave
  (let [units (layout/igt-layout (get-in doc [:igts 0]) (get-in (notes/note-index doc) [:marks (get-in doc [:igts 0 :id])]))
        words (mapcat :words units)
        nauta (nth words 0)]
    (is (= 7 (count units)))
    ;; Naut-a against Nau-ta
    (is (= [["Nau"] ["t" "a"]] (syl-texts nauta)))
    (is (= [nil "-"] (map :sep (:pieces (second (get-in nauta [:syls "text"]))))))
    (is (= [:long :short] (map :mark (get-in nauta [:syls "text"]))))
    (is (= [[] [] [] [] [] [] [{:key "coord" :n 1}]] (map :note-marks words)))))

(defn- texts
  "The text in hiccup `h`, in order."
  [h]
  (cond
    (string? h) [h]
    (vector? h) (mapcat texts (if (map? (second h)) (drop 2 h) (rest h)))
    (seq? h) (mapcat texts h)
    :else []))

(defn- row-cells
  "The text of each cell of row `row` in hiccup `h`."
  [h row]
  (cond
    (and (vector? h) (= :span.row (first h)) (some #{row} (flatten [(:class (second h))])))
    [(apply str (texts h))]
    (vector? h) (mapcat #(row-cells % row) (rest h))
    (seq? h) (mapcat #(row-cells % row) h)
    :else []))

(deftest grammatical-only-keeps-the-morphemes-apart
  (let [doc (:doc (parse/parse-document (str "\\abbr ACTFOC actor focus\n\\igt\n\\text poexali bumibili zz cause\n"
                                             "  \\morph poexa-l-i b<um>i~bili zz cause\n"
                                             "\\glmorph go-PST-PL <ACTFOC>IPFV~buy 1SG.DAT cause-ACC.PL")))
        ctx (assoc (render/context doc (assoc state/default-view :gloss :grammatical) {}) :read? true)]
    (is (= ["pst-pl" "<actfoc>ipfv" "1sg.dat" "acc.pl"]
           (row-cells (render/igt-body ctx (first (:igts doc)) {}) "glmorph")))))

(deftest a-lowercase-n-stays-lowercase
  (let [doc (:doc (parse/parse-document "\\igt\n\\text ab\n  \\morph a-b\n\\glmorph 3nSG-be.NPST"))
        h (render/igt-body (assoc (render/context doc state/default-view {}) :read? true) (first (:igts doc)) {})
        lower (atom [])]
    (clojure.walk/postwalk #(do (when (and (vector? %) (= :span.lower (first %))) (swap! lower conj %)) %) h)
    (is (= [[:span.lower "n"]] @lower))
    (is (= ["3nsg-be.npst"] (row-cells h "glmorph")))))

(deftest elision-joins-words
  (let [igt (get-in doc [:igts 2])
        units (layout/igt-layout igt (get-in (notes/note-index doc) [:marks (:id igt)]))
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
  (let [igt (first (:igts (:doc (parse/parse-document "\\tx puerōs\n\\morph puer-ōs"))))
        [unit] (layout/igt-layout igt {})]
    (is (= [["puer" "ōs"]] (syl-texts (first (:words unit)))))))

(deftest joints-zeros-and-roots
  (let [igt (first (:igts (:doc (parse/parse-document "\\tx well-known puer\n\\morph well-know-n [(puer)]-\\0"))))
        [w1 w2] (mapcat :words (layout/igt-layout igt {}))
        pieces #(mapcat :pieces (get-in % [:syls "text"]))]
    (testing "a hyphen of the text at a morpheme boundary is a joint, not part of a morpheme"
      (is (= [["well" false] ["-" true] ["know" false] ["n" false]] (map (juxt :text :joint?) (pieces w1))))
      (is (= [nil nil "-" "-"] (map :sep (pieces w1)))))
    (testing "roots are marked letter by letter; a zero morpheme is a piece of its own"
      (is (= [["puer" true] ["Ø" false]] (map (juxt :text (comp boolean :root?)) (pieces w2))))
      (is (:zero? (last (pieces w2))))
      (is (= "-" (:sep (last (pieces w2))))))))

(deftest group-switches
  (let [v {:orthography-group true :translit true :ruby true
           :scansion-group false :marks true :syllables true
           :glossing-group true :morph-breaks true :gloss :grammatical :wgloss true :pos :class
           :marking-group true :stems :underline :color false
           :commentary-group false :free :side :notes :below}
        e (render/effective-view v)]
    (is (= [false false] [(:scansion e) (:syllables e)]))
    (is (= [:grammatical :class true :underline] [(:gloss e) (:pos e) (:wgloss e) (:stems e)]))
    (is (= [false false] [(:free e) (:notes e)]))
    (let [e (render/effective-view (assoc v :commentary-group true))]
      (is (= [true true true false] [(:free e) (:free-side? e) (:notes e) (:notes-side? e)])))
    (testing "each group's switch hides its own settings only"
      (is (= [:none :none false false true] (let [e (render/effective-view (assoc v :glossing-group false))]
                                              [(:gloss e) (:pos e) (:wgloss e) (:morph-breaks e) (:translit e)])))
      (is (= [false false :grammatical] (let [e (render/effective-view (assoc v :orthography-group false))]
                                          [(:translit e) (:ruby e) (:gloss e)])))
      (is (= [:none false true] (let [e (render/effective-view (assoc v :marking-group false :color true))]
                                  [(:stems e) (:color e) (:wgloss e)]))))))

(deftest subline-groups
  (let [igt (first (:igts (:doc (parse/parse-document fx/hebrew))))
        units (layout/igt-layout igt {})]
    (is (= [["a" [0 1 2]] ["b" [3]]]
           (for [[label group] (render/sublines igt units)]
             [label (mapv #(:index (first (:words %))) group)])))
    (is (= [[nil 7]] (for [[label group] (render/sublines (get-in doc [:igts 0]) (layout/igt-layout (get-in doc [:igts 0]) {}))]
                       [label (count group)])))
    (is (= #{"translit" "glword"} (model/igt-rows igt)))))

(deftest ruby-pieces
  (let [igt (first (:igts (:doc (parse/parse-document fx/japanese))))
        [w0 _ w2] (mapcat :words (layout/igt-layout igt {}))
        pieces (fn [wl] (mapv (juxt :text #(get-in % [:ruby :text])) (mapcat :pieces (get-in wl [:syls "text"]))))]
    (is (= [["青" "あお"] ["空" "ぞら"]] (pieces w0)))
    (is (= [["白" "しろ"] ["い" nil]] (pieces w2)))))
