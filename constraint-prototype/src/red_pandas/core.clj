(ns red-pandas.core
  (:require
   [clojure.string :as str]
   [tapestry.core :as tap]
   [red-pandas.UnificationException])
  (:import red_pandas.UnificationException))

(defprotocol Unifiable
  (unify-self [self other substitution])
  (substitute-self [self substitution]))

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

(defn substitute [goal substitution]
  (if (unifiable? goal)
    (substitute-self goal substitution)
    goal))

(deftype Fresh-Variable [name]
  Unifiable
  (unify-self [self other substitution]
    (contains? substitution self) (unify (get substitution self) other substitution)
    (contains? substitution other) (unify self (get substitution other) substitution)
    :else (assoc substitution self other))
  (substitute-self [self substitution]
    (get substitution self self)))

(defmethod print-method Fresh-Variable [v ^java.io.Writer w]
  (.write w (.name v)))

;; Variable that has in-python equivalent
(deftype Python-Variable [facts]
  Unifiable
  (unify-self [self other substitution]
    nil))

;; Variable that does not exist in python, and cannot be unified with python predicates.
(deftype Pseudo-Variable [name]
  Unifiable
  )

(deftype Python-Predicate [variables template]
  Unifiable
  (unify-self [self other substitution]
    (if (and (instance? Python-Predicate other)
             (= (.template self) (.template other)))
      (->>
       (map vector (.variables self) (.variables other))
       (reduce (fn [subst [var1 var2]]
                 (if-let [new-subst (unify var1 var2 subst)]
                   new-subst
                   (throw (UnificationException. "variable fail predicate")))) substitution))))
  (substitute-self [_ substitution]
    (let [subst-vars (map #(substitute % substitution) variables)]
      (when (some #(instance? Pseudo-Variable %) subst-vars)
        (throw (UnificationException. "python pred pseudo")))
      (if (every? #(instance? Python-Variable %) subst-vars)
        (assert false "Unimplemented call python")
        (Python-Predicate. subst-vars template)))))

(defn python [str]
  (let [variables (->> str
                       (re-seq #"\$(\w+)")
                       (map second)
                       (map #(Fresh-Variable. %)))]
    (Python-Predicate. variables str)))

(defn DataFrame [d]
  (python "$d.type_str == data_frame"))


(deftype Predicate [name variables]
  Unifiable
  (unify-self [self other substitution]
    (if (and (instance? Predicate other)
             (== (count (.variables self)) (count (.variables other))))
      (->> (map vector (.variables self) (.variables other))
           (concat [[(.name self) (.name other)]])
           (reduce (fn [subst [var1 var2]]
                     (assert (map? subst))
                     (if-let [new-subst (unify var1 var2 subst)]
                       new-subst
                       (throw (UnificationException. (str "predicate unif fail" var1 var2))))) substitution))
      (throw (UnificationException. "cannot unify predicate with other")))
    )
  (substitute-self [_ substitution]
    (Predicate. (substitute name substitution) (map #(substitute % substitution) variables))))

(defn predicate? [p]
  (instance? Predicate p))

(defmethod print-method Predicate [p ^java.io.Writer w]
  (.write w (str (.name p) "("
                 (str/join ", " (map print-str (.variables p))) ")")))

(defn variable? [v]
  (instance? Fresh-Variable v))

(defn resolve-goal
  "Returns list of substitutions"
  [[goal & rest-goals] rules substitution]
  (try 
    (->> rules
         (map (fn [[head & body]]
                (if-let [sub (unify goal head substitution)]
                  [(concat (map #(substitute % sub) body)
                           rest-goals) sub] 
                  nil)))
         (filter identity)
         (doall))
    (catch UnificationException _
      [])))

(defn transitive-get [substitution key]
  (if-let [value (get substitution key)]
    (if (variable? value)
      (recur substitution value)
      value)
    nil))
(defn cleanup-substitution [substitution]
  (->> substitution
       (keys)
       (map (fn [key] [key (transitive-get substitution key)]))
       (into {})))

(defn resolve-goals
  ([goal rules] (resolve-goals [goal] rules {}))
  ([initial-goals rules initial-substitution]
   (loop [stack [[initial-goals initial-substitution]]
          results []]
     (if-not (empty? stack)
       (let [[[goals substitution] & new-stack] stack]
         (if-not (empty? goals)
           (recur (concat new-stack (resolve-goal goals rules substitution)) results)
           (recur new-stack (conj results substitution))))
       (map cleanup-substitution results)))))

(defn resolve-goals-parallel
  ([goal rules] (resolve-goals-parallel [goal] rules {}))
  ([initial-goals rules initial-substitution]
   (let [stack (atom [[initial-goals initial-substitution]])
         results (atom [])
         tasks (atom [])]
     (while (or (seq @stack) (seq (swap! tasks (partial filter tap/alive?))))
       (let [[old _new] (swap-vals! stack rest)]
         (when (seq old)
           (let [[[goals substitution] & _] old]
             (if-not (empty? goals)
               (swap! tasks conj
                      (tap/fiber
                        (swap! stack concat (resolve-goal goals rules substitution))))
               (swap! results conj substitution))))))
     @results)))

(defn resolve-pretty [goal rules & {:keys [parallel?]
                                    :or {parallel? false}}]
  (->> (if parallel?
         (resolve-goals-parallel goal rules)
         (resolve-goals goal rules))
       (map #(substitute goal %))))

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
(def query (fresh [a b x] (pred x a b)))

(resolve-pretty query rules :parallel? true)

;; rule: name(vars), [goals(vars)...]
;; goal: vals

;; goal has three states
;; unresolved
;; resolved
;; bail - unresolvable


