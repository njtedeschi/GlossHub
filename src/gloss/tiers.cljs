(ns gloss.tiers
  "The tiers of the input format, in one table. A tier is a kind of field
  in an IGT's source (`\\glmorph …`, docs/format.md §5): a text tier writes
  the IGT's words in full, a segmentation tier divides a text tier's words
  into units, an annotation tier analyses units, and commentary isn't
  aligned with the words. Most tiers are shown as a row, a cell for each
  word. Each tier is known by its standard name, which is what the site
  writes (`command`: a text tier other than \\text is written \\text[name]);
  its aliases are accepted on input only. What is arbitrary about a tier
  (which characters it reads, how it takes formatting) is a value here
  rather than code elsewhere.")

(def ascii-punctuation
  "The keyboard's symbols. A tier reads some as plain characters and some as
  its markup; the rest are reserved there (docs/format.md §4.3)."
  (set "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"))

(def tiers
  "Each tier:
  :name        the standard name (the tier's key everywhere)
  :aliases     other names accepted on input
  :kind        :text (the IGT's words, written in full), :segmentation (a
               text tier's words, divided into units), :annotation (an
               analysis of units) or :commentary (not aligned)
  :unit        what its entries or marks go with: :word, :syntactic-word,
               :morpheme or :syllable (docs/format.md §3.1; a description
               of the tier, which no code reads)
  :row         its label when it is shown as a row (Row order, \\order), in
               the default order
  :plain       ASCII punctuation read as itself, or :all
  :markup      ASCII punctuation read as the tier's syntax
  :markup-kept? whether the stored value keeps its markup (\\pos: V.trans), so
               writing it out doesn't escape it
  :formatting  :own (written in the tier), :from-text (the text's, carried:
               a whole word's, and a morpheme's where the tier has morphemes),
               :own+from-text (its own where it has any, else the text's), or
               :none (gloss.model/styles-from-text)
  :prose?      read as prose (docs/format.md §6), not split into entries
  :delimiters  for a transcription, how it is enclosed on the page
  The text tiers other than \\text are written \\text with their name as
  the option (\\text[translit]); their names and aliases are their commands
  before format version 2, which are still read."
  [{:name "text" :aliases ["tx"] :kind :text :unit :word :row "Text"
    :plain ".,;:\"'!?-/()" :formatting :own}
   {:name "translit" :aliases ["tl"] :kind :text :unit :word :row "Transliteration"
    :plain ".,;:\"'!?-/()" :formatting :from-text}
   ;; \text[ipa] is a catch-all for texts that don't distinguish phonemic
   ;; and phonetic transcription.
   {:name "ipa" :kind :text :unit :word :row "IPA transcription" :plain :all :formatting :none
    :delimiters ["/" "/"]}
   {:name "phonemic" :kind :text :unit :word :row "Phonemic transcription" :plain :all :formatting :none
    :delimiters ["/" "/"]}
   {:name "phonetic" :kind :text :unit :word :row "Phonetic transcription" :plain :all :formatting :none
    :delimiters ["[" "]"]}
   {:name "glmorph" :aliases ["gl"] :kind :annotation :unit :morpheme :row "Morphemic gloss"
    :plain ".:;'[]()<>_" :markup "-=~" :formatting :from-text}
   {:name "glword" :aliases ["wg"] :kind :annotation :unit :word :row "Informal gloss"
    :plain :all :formatting :own+from-text}
   {:name "pos" :kind :annotation :unit :syntactic-word :row "Parts of speech"
    :plain "" :markup ".=" :markup-kept? true :formatting :none}
   {:name "morph" :kind :segmentation :unit :morpheme
    :plain ".,;:!?\"'" :markup "-=~<>[]()" :formatting :none}
   {:name "syl" :kind :segmentation :unit :syllable
    :plain ".,;:!?\"'|" :markup "-_()" :formatting :none}
   {:name "scan" :kind :annotation :unit :syllable :formatting :none}
   {:name "ft" :kind :commentary :prose? true :plain :all :formatting :own}
   {:name "note" :kind :commentary :prose? true :plain :all :formatting :own}])

(def by-name
  "Each tier by its standard name."
  (into {} (map (juxt :name identity) tiers)))

(def canonical
  "Any accepted tier name (lowercase) → the tier's standard name."
  (into {} (for [{:keys [name aliases]} tiers, n (cons name aliases)] [n name])))

(defn standard-name
  "A command's name as written (any case, any alias) as the tier's standard
  name; other commands are returned as they are."
  [s]
  (let [s (.toLowerCase (str s))]
    (get canonical s s)))

(def text-options
  "The options of \\text, each the name of the text tier it writes: the
  transliteration (\\text[translit]) and the transcriptions."
  (set (for [{:keys [name kind]} tiers :when (and (= :text kind) (not= "text" name))] name)))

(def ^:private option-names
  "Each way of writing a text tier as an option (lowercase), to the tier's
  name: its name, and its aliases ([tl] for [translit])."
  (into {} (for [{:keys [name aliases]} tiers
                 :when (text-options name)
                 n (cons name aliases)]
             [n name])))

(defn option
  "An option of \\text, \\morph or \\syl as written, read: in any case, as
  the name of the text tier it names ([tl] is [translit]), and [text], the
  text itself, as no option (format version 1 wrote \\morph[text]). Any
  other option is kept, in lowercase."
  [opt]
  (when opt
    (let [o (.toLowerCase (str opt))]
      (when-not (= "text" o)
        (get option-names o o)))))

(defn command
  "How the tier with key `k` (\"translit\", \"morph[translit]\", \"glmorph\")
  is written, without its backslash: a text tier as \\text with its option
  (\"text[translit]\"), any other tier as its key."
  [k]
  (if (text-options k) (str "text[" k "]") k))

(def segmentation-indent
  "What the site writes before a segmentation tier (\\morph, \\syl), so that
  it stands indented under the text tier it divides."
  "  ")

(defn row-key
  "The key of the tier that `s`, a name in \\order, names: its command
  (`command`, \"text[translit]\", the option read as `option` reads it:
  \"text[tl]\") or any of its names (\"translit\", \"tl\"), in any case.
  Anything else, \"text[text]\" included, names no row: it is kept, in
  lowercase, for gloss.check to flag."
  [s]
  (let [s (.toLowerCase (str s))]
    (if-let [[_ opt] (re-matches #"text\[(.*)\]" s)]
      (let [o (option opt)]
        (if (text-options o) o s))
      (standard-name s))))

(def rows
  "The tiers shown as rows under the words, in their default order."
  (mapv :name (filter :row tiers)))

(defn row-label
  "The label of tier `name` as a row (its name if it has none)."
  [name]
  (get-in by-name [name :row] name))

(def transcriptions
  "The transcriptions: raw IPA, one entry per word, kept whole."
  (mapv :name (filter :delimiters tiers)))

(def word-entries
  "The tiers with one entry per word that the word stores under the tier's
  name (as a keyword): the glosses, the part of speech and the
  transcriptions. The transliteration is a surface instead (gloss.model)."
  (into ["glmorph" "glword" "pos"] transcriptions))

(defn- chars-of [x] (if (string? x) (set (map str x)) x))

(defn reserved?
  "Whether `c` is reserved in tier `name`: ASCII punctuation it reads
  neither as itself nor as markup. A backslash is never reserved: it is an
  escape, or flagged on its own terms."
  [name c]
  (let [{:keys [plain markup] :as tier} (by-name name)]
    (and tier
         (ascii-punctuation c)
         (not= c "\\")
         (not= plain :all)
         (not (contains? (chars-of plain) c))
         (not (contains? (chars-of markup) c)))))

(def escapes
  "The characters escaped when a stored value of tier `name` is written out:
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
  "How tier `name` takes formatting: :own, :from-text, :own+from-text or :none."
  [name]
  (get-in by-name [name :formatting] :none))
