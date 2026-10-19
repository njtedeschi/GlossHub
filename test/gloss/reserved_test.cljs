(ns gloss.reserved-test
  "Each row's reserved characters, and backslashes that
  escape nothing."
  (:require [cljs.test :refer [deftest is testing]]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

(defn- msgs [src] (map :msg (:diagnostics (parse/parse-document src))))
(defn- line [rows] (str "\\ex\n\\text a b\n" rows))
(defn- round-trips? [src]
  (let [d (:doc (parse/parse-document src))
        strip (fn [d] (update d :lines (fn [ls] (mapv #(dissoc % :id) ls))))]
    (= (strip d) (strip (:doc (parse/parse-document (ser/document-source d)))))))

(deftest by-row
  (testing "\\translit reserves what \\text does, formatting characters included"
    (is (= ["“*” (in “x*y”) is reserved in \\translit; write \\* for a literal “*”."]
           (msgs (line "\\translit x*y b"))))
    (is (empty? (msgs (line "\\translit don't, (x)/y?")))))
  (testing "\\glword is as free as \\ft, within its cell"
    (is (empty? (msgs (line "\\glword **the** boy"))))
    (is (empty? (msgs (line "\\glword the=boy {50% #1 & <x>|y}")))))
  (testing "\\glmorph: its markup and its punctuation"
    (is (empty? (msgs (line "\\glmorph boy-NOM.PL x:y;z(w)[v]"))))
    (is (= ["“#” (in “a#b”) is reserved in \\glmorph; write \\# for a literal “#”."
            "“,” (in “1SG,PL”) is reserved in \\glmorph; write \\, for a literal “,”."]
           (msgs (line "\\glmorph a#b 1SG,PL")))))
  (testing "\\pos: letters, . (subclass) and = (clitic)"
    (is (empty? (msgs (line "\\pos N.prop V.trans"))))
    (is (= ["“-” (in “V-trans”) is reserved in \\pos: subclasses follow a period (V.trans)."]
           (msgs (line "\\pos N V-trans"))))
    (is (= ["“#” (in “N#”) is reserved in \\pos; write \\# for a literal “#”."]
           (msgs (line "\\pos N# V")))))
  (testing "\\syl skips a reserved character and reports it"
    (is (= ["“#” is reserved in \\syl; write \\# for a literal “#”."]
           (msgs "\\ex\n\\syl a#\n\\text a"))))
  (testing "\\scan: only its marks"
    (is (some #(re-find #"“y” is reserved in \\scan" %) (msgs "\\ex\n\\scan _ y\n\\syl a-b\n\\text ab"))))
  (testing "transcriptions and prose reserve nothing"
    (is (empty? (msgs (line "\\phonemic #a$ b|\n\\ft 50% *of* #1 = x\n\\note {a} 2 * 3 & y"))))))

(deftest backslashes
  (testing "a backslash before a letter is a command; only \\0 exists, in \\morph"
    (is (= ["Unknown command \\PL in \\glmorph (in “boy\\PL”); write \\\\ for a backslash."]
           (msgs (line "\\glmorph boy\\PL x"))))
    (is (= ["Unknown command \\x in \\ft; write \\\\ for a backslash."]
           (msgs (line "\\ft A \\x here."))))
    (is (= ["Unknown command \\ta in \\phonemic (in “\\ta”); write \\\\ for a backslash."]
           (msgs (line "\\phonemic \\ta b")))))
  (testing "a backslash before a space or at the end escapes nothing"
    (is (= ["A backslash in \\ft escapes nothing; write \\\\ for a backslash."]
           (msgs (line "\\ft A \\ here.")))))
  (testing "in the header too"
    (is (= ["Unknown command \\emph in \\about; write \\\\ for a backslash."]
           (msgs "\\about An \\emph{x}.\n\\ex\n\\text a"))))
  (testing "escapes are fine"
    (is (empty? (msgs (line "\\ft A \\\\ and \\* here.\n\\glmorph root\\\\gloss x"))))))

(deftest written-back-escaped
  (doseq [src [(line "\\glword the=boy {a == b \\*}\n\\glmorph a\\#b 1SG\\,PL\n\\pos N\\-prop V\n\\translit x\\*y b")]]
    (is (round-trips? src) src)
    (is (empty? (msgs (ser/document-source (:doc (parse/parse-document src))))))))
