(ns red-pandas.python-test
  (:require [red-pandas.resolution :as rs]
            [red-pandas.python :as rp])
  (:import [red_pandas.python Python-Variable]))

(def python-rules [(rs/fresh [x] 
                     [(rs/pred :dataframe x) ;; dataframe(X) :- DataFrame(X).
                      (rp/DataFrame x)])])
(def python-query (rs/fresh [y] (rs/pred :dataframe (Python-Variable. "p")
                                   ;(Pseudo-Variable. "p")
                                   )))
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
