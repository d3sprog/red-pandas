(ns red-pandas.core
  (:require
   [clojure.string :as str]))

(defprotocol Unifiable
  (unify-self [self other substitution])
  (substitute-self [self substitution]))

(defn unifiable? [term]
  (satisfies? Unifiable term))

(defn unify [term1 term2 substitution]
  (cond
    (or (not term1) (not term2)) nil
    (= term1 term2) substitution
    (and (unifiable? term1) (unify-self term1 term2 substitution)) (unify-self term1 term2 substitution)
    (and (unifiable? term2) (unify-self term2 term1 substitution)) (unify-self term2 term1 substitution)
    :else nil))

(defn substitute [goal substitution]
  (if (unifiable? goal)
    (substitute-self goal substitution)
    goal))

(defrecord Fresh-Variable [name]
  Unifiable
  (unify-self [self other substitution]
    (cond 
      (contains? substitution self)   (unify (get substitution self) other substitution)
      (contains? substitution other)  (unify self (get substitution other) substitution )
      :else                           (assoc substitution self other)))
  (substitute-self [self substitution]
    (get substitution self self)))

(defmethod print-method Fresh-Variable [v ^java.io.Writer w]
  (.write w (.name v)))

(defn fresh-variable? [v]
  (instance? Fresh-Variable v))

(defrecord Predicate [name variables]
  Unifiable
  (unify-self [self other substitution]
    (if (and (instance? Predicate other)
             (== (count (.variables self)) (count (.variables other)))
             (= (.name self) (.name other)))
      (->> (map vector (.variables self) (.variables other))
           (reduce (fn [subst [var1 var2]]
                     (if-let [new-subst (unify var1 var2 subst)]
                       new-subst
                       (reduced nil))) substitution))
      nil))
  (substitute-self [_ substitution]
    (Predicate. (substitute name substitution) (map #(substitute % substitution) variables))))

(defmethod print-method Predicate [p ^java.io.Writer w]
  (.write w (str (.name p) "("
                 (str/join ", " (map print-str (.variables p))) ")")))

(defn predicate? [p]
  (instance? Predicate p))
