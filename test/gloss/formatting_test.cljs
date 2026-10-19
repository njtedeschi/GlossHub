(ns gloss.formatting-test
  "Formatting and links in \\text, \\glword cells and
  prose, and formatting carried into the other rows."
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gloss.edit :as edit]
            [gloss.formatting :as fmt]
            [gloss.model :as model]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

(defn- doc [src] (:doc (parse/parse-document src)))
(defn- words [src] (get-in (doc src) [:lines 0 :words]))
(defn- msgs [src] (map :msg (:diagnostics (parse/parse-document src))))
(defn- round-trips? [src]
  (let [d (doc src)
        strip #(update % :lines (fn [ls] (mapv (fn [l] (dissoc l :id)) ls)))]
    (= (strip d) (strip (doc (ser/document-source d))))))

(deftest styles-in-text
  (testing "each style, and styles together"
    (is (= [#{:italic} #{:italic} #{:bold} #{:bold} #{:strike} #{:highlight} #{:underline}]
           (map #(:styles (first (:fmt %)))
                (words "\\text *a* _b_ **c** __d__ ~~e~~ ==f== ++g++"))))
    (is (= [{:start 0 :end 1 :styles #{:bold}} {:start 1 :end 2 :styles #{:bold :italic}}]
           (:fmt (first (words "\\text **a*b***")))))
    (is (= #{:bold :italic} (:styles (first (:fmt (first (words "\\text ***a***"))))))))
  (testing "across words, from the edges"
    (is (= [true true false] (map #(boolean (seq (:fmt %))) (words "\\text **a b** c"))))
    (is (empty? (msgs "\\text **a b** c"))))
  (testing "inside a word, closing in it"
    (is (= [{:start 3 :end 6 :styles #{:bold}}] (:fmt (first (words "\\text rīd**ent**")))))
    (is (empty? (msgs "\\text rīd**ent**")))
    (is (some #(re-find #"must close in it" %) (msgs "\\text a**b c**"))))
  (testing "underscores inside a word are letters (and reserved)"
    (is (= ["a_b"] (map model/word-letters (words "\\text a\\_b"))))
    (is (some #(re-find #"“_”" %) (msgs "\\text a_b"))))
  (testing "a delimiter that closes nothing, or opens what is open"
    (is (some #(re-find #"isn't open" %) (msgs "\\text a**")))
    (is (some #(re-find #"already open" %) (msgs "\\text **a **b** c**"))))
  (testing "a delimiter must touch its word"
    (is (= [false false] (map #(boolean (seq (:fmt %))) (words "\\text ** a ** b"))))
    (is (some #(re-find #"must touch the word" %) (msgs "\\text ** a")))))

(deftest round-trips
  (doseq [src ["\\ex\n\\text *a* **b c** d~~e~~f ==g== ++h++, i"
               "\\ex\n\\text ***a*** **b _c_ d**"
               "\\ex\n\\text \"*word*\" rīd**ent**."
               "\\ex\n\\text {古|ふる}**{池|いけ}** や"
               "\\ex\n\\text [a b](https://example.org/x) c [d](#e)^{n}\n\\note {n} N."
               "\\ex\n\\text a\n\\glword {**the** boy}"]]
    (is (round-trips? src) src))
  (testing "written back with the site's delimiters"
    (is (str/includes? (ser/document-source (doc "\\text _a_ __b__")) "\\text *a* **b**"))))

(deftest links
  (is (= ["u" "u" nil] (map :link (words "\\text [a b](u) c"))))
  (is (some #(re-find #"needs its address" %) (msgs "\\text [a b] c")))
  (is (some #(re-find #"isn't closed in this line" %) (msgs "\\text [a b c"))))

(deftest glword-cells
  (let [w (first (words "\\text a\n\\glword {**the** boy}"))]
    (is (= "the boy" (:glword w)))
    (is (= [{:start 0 :end 3 :styles #{:bold}}] (:glword-fmt w))))
  (is (some #(re-find #"isn't closed in it" %) (msgs "\\text a\n\\glword **x")))
  (testing "editing a cell"
    (let [d (doc "\\text a\n\\glword x")
          id (get-in d [:lines 0 :id])
          w (get-in (edit/set-item d id 0 "glword" "*o* boy") [:lines 0 :words 0])]
      (is (= "o boy" (:glword w)))
      (is (= [{:start 0 :end 1 :styles #{:italic}}] (:glword-fmt w))))))

(deftest carried-into-other-rows
  (let [[w] (words "\\text **Mūs**a\n\\morph Mūs-a")]
    (is (= #{} (model/word-styles w)))
    (is (= [#{:bold} #{}] (model/morph-styles w))))
  (let [[w] (words "\\text **Mūsa,**\n\\morph Mūs-a")]
    (is (= #{:bold} (model/word-styles w))))
  (testing "part of a morpheme stays in the text"
    (is (= [#{} #{}] (model/morph-styles (first (words "\\text M**ū**sa\n\\morph Mūs-a")))))))

(deftest prose
  (let [p #(fmt/prose %)]
    (is (= [{:text "a " :styles #{} :link nil} {:text "b" :styles #{:italic} :link nil}]
           (p "a *b*")))
    (testing "an unmatched delimiter is just a character"
      (is (= [{:text "2 * 3 = 6" :styles #{} :link nil}] (p "2 * 3 = 6")))
      (is (= "snake_case word" (apply str (map :text (p "snake_case word"))))))
    (testing "escapes and links"
      (is (= [{:text "*not*" :styles #{} :link nil}] (p "\\*not\\*")))
      (is (= [{:text "see " :styles #{} :link nil} {:text "here" :styles #{:bold} :link "u"}]
             (p "see [**here**](u)"))))))
