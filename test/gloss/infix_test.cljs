(ns gloss.infix-test
  "Infixes, and morphemes in several pieces."
  (:require [cljs.test :refer [deftest is testing]]
            [gloss.check :as check]
            [gloss.layout :as layout]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

(defn- doc [src] (:doc (parse/parse-document src)))
(defn- word [src] (get-in (doc src) [:igts 0 :words 0]))
(defn- problems [src] (map :msg (check/problems (parse/parse-document src))))
(defn- entry [s] (morph/parse-morph-entry s))

(deftest reading-infixes
  (testing "an infix splits its host in two segments"
    (is (= {:segs [{:text "s"} {:text "um" :infix? true} {:text "ulat" :cont? true}] :problems []}
           (entry "s<um>ulat")))
    (is (= [[:plain [0 2]] [:infix [1]]] (morph/morphemes (:segs (entry "s<um>ulat")))))
    (is (= [[:plain [0 2]] [:infix [1]] [:plain [3]]] (morph/morphemes (:segs (entry "s<um>ulat-in"))))))
  (testing "a root continues across an infix, without it"
    (is (= [[[0 1]] nil [[0 4]]] (map :root (:segs (entry "(s<um>ulat)")))))
    (is (= (map :root (:segs (entry "(s<um>ulat)"))) (map :root (:segs (entry "(s)<um>(ulat)"))))))
  (testing "problems"
    (is (= ["An infix goes inside a morpheme: letters of the morpheme must come before it, as in s<um>ulat."]
           (:problems (entry "<um>sulat"))))
    (is (= ["An infix goes inside a morpheme: letters of the morpheme must follow it, as in s<um>ulat."]
           (:problems (entry "sulat<um>"))))
    (is (= ["An infix < is not closed with >."] (:problems (entry "s<umulat"))))
    (is (= ["A > closes no infix."] (:problems (entry "sum>ulat"))))
    (is (= ["An infix <…> is one morpheme; it can't contain separators."] (:problems (entry "s<u-m>ulat"))))))

(deftest glosses
  (testing "an infix's gloss is in angle brackets, wherever it stands"
    (is (= [{:text "AV" :infix? true} {:text "write"}] (morph/split-gloss "<AV>write")))
    (is (= [{:text "write"} {:text "AV" :infix? true} {:text "PV" :sep "-"}] (morph/split-gloss "write<AV>-PV")))
    (is (= [{:text "1>3"}] (morph/split-gloss "1>3")))
    (is (= [{:text "RED"} {:text "walk" :sep "~"}] (morph/split-gloss "RED~walk")))
    (is (= "<AV>write" (morph/join-segments (morph/split-gloss "<AV>write")))))
  (testing "infix glosses pair with infixes, the others in order"
    (let [segs (:segs (entry "s<um>ulat-in"))]
      (is (= [[1] [0 2] [3]] (morph/gloss-segments segs (morph/split-gloss "<AV>write-PV"))))
      (is (= [[0 2] [1] [3]] (morph/gloss-segments segs (morph/split-gloss "write<AV>-PV"))))
      (is (= [1 0 1 2] (morph/segment-glosses segs (morph/split-gloss "<AV>write-PV"))))))
  (let [src "\\abbr AV actor voice\n\\ex\n\\text sumulat\n\\morph (s<um>ulat)\n\\glmorph "]
    (is (empty? (problems (str src "<AV>write"))))
    (is (empty? (problems (str src "write<AV>"))))
    (is (= 2 (model/morph-count (word (str src "<AV>write")))))
    (is (some #(re-find #"Separator mismatch.*angle brackets" %) (problems (str src "AV-write"))))
    (is (some #(re-find #"has 2 morphemes but its gloss" %) (problems (str src "write"))))))

(deftest both-rows
  (is (empty? (problems "\\ex\n\\text sumulat\n\\morph s<um>ulat\n\\translit sumulat\n\\morph[translit] s<um>ulat")))
  (is (some #(re-find #"must have the same morphemes" %)
            (problems "\\ex\n\\text sumulat\n\\morph s<um>ulat\n\\translit sumulat\n\\morph[translit] s-um-ulat"))))

(deftest display
  (let [d (doc "\\ex\n\\text sumulat\n\\morph (s<um>ulat)\n\\glmorph <AV>write")
        igt (first (:igts d))
        pieces (mapcat :pieces (get-in (first (mapcat :words (layout/igt-layout igt nil))) [:syls "text"]))]
    (testing "with breaks shown, the infix is in angle brackets"
      (is (= [[nil "s"] ["<" "um"] [">" "ulat"]] (map (juxt :sep :text) pieces))))
    (testing "the host's pieces are root, the infix's aren't"
      (is (= [true false true] (map :root? pieces)))))
  (is (= "\\morph (s<um>ulat)\n\\glmorph <AV>write"
         (re-find #"\\morph.*\n\\glmorph.*" (ser/document-source (doc "\\ex\n\\text sumulat\n\\morph (s<um>ulat)\n\\glmorph <AV>write"))))))
