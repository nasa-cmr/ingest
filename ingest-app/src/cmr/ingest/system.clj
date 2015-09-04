(ns cmr.ingest.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.common.nrepl :as nrepl]
            [cmr.system-trace.context :as context]
            [cmr.ingest.api.routes :as routes]
            [cmr.transmit.config :as transmit-config]
            [cmr.oracle.config :as oracle-config]
            [cmr.ingest.config :as config]
            [cmr.ingest.services.jobs :as ingest-jobs]
            [cmr.common.jobs :as jobs]
            [cmr.acl.core :as acl]
            [cmr.acl.acl-fetcher :as af]
            [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
            [cmr.common-app.cache.consistent-cache :as consistent-cache]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.oracle.connection :as oracle]
            [cmr.message-queue.queue.rabbit-mq :as rmq]
            [cmr.ingest.api.ingest :as ingest-api]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.ingest.services.providers-cache :as pc]))

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :caches :db :queue-broker :scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defconfig ingest-public-protocol
  "The protocol to use in documentation examples for the ingest application."
  {:default "http"})

(def ingest-public-conf
  "Public ingest configuration used for generating example requests in documentation"
  {:protocol (ingest-public-protocol)
   :relative-root-url (transmit-config/ingest-relative-root-url)})

(defn create-system
  "Returns a new instance of the whole application."
  ([]
   (create-system "ingest"))
  ([connection-pool-name]
   (let [sys {:log (log/create-logger)
              :web (web/create-web-server (transmit-config/ingest-port) routes/make-api)
              :nrepl (nrepl/create-nrepl-if-configured (config/ingest-nrepl-port))
              :db (oracle/create-db (config/db-spec connection-pool-name))
              :zipkin (context/zipkin-config "Ingest" false)
              :scheduler (jobs/create-clustered-scheduler
                           `system-holder :db
                           (conj ingest-jobs/jobs (af/refresh-acl-cache-job "ingest-acl-cache-refresh")))
              :caches {acl/token-imp-cache-key (acl/create-token-imp-cache)
                       pc/providers-cache-key (pc/create-providers-cache)
                       af/acl-cache-key (af/create-acl-cache
                                          (stl-cache/create-single-thread-lookup-cache
                                            (consistent-cache/create-consistent-cache))
                                          [:catalog-item :system-object :provider-object])
                       ingest-api/user-id-cache-key (ingest-api/create-user-id-cache)
                       kf/kms-cache-key (kf/create-kms-cache)}
              :ingest-public-conf ingest-public-conf
              :queue-broker (rmq/create-queue-broker (config/rabbit-mq-config))}]
     (transmit-config/system-with-connections sys [:metadata-db :indexer :echo-rest :search :cubby
                                                   :kms]))))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/start % system))))
                               this
                               component-order)]
    (info "System started")
    started-system))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/stop % system))))
                               this
                               (reverse component-order))]
    (info "System stopped")
    stopped-system))

