(ns gloss.markdown-test
  (:require [cljs.test :refer [deftest is testing]]
            [gloss.app.markdown :as md]
            [gloss.app.markdown-pages :as md-pages]
            [gloss.app.state :as state]
            [gloss.check :as check]
            [gloss.model :as model]
            [gloss.parse :as parse]))

(deftest markdown
  (is (= [{:type :heading :level 2 :text "Hi"}
          {:type :para :text "one two"}
          {:type :list :items ["a" "b c"]}
          {:type :fence :info "gloss" :text "\\tx a"}]
         (md/blocks "## Hi\n\none\ntwo\n\n- a\n- b\n  c\n\n```gloss\n\\tx a\n```")))
  (is (= ["a " [:code "x"] " " [:em "y"] " " [:strong "z"] " " [:a {:href "#q"} "l"] ""]
         (md/inline "a `x` *y* **z** [l](#q)")))
  (testing "a heading is drawn at its own level"
    (is (= [[:h2 ["A"]] [:h3 ["B"]]] (md/render "## A\n\n### B" nil))))
  (testing "a fence's line can end with a caption"
    (is (= [{:type :fence :info "gloss broken" :caption "Latin (Lehmann 1982: 211)." :text "\\tx a"}]
           (md/blocks "```gloss broken \"Latin (Lehmann 1982: 211).\"\n\\tx a\n```")))
    (is (= [{:source "\\tx a" :broken? false}]
           (md/examples {:body "```gloss \"not broken\"\n\\tx a\n```"}))))
  (testing "a link out of the site opens in a new tab"
    (is (= ["" [:a {:href "https://example.org" :target "_blank" :rel "noopener"} "x"] ""]
           (md/inline "[x](https://example.org)")))))

(deftest reading-a-page
  (is (= {:front-matter {:description "D"} :title "T" :body "\nB"}
         (md/read-page "---\ndescription: D\n---\n\n# T\n\nB")))
  (is (nil? (:title (md/read-page "## Not a title\n\nB")))))

;; The rest checks every Markdown page: the Guide's, and About.

(deftest titles
  ;; One # heading: the page's title, first.
  (doseq [{:keys [address title body]} md-pages/pages]
    (is (seq title) address)
    (is (not-any? #(and (= :heading (:type %)) (= 1 (:level %))) (md/blocks body)) address)))

(deftest descriptions
  ;; What a page says it is about (the Guide's index shows it): short, and
  ;; in plain words (no commands or code).
  (doseq [{:keys [address description body]} md-pages/pages]
    (is (seq description) address)
    (is (<= (count description) 100) address)
    (is (not (re-find #"[\\`]" (str description))) address)
    (is (not (re-find #"(?m)^description:" body)) address)))

(deftest links-lead-to-pages
  ;; An address the site doesn't know is taken for one of the reader's own
  ;; texts (:own), so a link to one leads nowhere.
  (doseq [{:keys [address body]} md-pages/pages
          [_ target] (re-seq #"\]\(#([^)]*)\)" body)]
    (is (not= :own (:page (state/route-of (str "#" target))))
        (str address " links to #" target))))

(deftest examples-parse-cleanly
  ;; In the version the site writes, so an example written with version 1's
  ;; commands is flagged.
  (doseq [p md-pages/pages
          {:keys [source broken?]} (md/examples p)]
    (let [{:keys [doc] :as parsed} (parse/parse-document source model/format-version)
          problems (check/problems parsed)]
      (testing (str (:address p) ":\n" source)
        (is (seq (:igts doc)))
        (if broken?
          (is (seq problems))
          (is (empty? problems)))))))
