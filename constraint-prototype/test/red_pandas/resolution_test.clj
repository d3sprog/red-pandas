(ns red-pandas.resolution-test
  (:require
   [red-pandas.resolution :as rs]
   [clojure.test :as t]))

;; (def rules [[(pred :father :john :mary)]
;;             [(pred :father :john :alice)]
;;             [(pred :mother :susan :mary)]
;;             [(pred :mother :susan :alice)]
;;             (fresh [x y]
;;               [(pred :parent x y)
;;                (pred :father x y)])
;;             (fresh [x y]
;;               [(pred :parent x y)
;;                (pred :mother x y)])])
;; (def query (fresh [a] (pred :father a :mary)))

;; (resolve-pretty query rules :parallel? false)

(t/deftest test-resolution
  (let [rules [[(rs/pred :father :john :mary)]
               [(rs/pred :father :john :alice)]
               [(rs/pred :mother :susan :mary)]
               [(rs/pred :mother :susan :alice)]
               (rs/fresh [x y]
                 [(rs/pred :parent x y)
                  (rs/pred :father x y)])
               (rs/fresh [x y]
                 [(rs/pred :parent x y)
                  (rs/pred :mother x y)])]]
    (t/testing "father(X, Y)"
      (t/is (= [(rs/pred :father :john :mary)
                (rs/pred :father :john :alice)]
              (rs/resolve-pretty (rs/fresh [a b] (rs/pred :father a b)) rules))))

    (t/testing "mother(X, Y)"
      (t/is (= [(rs/pred :mother :susan :mary)
                (rs/pred :mother :susan :alice)]
               (rs/resolve-pretty (rs/fresh [a b] (rs/pred :mother a b)) rules))))
    
    (t/testing "mother(X, :mary)"
      (t/is (= [:susan]
               (rs/resolve-pretty (rs/fresh [a] (rs/pred :mother a :mary)) rules))))))
