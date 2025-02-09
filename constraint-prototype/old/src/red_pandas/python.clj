(ns red-pandas.python
  (:refer-clojure :exclude [get])
  (:require
   [red-pandas.core :as rc]
   [red-pandas.resolution :as rs]
   [red-pandas.UnificationException]
   [clojure.string :as str])
  (:import
   [red_pandas.core
    Fail
    Bail]))

(defrecord Python-Environment [])

(defn eval-python [code env]
  (println "Evaluating" code)
  ;; TODO
  true)

(def ^:dynamic *python-env*
  "Dynamic variable binding current python environment, for convenience."
  nil)

;; Variable that has in-python equivalent
(defrecord Python-Variable [python-name env])

;; Variable that does not exist in python, and cannot be unified with python predicates.
(defrecord Pseudo-Variable [name])

(defmethod print-method Python-Variable [p ^java.io.Writer w]
  (.write w (str "python/" (.python-name p))))


(defrecord Python-Predicate [variables template env]
  rc/Unifiable
  (unify-self [self other substitution]
    ;; TODO correct subst
    ;; this does not bail
    ;; we can only fail if eval fails
    ;; otherwise just return the new subst
    ;; evald -> pseudo
    (cond (some #(instance? Pseudo-Variable %) variables) nil ;; bail
          (every? #(instance? Python-Variable %) variables)
          
          (if (eval-python template env) ;; if python evals to true
            (assoc substitution self other) ;; return substitution
            nil) ;; else bail
          
          #_(and (instance? Python-Predicate other) ;; unify predicates
               (= (.template self) (.template other)))
          #_(->>
           (map vector (.variables self) (.variables other))
           (reduce (fn [subst [var1 var2]]
                     (if-let [new-subst (rc/unify var1 var2 subst)]
                       new-subst
                       (reduced nil))) substitution))
          :else nil))
  (substitute-self [_ substitution]
    (cond
      (and (every? #(instance? Python-Variable %) variables)
           (eval-python template env)) true
      (every? #(instance? Python-Variable %) variables) (Bail.)
      (some #(instance? Pseudo-Variable %) variables) (Bail.)
      :else (Python-Predicate. (map #(rc/substitute % substitution) variables) template env))))

(defmethod print-method Python-Predicate [p ^java.io.Writer w]
  (.write w (str "\"" (.template p) "\"" "("
                 (str/join ", " (map print-str (.variables p))) ")")))

(defn python [str & vars]
  (assert *python-env* "Environment bound")
  (let [variables (->> str
                       (re-seq #"\$(\w+)")
                       (map second)
                       (map #(red_pandas.core.Fresh-Variable. %)))]
    ;;; TODO: actually bind the variables
    (Python-Predicate. vars str *python-env*)))


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
    (Fail. "Cannot index DataFrame with Dict")]
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
    (Fail. "Cannot index Series with Dict")]
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
