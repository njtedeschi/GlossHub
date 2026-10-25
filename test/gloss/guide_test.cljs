(ns gloss.guide-test
  (:require-macros [gloss.inline :as inline])
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gloss.app.markdown-pages :as md-pages]
            [gloss.app.ui :as ui]
            [gloss.parse :as parse]
            [gloss.tiers :as tiers]))

;; The Guide's groups; markdown_test checks its pages as Markdown pages.

(deftest pages
  (doseq [{:keys [title pages]} md-pages/groups]
    (is (seq title))
    (is (seq pages) title))
  (let [guide-pages (mapcat :pages md-pages/groups)]
    (is (apply distinct? (map :slug guide-pages)))
    (doseq [p guide-pages]
      (is (= p (md-pages/by-slug (:slug p))))
      (is (= p (md-pages/by-address (str "guide-" (:slug p)))))
      (is (some #{p} (:pages (md-pages/group-of p))) (:slug p)))))

(deftest every-markdown-file-is-listed
  ;; A file under pages/ that gloss.app.markdown-pages doesn't list isn't shown.
  (is (= (set (cons "about" (map :slug (mapcat :pages md-pages/groups))))
         (set (for [path (inline/listing "pages")
                    :let [[_ name] (re-find #"([^/]+)\.md$" path)]
                    :when name]
                name)))))

(def ^:private header
  "The header's commands."
  ["title" "about" "abbr" "color" "dir" "lang" "order" "glosshubformat"])

(deftest the-commands-page-lists-every-command
  (let [body (:body (md-pages/by-slug "commands"))
        listed? (fn [c] (or (str/includes? body (str "`\\" c "`"))
                            (str/includes? body (str "`\\" c " "))))]
    (doseq [{:keys [name aliases kind]} tiers/tiers
            c (concat [(tiers/command name)]
                      (if (tiers/text-options name) (map #(str "text[" % "]") aliases) aliases)
                      ;; Version 1 wrote the other text tiers with commands of their own.
                      (when (tiers/text-options name) [name])
                      (when (= :segmentation kind) [(str name "[translit]")]))]
      (is (listed? c) c))
    (doseq [c (concat ["igt" "ex" "section" "end"] header)]
      (is (listed? c) c))
    (testing "the header commands it checks for are the parser's"
      (doseq [c header]
        (is (not-any? #(str/starts-with? (:msg %) "Unknown")
                      (:diagnostics (parse/parse-document (str "\\" c " x\n\\igt\n\\text a"))))
            c)))))

(deftest the-input-format-reference-lists-every-command
  ;; The reference beside the source editor (gloss.app.ui/syntax-help), as
  ;; format version 2 writes the commands: a text tier as an option of \text.
  (let [help (apply str (filter string? (flatten (ui/syntax-help))))]
    (doseq [{:keys [name aliases kind]} tiers/tiers
            s (concat (if (tiers/text-options name)
                        (map #(str "[" % "]") (cons name aliases))
                        (map #(str "\\" %) (cons name aliases)))
                      (when (= :segmentation kind) [(str "\\" name "[translit]")]))]
      (is (str/includes? help s) s))
    (doseq [c (concat ["igt" "ex" "section" "end"] header)]
      (is (str/includes? help (str "\\" c)) c))))

(deftest the-input-format-reference-links-to-the-commands-page
  (let [hrefs (for [x (tree-seq vector? seq (ui/syntax-help))
                    :when (and (vector? x) (= :a (first x)))]
                (:href (second x)))]
    (is (= ["commands"] (map #(:slug (md-pages/by-address (subs % 1))) hrefs)))))
