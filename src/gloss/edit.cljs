(ns gloss.edit
  "Edits: pure functions from a document to a document, used by the
  website's editors (a cell, an IGT's source, a note's text)."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.notes :as notes]))

(defn update-igt
  "The document with IGT `igt-id` replaced by (f igt & args)."
  [doc igt-id f & args]
  (if-let [i (model/igt-index doc igt-id)]
    (apply update-in doc [:igts i] f args)
    doc))

(defn- set-translit
  "A word with its transliteration (plain text) set, divided again by its
  \\morph[translit] entry."
  [word text]
  (let [core (chars/strip-punct (chars/hide-escapes text))
        result (morph/segment-word core (some-> (model/morph-src word "translit") chars/hide-escapes))]
    (update-in word [:surfaces "translit"] assoc
               :segs (chars/restore-escapes (:segs result))
               :morph-problems (morph/segment-problems result))))

(defn set-entry
  "Sets word `i`'s entry in tier `tier` (\\text[translit] or a
  gloss.tiers/word-entries tier) from the text typed for it."
  [doc igt-id i tier text]
  (update-igt doc igt-id update-in [:words i]
              (fn [word]
                (if (= tier "translit")
                  (set-translit word text)
                  (let [[fields] (model/read-entry tier (chars/hide-escapes text))]
                    (model/put-fields word (chars/restore-escapes fields)))))))

(defn set-free
  "Sets an IGT's free translation."
  [doc igt-id text]
  (update-igt doc igt-id assoc :free (str/trim text)))

(defn set-note
  "Sets the text of the note with key `k`."
  [doc k text]
  (if-let [[_ path] (notes/find-note doc k)]
    (assoc-in doc (conj path :text) (str/trim text))
    doc))

(defn- in-section
  "IGTs (just parsed) as IGTs of `section`, where they are put."
  [section new-igts]
  (map #(cond-> (dissoc % :section) section (assoc :section section)) new-igts))

(defn replace-igt
  "Replaces an IGT with parsed IGTs (a block can yield several, each with
  the notes written under it)."
  [doc igt-id new-igts]
  (let [i (model/igt-index doc igt-id)
        igts (:igts doc)]
    (assoc doc :igts (vec (concat (subvec igts 0 i)
                                  (in-section (get-in igts [i :section]) new-igts)
                                  (subvec igts (inc i)))))))

(defn insert-igts
  "Inserts IGTs after IGT `after-idx` (or at the end), in that IGT's
  section."
  [doc after-idx new-igts]
  (let [igts (:igts doc)
        at (if after-idx (inc after-idx) (count igts))]
    (assoc doc :igts (vec (concat (subvec igts 0 at)
                                  (in-section (:section (get igts (dec at))) new-igts)
                                  (subvec igts at))))))

(defn delete-igt
  "Removes IGT `igt-id`."
  [doc igt-id]
  (update doc :igts (fn [ls] (vec (remove #(= igt-id (:id %)) ls)))))
