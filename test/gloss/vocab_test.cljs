(ns gloss.vocab-test
  (:require [cljs.test :refer [deftest is]]
            [gloss.vocab :as vocab]))

(deftest grammatical
  (is (vocab/grammatical? "ACC"))
  (is (vocab/grammatical? "1SG"))
  (is (vocab/grammatical? "3"))
  (is (not (vocab/grammatical? "muse")))
  (is (not (vocab/grammatical? "Muse"))))

(deftest elements
  (is (= ["ABL" "N" "SG"] (vocab/gloss-elements "ABL.N.SG")))
  (is (= ["what" "ACC" "N" "SG"] (vocab/gloss-elements "what.ACC.N.SG")))
  (is (= ["so.many"] (vocab/gloss-elements "so.many")))
  (is (= ["so many"] (vocab/gloss-elements "so many")))
  (is (= [] (vocab/gloss-elements ""))))

(deftest lookup
  (is (= "accusative" (vocab/lookup vocab/defaults "ACC")))
  (is (= "first person singular" (vocab/lookup vocab/defaults "1SG")))
  (is (= "first person/third person plural" (vocab/lookup vocab/defaults "13PL")))
  (is (nil? (vocab/lookup vocab/defaults "XYZ")))
  (is (= "custom" (vocab/lookup (merge vocab/defaults {"XYZ" "custom"}) "XYZ"))))

(deftest parts-of-speech
  (doseq [s ["N" "Noun" "noun" "NOUN"]]
    (is (= "N" (get-in (vocab/parse-pos s) [:class :entry :abbr]))))
  (let [p (vocab/parse-pos "verb.transitive")]
    (is (= "V" (get-in p [:class :entry :abbr])))
    (is (= ["trans"] (map (comp :abbr :entry) (:subs p)))))
  (is (= ["Det" "quant"] (let [p (vocab/parse-pos "determiner.quantifier")]
                          (cons (get-in p [:class :entry :abbr]) (map (comp :abbr :entry) (:subs p))))))
  (is (nil? (get-in (vocab/parse-pos "Foo") [:class :entry])))
  (is (= ["Pro" "Conj"] (map #(get-in % [:class :entry :abbr]) (vocab/pos-parts "Pro=conj")))))
