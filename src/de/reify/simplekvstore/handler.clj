(ns de.reify.simplekvstore.handler
  "Command parsing and handlers.  All interaction with the client happens here."
  (:require
    [clojure.string :as string]))

(defn log!
  "Print argument with a prefix and pass on the argument."
  [prefix line]
  (println prefix line)
  line)

(defn split-words
  "Split a line into words by whitespace."
  [line]
  (string/split line #"\s+"))

(defn handle-get
  "Handle the GET command."
  [session key]
  (let [v (some #(get % key) (:contexts session))]
    {:response (or v (get @(:root session) key) "Error: Key not found.")
     :session session}))

(defn assoc-in-first
  "Given a list `coll`, set `key` in its first element."
  [coll key value]
  (conj (pop coll)
        (assoc (peek coll) key value)))

(defn handle-set
  "Handle the SET command."
  [session key value]
  {:session (update session :contexts assoc-in-first key value)})

(defn handle-delete
  "Handle the DELETE command."
  [session key]
  {:session (update session :contexts assoc-in-first key nil)})

(defn merge-top-contexts
  "Merge the two innermost transaction's context,
  i.e. commit the top transaction into the next lower context."
  [contexts]
  (conj (nthrest contexts 2)
        (merge (second contexts) (first contexts))))

; TODO: This should probably fail or retry, if the parent context has changed?
(defn handle-commit
  "Handle the COMMIT command."
  [session]
  (if (= (count (:contexts session)) 1)
    (do
      (swap! (:root session) merge (first (:contexts session)))
      {:session (assoc session :contexts '())})
    {:session (update session :contexts merge-top-contexts)}))

(defn handle-begin
  "Handle the BEGIN command."
  [session]
  {:session (update session :contexts conj {})})

(defn handle-rollback
  "Handle the ROLLBACK command."
  [session]
  {:session (update session :contexts pop)})

(defn handle-exit
  "Handle the EXIT command."
  [session]
  {:response "Goodbye."
   :session (assoc session :close true)})

(def commands
  "All available commands, indexed by the command name.
  `nargs` is the expected number of arguments, used for basic validation.
  `handler` is the function that executes the command."
  {"GET" {:nargs 1
          :handler handle-get}
   "SET" {:nargs 2
          :handler handle-set}
   "DELETE" {:nargs 1
             :handler handle-delete}
   "BEGIN" {:nargs 0
            :handler handle-begin}
   "COMMIT" {:nargs 0
             :handler handle-commit}
   "ROLLBACK" {:nargs 0
               :handler handle-rollback}
   "EXIT" {:nargs 0
           :handler handle-exit}})

(defn pick-handler
  "For a line that was split by words, select the handler for the first word (command)."
  [[command & args]]
  (if-let [{:keys [nargs handler]} (commands command)]
    (if (= nargs (count args))
      {:handler #(apply handler % args)}
      {:error (str "Error: Wrong number of arguments. Expected: " nargs ", got: " (count args))})
    {:error (str "Error: Unknown command: " command)}))

(defn respond!
  "Send response to client."
  [w response]
  (when (some? response)
    (.write w (str response "\r\n"))
    (.flush w)))

(defn execute-handler
  "After a handler has been selected for the given command by `pick-handler`,
  handle command validation errors or execute the command."
  [session {:keys [handler error]}]
  (cond
    (some? error)
    {:response error
     :session session}
    (some? handler)
    (handler session)
    :else
    {:response "Unknown error"
     :session session}))

(defn handle-lines
  "Parse lines coming from client, hand over to handler functions, send responses to client."
  [session]
  (let [session (update session :contexts #(or (seq %) '({})))]
    (when-let [request (.readLine (:reader session))]
      (let [{:keys [response session]} (->> request
                                            (log! ">")
                                            split-words
                                            pick-handler
                                            (execute-handler session))]
        (log! "<" response)
        (respond! (:writer session) response)
        (if (:close session)
          (do
            (.close (:reader session))
            (.close (:writer session)))
          (recur session))))))
