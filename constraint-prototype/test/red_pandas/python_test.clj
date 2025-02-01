(ns red-pandas.python-test
  (:require [red-pandas.resolution :as rs]
            [red-pandas.python :as rp])
  (:import [red_pandas.python
            Python-Variable
            Pseudo-Variable
            Python-Environment]))

(binding [rp/*python-env* (Python-Environment.)]
  (def python-rules [(rs/fresh [x] 
                       [(rs/pred :dataframe x) ;; dataframe(X) :- DataFrame(X).
                        (rp/DataFrame x)])])
  (def python-query (rs/fresh [y] (rs/pred :dataframe (Python-Variable. "p" rp/*python-env*)
                                        ;(Pseudo-Variable. "p")
                                           ))))
(binding [red-pandas.core/*unification-trace* true]
  (rs/resolve-goals python-query python-rules))
;; A(B) = "$d.type_str == data_frame"(D)
;; A = "$d.type_str == data_frame"
;; B = D

;; { A = "$d.type_str == data_frame"
;;   B = D }
;;

;; [  ]  [() :- (), ()]
;; (rs/resolve-goals python-query python-rules)
;; (map (fn [substitution]
;;        (map (fn [rule]
;;               (substitute-self rule substitution)) python-rules))
;;      (resolve-goals python-query python-rules))

;; rule: name(vars), [goals(vars)...]
;; goal: vals
