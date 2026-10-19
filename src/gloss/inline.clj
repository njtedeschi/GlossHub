(ns gloss.inline
  "Compile-time helpers.")

(defmacro file
  "The contents of a file (path relative to the project root), embedded in
  the build."
  [path]
  (slurp path))
