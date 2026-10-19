(ns gloss.morph-test
  "Plain \\text with \\morph and \\syl rows (and their [translit]
  versions), sections, \\end, and Fill from the words."
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gloss.check :as check]
            [gloss.edit :as edit]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

(defn- doc [src] (:doc (parse/parse-document src)))
(defn- diags [src] (map :msg (:diagnostics (parse/parse-document src))))
(defn- checks [src] (map :msg (check/check-doc (doc src))))
(defn- word [src i] (get-in (doc src) [:lines 0 :words i]))

(deftest morph-items
  (testing "separators, stems, roots and zero morphemes"
    (let [{:keys [segs problems]} (morph/parse-morph-item "[over-re-(act)]-ing")]
      (is (empty? problems))
      (is (= ["over" "re" "act" "ing"] (map :text segs)))
      (is (= [nil "-" "-" "-"] (map :sep segs)))
      (is (= [true true true nil] (map :stem? segs)))
      (is (= [nil nil [[0 3]] nil] (map :root segs))))
    (is (= [[[0 1] [2 3] [4 5]]] (keep :root (:segs (morph/parse-morph-item "(k)a(t)a(v)")))))
    (is (= [nil true] (map :zero? (:segs (morph/parse-morph-item "puer-\\0")))))
    (is (= [nil "~"] (map :sep (:segs (morph/parse-morph-item "bi~bili"))))))
  (testing "problems"
    (let [problems #(:problems (morph/parse-morph-item %))]
      (is (= 1 (count (problems "a--b"))))
      (is (= 1 (count (problems "a[b]"))))
      (is (seq (problems "(a-b)")))
      (is (= ["Unknown command \\x."] (problems "a\\x")))
      (testing "an unknown command is read whole; its letters aren't the word's"
        (let [{:keys [segs problems]} (morph/parse-morph-item "ab\\xyz")]
          (is (= ["Unknown command \\xyz."] problems))
          (is (= ["ab"] (map :text segs)))))
      (is (= ["“#” is reserved in \\morph; write \\# for a literal “#”."] (problems "oz#-di")))
      (is (empty? (problems "a,b.")))
      (is (empty? (problems "s<um>ulat"))))))

(deftest morph-on-the-text
  (testing "capitals, diacritics and punctuation don't count; the text shows as written"
    (let [w (word "\\text Mūsa,\n\\morph mus-a" 0)]
      (is (= ["Mūs" "a"] (map :text (model/segs w "text"))))
      (is (= "," (:post w)))))
  (testing "a hyphen of the text stands where a break falls on it"
    (is (= [{:text "well"} {:text "know" :sep "-" :joint "-"} {:text "n" :sep "-"}]
           (model/segs (word "\\text well-known\n\\morph well-know-n" 0) "text")))
    (is (= [{:text "e-mail"}] (model/segs (word "\\text e-mail\n\\morph email" 0) "text"))))
  (testing "apostrophes are letters, escaped or not; quotations use other marks"
    (is (= ["don" "'t"] (map :text (model/segs (word "\\text don't\n\\morph don-'t" 0) "text"))))
    (is (= ["don" "'t"] (map :text (model/segs (word "\\text don\\'t\n\\morph don-'t" 0) "text"))))
    (is (= 1 (count (checks "\\text don't\n\\morph dont"))))
    (let [d (doc "\\text “Run,” she said ‘now’.\n\\morph run she said now’")]
      (is (empty? (check/check-doc d)))
      (is (str/includes? (ser/document-source d) "\\text “Run,” she said ‘now’."))))
  (testing "a \\morph item that spells another word is an error, and the word stays whole"
    (let [d (doc "\\text Mūsa\n\\morph mus-o")]
      (is (= [{:text "Mūsa"}] (model/segs (get-in d [:lines 0 :words 0]) "text")))
      (is (= [:error] (map :level (check/check-doc d))))
      (is (re-find #"\\morph doesn't spell the word “Mūsa”: it has “o” where the word has “a”"
                   (:msg (first (check/check-doc d))))))))

(deftest glosses-follow-the-morphemes
  (testing "without \\morph each word is one morpheme"
    (is (empty? (checks "\\text wǒ ài nǐ\n\\glmorph 1SG love 2SG")))
    (is (re-find #"Add a \\morph row" (first (checks "\\text puerī\n\\glmorph boy-NOM.PL"))))
    (is (re-find #"Add a \\morph row" (first (checks "\\text quidve\n\\pos Pro=Conj")))))
  (testing "with \\morph[translit] only, the glosses follow the transliteration"
    (let [src "\\text בבית\n\\translit babayit\n\\morph[translit] ba=bayit\n\\glmorph in=house"]
      (is (empty? (checks src)))
      (is (= 2 (model/morph-count (word src 0))))))
  (testing "both \\morph rows must divide words alike, and mark stems alike when both do"
    (is (empty? (checks "\\text ab\n\\morph a-[b]\n\\tl ab\n\\morph[translit] a-b")))
    (is (= 1 (count (checks "\\text ab\n\\morph [a]-b\n\\tl ab\n\\morph[translit] a-[b]"))))
    (is (= 1 (count (checks "\\text ab\n\\morph a-b\n\\tl ab\n\\morph[translit] a=b"))))))

(deftest root-glosses
  (is (= 5 (morph/root-gloss-end "write\\PST.3SG.M")))
  (is (= 5 (morph/root-gloss-end "write.PST.3SG.M")))
  (is (= 8 (morph/root-gloss-end "come.out.PST")))
  (is (nil? (morph/root-gloss-end "John")))
  (is (nil? (morph/root-gloss-end "go"))))

(deftest syllables
  (testing "feet and caesurae come from \\scan only"
    (let [d (doc "\\ex\n\\scan _ u || _\n\\syl ab-c d\n\\text abc d")]
      (is (= #{2} (get-in d [:lines 0 :prosody "text" :caesurae])))))
  (testing "\\syl[translit] syllabifies the transliteration, and the marks go over it"
    (let [d (doc "\\ex\n\\scan _ u\n\\syl[translit] ka-tav\n\\text כתב\n\\tl katav")
          l (first (:lines d))]
      (is (empty? (:diagnostics (parse/parse-document "\\ex\n\\scan _ u\n\\syl[translit] ka-tav\n\\text כתב\n\\tl katav"))))
      (is (nil? (model/prosody l "text")))
      (is (= [:long :short] (map :weight (get-in l [:prosody "translit" :syllables]))))
      (is (contains? (model/doc-features d) :scansion))))
  (testing "both rows must have as many syllables"
    (is (some #(re-find #"\\syl has 2 syllables but \\syl\[translit\] has 1" %)
              (diags "\\ex\n\\syl ab-c\n\\syl[translit] abc\n\\text abc\n\\tl abc"))))
  (testing "\\scan needs a \\syl"
    (is (some #(re-find #"\\scan needs a \\syl" %) (diags "\\ex\n\\scan _\n\\text a")))))

(deftest rows-and-options
  (testing "+ belongs in \\text only"
    (is (some #(re-find #"Subline breaks \(\+\) belong in \\text only" %)
              (diags "\\ex\n\\text a + b\n\\glword x + y"))))
  (testing "options"
    (is (some #(re-find #"\\glmorph takes no options" %) (diags "\\ex\n\\text a\n\\gl[x] y")))
    (is (some #(re-find #"must be text or translit" %) (diags "\\ex\n\\text a\n\\morph[foo] a"))))
  (testing "\\morph[text] is \\morph"
    (is (= [{:text "a"} {:text "b" :sep "-"}] (model/segs (word "\\ex\n\\text ab\n\\morph[text] a-b" 0) "text")))))

(deftest sections-and-end
  (let [src "\\title T\n\\section[a] One\n\\ex\n\\text x\n\\ex 9\n\\text y\n\\section[b] Two\n\\ex\n\\text z\n\\end\n\\note[n] About the text."
        {:keys [doc diagnostics]} (parse/parse-document src)]
    (is (empty? diagnostics))
    (is (= ["a" "b"] (map :label (:sections doc))))
    (is (= ["1" "9" "1"] (map (model/line-labels doc) (map :id (:lines doc)))))
    (is (= [["a" "One"] ["b" "Two"]] (map (juxt :label :title) (vals (model/section-starts doc)))))
    (is (= [{:tag "n" :groups [] :text "About the text."}] (:notes doc)))
    (testing "written back in place"
      (let [out (ser/document-source doc)]
        (is (str/includes? out "\\section[a] One\n\n\\ex\n\\text x"))
        (is (str/includes? out "\\section[b] Two\n\n\\ex\n\\text z"))
        (is (str/ends-with? out "\\end\n\\note[n] About the text.\n")))))
  (testing "labels are unique within a section"
    (is (empty? (diags "\\section[a] A\n\\ex 1\n\\text x\n\\section[b] B\n\\ex 1\n\\text y")))
    (is (some #(re-find #"Two lines are labelled “1”" %) (diags "\\ex 1\n\\text x\n\\ex 1\n\\text y")))
    (is (some #(re-find #"Two sections are labelled “a”" %) (diags "\\section[a] A\n\\ex\n\\text x\n\\section[a] B\n\\ex\n\\text y"))))
  (testing "only notes follow \\end; notes in the header move there"
    (is (some #(re-find #"Only notes may follow \\end" %) (diags "\\ex\n\\text x\n\\end\n\\glmorph y")))
    (let [{:keys [doc diagnostics]} (parse/parse-document "\\title T\n\\note {@1 x} Text.\n\\ex 1\n\\text x")]
      (is (some #(re-find #"Notes belong under a line or after \\end" (:msg %)) diagnostics))
      (is (str/ends-with? (ser/document-source doc) "\\end\n\\note {@1 x} Text.\n"))))
  (testing "a line added to a section stays in it"
    (let [d (doc "\\section[a] A\n\\ex\n\\text x")
          d2 (edit/insert-lines d 0 [{:id "new" :words []}])]
      (is (= (:section (first (:lines d2))) (:section (second (:lines d2))))))))

(deftest fill-from-the-words
  (is (= "\\ex\n\\text Mūsa, mihī\n\\morph Mūsa mihī\n\\glword x y"
         (parse/fill-row "\\ex\n\\text Mūsa, mihī\n\\glword x y" "morph")))
  (is (= "\\text a\n\\syl a" (parse/fill-row "\\text a" "syl")))
  (is (= "\\text a\n\\tl don't\n\\morph[translit] don't" (parse/fill-row "\\text a\n\\tl don't" "morph[translit]")))
  (testing "lines that have the row already are left alone"
    (is (= "\\text ab\n\\morph a-b" (parse/fill-row "\\text ab\n\\morph a-b" "morph"))))
  (testing "the [translit] rows are offered only when a line has a \\translit"
    (is (= ["morph" "syl"] (parse/fillable "\\text a")))
    (is (= ["morph" "syl" "morph[translit]" "syl[translit]"]
           (parse/fillable "\\text a\n\\TL b")))))
