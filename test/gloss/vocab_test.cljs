(ns gloss.vocab-test
  (:require [cljs.test :refer [deftest is]]
            [gloss.vocab :as vocab]))

(deftest grammatical
  (is (vocab/grammatical? "ACC"))
  (is (vocab/grammatical? "1SG"))
  (is (vocab/grammatical? "3"))
  (is (not (vocab/grammatical? "muse")))
  (is (not (vocab/grammatical? "Muse")))
  ;; The prefix non- written as a lowercase n.
  (is (vocab/grammatical? "nSG"))
  (is (vocab/grammatical? "3nSG"))
  (is (not (vocab/grammatical? "n")))
  (is (not (vocab/grammatical? "nominal"))))

(deftest elements
  (is (= ["ABL" "N" "SG"] (vocab/gloss-elements "ABL.N.SG")))
  (is (= ["what" "ACC" "N" "SG"] (vocab/gloss-elements "what.ACC.N.SG")))
  (is (= ["so.many"] (vocab/gloss-elements "so.many")))
  (is (= ["so many"] (vocab/gloss-elements "so many")))
  (is (= [] (vocab/gloss-elements "")))
  ;; Labels behind or inside the other Leipzig symbols are elements too.
  (is (= ["write" "PST" "3SG"] (vocab/gloss-elements "write\\PST.3SG")))
  (is (= ["boy" "NOM" "SG"] (vocab/gloss-elements "boy[NOM.SG]")))
  (is (= ["eye" "N"] (vocab/gloss-elements "eye(N)")))
  (is (= ["1" "3"] (vocab/gloss-elements "1>3")))
  (is (= ["sing" "PST"] (vocab/gloss-elements "sing:PST"))))

(deftest pieces
  (is (= ["boy" "[" "NOM" "." "SG" "]"] (vocab/gloss-pieces "boy[NOM.SG]")))
  (is (= ["father" "\\" "PL"] (vocab/gloss-pieces "father\\PL")))
  (is (= ["so many"] (vocab/gloss-pieces "so many")))
  (is (= [] (vocab/gloss-pieces ""))))

(deftest lookup
  (is (= "accusative" (vocab/lookup vocab/defaults "ACC")))
  (is (= "first person singular" (vocab/lookup vocab/defaults "1SG")))
  (is (= "first person/third person plural" (vocab/lookup vocab/defaults "13PL")))
  (is (nil? (vocab/lookup vocab/defaults "XYZ")))
  (is (= "custom" (vocab/lookup (merge vocab/defaults {"XYZ" "custom"}) "XYZ"))))

(deftest the-prefix-non
  (let [name #(vocab/lookup vocab/defaults %)]
    (is (= ["non-singular" "non-singular" "non-singular"] (map name ["NSG" "NONSG" "nSG"])))
    (is (= "non-past" (name "NPST")))
    (is (= "non-neuter" (name "NN")))
    (is (= "third person non-singular" (name "3NSG")))
    (is (= "third person non-singular" (name "3nSG")))
    (is (= "non-third person singular" (name "N3SG")))
    (is (= "non-custom" (vocab/lookup (merge vocab/defaults {"XYZ" "custom"}) "NXYZ")))
    (is (nil? (name "NNSG")) "read once")
    (is (nil? (name "NONE")))
    (is (nil? (name "nsg")))
    (doseq [[tag own] [["NEG" "negation, negative"] ["NOM" "nominative"] ["NMLZ" "nominalizer/nominalization"] ["N" "neuter"]]]
      (is (= own (name tag)) "the vocabulary's own abbreviations come first"))
    (is (= "nonsingular" (vocab/lookup (merge vocab/defaults {"NSG" "nonsingular"}) "NSG")) "a text's own too")))

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
