(ns gloss.rows
  "The rows of the input format, in one table. A row is a kind of line in a
  text's source (`\\glmorph …`); most are drawn as a layer under the words.
  Each row is known by its standard name, which is what the site writes;
  its aliases are accepted on input only. What is arbitrary about a row
  (which characters it reads, how it takes formatting) is a value here
  rather than code elsewhere.")

(def ascii-punctuation
  "The keyboard's symbols. A row reads some as plain characters and some as
  its markup; the rest are reserved there (docs/format.md §4.3)."
  (set "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"))

(def rows
  "Each row:
  :name        the standard name (the row's key everywhere)
  :aliases     other names accepted on input
  :layer       its label when it is a layer under the words (Layers, \\order),
               in the default order
  :aligns      :surface (\\text, \\translit), :word, :morpheme or :line
  :plain       ASCII punctuation read as itself, or :all
  :markup      ASCII punctuation read as the row's syntax
  :markup-kept? whether the stored value keeps its markup (\\pos: V.trans), so
               writing it out doesn't escape it
  :formatting  :own (written in the row), :from-text (the text's, carried:
               a whole word's, and a morpheme's where the row has morphemes),
               :own+from-text (its own where it has any, else the text's), or
               :none (gloss.model/styles-from-text)
  :prose?      text read as prose (docs/format.md §6), not split into items
  :delimiters  for a transcription, how it is enclosed on the page"
  [{:name "text" :aliases ["tx"] :layer "Text" :aligns :surface
    :plain ".,;:\"'!?-/()" :formatting :own}
   {:name "translit" :aliases ["tl"] :layer "Transliteration" :aligns :surface
    :plain ".,;:\"'!?-/()" :formatting :from-text}
   ;; \ipa is a catch-all for texts that don't distinguish phonemic and
   ;; phonetic transcription.
   {:name "ipa" :layer "IPA transcription" :aligns :word :plain :all :formatting :none
    :delimiters ["/" "/"]}
   {:name "phonemic" :layer "Phonemic transcription" :aligns :word :plain :all :formatting :none
    :delimiters ["/" "/"]}
   {:name "phonetic" :layer "Phonetic transcription" :aligns :word :plain :all :formatting :none
    :delimiters ["[" "]"]}
   {:name "glmorph" :aliases ["gl"] :layer "Morphemic gloss" :aligns :morpheme
    :plain ".:;'[]()<>" :markup "-=~" :formatting :from-text}
   {:name "glword" :aliases ["wg"] :layer "Informal gloss" :aligns :word
    :plain :all :formatting :own+from-text}
   {:name "pos" :layer "Parts of speech" :aligns :word
    :plain "" :markup ".=" :markup-kept? true :formatting :none}
   {:name "morph" :aligns :surface
    :plain ".,;:!?\"'" :markup "-=~<>[]()" :formatting :none}
   {:name "syl" :aligns :surface
    :plain ".,;:!?\"'|" :markup "-_()" :formatting :none}
   {:name "scan" :aligns :line :formatting :none}
   {:name "ft" :aligns :line :prose? true :plain :all :formatting :own}
   {:name "note" :aligns :line :prose? true :plain :all :formatting :own}])

(def by-name
  "Each row by its standard name."
  (into {} (map (juxt :name identity) rows)))

(def canonical
  "Any accepted row name (lowercase) → the row's standard name."
  (into {} (for [{:keys [name aliases]} rows, n (cons name aliases)] [n name])))

(defn row-name
  "A name as written (any case, any alias) as the row's standard name;
  other markers are returned as they are."
  [s]
  (let [s (.toLowerCase (str s))]
    (get canonical s s)))

(def layers
  "The layers under each word, in their default order."
  (mapv :name (filter :layer rows)))

(defn layer-label
  "The label of row `name` as a layer under the words (its name if it has
  none)."
  [name]
  (get-in by-name [name :layer] name))

(def transcriptions
  "The transcription rows: raw IPA, one item per word, kept whole."
  (mapv :name (filter :delimiters rows)))

(def word-items
  "The rows with one item per word that the word stores under the row's name
  (as a keyword): the glosses, the part of speech and the transcriptions.
  The transliteration is a surface instead (gloss.model)."
  (into ["glmorph" "glword" "pos"] transcriptions))

(defn- chars-of [x] (if (string? x) (set (map str x)) x))

(defn reserved?
  "Whether `c` is reserved in row `name`: ASCII punctuation it reads
  neither as itself nor as markup. A backslash is never reserved: it is an
  escape, or flagged on its own terms."
  [name c]
  (let [{:keys [plain markup] :as row} (by-name name)]
    (and row
         (ascii-punctuation c)
         (not= c "\\")
         (not= plain :all)
         (not (contains? (chars-of plain) c))
         (not (contains? (chars-of markup) c)))))

(def escapes
  "The characters escaped when a stored value of row `name` is written out:
  the backslash, grouping braces, what it reserves, and its markup unless
  the value keeps it."
  (memoize
   (fn [name]
     (let [{:keys [plain markup markup-kept?]} (by-name name)]
       (into #{"\\" "{" "}"}
             (when-not (= plain :all)
               (remove (into (chars-of plain) (when markup-kept? (chars-of markup)))
                       ascii-punctuation)))))))

(defn formatting
  "How row `name` takes formatting: :own, :from-text, :own+from-text or :none."
  [name]
  (get-in by-name [name :formatting] :none))
