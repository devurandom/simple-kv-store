(ns de.reify.simplekvstore.core
  "Parse command line argument and start the server."
  (:require
    [clojure.core.async :refer [close!]]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [de.reify.simplekvstore.server :as server])
  (:gen-class))

(defn pass-if
  "Helper for `some->`: If argument satisfies the predicate return the value, otherwise return nil."
  [pred? v]
  (when (pred? v) v))

(defn getenv-as-int
  "If the environment variable `var-name` is set, parse it as an integer.
  Otherwise return a default value."
  [var-name default]
  (or
    (some->> (System/getenv var-name)
             (pass-if (comp not string/blank?))
             (Integer/parseInt))
    default))

(def cli-options
  [["-p" "--port PORT" "Port number (can be specified using PORT env var)"
    :default 3030
    :default-fn #(getenv-as-int "PORT" (:port %))
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [errors options summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (do
        (println summary)
        (System/exit 0))
      (seq errors)
      (do
        (run! println errors)
        (println summary)
        (System/exit 1))
      :else
      (let [stop (server/start (:port options))]
        (.addShutdownHook
          (Runtime/getRuntime)
          (Thread. ^Runnable (fn [] (close! stop))))))))
