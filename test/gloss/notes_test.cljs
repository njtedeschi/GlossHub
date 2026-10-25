(ns gloss.notes-test
  "Notes that quote their words."
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gloss.check :as check]
            [gloss.edit :as edit]
            [gloss.model :as model]
            [gloss.notes :as notes]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

(defn- doc [src] (:doc (parse/parse-document src)))
(defn- problems [src] (map :msg (check/check-doc (doc src))))
(defn- marks
  "Each IGT's marks, as [[word-index [note-number …]] …]."
  [d]
  (for [l (:igts d)]
    (sort (for [[i ms] (get-in (notes/note-index d) [:marks (:id l)])] [i (map :n ms)]))))
(defn- round-trips? [d]
  (= (model/without-ids d) (model/without-ids (doc (ser/document-source d)))))

(deftest reading-notes
  (let [d (doc "\\ex\n\\text Mūsa, mihī causās memorā\n\\note[muse] {musa} The Muse.\n\\note {mihī causās} To me.")
        [l] (:igts d)]
    (is (= [{:tag "muse" :groups [{:words "musa"}] :text "The Muse."}
            {:groups [{:words "mihī causās"}] :text "To me."}]
           (:notes l)))
    (is (empty? (check/check-doc d)))
    (testing "the number goes after the last word quoted"
      (is (= [[[0 [1]] [2 [2]]]] (marks d))))
    (testing "members, highlighted together"
      (is (= {(:id l) #{1 2}} (notes/highlighted-words d #{1}))))
    (testing "a tag is the note's key; otherwise its place"
      (is (= {"muse" 1, 1 2} (notes/note-numbers d))))
    (is (round-trips? d))
    (is (str/includes? (ser/document-source d) "\\note[muse] {musa} The Muse.\n\\note {mihī causās} To me."))))

(deftest matching
  (testing "capitals, diacritics and punctuation don't matter"
    (is (empty? (problems "\\ex\n\\text Mūsa, mihī\n\\note {MUSA mihi!} x"))))
  (testing "a quote must match exactly one place"
    (is (= ["“a” matches 2 places in this IGT; quote more words, or say which with #: “a#2” is the second."]
           (problems "\\ex\n\\text a b a c\n\\note {a} x")))
    (is (= ["“z” matches no words in this IGT."] (problems "\\ex\n\\text a b\n\\note {z} x")))
    (is (empty? (problems "\\ex\n\\text a b a c\n\\note {b a} x"))))
  (testing "\\translit when nothing matches in \\text"
    (let [d (doc "\\ex\n\\text כתב שלום\n\\translit katav shalom\n\\note {shalom} Peace.")]
      (is (empty? (check/check-doc d)))
      (is (= [[[1 [1]]]] (marks d)))))
  (testing "a span runs to the next match, across IGTs"
    (let [d (doc "\\ex\n\\text a b c\n\\note {b … e} x\n\\ex\n\\text d e f e")
          [l1 l2] (:igts d)]
      (is (empty? (check/check-doc d)))
      (is (= {(:id l1) #{1 2} (:id l2) #{0 1}} (notes/highlighted-words d #{0})))
      (is (= [[] [[1 [1]]]] (marks d)))
      (testing "and its text is shown where its number is"
        (is (= {(:id l2) #{0}} (notes/first-cited d))))
      (is (round-trips? d))
      (is (str/includes? (ser/document-source d) "\\note {b … e} x"))))
  (is (empty? (problems "\\ex\n\\text a b c\n\\note {a ... c} x")))
  (is (= ["“z” doesn't follow “a” in this IGT or the ones after it."]
         (problems "\\ex\n\\text a b c\n\\note {a … z} x"))))

(deftest which-place
  (testing "#n picks the nth place of a quote"
    (let [d (doc "\\ex\n\\text a b a b\n\\note {a b#2} x\n\\note {a#1} y")]
      (is (empty? (check/check-doc d)))
      (is (= [[[0 [1]] [3 [2]]]] (marks d)))
      (is (round-trips? d))
      (is (str/includes? (ser/document-source d) "\\note {a b#2} x\n\\note {a#1} y"))))
  (is (= ["“a#3”: “a” occurs only 2 times in this IGT."]
         (problems "\\ex\n\\text a b a\n\\note {a#3} x")))
  (testing "in a span, the end's place counts from the start"
    (let [d (doc "\\ex\n\\text a b c b\n\\note {a … b#2} x")]
      (is (= {(:id (first (:igts d))) #{0 1 2 3}} (notes/highlighted-words d #{0}))))
    (is (= ["“b#3”: “b” follows “a” only 2 times."]
           (problems "\\ex\n\\text a b c b\n\\note {a … b#3} x")))
    (is (empty? (problems "\\ex\n\\text a x a b\n\\note {a#2 … b} x"))))
  (testing "an escaped # is part of the quote"
    (is (= "a\\#2" (notes/group-text {:words "a#2"})))))

(deftest other-igts
  (testing "@label, by the label written after \\ex"
    (let [d (doc "\\ex 1\n\\text a b\n\\ex 2\n\\text c d\n\\note {@1 b} {d} x")]
      (is (empty? (check/check-doc d)))
      (is (= [[[1 [1]]] [[1 [1]]]] (marks d)))
      (is (round-trips? d))))
  (testing "automatic numbers can't be used"
    (is (some #(re-find #"no IGT is labelled “1”. Notes refer to IGTs by their own labels \(\\igt 1\)" %)
              (problems "\\ex\n\\text a\n\\ex\n\\text b\n\\note {@1 a} x"))))
  (testing "sections"
    (let [src "\\section[s] S\n\\ex 1\n\\text a\n\\section[t] T\n\\ex 1\n\\text b\n\\note "]
      (is (empty? (problems (str src "{@s/1 a} x"))))
      (is (= ["“a” matches no words in that IGT."] (problems (str src "{@1 a} x"))))
      (is (= ["@u/1: no section is labelled “u”."] (problems (str src "{@u/1 a} x"))))))
  (testing "a label used twice"
    (is (some #(re-find #"more than one IGT is labelled" %)
              (problems "\\ex 1\n\\text a\n\\ex 1\n\\text a\n\\note {@1 a} x")))))

(deftest stars-and-numbers
  (let [d (doc "\\ex\n\\text a b c\n\\note {* a} {c} x")]
    (is (empty? (check/check-doc d)))
    (is (= [[[2 [1]]]] (marks d)))
    (is (= {(:id (first (:igts d))) #{0 2}} (notes/highlighted-words d #{0})))
    (is (round-trips? d)))
  (is (= ["Every group of this note is starred, so its number appears nowhere in the text."]
         (problems "\\ex\n\\text a b\n\\note {* a} x")))
  (is (empty? (problems "\\ex 1\n\\text a b\n\\note {@1* a} {b} x")))
  (testing "numbers follow the text, wherever the notes are written"
    (let [d (doc "\\ex 1\n\\text a b\n\\ex\n\\text c\n\\note {c} first written\n\\note {@1 b} second written")]
      (is (= {0 2, 1 1} (notes/note-numbers d))))))

(deftest after-the-end
  (testing "notes on the whole text quote nothing"
    (let [d (doc "\\ex\n\\text a\n\\end\n\\note[w] On the whole text.")]
      (is (empty? (check/check-doc d)))
      (is (= ["w"] (notes/uncited-notes d)))
      (is (round-trips? d))))
  (testing "a group there needs @label"
    (is (= ["A note after \\end must say which IGT each group is in: {@label …}."]
           (problems "\\ex\n\\text a\n\\end\n\\note {a} x")))
    (is (empty? (problems "\\ex 1\n\\text a\n\\end\n\\note {@1 a} x"))))
  (testing "a note in the header moves after \\end"
    (let [{:keys [doc diagnostics]} (parse/parse-document "\\title T\n\\note {@1 a} x\n\\ex 1\n\\text a")]
      (is (= 1 (count diagnostics)))
      (is (str/ends-with? (ser/document-source doc) "\\end\n\\note {@1 a} x\n")))))

(deftest other-problems
  (is (= ["This note quotes no words: start it with the words it is about, in braces ({words})."]
         (problems "\\ex\n\\text a\n\\note Just text.")))
  (is (some #(re-find #"Two notes are tagged “t”" %)
            (problems "\\ex\n\\text a b\n\\note[t] {a} x\n\\note[t] {b} y")))
  (testing "^ is reserved in \\text"
    (is (some #(re-find #"“\^” .* reserved" (:msg %))
              (:diagnostics (parse/parse-document "\\ex\n\\text a^{n}"))))))

(deftest editing
  (let [d (doc "\\ex\n\\text a b\n\\note {a} One\n\\note[t] {b} Two")]
    (is (= "Uno" (:text (first (notes/find-note (edit/set-note d 0 " Uno ") 0)))))
    (is (= "Dos" (:text (first (notes/find-note (edit/set-note d "t" "Dos") "t"))))))
  (testing "an IGT's source carries its notes"
    (let [d (doc "\\ex\n\\text a b\n\\note {a} One")
          igt (first (:igts d))
          [again] (:igts (parse/parse-igts (ser/igt-block igt)))]
      (is (= (:notes igt) (:notes again))))))
