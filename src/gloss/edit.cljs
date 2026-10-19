(ns gloss.edit
  "Edits: pure functions from a document to a document, used by the
  website's editors (a cell, a line's source, a note's text)."
  (:require [clojure.string :as str]
            [gloss.chars :as chars]
            [gloss.model :as model]
            [gloss.morph :as morph]
            [gloss.notes :as notes]))

(defn update-line
  "The document with line `line-id` replaced by (f line & args)."
  [doc line-id f & args]
  (if-let [i (model/line-index doc line-id)]
    (apply update-in doc [:lines i] f args)
    doc))

(defn- set-translit
  "A word with its transliteration (plain text) set, divided again by its
  \\morph[translit] item."
  [word text]
  (let [core (chars/strip-punct (chars/hide-escapes text))
        result (morph/segment-word core (some-> (model/morph-src word "translit") chars/hide-escapes))]
    (update-in word [:surfaces "translit"] assoc
               :segs (chars/restore-escapes (:segs result))
               :morph-problems (morph/segment-problems result))))

(defn set-item
  "Sets word `i`'s item in row `row` (\\translit or a gloss.rows/word-items
  row) from the text typed for it."
  [doc line-id i row text]
  (update-line doc line-id update-in [:words i]
               (fn [word]
                 (if (= row "translit")
                   (set-translit word text)
                   (let [[fields] (model/read-item row (chars/hide-escapes text))]
                     (model/put-fields word (chars/restore-escapes fields)))))))

(defn set-free
  "Sets a line's free translation."
  [doc line-id text]
  (update-line doc line-id assoc :free (str/trim text)))

(defn set-note
  "Sets the text of the note with key `k`."
  [doc k text]
  (if-let [[_ path] (notes/find-note doc k)]
    (assoc-in doc (conj path :text) (str/trim text))
    doc))

(defn- in-section
  "Lines (just parsed) as lines of `section`, where they are put."
  [section new-lines]
  (map #(cond-> (dissoc % :section) section (assoc :section section)) new-lines))

(defn replace-line
  "Replaces a line with parsed lines (a block can yield several, each with
  the notes written under it)."
  [doc line-id new-lines]
  (let [i (model/line-index doc line-id)
        lines (:lines doc)]
    (assoc doc :lines (vec (concat (subvec lines 0 i)
                                   (in-section (get-in lines [i :section]) new-lines)
                                   (subvec lines (inc i)))))))

(defn insert-lines
  "Inserts lines after line `after-idx` (or at the end), in that line's
  section."
  [doc after-idx new-lines]
  (let [lines (:lines doc)
        at (if after-idx (inc after-idx) (count lines))]
    (assoc doc :lines (vec (concat (subvec lines 0 at)
                                   (in-section (:section (get lines (dec at))) new-lines)
                                   (subvec lines at))))))

(defn delete-line
  "Removes line `line-id`."
  [doc line-id]
  (update doc :lines (fn [ls] (vec (remove #(= line-id (:id %)) ls)))))
