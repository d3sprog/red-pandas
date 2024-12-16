(ns red-pandas.resolution 
  (:require
   [red-pandas.core :as rp]
   [red-pandas.UnificationException]
   [manifold.stream :as s]
   [tapestry.core :as tap])
  (:import [red_pandas UnificationException]
           [red_pandas.core
            Fresh-Variable
            Predicate]))

(defn resolve-goal
  "Returns list of substitutions"
  [[goal & rest-goals] rules substitution]
  (try 
    (->> rules
         (map (fn [[head & body]]
                (if-let [sub (rp/unify goal head substitution)]
                  [(concat (mapv #(rp/substitute % sub) body)
                           rest-goals) sub] 
                  nil)))
         (filter identity)
         (doall))
    (catch UnificationException _e
      [])))

(defn transitive-get [substitution key]
  (if-let [value (get substitution key)]
    (if (rp/fresh-variable? value)
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

(defn resolve-goal-parallel [rules [goals substitution]]
  (if-not (empty? goals)
    (do
      (->> (resolve-goal goals rules substitution)
           (s/put-all!))
      nil)
    substitution))

(defn resolve-goals-parallel
  ([goal rules] (resolve-goals-parallel [goal] rules {}))
  ([initial-goals rules initial-substitution]
   (let [tasks (s/stream)
         results (atom [])]
     (s/put! tasks [initial-goals initial-substitution])
     ;;(while (s/drained?))
     (->> (tap/asyncly (partial resolve-goal-parallel rules) tasks)
          )
     ;; (swap! tasks conj
     ;;        (resolve-goal-parallel rules tasks results))
     (while (seq (swap! tasks (partial filter tap/alive?)))
       )
     @results)))

(defn resolve-pretty [goal rules & {:keys [parallel?]
                                    :or {parallel? false}}]
  (->> (if parallel?
         (resolve-goals-parallel goal rules)
         (resolve-goals goal rules))
       (map #(rp/substitute goal %))))

(defmacro fresh
  "Recieves a list of symbols to bind as fresh variables and a body"
  [fresh-vars & body]
  (let [var-binds (mapcat (fn [fresh-var] [fresh-var `(Fresh-Variable. (str "?" '~fresh-var))]) fresh-vars)]
    `(let [~@var-binds]
       ~@body)))

(defn pred [name & params]
  (Predicate. name params))
