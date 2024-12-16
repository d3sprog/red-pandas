(ns red-pandas.resolution-test
  (:require
   [red-pandas.resolution :as rs]
   [red-pandas.TypeError]
   [red-pandas.core]
   [clojure.test :as t])
  (:import
   [red_pandas TypeError]
   [red_pandas.core Fail]))

(def rules [[(rs/pred :father :john :mary)]
            [(rs/pred :father :john :alice)]
            [(rs/pred :mother :susan :mary)]
            [(rs/pred :mother :susan :alice)]
            (rs/fresh [x y]
              [(rs/pred :parent x y)
               (rs/pred :father x y)])
            (rs/fresh [x y]
              [(rs/pred :parent x y)
               (rs/pred :mother x y)])])
;;(def query (fresh [a] (pred :father a :mary)))

;; (resolve-pretty query rules :parallel? false)


(t/deftest test-resolution
  (t/testing "father(X, Y)"
    (t/is (= [(rs/pred :father :john :mary)
              (rs/pred :father :john :alice)]
             (rs/resolve-pretty (rs/fresh [a b] (rs/pred :father a b)) rules))))

  (t/testing "mother(X, Y)"
    (t/is (= [(rs/pred :mother :susan :mary)
              (rs/pred :mother :susan :alice)]
             (rs/resolve-pretty (rs/fresh [a b] (rs/pred :mother a b)) rules))))
  
  (t/testing "mother(X, :mary)"
    (t/is (= [(rs/pred :mother :susan :mary)]
             (rs/resolve-pretty (rs/fresh [a] (rs/pred :mother a :mary)) rules))))
  (t/testing "fail"
    (t/is (thrown-with-msg? TypeError #"test"
                            (rs/resolve-pretty (Fail. "test") rules)))))
