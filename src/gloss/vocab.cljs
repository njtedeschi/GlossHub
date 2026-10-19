(ns gloss.vocab
  "The site-wide glossing vocabulary: Leipzig Glossing Rules standard
  abbreviations plus a few common extras. Documents can add to or override
  these with `\\abbr`."
  (:require [clojure.string :as str]))

(def leipzig
  "The standard abbreviations of the Leipzig Glossing Rules, by tag."
  {"1" "first person"
   "2" "second person"
   "3" "third person"
   "A" "agent-like argument of canonical transitive verb"
   "ABL" "ablative"
   "ABS" "absolutive"
   "ACC" "accusative"
   "ADJ" "adjective"
   "ADV" "adverb(ial)"
   "AGR" "agreement"
   "ALL" "allative"
   "ANTIP" "antipassive"
   "APPL" "applicative"
   "ART" "article"
   "AUX" "auxiliary"
   "BEN" "benefactive"
   "CAUS" "causative"
   "CLF" "classifier"
   "COM" "comitative"
   "COMP" "complementizer"
   "COMPL" "completive"
   "COND" "conditional"
   "COP" "copula"
   "CVB" "converb"
   "DAT" "dative"
   "DECL" "declarative"
   "DEF" "definite"
   "DEM" "demonstrative"
   "DET" "determiner"
   "DIST" "distal"
   "DISTR" "distributive"
   "DU" "dual"
   "DUR" "durative"
   "ERG" "ergative"
   "EXCL" "exclusive"
   "F" "feminine"
   "FOC" "focus"
   "FUT" "future"
   "GEN" "genitive"
   "IMP" "imperative"
   "INCL" "inclusive"
   "IND" "indicative"
   "INDF" "indefinite"
   "INF" "infinitive"
   "INS" "instrumental"
   "INTR" "intransitive"
   "IPFV" "imperfective"
   "IRR" "irrealis"
   "LOC" "locative"
   "M" "masculine"
   "N" "neuter"
   "NEG" "negation, negative"
   "NMLZ" "nominalizer/nominalization"
   "NOM" "nominative"
   "OBJ" "object"
   "OBL" "oblique"
   "P" "patient-like argument of canonical transitive verb"
   "PASS" "passive"
   "PFV" "perfective"
   "PL" "plural"
   "POSS" "possessive"
   "PRED" "predicative"
   "PRF" "perfect"
   "PRS" "present"
   "PROG" "progressive"
   "PROH" "prohibitive"
   "PROX" "proximal/proximate"
   "PST" "past"
   "PTCP" "participle"
   "PURP" "purposive"
   "Q" "question particle/marker"
   "QUOT" "quotative"
   "RECP" "reciprocal"
   "REFL" "reflexive"
   "REL" "relative"
   "RES" "resultative"
   "S" "single argument of canonical intransitive verb"
   "SBJ" "subject"
   "SBJV" "subjunctive"
   "SG" "singular"
   "TOP" "topic"
   "TR" "transitive"
   "VOC" "vocative"})

(def extras
  "Common abbreviations outside the Leipzig list, mostly for classical
  languages."
  {"ACT" "active"
   "AOR" "aorist"
   "CSTR" "construct state"
   "GER" "gerund"
   "GERV" "gerundive"
   "IMPF" "imperfect"
   "MID" "middle"
   "OPT" "optative"
   "PLPRF" "pluperfect"
   "SUP" "supine"})

(def defaults
  "The site-wide vocabulary: the Leipzig abbreviations and the extras."
  (merge leipzig extras))

