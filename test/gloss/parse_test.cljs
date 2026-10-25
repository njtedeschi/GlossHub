(ns gloss.parse-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gloss.check :as check]
            [gloss.edit :as edit]
            [gloss.fixtures :as fx]
            [gloss.model :as model]
            [gloss.notes :as notes]
            [gloss.parse :as parse]
            [gloss.parse.prosody :as prosody]
            [gloss.parse.text :as text]
            [gloss.app.samples :as samples]
            [gloss.serialize :as ser]))

(defn- errors [diags] (filter #(= :error (:level %)) diags))

(deftest text-tier
  (let [{:keys [words diagnostics]} (text/parse-tier "Nauta, *canit*, et lūna well-known nunc x")]
    (is (empty? diagnostics))
    (is (= ["Nauta"] (map :text (model/segs (first words) "text"))))
    (testing "a hyphen in the text is part of the word"
      (is (= ["well-known"] (map :text (model/segs (nth words 4) "text")))))
    (is (= "," (:post (first words))))
    (is (= [{:start 0 :end 5 :styles #{:italic}}] (:fmt (second words))))
    (is (nil? (:fmt (nth words 2)))))
  (testing "links"
    (let [{:keys [words diagnostics]} (text/parse-tier "[a b](https://x.org/a?b=1) c [d](#e).")]
      (is (empty? diagnostics))
      (is (= ["https://x.org/a?b=1" "https://x.org/a?b=1" nil "#e"] (map :link words)))
      (is (= "." (:post (nth words 3))))))
  (testing "free-standing markup attaches to its word"
    (let [{:keys [words diagnostics]} (text/parse-tier "[ puer-ī rīd-ent ](u) — x")]
      (is (empty? diagnostics))
      (is (= 3 (count words)))
      (is (= " —" (:post (second words))))
      (is (= ["u" "u" nil] (map :link words)))))
  (testing "problems are reported, not fatal"
    (is (= 1 (count (:diagnostics (text/parse-tier "a](x) b")))))
    (is (= 1 (count (:diagnostics (text/parse-tier "[a b")))))
    (is (= 2 (count (:diagnostics (text/parse-tier "[a b]")))))))

(deftest braces
  (is (= ["the sailor" "" "sings"] (parse/tokenize-braced "{the sailor} {} sings"))))

(deftest scansion
  (let [s (prosody/parse-scan "_ u u ' _ _ || ' _uu")]
    (is (= [:long :short :short :long :long :long :short :short] (:weights s)))
    (is (= #{3 5} (:feet s)))
    (is (= #{5} (:caesurae s))))
  (let [s (prosody/parse-syl "Il-l(e)‿u-bi vē-nit || a")]
    (is (= "illeubivēnita" (.toLowerCase (:letters s))))
    (is (= [[0 2] [2 5] [5 7] [7 9] [9 12] [12 13]]
           (map (juxt :start :end) (:syllables s))))
    (is (= [[3 4]] (:elided s)))
    (testing "feet and caesurae belong in \\scan: here they only separate syllables"
      (is (nil? (:caesurae s)))
      (is (= 1 (count (:problems s)))))))

(deftest latin
  (let [{:keys [doc diagnostics]} (parse/parse-document fx/latin)]
    (is (empty? diagnostics))
    (is (empty? (check/check-doc doc)))
    (is (= "Latin sentences" (:title doc)))
    (is (= 5 (count (:igts doc))))
    (is (= 4 (count (:colors doc))))
    (is (= {"coord" 1 "abl-abs" 2 "manner" 3 "pairs" 4 "wine" 5} (notes/note-numbers doc)))
    (is (= "N=Conj" (get-in doc [:igts 1 :words 2 :pos])))
    (let [l (first (:igts doc))]
      (is (= "1" (:label l)))
      (is (= 15 (count (get-in l [:prosody "text" :syllables]))))
      (is (= #{7} (get-in l [:prosody "text" :caesurae])))
      (is (= "Nauta canit, puerī rīdent, et lūna refulget" (str/join " " (map model/word-text (:words l))))))
    (is (= 17 (count (get-in doc [:igts 2 :prosody "text" :syllables]))))
    (is (= [[3 4]] (get-in doc [:igts 2 :prosody "text" :elided])))))

(deftest round-trip
  (doseq [src [fx/latin fx/hebrew fx/japanese samples/blank
               "\\title T\n\\order tx wg gl\n\\ex\n\\tx a\n\\gl b\n\\wg c"
               "\\tx {白|しろ}い{なし|xx} {a|b}{c|d}e\n\\morph [白]-い-なし ace"
               "\\ex\n\\scan _ u u _\n\\syl Lū-na re-get\n\\tx Lūna + reget\n\\morph [Lūn]-a reget\n\\tl Lūna reget\n\\morph[translit] Lūn-a reget"
               "\\ex\n\\scan _ u\n\\syl[translit] ka-tav\n\\tx כתב\n\\morph (כ)(ת)(ב)\n\\tl katav\n\\morph[translit] (k)a(t)a(v)\n\\gl write\\\\PST"
               "\\section[a] One\n\\ex 1\n\\tx a well-known e-mail\n\\morph a well-know-n email\n\\section[b] Two\n\\ex 1\n\\tx b\n\\end\n\\note[n] A note."]]
    (let [doc (:doc (parse/parse-document src))
          again (:doc (parse/parse-document (ser/document-source doc)))]
      (is (= (model/without-ids doc) (model/without-ids again)))))
  (testing "a single IGT's source re-parses to the same IGT"
    (let [doc (:doc (parse/parse-document fx/latin))
          igt (get-in doc [:igts 2])
          {:keys [igts]} (parse/parse-igts (ser/igt-block igt))]
      (is (= (dissoc igt :id) (dissoc (first igts) :id)))
      (is (= [{:tag "manner" :groups [{:words "ōre benignō"}] :text "Ablative of manner"}] (:notes (first igts)))))))

(deftest parts-of-speech
  (let [{:keys [doc]} (parse/parse-document
                       "\\ex\n\\tx a bc d e\n\\morph a b=c d e\n\\pos noun Pro=conj VERB.transitive det.q")]
    (is (empty? (check/check-doc doc)))
    (is (= "VERB.transitive" (get-in doc [:igts 0 :words 2 :pos]))))
  (let [{:keys [doc]} (parse/parse-document "\\ex\n\\tx a bc\n\\morph a b=c\n\\pos Nn V")]
    (is (= ["Unknown part of speech “Nn”."
            "“bc” has 2 syntactic words but its part of speech “V” has 1; separate them with =."]
           (sort (map :msg (check/check-doc doc)))))))

(deftest robustness
  (testing "gloss count mismatches are kept and flagged"
    (let [{:keys [doc]} (parse/parse-document "\\ex\n\\tx puer-ōs\n\\gl boy-ACC-PL")]
      (is (= 3 (count (get-in doc [:igts 0 :words 0 :glmorph]))))
      (is (= 1 (count (check/check-doc doc))))))
  (testing "word count mismatches are reported"
    (let [{:keys [diagnostics]} (parse/parse-document "\\ex\n\\tx a b\n\\gl x")]
      (is (= 1 (count diagnostics)))))
  (testing "misaligned scansion is kept verbatim"
    (let [src "\\ex\n\\scan _ u\n\\syl lū-na\n\\tx lūnae"
          {:keys [doc diagnostics]} (parse/parse-document src)
          igt (first (:igts doc))]
      (is (= 1 (count (errors diagnostics))))
      (is (nil? (model/prosody igt "text")))
      (is (= "lū-na" (get-in igt [:raw "syl" :text])))
      (is (= 2 (count (:raw igt))))
      (is (= 1 (count (check/check-igt doc igt))) "listed once afterwards, though \\scan and \\syl are both kept")
      (is (= [3] (map :source-line (check/problems (parse/parse-document src))))
          "and once in all, with the parser's problems, at the \\syl")
      (is (re-find #"\\syl lū-na" (ser/document-source doc)))))
  (testing "wrong number of marks"
    (let [{:keys [diagnostics]} (parse/parse-document "\\ex\n\\scan _ u u\n\\syl lū-na\n\\tx lūna")]
      (is (= 1 (count (errors diagnostics))))))
  (testing "continuation lines, comments, implicit blocks"
    (let [{:keys [doc]} (parse/parse-document "% comment\n\\tx a b\n\\ft one\n two\n\\tx c")]
      (is (= 2 (count (:igts doc))))
      (is (= "one\ntwo" (get-in doc [:igts 0 :free])))))
  (testing "unknown abbreviations are flagged, document ones are not"
    (let [{:keys [doc]} (parse/parse-document "\\abbr FOO custom\n\\ex\n\\tx a b\n\\gl FOO BAR")]
      (is (= ["Unknown abbreviation BAR."] (map :msg (check/check-doc doc))))))
  (testing "a tier written twice: the first is read, and the second flagged at its line"
    (let [{:keys [doc diagnostics]} (parse/parse-document
                                     (str "\\ex\n\\text a\n\\gl x\n\\text[translit] b\n\\ft one\n"
                                          "\\note {a} N1\n\\note {a} N2\n\\gl y\n\\tl c\n\\ft two"))]
      (is (= [["This IGT already has \\glmorph; this one is dropped." 8]
              ["This IGT already has \\text[translit]; this one is dropped." 9]
              ["This IGT already has \\ft; this one is dropped." 10]]
             (map (juxt :msg :source-line) diagnostics)))
      (is (every? #(= :error (:level %)) diagnostics))
      (is (= "x" (get-in doc [:igts 0 :words 0 :glmorph 0 :text])))
      (is (= "one" (get-in doc [:igts 0 :free])))
      (is (= 2 (count (get-in doc [:igts 0 :notes])))))))

(deftest edits
  (let [doc (:doc (parse/parse-document fx/latin))
        id (get-in doc [:igts 0 :id])
        doc2 (edit/set-entry doc id 2 "glmorph" " boy-NOM.PL.X ")]
    (is (= [{:text "boy"} {:text "NOM.PL.X" :sep "-"}]
           (get-in doc2 [:igts 0 :words 2 :glmorph])))
    (is (= ["Unknown abbreviation X."] (map :msg (check/check-doc doc2))))))

(deftest hebrew
  (let [{:keys [doc diagnostics]} (parse/parse-document fx/hebrew)
        [l1 l2] (:igts doc)]
    (is (empty? diagnostics))
    (is (empty? (check/check-doc doc)))
    (is (= "rtl" (:dir doc)))
    (is (= [3] (:breaks l1)))
    (is (= "־" (get-in l2 [:words 2 :post])))
    (testing "the text and the transliteration each have their \\morph tier"
      (is (= ["בְּ" "בֵית" "וֹ"] (map :text (model/segs (get-in l1 [:words 3]) "text"))))
      (is (= ["be" "vet" "o"] (map :text (model/segs (get-in l1 [:words 3]) "translit"))))
      (is (= [nil true nil] (map :stem? (model/segs (get-in l1 [:words 3]) "translit"))))
      (is (every? #(nil? (:stem? %)) (model/segs (get-in l1 [:words 3]) "text"))))
    (is (= "in his house" (get-in l1 [:words 3 :glword])))
    (is (nil? (get-in l2 [:words 2 :glword])))
    (is (= [false false false false true] (map #(boolean (seq (:fmt %))) (:words l2))))
    (is (= {"yeled" 1 "sefer" 2} (notes/note-numbers doc)))
    (is (= [4] (:breaks l2)))))

(deftest sublines-and-stems-edit
  (let [doc (:doc (parse/parse-document fx/hebrew))
        id (get-in doc [:igts 0 :id])
        doc2 (edit/set-entry doc id 3 "translit" "beveto")
        doc3 (edit/set-entry doc id 3 "translit" "bevayto")]
    (testing "an edited transliteration is segmented by its \\morph[translit] entry"
      (is (= ["be" "vet" "o"] (map :text (model/segs (get-in doc2 [:igts 0 :words 3]) "translit"))))
      (is (empty? (check/check-doc doc2)))
      (is (= ["bevayto"] (map :text (model/segs (get-in doc3 [:igts 0 :words 3]) "translit"))))
      (is (every? #(= :error (:level %)) (check/check-doc doc3)))
      (is (some #(re-find #"doesn't spell the word" (:msg %)) (check/check-doc doc3))))
    (is (empty? (:diagnostics (parse/parse-document "\\tx a + + b +"))))
    (is (= [1] (:breaks (first (:igts (:doc (parse/parse-document "\\tx + a + + b +"))))))))
  (testing "\\morph and \\morph[translit] must agree"
    (let [{:keys [doc]} (parse/parse-document "\\tx ab\n\\morph a-b\n\\tl ab\n\\morph[translit] ab")]
      (is (= 1 (count (check/check-doc doc)))))))

(deftest features
  (is (= #{:scansion :gloss :gram :pos :wgloss :morph-breaks :color :free :notes :fmt}
         (model/doc-features (:doc (parse/parse-document fx/latin)))))
  (is (= #{:translit :gloss :gram :wgloss :morph-breaks :stems :roots :free :notes :fmt}
         (model/doc-features (:doc (parse/parse-document fx/hebrew))))))

(deftest ruby
  (is (= {:text "白いなし" :rubies [{:start 0 :end 1 :text "しろ"}] :toggles []}
         (text/read-core "{白|しろ}いなし")))
  (is (= {:text "青空" :rubies [{:start 0 :end 1 :text "あお"} {:start 1 :end 2 :text "ぞら"}] :toggles []}
         (text/read-core "{青|あお}{空|ぞら}")))
  (let [{:keys [doc diagnostics]} (parse/parse-document fx/japanese)
        words (get-in doc [:igts 0 :words])]
    (is (empty? diagnostics))
    (is (empty? (check/check-doc doc)))
    (is (= ["白" "い"] (map :text (model/segs (nth words 2) "text"))))
    (is (= "。" (:post (nth words 5))))
    (is (= [{:start 0 :end 1 :text "あお"} {:start 1 :end 2 :text "ぞら"}] (:ruby (first words))))
    (is (contains? (model/doc-features doc) :ruby)))
  (is (= 1 (count (:diagnostics (parse/parse-document "\\tx {雲|くも り"))))))

(deftest an-underscore-joins-the-words-of-a-lexical-gloss
  ;; Leipzig Rule 4A: come_out, a gloss of several words for one that has no parts.
  (let [src "\\igt\n\\text çıkmak\n  \\morph çık-mak\n\\glmorph come_out-INF"
        {:keys [doc diagnostics]} (parse/parse-document src)]
    (is (empty? diagnostics))
    (is (empty? (check/check-doc doc)))
    (is (= ["come_out" "INF"] (map :text (get-in doc [:igts 0 :words 0 :glmorph]))))
    (is (str/includes? (ser/document-source doc) "\\glmorph come_out-INF\n"))))

(deftest a-gloss-starting-with-a-separator-is-reserved
  ;; Kept for Leipzig Rule 2A (docs/format.md §8): -FUT for a word written apart.
  (let [{:keys [doc diagnostics]} (parse/parse-document "\\igt\n\\text a b c d\n\\glmorph -FUT =and - x")]
    (is (= ["A gloss can't start with “-” (“-FUT”): that is reserved for a word written apart from the word it belongs to. Write \\- for a literal “-”."
            "A gloss can't start with “=” (“=and”): that is reserved for a word written apart from the word it belongs to. Write \\= for a literal “=”."]
           (map :msg diagnostics)))
    (is (= ["-FUT" "=and" "-" "x"] (map #(:text (first (:glmorph %))) (get-in doc [:igts 0 :words]))))
    (is (str/includes? (ser/document-source doc) "\\glmorph \\-FUT \\=and \\- x\n")))
  (testing "escaped, it is a literal separator"
    (is (empty? (:diagnostics (parse/parse-document "\\igt\n\\text a\n\\glmorph \\-FUT"))))))

(deftest the-source-on-a-grid
  (let [igt (first (:igts (:doc (parse/parse-document
                                 (str "\\igt\n\\text ab cd, e\n  \\morph a-b cd e\n  \\syl a-b‿c-d e\n"
                                      "\\glword x {y z} w\n\\ft A translation.")))))
        one (fn [& ts] (mapv (fn [t] {:text t :span 1}) ts))]
    (is (= [["text" (one "ab" "cd," "e")]
            ["morph" (one "a-b" "cd" "e")]
            ["syl" [{:text "a-b‿c-d" :span 2} {:text "e" :span 1}]]
            ["glword" (one "x" "y z" "w")]
            ["ft" [{:text "A translation." :span 3}]]]
           (map (fn [[command cells]] [command (vec cells)]) (ser/igt-grid igt))))))

(deftest reserved-commands
  (let [src "\\title T\n\\rowname[ja] Japanese\n\n\\ex\n\\text {学|xué} 生\n\\row[ja] {学|がく} \\-せい\n\\ft student\n"
        {:keys [doc diagnostics]} (parse/parse-document src)]
    (testing "\\row and \\rowname are kept as written, with a warning"
      (is (= ["\\rowname isn't supported yet. It is kept as written but not shown."
              "\\row isn't supported yet. It is kept as written but not shown."]
             (map :msg diagnostics)))
      (is (= [{:command "rowname" :opt "ja" :text "Japanese"}] (:unsupported doc)))
      (is (= [{:command "row" :opt "ja" :text "{学|がく} \\-せい"}] (get-in doc [:igts 0 :unsupported])))
      (is (= #{:ruby :free} (model/doc-features doc))))
    (testing "and written back out"
      (let [out (ser/document-source doc)]
        (is (str/includes? out "\\rowname[ja] Japanese"))
        (is (str/includes? out "\\text {学|xué} 生\n\\row[ja] {学|がく} \\-せい\n"))))))

(deftest format-version
  (let [msgs #(map :msg (:diagnostics (parse/parse-document (str % "\n\\ex\n\\text a"))))]
    (is (empty? (msgs "")))
    (is (empty? (msgs "\\glosshubformat 1")))
    (is (empty? (msgs "\\glosshubformat 2")))
    (is (= ["This text is written in version 3 of the GlossHub format, and this site reads versions up to 2. Parts of it may not show."]
           (msgs "\\glosshubformat 3")))
    (is (= 1 (count (msgs "\\glosshubformat one")))))
  (testing "every document is written with the version"
    (is (str/starts-with? (ser/document-source (:doc (parse/parse-document "\\text a"))) "\\glosshubformat 2\n\\title"))))

(deftest version-1-commands
  (let [src "\\ex\n\\scan _\n\\text a\n\\morph[text] a\n\\syl[text] a\n\\translit b\n\\phonetic c"
        parse (fn [header] (parse/parse-document (str header src)))
        msgs #(map :msg (:diagnostics (parse %)))]
    (testing "are read the same in every version"
      (is (= (model/without-ids (:doc (parse ""))) (model/without-ids (:doc (parse "\\glosshubformat 2\n"))))))
    (testing "are flagged only in a text of version 2, with how it writes them"
      (is (empty? (msgs "")))
      (is (empty? (msgs "\\glosshubformat 1\n")))
      (is (= ["\\morph[text] is now written \\morph."
              "\\syl[text] is now written \\syl."
              "\\translit is now written \\text[translit]."
              "\\phonetic is now written \\text[phonetic]."]
             (msgs "\\glosshubformat 2\n"))))
    (testing "the short name, as the short option"
      (is (= ["\\tl is now written \\text[tl]."]
             (map :msg (:diagnostics (parse/parse-document "\\glosshubformat 2\n\\ex\n\\text a\n\\TL b"))))))
    (testing "an IGT's source editor reads it as the version the site writes"
      (is (= ["\\translit is now written \\text[translit]."]
             (map :msg (:diagnostics (parse/parse-igts "\\ex\n\\text a\n\\translit b"))))))
    (testing "a note's option is its tag, even [text]"
      (is (= "text" (get-in (parse/parse-document "\\ex\n\\text a\n\\note[text] {a} N") [:doc :igts 0 :notes 0 :tag]))))))

(deftest versions-of-the-text
  (let [doc #(model/without-ids (:doc (parse/parse-document %)))
        msgs #(map :msg (:diagnostics (parse/parse-document %)))]
    (testing "are \\text with an option, the tiers of version 1's commands"
      (is (= (doc "\\ex\n\\text כתב\n\\translit katav\n\\morph[translit] ka-tav\n\\syl[translit] ka-tav\n\\phonemic katav")
             (doc "\\ex\n\\text כתב\n\\text[translit] katav\n\\morph[translit] ka-tav\n\\syl[translit] ka-tav\n\\text[phonemic] katav")
             (doc "\\ex\n\\tx כתב\n\\text[tl] katav\n\\morph[tl] ka-tav\n\\syl[tl] ka-tav\n\\text[phonemic] katav"))))
    (testing "\\text[translit] doesn't start an IGT; a second \\text does"
      (is (= 1 (count (:igts (doc "\\text a\n\\text[translit] b")))))
      (is (= 2 (count (:igts (doc "\\text a\n\\text[translit] b\n\\text c"))))))
    (testing "options \\text doesn't take"
      (is (= ["\\text[xyz]: the option must be translit, ipa, phonemic or phonetic."]
             (msgs "\\ex\n\\text[xyz] a"))))
    (testing "a + in a version of the text is flagged: sublines belong to \\text"
      (is (= ["Subline breaks (+) belong in \\text only; the + in \\text[translit] is ignored."]
             (msgs "\\ex\n\\text a + b\n\\text[translit] a + b"))))
    (testing "problems name the rows as they are written"
      (is (= ["The \\text[translit] tier has 1 entry for 2 words; the last word is left without one."]
             (msgs "\\ex\n\\text a b\n\\text[translit] a")))
      (is (= ["\\morph[translit] segments the \\text[translit] tier, but this IGT has none."]
             (msgs "\\ex\n\\text a\n\\morph[translit] a"))))
    (testing "are written with the tiers that divide them indented under them"
      (let [src (str "\\igt 1\n\\scan _ u\n\\text כתב\n  \\morph (כ)(ת)(ב)\n"
                     "\\text[translit] katav\n  \\morph[translit] (k)a(t)a(v)\n  \\syl[translit] ka-tav\n"
                     "\\text[phonemic] katav\n\\glmorph write\\\\PST\n\\ft he wrote")
            {:keys [doc diagnostics]} (parse/parse-document src)]
        (is (empty? diagnostics))
        (is (= src (ser/igt-block (first (:igts doc)))))))))

(deftest reserved-characters
  (let [msgs #(map :msg (:diagnostics (parse/parse-document (str "\\ex\n\\text " %))))]
    (testing "characters with no use yet"
      (is (= ["“#” (in “a#b”, “#c”) is reserved in \\text for future use; write \\# for a literal “#”."]
             (msgs "a#b #c")))
      (doseq [c ["`" "~" "@" "$" "%" "&"]]
        (is (= 1 (count (msgs (str "a" c "b")))) c)))
    (testing "markup characters where they aren't markup"
      (is (= ["Formatting opened inside “a*b” must close in it; it is closed at the word's end."] (msgs "a*b")))
      (is (= ["“_” (in “a_b”) isn't read as markup there; write \\_ for a literal “_”."] (msgs "a_b")))
      (doseq [t ["a^" "a[b" "a+b" "a|b"]]
        (is (= 1 (count (msgs t))) t))
      (doseq [t ["a=b" "a<b" "a>b" "a~b" "a^b"]]
        (is (re-find #"reserved in \\text for future use" (first (msgs t))) t))
      (is (= ["A backslash before a letter starts a command, and \\text has none (in “a\\b”); write \\\\ for a backslash."]
             (msgs "a\\b"))))
    (testing "allowed as they are"
      (is (empty? (msgs "don't \"yes,\" (no) U.S.A. a; b: c! d? e-f a/b a(b)c *i* [k](u) + n")))
      (is (empty? (msgs "\\# \\` \\~ \\@ \\$ \\% \\& \\( \\) \\. \\, \\'"))))
    (testing "a backslash escapes any punctuation"
      (is (= ["etc." "a,b" "x→y"]
             (map model/word-letters (get-in (parse/parse-document "\\ex\n\\text etc\\. a\\,b x\\→y") [:doc :igts 0 :words])))))))

(deftest punctuation-only-in-text
  (let [{:keys [doc]} (parse/parse-document "\\tx a, b.\n\\gl x, {y z}.\n\\wg ¿p? q!\n\\tl r, s\n\\pos N, V.")
        [a b] (get-in doc [:igts 0 :words])]
    (is (= "," (:post a)))
    (is (= "x" (get-in a [:glmorph 0 :text])))
    (is (= "y z" (get-in b [:glmorph 0 :text])))
    (is (= ["p" "q"] [(:glword a) (:glword b)]))
    (is (= "r" (get-in a [:surfaces "translit" :segs 0 :text])))
    (is (= ["N" "V"] [(:pos a) (:pos b)])))
  (let [doc (:doc (parse/parse-document fx/japanese))
        id (get-in doc [:igts 0 :id])]
    (is (= "float" (:glword (get-in (edit/set-entry doc id 5 "glword" "float.") [:igts 0 :words 5])))))
  (testing "grammatical only needs glossing abbreviations"
    (is (not ((model/doc-features (:doc (parse/parse-document "\\tx a b\n\\gl cloud float"))) :gram)))))

(deftest row-orders
  (let [doc (:doc (parse/parse-document fx/hebrew))]
    (is (= ["text" "translit" "ipa" "phonemic" "phonetic" "glmorph" "glword" "pos"] (model/row-order doc)))
    (is (= ["translit" "text" "ipa" "phonemic" "phonetic" "glmorph" "glword" "pos"]
           (model/row-order (assoc doc :order ["translit" "text"]))))
    (is (= 1 (count (check/check-doc (assoc doc :order ["nope"])))))))

(deftest syllables-divide-the-words-where-the-text-does
  (let [problems #(check/problems (parse/parse-document %))
        msgs #(map :msg (problems %))]
    (testing "words divide at a space between syllables, or at ‿ inside one"
      (is (empty? (msgs "\\igt\n\\text Mūsa, mihī\n  \\syl Mū-sa mi-hī")))
      (is (empty? (msgs "\\igt\n\\text multum ille\n  \\syl mul-t(um)‿il-le")))
      (is (empty? (msgs "\\igt\n\\text a — b\n  \\syl a b")) "a word without letters divides nothing"))
    (testing "a space or ‿ inside a word"
      (is (= ["\\syl has a space inside “Mūsa”, which \\text writes as one word: write - between its syllables."]
             (msgs "\\igt\n\\text Mūsa, mihī\n  \\syl Mū sa mi-hī")))
      (is (= ["\\syl has ‿ inside “Mūsa”, which \\text writes as one word; ‿ joins two words in a syllable they share."]
             (msgs "\\igt\n\\text Mūsa, mihī\n  \\syl Mū‿sa mi-hī"))))
    (testing "a division of the text that the syllables don't mark: the first is reported"
      (is (= ["\\syl doesn't separate “Mūsa” from “mihī”, which \\text writes as two words: put a space between “sa” and “mi”."]
             (msgs "\\igt\n\\text Mūsa, mihī causās\n  \\syl Mū-sa-mi-hī-cau-sās")))
      (is (= ["\\syl doesn't separate “Mūsa” from “mihī”, which \\text writes as two words: write the syllable they share as “sa‿m”."]
             (msgs "\\igt\n\\text Mūsa, mihī\n  \\syl Mū-sam-i-hī"))))
    (testing "\\syl[translit] is checked against the transliteration"
      (is (= ["\\syl[translit] has a space inside “abc”, which \\text[translit] writes as one word: write - between its syllables."]
             (msgs "\\igt\n\\text אבג דה\n\\text[translit] abc de\n  \\syl[translit] ab c-de"))))
    (testing "\\syl and \\syl[translit] begin each word at the same syllable"
      (is (empty? (msgs "\\igt\n\\text abc de\n  \\syl a-bc de\n\\text[translit] abc de\n  \\syl[translit] a-bc de")))
      (let [src "\\igt\n\\text abc de\n  \\syl a-bc de\n\\text[translit] ab cde\n  \\syl[translit] ab c-de"]
        (is (= ["\\syl and \\syl[translit] divide the words at different syllables: “de” begins with syllable 3 in \\syl, but “cde” begins with syllable 2 in \\syl[translit]."]
               (msgs src)))
        (is (= [5] (map :source-line (problems src))))
        (is (= (msgs src) (map :msg (check/check-doc (:doc (parse/parse-document src)))))
            "a check of the stored IGT, so it stays listed under it, like \\morph's"))
      (is (= ["\\syl and \\syl[translit] divide the words at different syllables: “cd” begins inside syllable 2 in \\syl, but “d” begins with syllable 3 in \\syl[translit]."]
             (msgs "\\igt\n\\text ab cd\n  \\syl a-b‿c-d\n\\text[translit] abc d\n  \\syl[translit] a-bc d"))))
    (testing "these are warnings: the syllables are still placed"
      (let [{:keys [doc diagnostics]} (parse/parse-document "\\igt\n\\text Mūsa\n  \\syl Mū sa")]
        (is (= [:warning] (map :level diagnostics)))
        (is (= [3] (map :source-line diagnostics)))
        (is (= 2 (count (get-in doc [:igts 0 :prosody "text" :syllables]))))))))

(deftest syl-tx-comparison
  (testing "diacritics and case don't cause a mismatch"
    (let [{:keys [doc diagnostics]} (parse/parse-document "\\ex\n\\scan _ u\n\\syl Lu-na\n\\tx Lūna")]
      (is (empty? diagnostics))
      (is (= [[0 2] [2 4]] (map (juxt :start :end) (get-in doc [:igts 0 :prosody "text" :syllables]))))))
  (testing "syllables are placed on the \\tx letters even when a diacritic is a separate mark"
    (let [tx (str "q" "\u0304" "ara")                   ; q̄ara: q + combining macron (no single character)
          {:keys [doc diagnostics]} (parse/parse-document (str "\\ex\n\\syl qa-ra\n\\tx " tx))]
      (is (empty? diagnostics))
      (is (= [[0 3] [3 5]] (map (juxt :start :end) (get-in doc [:igts 0 :prosody "text" :syllables]))))))
  (testing "the message quotes the syllable with its neighbours, and the same stretch of \\tx"
    (let [msg (fn [syl tx] (:msg (first (:diagnostics (parse/parse-document (str "\\ex\n\\syl " syl "\n\\tx " tx))))))]
      (is (= "The \\syl and \\text tiers spell different letters: \\syl has “Lu-ma ca” where \\text has “Lūna ca” (“m” vs “n”)."
             (msg "Lu-ma ca-nit" "Lūna canit")))
      (is (= "The \\syl and \\text tiers spell different letters: \\syl has “ta ca ni” where \\text has “ta cō nī” (“a” vs “ō”)."
             (msg "ta ca ni" "ta cō nī")))
      (is (re-find #"\\syl has “ca-nit” where \\text has “canit” \(nothing vs “c”\)" (msg "ca-nit" "canit c"))))))

(deftest command-names
  (testing "the short names read the same tiers"
    (let [doc #(model/without-ids (:doc (parse/parse-document %)))
          standard (doc "\\ex\n\\scan _ u\n\\syl Lū-na\n\\text Lūn-a\n\\translit Lun-a\n\\glmorph moon-NOM\n\\glword moon\n\\pos N\n\\ft Moon")]
      (is (= standard (doc "\\ex\n\\scan _ u\n\\syl Lū-na\n\\TX Lūn-a\n\\tl Lun-a\n\\gl moon-NOM\n\\wg moon\n\\pos N\n\\ft Moon")))))
  (testing "other names are unknown commands"
    (let [{:keys [diagnostics]} (parse/parse-document "\\ex\n\\text Lūna\n\\glw moon\n\\translation Moon")]
      (is (= #{"Unknown command \\glw." "Unknown command \\translation."}
             (set (map :msg diagnostics))))
      ;; Errors: the fields aren't kept.
      (is (every? #(= :error (:level %)) diagnostics))))
  (testing "documents are written with the standard names"
    (let [src (ser/document-source (:doc (parse/parse-document
                                          "\\order tx wg gl\n\\ex\n\\tx a\n\\tl b\n\\gl c\n\\wg d\n\\phonemic e")))]
      (is (re-find #"\\order text glword glmorph" src))
      (is (re-find #"\\text a\n\\text\[translit\] b\n\\text\[phonemic\] e\n\\glmorph c\n\\glword d" src))))
  (testing "\\order names a row by its tier's command or by any of its names, and is written with the commands"
    (let [order #(:order (:doc (parse/parse-document (str "\\order " % "\n\\text a"))))]
      (is (= ["glword" "glmorph"] (order "wg glmorph")))
      (is (= ["translit" "translit" "translit" "translit"] (order "text[translit] TEXT[Translit] text[tl] tl")))
      (is (= ["ipa" "phonemic" "phonetic" "ipa" "phonemic" "phonetic"]
             (order "text[ipa] text[phonemic] text[phonetic] ipa phonemic phonetic")))
      (is (= ["text[text]" "morph"] (order "text[text] morph")) "names of no row are kept, and flagged by check-doc"))
    (let [{:keys [doc]} (parse/parse-document "\\order text[translit] text[phonemic] text glmorph\n\\igt\n\\text a\n\\tl b\n\\phonemic c\n\\gl d")]
      (is (empty? (check/check-doc doc)))
      (is (re-find #"\\order text\[translit\] text\[phonemic\] text glmorph\n" (ser/document-source doc)))))
  (testing "\\ex is \\igt's short form, in every version"
    (let [doc #(model/without-ids (:doc (parse/parse-document %)))]
      (is (= (doc "\\igt 1.8\n\\text a\n\\igt\n\\text b") (doc "\\ex 1.8\n\\text a\n\\EX\n\\text b")))
      (is (= ["1.8" nil] (map :label (:igts (doc "\\igt 1.8\n\\text a\n\\ex\n\\text b")))))
      (doseq [version ["" "\\glosshubformat 1\n" "\\glosshubformat 2\n"]]
        (is (empty? (:diagnostics (parse/parse-document (str version "\\ex 1\n\\text a\n\\igt 2\n\\text b"))))))
      (is (str/starts-with? (ser/igt-block (first (:igts (doc "\\ex 3\n\\text a")))) "\\igt 3\n")))))

(deftest escapes
  (let [parse #(:doc (parse/parse-document %))
        word (fn [src] (get-in (parse src) [:igts 0 :words 0]))
        round-trip (fn [src] (let [d (parse src)]
                               (= (model/without-ids d) (model/without-ids (parse (ser/document-source d))))))]
    (testing "a backslash makes markup characters literal"
      (is (= [{:text "well-known"}] (model/segs (word "\\ex\n\\text well\\-known") "text")))
      (is (= [{:text "*wódr̥"}] (model/segs (word "\\ex\n\\text \\*wódr̥") "text")))
      (is (not (:emph? (word "\\ex\n\\text \\*wódr̥"))))
      (is (= "(" (:pre (word "\\ex\n\\text (a"))))
      (is (= [{:text "(a"}] (model/segs (word "\\ex\n\\text \\(a") "text")))
      (is (= [{:text "a_b"}] (model/segs (word "\\ex\n\\text a\\_b") "text")))
      (is (= [{:text "x"} {:text "1>3" :sep "-"}] (:glmorph (word "\\ex\n\\text a-b\n\\glmorph x-1>3"))))
      (is (= [{:text "x-y"}] (:glmorph (word "\\ex\n\\text a\n\\glmorph x\\-y"))))
      (is (= "{set}" (:glword (word "\\ex\n\\text a\n\\glword \\{set\\}"))))
      (is (= "a\\b" (apply str (map :text (model/segs (word "\\ex\n\\text a\\\\b") "text"))))))
    (testing "a backslash before a letter is kept (and flagged)"
      (is (= [{:text "a\\b"}] (model/segs (word "\\ex\n\\text a\\b") "text"))))
    (testing "prose is taken as written"
      (is (= "a \\- b" (get-in (parse "\\ex\n\\text a\n\\ft a \\- b") [:igts 0 :free]))))
    (testing "escaped characters are written back escaped"
      (let [src (ser/document-source (parse "\\ex\n\\text well\\-known \\*wódr̥ \\(a b\\.\n\\glmorph x\\-y z w v\n\\glword {a b} \\{c\\} d e"))]
        (is (str/includes? src "\\text well-known \\*wódr̥ \\(a b\\."))
        (is (str/includes? src "\\glmorph x\\-y z w v"))
        (is (str/includes? src "\\glword {a b} \\{c\\} d e"))))
    (testing "escapes round-trip with ruby, stems and syllables"
      (is (round-trip "\\ex\n\\text {古|ふる}\\-池 _a_-\\=b\n\\glmorph old\\-pond stem-x"))
      (is (round-trip "\\ex\n\\scan _ u\n\\syl ab\\--cd\n\\text ab\\-cd"))
      (is (round-trip "\\ex\n\\row[x] a\\-b {c|d}\n\\text p q")))))

(deftest transcriptions
  (let [{:keys [doc diagnostics]} (parse/parse-document
                                   "\\ex\n\\text furu-ike ya\n\\phonemic huru-ike ja\n\\phonetic ˈɸɯ.ɾɯ|ike ja")
        [w0 w1] (get-in doc [:igts 0 :words])]
    (is (empty? diagnostics))
    (is (empty? (check/check-doc doc)))
    (testing "raw IPA, kept whole, every character as written"
      (is (= "huru-ike" (:phonemic w0)))
      (is (= "ˈɸɯ.ɾɯ|ike" (:phonetic w0))))
    (is (= "ja" (:phonemic w1)))
    (is (= #{:phonemic :phonetic} (set (filter #{:phonemic :phonetic :ipa} (model/doc-features doc)))))
    (is (= ["text" "phonemic" "phonetic"] (model/present-rows doc nil)))
    (is (= (model/without-ids doc) (model/without-ids (:doc (parse/parse-document (ser/document-source doc)))))))
  (testing "\\text[ipa] can't be combined with \\text[phonemic] or \\text[phonetic]"
    (is (empty? (check/check-doc (:doc (parse/parse-document "\\text a\n\\text[ipa] ə")))))
    (doseq [src ["\\text a\n\\text[ipa] ə\n\\text[phonetic] ə" "\\text a\n\\ipa ə\n\\phonetic ə"]]
      (is (= ["\\text[ipa] can't be used together with \\text[phonemic] or \\text[phonetic]: use \\text[ipa] alone, or the other two."]
             (map :msg (check/check-doc (:doc (parse/parse-document src)))))))
    (is (= ["text" "translit" "ipa" "glmorph"]
           (model/present-rows (:doc (parse/parse-document "\\text a\n\\translit a\n\\ipa ə\n\\glmorph x")) nil))))
  (testing "editing a transcription cell"
    (let [doc (:doc (parse/parse-document "\\text furuike"))
          id (get-in doc [:igts 0 :id])]
      (is (= "huru-ike"
             (get-in (edit/set-entry doc id 0 "phonemic" " huru-ike ") [:igts 0 :words 0 :phonemic]))))))

(deftest row-order
  (let [doc (:doc (parse/parse-document fx/hebrew))]
    (testing "a reader's order for the text comes first, else \\order, then the defaults"
      (is (= "translit" (first (model/row-order (assoc doc :order ["translit"])))))
      (is (= "glword" (first (model/row-order (assoc doc :order ["translit"]) ["glword"]))))
      (is (= ["glmorph" "text" "translit" "ipa" "phonemic" "phonetic" "glword" "pos"] (model/row-order doc ["glmorph"]))))))

(deftest formatting-stays-in-its-igt
  (let [{:keys [doc diagnostics]} (parse/parse-document "\\ex\n\\text a **b c\n\\ex\n\\text **d e** f")]
    (is (= ["Formatting opened in this line isn't closed in it; it is closed at the line's end."]
           (map :msg diagnostics)))
    (is (= [[false true true] [true true false]]
           (for [l (:igts doc)] (map #(boolean (seq (:fmt %))) (:words l)))))
    (testing "written back closed in its line"
      (is (str/includes? (ser/document-source doc) "\\text a **b c**\n")))))
