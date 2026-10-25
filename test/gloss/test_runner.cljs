(ns gloss.test-runner
  "Runs the unit tests on Node: every namespace required here whose name
  ends in -test. A new test namespace only needs adding to this list."
  (:require [cljs.test :as t]
            [gloss.app-test]
            [gloss.formatting-test]
            [gloss.guide-test]
            [gloss.infix-test]
            [gloss.layout-test]
            [gloss.markdown-test]
            [gloss.morph-test]
            [gloss.notes-test]
            [gloss.parse-test]
            [gloss.reserved-test]
            [gloss.tiers-test]
            [gloss.vocab-test]))

;; A failing test makes the process exit with a failure status, so that CI
;; notices.
(defmethod t/report [::t/default :end-run-tests] [m]
  (when-not (t/successful? m)
    (set! (.-exitCode js/process) 1)))

(defn -main []
  (t/run-all-tests #"gloss\..*-test"))

(set! *main-cli-fn* -main)
