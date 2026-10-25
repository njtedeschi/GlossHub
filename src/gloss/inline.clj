(ns gloss.inline
  "Compile-time helpers."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defmacro file
  "The contents of a file (path relative to the project root), embedded in
  the build."
  [path]
  (slurp path))

(defmacro markdown-files
  "The Markdown files <slug>.md in directory `dir` (relative to the project
  root), embedded in the build in the order of `slugs`: [[slug markdown] …].
  A file that isn't there stops the build."
  [dir slugs]
  (vec (for [slug slugs] [slug (slurp (str dir "/" slug ".md"))])))

(defmacro listing
  "The paths of the files under directory `dir` (relative to the project
  root) when the build is made, sorted."
  [dir]
  (vec (sort (for [f (file-seq (io/file dir)) :when (.isFile f)]
               (str/replace (.getPath f) "\\" "/")))))
