(ns gloss.check-cli
  "Checks .gloss files: lists their problems, or says whether each reads
  the same once written out by the site. A folder stands for every .gloss
  file in it, at any depth.
  node target/check/check.js FILE-OR-FOLDER…
  node target/check/check.js --round-trip FILE-OR-FOLDER…
  The exit status is 1 when a file has a problem, doesn't read the same or
  can't be read, and 2 when there is nothing to check."
  (:require [clojure.data :as data]
            [gloss.check :as check]
            [gloss.english :as english]
            [gloss.model :as model]
            [gloss.parse :as parse]
            [gloss.serialize :as ser]))

(def ^:private fs (js/require "fs"))
(def ^:private path (js/require "path"))

(defn- gloss-files
  "The files an argument names: the file itself, or every .gloss file under
  a folder, in order."
  [arg]
  (if (and (.existsSync fs arg) (.isDirectory (.statSync fs arg)))
    (->> (.readdirSync fs arg #js {:recursive true})
         (filter #(.endsWith % ".gloss"))
         sort
         (mapv #(.join path arg %)))
    [arg]))

(defn- read-file
  "File `f`'s text, or nil after saying why it can't be read."
  [f]
  (try (.readFileSync fs f "utf8")
       (catch :default e
         (println (str f ": can't be read (" (.-message e) ")")))))

(defn- check
  "Prints the problems of file `f`, each with its source line; true if it
  has none."
  [f]
  (when-let [src (read-file f)]
    (let [problems (check/problems (parse/parse-document src))]
      (println (str f ": " (english/counted (count problems) "problem" "problems")))
      (doseq [{:keys [level source-line msg]} (sort-by #(or (:source-line %) 0) problems)]
        (println (str "  " (if (= :error level) "Error" "Warning") (when source-line (str " line " source-line)) ": " msg)))
      (empty? problems))))

(defn- round-trip
  "Whether file `f` reads the same after being written out by the site; if
  not, prints the difference."
  [f]
  (when-let [src (read-file f)]
    (let [doc (:doc (parse/parse-document src))
          written (ser/document-source doc)
          again (:doc (parse/parse-document written))
          [a b] (data/diff (model/without-ids doc) (model/without-ids again))]
      (if (and (nil? a) (nil? b))
        (do (println (str f ": round-trips"))
            true)
        (do (println (str f ": differs after writing out"))
            (println "  read:   " (pr-str a))
            (println "  written:" (pr-str b))
            (println written)
            false)))))

(defn -main
  "Prints the problems of each file named, or, after `--round-trip`, says
  whether each reads the same once written out; sets the exit status."
  [& args]
  (let [rt? (= "--round-trip" (first args))
        files (mapcat gloss-files (if rt? (rest args) args))]
    (if (empty? files)
      (do (println (str "Usage: node target/check/check.js [--round-trip] FILE-OR-FOLDER …\n"
                        "Lists each .gloss file's problems; with --round-trip, says whether it reads the same once written out."))
          (set! (.-exitCode js/process) 2))
      (when-not (every? true? (mapv (if rt? round-trip check) files))
        (set! (.-exitCode js/process) 1)))))

(set! *main-cli-fn* -main)
