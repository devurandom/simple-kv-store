(ns de.reify.simplekvstore.server
  "Server startup, connection handling and the global key/value storage."
  (:require
    [clojure.core.async :refer [<! go chan]]
    [clojure.java.io :as io]
    [de.reify.simplekvstore.handler :as handler])
  (:import
    (java.net ServerSocket SocketException Socket)))

(def root-context
  "The context that represents the server's permanent key/value storage that is shared between all clients."
  (atom {}))

(defn process-connection
  "Process an incoming connection line by line."
  [sock]
  (with-open [r (io/reader sock)
              w (io/writer sock)]
    (try
      (let [session {:root root-context
                     :reader r
                     :writer w}]
        (handler/handle-lines session))
      (catch Exception e
        (.write w "Internal server error\r\n")
        (throw e)))))

(defn start-thread
  "Run a function `(f ...args)` on a thread."
  [f & args]
  (.start (Thread. ^Runnable (apply partial f args))))

(defn maybe-accept-connection
  "Wait for an incoming connection and return it, or `nil` if the server socket was closed."
  ^Socket [^ServerSocket server]
  (try
    (.accept server)
    ; .accept throws SocketException when ServerSocket is closed:
    (catch SocketException _)))

(defn serve
  "Run the server listening on `port` and process incoming connections.
  Listens to the `stop` channel for the signal to stop the server."
  [port stop]
  (with-open [server (ServerSocket. port)]
    (println (str "Listening on port TCP/" port))
    (go
      (<! stop)
      (println "Stopping...")
      (.close server))
    (while true
      ; We do not use with-open here, because we want to block at `.accept`,
      ; but call `.close` in the handler thread.
      (when-let [sock (maybe-accept-connection server)]
        (start-thread
          #(try
             (process-connection sock)
             (finally
               (.close sock))))))))

(defn start
  "Start the server. Returns a channel that can be used to stop it."
  [port]
  (let [stop (chan)]
    (start-thread serve port stop)
    stop))
