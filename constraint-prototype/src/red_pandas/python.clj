(ns red-pandas.python
  (:refer-clojure :exclude [get])
  (:require
   [red-pandas.core :as rc]
   [red-pandas.resolution :as rs]
   [red-pandas.UnificationException]
   [clojure.string :as str])
  (:import [red_pandas UnificationException]))

;; Variable that has in-python equivalent
(defrecord Python-Variable [python-name])

;; Variable that does not exist in python, and cannot be unified with python predicates.
(defrecord Pseudo-Variable [name])

(defrecord Python-Predicate [variables template]
  rc/Unifiable
  (unify-self [self other substitution]
    (cond
      (and (instance? Python-Predicate other)
           (= (.template self) (.template other)))
      (->>
       (map vector (.variables self) (.variables other))
       (reduce (fn [subst [var1 var2]]
                 (if-let [new-subst (rc/unify var1 var2 subst)]
                   new-subst
                   (throw (UnificationException. "variable fail predicate")))) substitution))

      :else (throw (UnificationException. (print-str "Cannot unify with non-python predicate"
                                                     (count (.variables self)) (count (.variables other))
                                                     other (and (rc/predicate? other)
                                                                                                                                                              (== (count (.variables self)) (count (.variables other)))))))))
  (substitute-self [_ substitution]
    (let [subst-vars (map #(rc/substitute % substitution) variables)]
      (when (some #(instance? Pseudo-Variable %) subst-vars)
        (throw (UnificationException. "python pred pseudo")))
      (if (every? #(instance? Python-Variable %) subst-vars)
        (assert false "Unimplemented call python")
        (Python-Predicate. subst-vars template)))))

(defmethod print-method Python-Predicate [p ^java.io.Writer w]
  (.write w (str "\"" (.template p) "\"" "("
                 (str/join ", " (map print-str (.variables p))) ")")))

(defn python [str & vars]
  (let [variables (->> str
                       (re-seq #"\$(\w+)")
                       (map second)
                       (map #(red_pandas.core.Fresh-Variable. %)))]
    ;;; TODO: actually bind the variables
    (Python-Predicate. vars str)))


(defn DataFrame [obj]
  (python "$d.type_str == data_frame" obj))

(defn Series [obj]
  (python "$s.type_str == series" obj))

(defn Dict [obj]
  (python "$s.type_str == dict" obj))

(defn List [obj]
  (python "$s.type_str == list" obj))

(defn Index [obj] [])


(defn index [obj out] 
  [[(rs/pred :index obj out) (DataFrame obj) (Index out)]])
(defn columns [obj out]
  [[(rs/pred :columns obj out) (index obj out)]])

(defn get [obj ix out]
  [[(rs/pred :get obj ix out)
    (DataFrame obj)
    (Dict ix)
    :fail]
   ;; TODO
   ;; [(rs/pred :get obj ix out)
   ;;  (DataFrame obj)
   ;;  (Loc_Info ix)
   ;;  :fail]
   ;; TODO
   ;; [(rs/pred :get obj ix out)
   ;;  (DataFrame obj)
   ;;  (GroupBy ix)
   ;;  :fail]
   [(rs/pred :get obj ix out)
    (Series obj)
    (Dict ix)
    :fail]
   ;; TODO
   ;; [(rs/pred :get obj ix out)
   ;;  (Series obj)
   ;;  (Loc_Info ix)
   ;;  :fail]
   ;; TODO
   ;; [(rs/pred :get obj ix out)
   ;;  (Series obj)
   ;;  (GroupBy ix)
   ;;  :fail]
   [(rs/pred :get obj ix out)
    ()]
   [(rs/pred :get obj ix out)]
   [(rs/pred :get obj ix out)]])
