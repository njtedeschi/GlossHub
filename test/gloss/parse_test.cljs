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

(defn- strip-ids
  "A document without its generated ids (sections by their label instead)."
  [doc]
  (let [label (into {} (map (juxt :id :label) (:sections doc)))]
    (cond-> (update doc :lines (fn [ls] (mapv #(cond-> (dissoc % :id) (:section %) (update :section label)) ls)))
      (:sections doc) (update :sections (fn [ss] (mapv #(dissoc % :id) ss))))))

(defn- errors [diags] (filter #(= :error (:level %)) diags))

(deftest text-row
  (let [{:keys [words diagnostics]} (text/parse-row "Nauta, *canit*, et lūna well-known nunc x")]
    (is (empty? diagnostics))
    (is (= ["Nauta"] (map :text (model/segs (first words) "text"))))
    (testing "a hyphen in the text is part of the word"
      (is (= ["well-known"] (map :text (model/segs (nth words 4) "text")))))
    (is (= "," (:post (first words))))
    (is (= [{:start 0 :end 5 :styles #{:italic}}] (:fmt (second words))))
    (is (nil? (:fmt (nth words 2)))))
  (testing "links"
    (let [{:keys [words diagnostics]} (text/parse-row "[a b](https://x.org/a?b=1) c [d](#e).")]
      (is (empty? diagnostics))
      (is (= ["https://x.org/a?b=1" "https://x.org/a?b=1" nil "#e"] (map :link words)))
      (is (= "." (:post (nth words 3))))))
  (testing "free-standing markup attaches to its word"
    (let [{:keys [words diagnostics]} (text/parse-row "[ puer-ī rīd-ent ](u) — x")]
      (is (empty? diagnostics))
      (is (= 3 (count words)))
      (is (= " —" (:post (second words))))
      (is (= ["u" "u" nil] (map :link words)))))
  (testing "problems are reported, not fatal"
    (is (= 1 (count (:diagnostics (text/parse-row "a](x) b")))))
    (is (= 1 (count (:diagnostics (text/parse-row "[a b")))))
    (is (= 2 (count (:diagnostics (text/parse-row "[a b]")))))))

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
    (is (= 5 (count (:lines doc))))
    (is (= 4 (count (:colors doc))))
    (is (= {"coord" 1 "abl-abs" 2 "manner" 3 "pairs" 4 "wine" 5} (notes/note-numbers doc)))
    (is (= "N=Conj" (get-in doc [:lines 1 :words 2 :pos])))
    (let [l (first (:lines doc))]
      (is (= "1" (:label l)))
      (is (= 15 (count (get-in l [:prosody "text" :syllables]))))
      (is (= #{7} (get-in l [:prosody "text" :caesurae])))
      (is (= "Nauta canit, puerī rīdent, et lūna refulget" (str/join " " (map model/word-text (:words l))))))
    (is (= 17 (count (get-in doc [:lines 2 :prosody "text" :syllables]))))
    (is (= [[3 4]] (get-in doc [:lines 2 :prosody "text" :elided])))))

(deftest round-trip
  (doseq [src [fx/latin fx/hebrew fx/japanese samples/blank
               "\\title T\n\\order tx wg gl\n\\ex\n\\tx a\n\\gl b\n\\wg c"
               "\\tx {白|しろ}い{なし|xx} {a|b}{c|d}e\n\\morph [白]-い-なし ace"
               "\\ex\n\\scan _ u u _\n\\syl Lū-na re-get\n\\tx Lūna + reget\n\\morph [Lūn]-a reget\n\\tl Lūna reget\n\\morph[translit] Lūn-a reget"
               "\\ex\n\\scan _ u\n\\syl[translit] ka-tav\n\\tx כתב\n\\morph (כ)(ת)(ב)\n\\tl katav\n\\morph[translit] (k)a(t)a(v)\n\\gl write\\\\PST"
               "\\section[a] One\n\\ex 1\n\\tx a well-known e-mail\n\\morph a well-know-n email\n\\section[b] Two\n\\ex 1\n\\tx b\n\\end\n\\note[n] A note."]]
    (let [doc (:doc (parse/parse-document src))
          again (:doc (parse/parse-document (ser/document-source doc)))]
      (is (= (strip-ids doc) (strip-ids again)))))
  (testing "a single line's source re-parses to the same line"
    (let [doc (:doc (parse/parse-document fx/latin))
          line (get-in doc [:lines 2])
          {:keys [lines]} (parse/parse-lines (ser/line-block line))]
      (is (= (dissoc line :id) (dissoc (first lines) :id)))
      (is (= [{:tag "manner" :groups [{:words "ōre benignō"}] :text "Ablative of manner"}] (:notes (first lines)))))))

(deftest parts-of-speech
  (let [{:keys [doc]} (parse/parse-document
                       "\\ex\n\\tx a bc d e\n\\morph a b=c d e\n\\pos noun Pro=conj VERB.transitive det.q")]
    (is (empty? (check/check-doc doc)))
    (is (= "VERB.transitive" (get-in doc [:lines 0 :words 2 :pos]))))
  (let [{:keys [doc]} (parse/parse-document "\\ex\n\\tx a bc\n\\morph a b=c\n\\pos Nn V")]
    (is (= ["Unknown part of speech “Nn”."
            "“bc” has 2 clitic group(s) but its part of speech “V” has 1; separate them with =."]
           (sort (map :msg (check/check-doc doc)))))))

(deftest robustness
  (testing "gloss count mismatches are kept and flagged"
    (let [{:keys [doc]} (parse/parse-document "\\ex\n\\tx puer-ōs\n\\gl boy-ACC-PL")]
      (is (= 3 (count (get-in doc [:lines 0 :words 0 :glmorph]))))
      (is (= 1 (count (check/check-doc doc))))))
  (testing "word count mismatches are reported"
    (let [{:keys [diagnostics]} (parse/parse-document "\\ex\n\\tx a b\n\\gl x")]
      (is (= 1 (count diagnostics)))))
  (testing "misaligned scansion is kept verbatim"
    (let [src "\\ex\n\\scan _ u\n\\syl lū-na\n\\tx lūnae"
          {:keys [doc diagnostics]} (parse/parse-document src)
          line (first (:lines doc))]
      (is (= 1 (count (errors diagnostics))))
      (is (nil? (model/prosody line "text")))
      (is (= "lū-na" (get-in line [:raw "syl" :text])))
      (is (re-find #"\\syl lū-na" (ser/document-source doc)))))
  (testing "wrong number of marks"
    (let [{:keys [diagnostics]} (parse/parse-document "\\ex\n\\scan _ u u\n\\syl lū-na\n\\tx lūna")]
      (is (= 1 (count (errors diagnostics))))))
  (testing "continuation lines, comments, implicit blocks"
    (let [{:keys [doc]} (parse/parse-document "% comment\n\\tx a b\n\\ft one\n two\n\\tx c")]
      (is (= 2 (count (:lines doc))))
      (is (= "one\ntwo" (get-in doc [:lines 0 :free])))))
  (testing "unknown abbreviations are flagged, document ones are not"
    (let [{:keys [doc]} (parse/parse-document "\\abbr FOO custom\n\\ex\n\\tx a b\n\\gl FOO BAR")]
      (is (= ["Unknown abbreviation BAR."] (map :msg (check/check-doc doc)))))))

(deftest edits
  (let [doc (:doc (parse/parse-document fx/latin))
        id (get-in doc [:lines 0 :id])
        doc2 (edit/set-item doc id 2 "glmorph" " boy-NOM.PL.X ")]
    (is (= [{:text "boy"} {:text "NOM.PL.X" :sep "-"}]
           (get-in doc2 [:lines 0 :words 2 :glmorph])))
    (is (= ["Unknown abbreviation X."] (map :msg (check/check-doc doc2))))))

(deftest hebrew
  (let [{:keys [doc diagnostics]} (parse/parse-document fx/hebrew)
        [l1 l2] (:lines doc)]
    (is (empty? diagnostics))
    (is (empty? (check/check-doc doc)))
    (is (= "rtl" (:dir doc)))
    (is (= [3] (:breaks l1)))
    (is (= "־" (get-in l2 [:words 2 :post])))
    (testing "the text and the transliteration each have their \\morph row"
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
        id (get-in doc [:lines 0 :id])
        doc2 (edit/set-item doc id 3 "translit" "beveto")
        doc3 (edit/set-item doc id 3 "translit" "bevayto")]
    (testing "an edited transliteration is segmented by its \\morph[translit] item"
      (is (= ["be" "vet" "o"] (map :text (model/segs (get-in doc2 [:lines 0 :words 3]) "translit"))))
      (is (empty? (check/check-doc doc2)))
      (is (= ["bevayto"] (map :text (model/segs (get-in doc3 [:lines 0 :words 3]) "translit"))))
      (is (every? #(= :error (:level %)) (check/check-doc doc3)))
      (is (some #(re-find #"doesn't spell the word" (:msg %)) (check/check-doc doc3))))
    (is (empty? (:diagnostics (parse/parse-document "\\tx a + + b +"))))
    (is (= [1] (:breaks (first (:lines (:doc (parse/parse-document "\\tx + a + + b +"))))))))
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
        words (get-in doc [:lines 0 :words])]
    (is (empty? diagnostics))
    (is (empty? (check/check-doc doc)))
    (is (= ["白" "い"] (map :text (model/segs (nth words 2) "text"))))
    (is (= "。" (:post (nth words 5))))
    (is (= [{:start 0 :end 1 :text "あお"} {:start 1 :end 2 :text "ぞら"}] (:ruby (first words))))
    (is (contains? (model/doc-features doc) :ruby)))
  (is (= 1 (count (:diagnostics (parse/parse-document "\\tx {雲|くも り"))))))

(deftest reserved-markers
  (let [src "\\title T\n\\rowname[ja] Japanese\n\n\\ex\n\\text {学|xué} 生\n\\row[ja] {学|がく} \\-せい\n\\ft student\n"
        {:keys [doc diagnostics]} (parse/parse-document src)]
    (testing "\\row and \\rowname are kept as written, with a warning"
      (is (= ["\\rowname isn't supported yet. The row is kept as written but not shown."
              "\\row isn't supported yet. The row is kept as written but not shown."]
             (map :msg diagnostics)))
      (is (= [{:marker "rowname" :opt "ja" :text "Japanese"}] (:unsupported doc)))
      (is (= [{:marker "row" :opt "ja" :text "{学|がく} \\-せい"}] (get-in doc [:lines 0 :unsupported])))
      (is (= #{:ruby :free} (model/doc-features doc))))
    (testing "and written back out"
      (let [out (ser/document-source doc)]
        (is (str/includes? out "\\rowname[ja] Japanese"))
        (is (str/includes? out "\\text {学|xué} 生\n\\row[ja] {学|がく} \\-せい\n"))))))

(deftest format-version
  (let [msgs #(map :msg (:diagnostics (parse/parse-document (str % "\n\\ex\n\\text a"))))]
    (is (empty? (msgs "")))
    (is (empty? (msgs "\\glosshubformat 1")))
    (is (= ["This text is written in version 2 of the GlossHub format, and this site reads version 1. Parts of it may not show."]
           (msgs "\\glosshubformat 2")))
    (is (= 1 (count (msgs "\\glosshubformat one")))))
  (testing "every document is written with the version"
    (is (str/starts-with? (ser/document-source (:doc (parse/parse-document "\\text a"))) "\\glosshubformat 1\n\\title"))))

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
             (map model/word-letters (get-in (parse/parse-document "\\ex\n\\text etc\\. a\\,b x\\→y") [:doc :lines 0 :words])))))))

(deftest punctuation-only-in-text
  (let [{:keys [doc]} (parse/parse-document "\\tx a, b.\n\\gl x, {y z}.\n\\wg ¿p? q!\n\\tl r, s\n\\pos N, V.")
        [a b] (get-in doc [:lines 0 :words])]
    (is (= "," (:post a)))
    (is (= "x" (get-in a [:glmorph 0 :text])))
    (is (= "y z" (get-in b [:glmorph 0 :text])))
    (is (= ["p" "q"] [(:glword a) (:glword b)]))
    (is (= "r" (get-in a [:surfaces "translit" :segs 0 :text])))
    (is (= ["N" "V"] [(:pos a) (:pos b)])))
  (let [doc (:doc (parse/parse-document fx/japanese))
        id (get-in doc [:lines 0 :id])]
    (is (= "float" (:glword (get-in (edit/set-item doc id 5 "glword" "float.") [:lines 0 :words 5])))))
  (testing "tags only needs grammatical labels"
    (is (not ((model/doc-features (:doc (parse/parse-document "\\tx a b\n\\gl cloud float"))) :gram)))))

(deftest layer-orders
  (let [doc (:doc (parse/parse-document fx/hebrew))]
    (is (= ["text" "translit" "ipa" "phonemic" "phonetic" "glmorph" "glword" "pos"] (model/layer-order doc)))
    (is (= ["translit" "text" "ipa" "phonemic" "phonetic" "glmorph" "glword" "pos"]
           (model/layer-order (assoc doc :order ["translit" "text"]))))
    (is (= 1 (count (check/check-doc (assoc doc :order ["nope"])))))))

(deftest syl-tx-comparison
  (testing "diacritics and case don't cause a mismatch"
    (let [{:keys [doc diagnostics]} (parse/parse-document "\\ex\n\\scan _ u\n\\syl Lu-na\n\\tx Lūna")]
      (is (empty? diagnostics))
      (is (= [[0 2] [2 4]] (map (juxt :start :end) (get-in doc [:lines 0 :prosody "text" :syllables]))))))
  (testing "syllables are placed on the \\tx letters even when a diacritic is a separate mark"
    (let [tx (str "q" "\u0304" "ara")                   ; q̄ara: q + combining macron (no single character)
          {:keys [doc diagnostics]} (parse/parse-document (str "\\ex\n\\syl qa-ra\n\\tx " tx))]
      (is (empty? diagnostics))
      (is (= [[0 3] [3 5]] (map (juxt :start :end) (get-in doc [:lines 0 :prosody "text" :syllables]))))))
  (testing "the message quotes the syllable with its neighbours, and the same stretch of \\tx"
    (let [msg (fn [syl tx] (:msg (first (:diagnostics (parse/parse-document (str "\\ex\n\\syl " syl "\n\\tx " tx))))))]
      (is (= "The \\syl and \\text rows spell different letters: \\syl has “Lu-ma ca” where \\text has “Lūna ca” (“m” vs “n”)."
             (msg "Lu-ma ca-nit" "Lūna canit")))
      (is (= "The \\syl and \\text rows spell different letters: \\syl has “ta ca ni” where \\text has “ta cō nī” (“a” vs “ō”)."
             (msg "ta ca ni" "ta cō nī")))
      (is (re-find #"\\syl has “ca-nit” where \\text has “canit” \(nothing vs “c”\)" (msg "ca-nit" "canit c"))))))

(deftest marker-names
  (testing "the short names read the same rows"
    (let [doc #(strip-ids (:doc (parse/parse-document %)))
          standard (doc "\\ex\n\\scan _ u\n\\syl Lū-na\n\\text Lūn-a\n\\translit Lun-a\n\\glmorph moon-NOM\n\\glword moon\n\\pos N\n\\ft Moon")]
      (is (= standard (doc "\\ex\n\\scan _ u\n\\syl Lū-na\n\\TX Lūn-a\n\\tl Lun-a\n\\gl moon-NOM\n\\wg moon\n\\pos N\n\\ft Moon")))))
  (testing "other names are unknown markers"
    (let [{:keys [diagnostics]} (parse/parse-document "\\ex\n\\text Lūna\n\\glw moon\n\\translation Moon")]
      (is (= #{"Unknown marker \\glw." "Unknown marker \\translation."}
             (set (map :msg diagnostics))))))
  (testing "documents are written with the standard names"
    (let [src (ser/document-source (:doc (parse/parse-document
                                          "\\order tx wg gl\n\\ex\n\\tx a\n\\tl b\n\\gl c\n\\wg d\n\\phonemic e")))]
      (is (re-find #"\\order text glword glmorph" src))
      (is (re-find #"\\text a\n\\translit b\n\\phonemic e\n\\glmorph c\n\\glword d" src))))
  (testing "\\order accepts either name"
    (is (= ["glword" "glmorph"] (:order (:doc (parse/parse-document "\\order wg glmorph\n\\text a")))))))

(deftest escapes
  (let [parse #(:doc (parse/parse-document %))
        word (fn [src] (get-in (parse src) [:lines 0 :words 0]))
        round-trip (fn [src] (let [d (parse src)]
                               (= (strip-ids d) (strip-ids (parse (ser/document-source d))))))]
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
      (is (= "a \\- b" (get-in (parse "\\ex\n\\text a\n\\ft a \\- b") [:lines 0 :free]))))
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
        [w0 w1] (get-in doc [:lines 0 :words])]
    (is (empty? diagnostics))
    (is (empty? (check/check-doc doc)))
    (testing "raw IPA, kept whole, every character as written"
      (is (= "huru-ike" (:phonemic w0)))
      (is (= "ˈɸɯ.ɾɯ|ike" (:phonetic w0))))
    (is (= "ja" (:phonemic w1)))
    (is (= #{:phonemic :phonetic} (set (filter #{:phonemic :phonetic :ipa} (model/doc-features doc)))))
    (is (= ["text" "phonemic" "phonetic"] (model/present-layers doc nil)))
    (is (= (strip-ids doc) (strip-ids (:doc (parse/parse-document (ser/document-source doc)))))))
  (testing "\\ipa can't be combined with \\phonemic or \\phonetic"
    (is (empty? (check/check-doc (:doc (parse/parse-document "\\text a\n\\ipa ə")))))
    (is (= ["\\ipa can't be used together with \\phonemic or \\phonetic: use \\ipa alone, or the other two."]
           (map :msg (check/check-doc (:doc (parse/parse-document "\\text a\n\\ipa ə\n\\phonetic ə"))))))
    (is (= ["text" "translit" "ipa" "glmorph"]
           (model/present-layers (:doc (parse/parse-document "\\text a\n\\translit a\n\\ipa ə\n\\glmorph x")) nil))))
  (testing "editing a transcription cell"
    (let [doc (:doc (parse/parse-document "\\text furuike"))
          id (get-in doc [:lines 0 :id])]
      (is (= "huru-ike"
             (get-in (edit/set-item doc id 0 "phonemic" " huru-ike ") [:lines 0 :words 0 :phonemic]))))))

(deftest layer-order
  (let [doc (:doc (parse/parse-document fx/hebrew))]
    (testing "a reader's order for the text comes first, else \\order, then the defaults"
      (is (= "translit" (first (model/layer-order (assoc doc :order ["translit"])))))
      (is (= "glword" (first (model/layer-order (assoc doc :order ["translit"]) ["glword"]))))
      (is (= ["glmorph" "text" "translit" "ipa" "phonemic" "phonetic" "glword" "pos"] (model/layer-order doc ["glmorph"]))))))

(deftest formatting-stays-in-its-line
  (let [{:keys [doc diagnostics]} (parse/parse-document "\\ex\n\\text a **b c\n\\ex\n\\text **d e** f")]
    (is (= ["Formatting opened in this line isn't closed in it; it is closed at the line's end."]
           (map :msg diagnostics)))
    (is (= [[false true true] [true true false]]
           (for [l (:lines doc)] (map #(boolean (seq (:fmt %))) (:words l)))))
    (testing "written back closed in its line"
      (is (str/includes? (ser/document-source doc) "\\text a **b c**\n")))))