(defn grammatical?
  "Whether a gloss element is a grammatical label. By the Leipzig
  convention, labels are written in capitals (with person and number
  digits: 3SG), lexical glosses in lowercase."
  [element]
  (boolean (re-matches #"[0-9]*[A-Z][0-9A-Z]*|[0-9]+" element)))

(defn gloss-elements
  "Splits one morpheme's gloss into its meta-language elements, e.g.
  \"ABL.N.SG\" -> [\"ABL\" \"N\" \"SG\"]. Lexical glosses written with spaces
  or periods (\"so many\", \"come.out\") stay together."
  [gloss]
  (if (str/blank? gloss)
    []
    (let [parts (str/split gloss #"[.:;]")]
      ;; Re-join runs of lexical parts so "come.out" is one element.
      (->> parts
           (partition-by grammatical?)
           (mapcat (fn [run]
                     (if (grammatical? (first run))
                       run
                       [(str/join "." run)])))
           (remove str/blank?)
           vec))))

(defn lookup
  "Full name of an abbreviation, or nil if unknown. Composite person+number
  labels such as 1SG or 3PL are decomposed."
  [vocab tag]
  (or (get vocab tag)
      (when-let [[_ person rest] (re-matches #"([123]+)([A-Z]+)" tag)]
        (let [names (keep #(get vocab (str %)) person)
              rest-name (get vocab rest)]
          (when (and rest-name (= (count names) (count person)))
            (str (str/join "/" names) " " rest-name))))))

(defn document-vocab
  "The vocabulary in effect for a document."
  [doc]
  (merge defaults (:abbrevs doc)))

;; ---------------------------------------------------------------------------
;; Parts of speech
;;
;; Site-wide like the glossing vocabulary. Each class and subclass has the
;; abbreviation it displays as, a full name, and other spellings accepted on
;; input (all matched case-insensitively). Articles and numerals are
;; subclasses of determiners and adjectives that get their own top level.

(def pos-classes
  "The part-of-speech classes."
  [{:abbr "N"    :name "noun"          :aliases ["n" "noun"]}
   {:abbr "Pro"  :name "pronoun"       :aliases ["pro" "pron" "pronoun"]}
   {:abbr "V"    :name "verb"          :aliases ["v" "vb" "verb"]}
   {:abbr "Ptcp" :name "participle"    :aliases ["ptcp" "participle"]}
   {:abbr "Adj"  :name "adjective"     :aliases ["adj" "adjective"]}
   {:abbr "Adv"  :name "adverb"        :aliases ["adv" "adverb"]}
   {:abbr "Prep" :name "preposition"   :aliases ["prep" "preposition"]}
   {:abbr "Conj" :name "conjunction"   :aliases ["conj" "conjunction"]}
   {:abbr "Int"  :name "interjection"  :aliases ["int" "intj" "interj" "interjection"]}
   {:abbr "Det"  :name "determiner"    :aliases ["det" "determiner"]}
   {:abbr "Art"  :name "article"       :aliases ["art" "article"]}
   {:abbr "Comp" :name "complementizer" :aliases ["comp" "complementizer" "complementiser"]}
   {:abbr "Num"  :name "numeral"       :aliases ["num" "numeral"]}
   {:abbr "Part" :name "particle"      :aliases ["part" "particle" "prt"]}])

(def pos-subclasses
  "The subclasses a class can take after a period (V.trans)."
  [{:abbr "trans"  :name "transitive"      :aliases ["t" "tr" "trans" "transitive"]}
   {:abbr "intr"   :name "intransitive"    :aliases ["i" "intr" "intrans" "intransitive"]}
   {:abbr "ditr"   :name "ditransitive"    :aliases ["ditr" "ditrans" "ditransitive"]}
   {:abbr "aux"    :name "auxiliary"       :aliases ["aux" "auxiliary"]}
   {:abbr "cop"    :name "copula"          :aliases ["cop" "copula" "copular"]}
   {:abbr "quant"  :name "quantifier"      :aliases ["q" "quant" "quantifier"]}
   {:abbr "dem"    :name "demonstrative"   :aliases ["dem" "demonstrative"]}
   {:abbr "poss"   :name "possessive"      :aliases ["poss" "possessive"]}
   {:abbr "pers"   :name "personal"        :aliases ["pers" "personal"]}
   {:abbr "rel"    :name "relative"        :aliases ["rel" "relative"]}
   {:abbr "interr" :name "interrogative"   :aliases ["interr" "interrogative" "wh"]}
   {:abbr "refl"   :name "reflexive"       :aliases ["refl" "reflexive"]}
   {:abbr "indef"  :name "indefinite"      :aliases ["indef" "indefinite"]}
   {:abbr "def"    :name "definite"        :aliases ["def" "definite"]}
   {:abbr "card"   :name "cardinal"        :aliases ["card" "cardinal"]}
   {:abbr "ord"    :name "ordinal"         :aliases ["ord" "ordinal"]}
   {:abbr "coord"  :name "coordinating"    :aliases ["coord" "coordinating"]}
   {:abbr "subord" :name "subordinating"   :aliases ["sub" "subord" "subordinating"]}
   {:abbr "prop"   :name "proper"          :aliases ["prop" "proper"]}])

(defn- alias-index [entries]
  (into {} (for [e entries, a (:aliases e)] [(str/lower-case a) e])))

(def ^:private class-index (alias-index pos-classes))
(def ^:private subclass-index (alias-index pos-subclasses))

(defn parse-pos
  "Reads one part-of-speech tag such as \"Verb.trans\" or \"det.quantifier\"
  (subclasses after .):
  {:class {:text :entry} :subs [{:text :entry} ...]}. Entries are nil for
  spellings the vocabulary doesn't know."
  [s]
  (let [[c & subs] (str/split (str/trim s) #"\.")]
    {:class {:text c :entry (get class-index (str/lower-case c))}
     :subs (vec (for [sub subs :when (not (str/blank? sub))]
                  {:text sub :entry (get subclass-index (str/lower-case sub))}))}))

(defn pos-parts
  "A word's tag split at clitic boundaries: \"Pro=Conj\" -> two tags."
  [s]
  (if (str/blank? s) [] (mapv parse-pos (str/split (str/trim s) #"="))))
