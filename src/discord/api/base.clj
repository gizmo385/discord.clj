(ns discord.api.base
  (:require
    [clojure.data.json :as json]
    [clojure.string :as s]
    [clj-http.client :as client]
    [overtone.at-at :as at]
    [slingshot.slingshot :refer [try+]]
    [taoensso.timbre :as timbre]
    [discord.constants :as constants]
    [discord.types.auth :as a]
    [discord.types.snowflake :as sf]
    [discord.utils :as utils]))

(defonce rate-limit-pool (at/mk-pool))

(defn- build-request
  "Builds the API request based on the selected endpoint on the supplied arguments."
  [endpoint method auth json params]
  (let  [url      (str constants/discord-url endpoint)
         headers  {:User-Agent    constants/user-agent
                   :Authorization (format "%s %s" (a/token-type auth) (a/token auth))
                   :Accept        "application/json"}
         request  {:headers headers
                   :url     url
                   :method  method}]
    ;; Based on the HTTP method of the request being performed, we'll be attaching either a JSON
    ;; body or URL query parameters to the request.
    (condp = method
      :get      (assoc request :params params)
      :post     (assoc request :body (json/write-str json) :content-type :json)
      :put      (assoc request :body (json/write-str json) :content-type :json)
      :patch    (assoc request :body (json/write-str json) :content-type :json)
      :delete   (assoc request :body (json/write-str json) :content-type :json)
      (throw (ex-info "Unknown request method" {:endpoint endpoint :method method})))))

(defn- send-api-request
  "Sends a request to the Discord API and handles the response."
  [request constructor]
  (let  [response   (client/request request)
         status     (:status response)]
    (case status
      200 (as-> response response
            (:body response)
            (json/read-str response :key-fn keyword)
            (if (seq? response)
              (map constructor response)
              (constructor response)))
      204 true

      ;; Default
      false)))

(defn discord-request
  "General wrapper function for sending a request to one of the pre-defined Discord API endpoints.
   This function calls other helper functions to handle the following:
    - Retrieving the API endpoint to call
    - Formatting the request
    - Sending the API call
    - Deferred retries of API calls in the event of a 429 Rate Limit response

   Arguments:
   endpoint-key: A keyword that maps to a defined endpoint in endpoint-mapping
   auth: Something that implements the Authenticated protocol to auth with the Discord APIs

   Options are passed :key val ... Supported options:

   :json map - An optional JSON body to pass along with post/put/patch/delete request
   :params map - Optional query parameters to pass along with a get request
   :args list - In order (format) arguments to correctly format the endpoint from endpoint-mapping
   :constructor f - A function which is mapped over API responses to create appropriate Records."
  [auth endpoint method & {:keys [json params args constructor] :or {constructor identity} :as opts}]
  (let [request (build-request endpoint method auth json params)]
    (timbre/infof "Sending request: %s" request)
    (try+
      (send-api-request request constructor)

      ;; Handle an API rate limit (return code 429)
      (catch [:status 429] {:keys [body]}
        (let [rate-limit-info (json/read-str body)
              wait-time       (get rate-limit-info "retry_after")]
          (timbre/info (format "Rate limited by API, waiting for %d milliseconds." wait-time))
          (at/after wait-time #(send-api-request request constructor) rate-limit-pool)))

      ;; Log any other errors that we encounter
      (catch Exception e
        (timbre/error e)
        false))))
