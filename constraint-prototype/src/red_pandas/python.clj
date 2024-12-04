(ns red-pandas.python)

(deftype Python [inputs]
  )

(defn python [str]
  (assert (string? str))
  (println "success " str))
