(ns de.reify.simplekvstore.handler-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [de.reify.simplekvstore.handler :as handler]))

(deftest split-words
  (testing "That it splits a line into words."
    (is (= (handler/split-words "first-word second_word 3rd.word")
           ["first-word" "second_word" "3rd.word"]))))

(deftest handle-get
  (testing "That GET handles missing keys."
    (let [session {:contexts '({})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-get session "sample")]
      (is (= (:response result)
             "Error: Key not found."))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             (:contexts session)))
      (is (= (type (-> result :session :contexts))
             (type (:contexts session))))))
  (testing "That GET picks up values from the shared / root context."
    (let [session {:contexts '({})
                   :root (atom {"sample" "value"})}
          root @(:root session)
          result (handler/handle-get session "sample")]
      (is (= (:response result)
             "value"))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             (:contexts session)))
      (is (= (type (-> result :session :contexts))
             (type (:contexts session))))))
  (testing "That GET picks up values from the top context."
    (let [session {:contexts '({"sample" "new"} {})
                   :root (atom {"sample" "value"})}
          root @(:root session)
          result (handler/handle-get session "sample")]
      (is (= (:response result)
             "new"))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             (:contexts session)))
      (is (= (type (-> result :session :contexts))
             (type (:contexts session)))))))

(deftest handle-set
  (testing "That SET handles new keys."
    (let [session {:contexts '({} {})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-set session "sample" "value")]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" "value"} {})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" "value"} {}))))))
  (testing "That SET handles updated keys."
    (let [session {:contexts '({"sample" "value"} {})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-set session "sample" "new")]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" "new"} {})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" "new"} {}))))))
  (testing "That SET handles keys that exist in inner and outer contexts."
    (let [session {:contexts '({"sample" "value"} {"sample" "old"})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-set session "sample" "new")]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" "new"} {"sample" "old"})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" "new"} {"sample" "old"}))))))
  (testing "That SET handles keys that only exist in outer contexts."
    (let [session {:contexts '({} {"sample" "old"})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-set session "sample" "new")]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" "new"} {"sample" "old"})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" "new"} {"sample" "old"})))))))

(deftest handle-delete
  (testing "That DELETE handles keys that exist in inner and outer contexts."
    (let [session {:contexts '({"sample" "value"} {"sample" "old"})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-delete session "sample")]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" nil} {"sample" "old"})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" nil} {"sample" "old"}))))))
  (testing "That DELETE handles keys that only exist in inner contexts."
    (let [session {:contexts '({"sample" "value"} {})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-delete session "sample")]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" nil} {})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" nil} {}))))))
  (testing "That DELETE handles keys that only exist in outer contexts."
    (let [session {:contexts '({} {"sample" "old"})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-delete session "sample")]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" nil} {"sample" "old"})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" nil} {"sample" "old"})))))))

(deftest handle-commit
  ; cf. test of merge-top-contexts for tests regarding the handling of :contexts.
  (testing "That COMMIT handles keys that exist in multiple contexts."
    (let [session {:contexts '({"sample" "value"} {"sample" "old"} {"sample" "veryold"})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-commit session)]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" "value"} {"sample" "veryold"})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" "value"} {"sample" "veryold"}))))))
  (testing "That COMMIT applies to root when only one context exists."
    (let [session {:contexts '({"sample" "value"})
                   :root (atom {})}
          result (handler/handle-commit session)]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             {"sample" "value"}))
      (is (= (-> result :session :contexts)
             '()))
      (is (= (type (-> result :session :contexts))
             (type '())))))
  (testing "That COMMIT also applies deletions to the root context."
    (let [session {:contexts '({"sample" nil})
                   :root (atom {"sample" "value"})}
          result (handler/handle-commit session)]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             {"sample" nil}))
      (is (= (-> result :session :contexts)
             '()))
      (is (= (type (-> result :session :contexts))
             (type '()))))))

(deftest handle-begin
  (testing "That BEGIN pushes an empty context."
    (let [session {:contexts '({"sample" "value"})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-begin session)]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({} {"sample" "value"})))
      (is (= (type (-> result :session :contexts))
             (type '({} {"sample" "value"})))))))

(deftest handle-rollback
  (testing "That ROLLBACK handles multiple contexts."
    (let [session {:contexts '({"sample" "value"} {"sample" "old"})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-rollback session)]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '({"sample" "old"})))
      (is (= (type (-> result :session :contexts))
             (type '({"sample" "old"}))))))
  (testing "That ROLLBACK handles single contexts."
    (let [session {:contexts '({"sample" "value"})
                   :root (atom {})}
          root @(:root session)
          result (handler/handle-rollback session)]
      (is (nil? (:response result)))
      (is (= @(-> result :session :root)
             root))
      (is (= (-> result :session :contexts)
             '()))
      (is (= (type (-> result :session :contexts))
             (type '()))))))

(deftest handle-exit
  (testing "That EXIT requests to close the connection."
    (let [session {:contexts '({})
                   :root (atom {})}
          result (handler/handle-exit session)]
      (is (true? (-> result :session :close))))))

(deftest merge-top-contexts
  (testing "That it handles multiple contexts."
    (is (= (handler/merge-top-contexts '({"sample" "value"} {"sample" "old"} {"sample" "veryold"}))
           '({"sample" "value"} {"sample" "veryold"}))))
  (testing "That it handles deletion."
    (is (= (handler/merge-top-contexts '({"sample" nil} {"sample" "old"} {"sample" "veryold"}))
           '({"sample" nil} {"sample" "veryold"})))))

(deftest pick-handler
  (testing "That it finds a handler."
    (let [ret (handler/pick-handler ["GET" "a"])]
      (is (nil? (:error ret)))
      (is (some? (:handler ret)))))
  (testing "That it checks the command."
    (let [ret (handler/pick-handler ["UNKNOWN"])]
      (is (= (:error ret)
             "Error: Unknown command: UNKNOWN"))
      (is (nil? (:handler ret)))))
  (testing "That it checks the number of arguments."
    (let [ret (handler/pick-handler ["GET" "a" "2"])]
      (is (= (:error ret)
             "Error: Wrong number of arguments. Expected: 1, got: 2"))
      (is (nil? (:handler ret))))))
