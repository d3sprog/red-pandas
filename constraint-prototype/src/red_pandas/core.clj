(ns red-pandas.core
  (:require
;;   [clojure.core.logic :as logic]
;;            [clojure.core.async :as a]
;;            [clojure.core.async.impl.protocols]
            [clojure.string :as str]
;;            [clojure.core.async.impl.protocols :as impl]
  )
)

(deftype Python-Predicate [variables template])

(defn python [str]
  (let [variables (->> str
                       (re-seq #"\$(\w+)")
                       (map second)
                       (map symbol))]
    (Python-Predicate. variables str)))

(defn DataFrame [d]
  (python "$d.type_str == data_frame"))

(defprotocol Unifiable
  (unify-self [self other substitution]))

(defn unifiable? [term]
  (satisfies? Unifiable term))

(defn unify [term1 term2 substitution]
  (if (or (not term1) (not term2))
    nil
    (if (= term1 term2)
      substitution
      (if-let [subst (and (unifiable? term1)
                          (unify-self term1 term2 substitution))]
        subst
        (if-let [subst (and (unifiable? term2)
                            (unify-self term2 term1 substitution))]
          subst
          (do
            #_(println "Failed unifying:" term1 term2 "with subst:" substitution)
            nil))))))

(deftype Fresh-Variable [name]
  Unifiable
  (unify-self [self other substitution]
    (contains? substitution self) (unify (get substitution self) other substitution)
    (contains? substitution other) (unify self (get substitution other) substitution)
    :else (assoc substitution self other)))

(defmethod print-method Fresh-Variable [v ^java.io.Writer w]
  (.write w (.name v)))

(deftype Python-Variable [facts]
  Unifiable
  (unify-self [self other substitution]
    nil))


(declare same-predicate?)

(deftype Predicate [name variables]
  Unifiable
  (unify-self [self other substitution]
    (try
      (if (same-predicate? self other)
        (->> (map vector (.variables self) (.variables other))
             (reduce (fn [subst [var1 var2]]
                       (assert (map? subst))
                       (if-let [new-subst (unify var1 var2 subst)]
                         new-subst
                         (throw (ex-info "Ununifiable" {})))) substitution))
        nil)
      (catch clojure.lang.ExceptionInfo _
        nil))))
(defmethod print-method Predicate [p ^java.io.Writer w]
  (.write w (str (.name p) "("
                 (str/join ", " (map print-str (.variables p))) ")")))

(defn same-predicate? [p1 p2]
  (and (instance? Predicate p2)
       (= (.name p1) (.name p2))
       (== (-> p1
               (.variables)
               (count))
           (-> p2
               (.variables)
               (count)))))

(defn variable? [v]
  (instance? Fresh-Variable v))

(defn substitute [goal substitution]
  (assert (map? substitution))
  (cond
    (variable? goal) (get substitution goal goal)
    (vector? goal) (mapv #(substitute % substitution) goal)
    :else goal))

(defn resolve-goal [[goal & rest-goals] rules substitution]
  #_(println "resolving" goal "\nwith rules:" rules "\nin substitution:" substitution)
  (->> rules
       (map (fn [[head & body]]
              #_(println "unifying:" goal "with" head)
              (if-let [sub (unify goal head substitution)]
                (do
                  #_(println "got sub:" sub)
                  [(concat (map #(substitute % sub) body)
                           rest-goals) sub]) 
                nil)))
       (filter identity)))

(defn resolve-goals
  ([goal rules] (resolve-goals [goal] rules {}))
  ([initial-goals rules initial-substitution]
   (loop [stack [[initial-goals initial-substitution]]
          results []]
     #_(println "stack:" stack)
     (if-not (empty? stack)
       (let [[[goals substitution] & new-stack] stack]
         #_(println "solving goals:" goals)
         (if (empty? goals)
           (recur new-stack (conj results substitution))
           (let [resolved (resolve-goal goals rules substitution)]
             #_(println "resolved" resolved (concat new-stack resolved))
             (recur (concat new-stack resolved) results))))
       results))))

(defmacro fresh
  "Recieves a list of symbols to bind as fresh variables and a body"
  [fresh-vars & body]
  (let [var-binds (mapcat (fn [fresh-var] [fresh-var `(Fresh-Variable. (str "?" '~fresh-var))]) fresh-vars)]
    `(let [~@var-binds]
       ~@body)))

(defn pred [name & params]
  (Predicate. name params))

(def rules [[(pred :father :john :mary)]
            [(pred :father :john :alice)]
            [(pred :mother :susan :mary)]
            [(pred :mother :susan :alice)]
            (fresh [x y]
              [(pred :parent x y)
               (pred :father x y)])
            (fresh [x y]
              [(pred :parent x y)
               (pred :mother x y)])])
(def query (fresh [a b] (pred :parent a b)))

(resolve-goals query rules)

(def thread-count 8)


;; rule: name(vars), [goals(vars)...]
;; goal: vals

;; goal has three states
;; unresolved
;; resolved
;; bail - unresolvable


