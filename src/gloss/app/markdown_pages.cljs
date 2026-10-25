(ns gloss.app.markdown-pages
  "The Markdown pages, the site's pages written in prose: every file under
  pages/, embedded at compile time and read by gloss.app.markdown. The
  Guide's pages (pages/guide/<group>/<slug>.md) are in groups by topic;
  About is pages/about.md."
  (:require-macros [gloss.inline :as inline])
  (:require [gloss.app.markdown :as md]))

(def ^:private guide-sources
  "The Guide's groups in order, each with its pages in order, read from
  pages/guide/<group's folder>/<slug>.md: [title [[slug markdown] …]]."
  [["Getting started"
    (inline/markdown-files "pages/guide/getting-started"
                           ["how-the-site-works" "writing-a-text" "tiers-and-rows" "display-options"
                            "editing-a-text" "saving-and-exporting"])]
   ["Interlinear glossing"
    (inline/markdown-files "pages/guide/interlinear-glossing"
                           ["informal-glosses" "morphemes-and-glosses" "glossing-abbreviations" "infixes"
                            "stems-and-roots" "parts-of-speech"])]
   ["Verse"
    (inline/markdown-files "pages/guide/verse" ["scansion"])]
   ["Commentary"
    (inline/markdown-files "pages/guide/commentary" ["free-translations" "notes"])]
   ["Orthography and transcription"
    (inline/markdown-files "pages/guide/orthography-and-transcription"
                           ["text-direction-and-fonts" "transliteration" "ruby" "transcriptions"])]
   ["Text formatting and document structure"
    (inline/markdown-files "pages/guide/formatting-and-structure"
                           ["formatting-and-links" "color-coding" "sublines" "sections"])]
   ["Format reference"
    (inline/markdown-files "pages/guide/format-reference" ["commands" "special-characters"])]])

(defn- page
  "A page read from its Markdown: {:address :title :description :body},
  `address` being where it is shown (its location hash, without the #) and
  the description coming from its front matter."
  [address src]
  (let [{:keys [front-matter title body]} (md/read-page src)]
    {:address address
     :title title
     :description (:description front-matter)
     :body body}))

(def groups
  "The Guide's groups in order: {:title :pages}, each page
  {:address :slug :group :title :description :body}, :group being its
  group's title."
  (mapv (fn [[group pages]]
          {:title group
           :pages (mapv (fn [[slug src]] (assoc (page (str "guide-" slug) src) :slug slug :group group))
                        pages)})
        guide-sources))

(def about
  "The About page."
  (page "about" (inline/file "pages/about.md")))

(def ^:private guide-pages
  "Every page of the Guide, group by group."
  (into [] (mapcat :pages) groups))

(def pages
  "Every Markdown page: the Guide's, group by group, then About."
  (conj guide-pages about))

(defn by-slug
  "The Guide page with this slug, or nil."
  [slug]
  (some #(when (= slug (:slug %)) %) guide-pages))

(defn by-address
  "The Guide page shown at `address` (a location hash without its #), or
  nil."
  [address]
  (some #(when (= address (:address %)) %) guide-pages))

(defn group-of
  "The Guide group a page belongs to."
  [page]
  (some #(when (= (:group page) (:title %)) %) groups))
