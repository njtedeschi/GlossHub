(ns gloss.reserved-test
  "Each tier's reserved characters, and backslashes that
  escape nothing."
  (:require [cljs.test :refer [deftest is testing]]
            [gloss.chars :as chars]
            [gloss.model :as model]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

(defn- msgs [src] (map :msg (:diagnostics (parse/parse-document src))))
(defn- igt [tiers] (str "\\ex\n\\text a b\n" tiers))
(defn- round-trips? [src]
  (let [d (:doc (parse/parse-document src))]
    (= (model/without-ids d) (model/without-ids (:doc (parse/parse-document (ser/document-source d)))))))

(deftest by-tier
  (testing "\\text[translit] reserves what \\text does, formatting characters included"
    (is (= ["“*” (in “x*y”) is reserved in \\text[translit]; write \\* for a literal “*”."]
           (msgs (igt "\\text[translit] x*y b"))))
    (is (empty? (msgs (igt "\\text[translit] don't, (x)/y?")))))
  (testing "\\glword is as free as \\ft, within its cell"
    (is (empty? (msgs (igt "\\glword **the** boy"))))
    (is (empty? (msgs (igt "\\glword the=boy {50% #1 & <x>|y}")))))
  (testing "\\glmorph: its markup and its punctuation"
    (is (empty? (msgs (igt "\\glmorph boy-NOM.PL x:y;z(w)[v]"))))
    (is (= ["“#” (in “a#b”) is reserved in \\glmorph; write \\# for a literal “#”."
            "“,” (in “1SG,PL”) is reserved in \\glmorph; write \\, for a literal “,”."]
           (msgs (igt "\\glmorph a#b 1SG,PL")))))
  (testing "\\pos: letters, . (subclass) and = (clitic)"
    (is (empty? (msgs (igt "\\pos N.prop V.trans"))))
    (is (= ["“-” (in “V-trans”) is reserved in \\pos: subclasses follow a period (V.trans)."]
           (msgs (igt "\\pos N V-trans"))))
    (is (= ["“#” (in “N#”) is reserved in \\pos; write \\# for a literal “#”."]
           (msgs (igt "\\pos N# V")))))
  (testing "\\syl skips a reserved character and reports it"
    (is (= ["“#” is reserved in \\syl; write \\# for a literal “#”."]
           (msgs "\\ex\n\\syl a#\n\\text a"))))
  (testing "\\scan: only its marks"
    (is (some #(re-find #"“y” is reserved in \\scan" %) (msgs "\\ex\n\\scan _ y\n\\syl a-b\n\\text ab"))))
  (testing "transcriptions and prose reserve nothing"
    (is (empty? (msgs (igt "\\phonemic #a$ b|\n\\ft 50% *of* #1 = x\n\\note {a} 2 * 3 & y"))))))

(deftest backslashes
  (testing "a backslash before a letter is a command; only \\0 exists, in \\morph"
    (is (= ["Unknown command \\PL in \\glmorph (in “boy\\PL”); write \\\\ for a backslash."]
           (msgs (igt "\\glmorph boy\\PL x"))))
    (is (= ["Unknown command \\x in \\ft; write \\\\ for a backslash."]
           (msgs (igt "\\ft A \\x here."))))
    (is (= ["Unknown command \\ta in \\text[phonemic] (in “\\ta”); write \\\\ for a backslash."]
           (msgs (igt "\\text[phonemic] \\ta b")))))
  (testing "a backslash before a space or at the end escapes nothing"
    (is (= ["A backslash in \\ft escapes nothing; write \\\\ for a backslash."]
           (msgs (igt "\\ft A \\ here.")))))
  (testing "in the header too"
    (is (= ["Unknown command \\emph in \\about; write \\\\ for a backslash."]
           (msgs "\\about An \\emph{x}.\n\\ex\n\\text a"))))
  (testing "escapes are fine"
    (is (empty? (msgs (igt "\\ft A \\\\ and \\* here.\n\\glmorph root\\\\gloss x"))))))

(deftest written-back-escaped
  (doseq [src [(igt "\\glword the=boy {a == b \\*}\n\\glmorph a\\#b 1SG\\,PL\n\\pos N\\-prop V\n\\translit x\\*y b")]]
    (is (round-trips? src) src)
    (is (empty? (msgs (ser/document-source (:doc (parse/parse-document src))))))))

(deftest closing-brackets
  ;; Punctuation at an entry's edges is the text's, but a closing
  ;; parenthesis opened inside the entry is the entry's (Leipzig eye(N)).
  (is (= "eye(N)" (chars/strip-punct "eye(N)")))
  (is (= "eye(N)" (chars/strip-punct "eye(N).")))
  (is (= "eye(N)" (chars/strip-punct "(eye(N))")))
  (is (= "word" (chars/strip-punct "(word)")))
  (is (= "word" (chars/strip-punct "word),")))
  (is (= "" (chars/strip-punct "")))
  (let [src (igt "\\glmorph eye(N) boy[NOM]\n")
        doc (:doc (parse/parse-document src))]
    (is (= ["eye(N)" "boy[NOM]"] (map #(-> % :glmorph first :text) (:words (first (:igts doc))))))
    (is (empty? (msgs src)))
    (is (round-trips? src))))
