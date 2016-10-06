(ns cmr.ingest.services.jobs
  "This contains the scheduled jobs for the ingest application."
  (:require [cmr.common.jobs :as jobs :refer [def-stateful-job defjob]]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.acl.acl-fetcher :as acl-fetcher]
            [cmr.ingest.data.provider-acl-hash :as pah]
            [cmr.ingest.data.ingest-events :as ingest-events]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.common.log :refer (debug info warn error)]))

(def REINDEX_COLLECTION_PERMITTED_GROUPS_INTERVAL
  "The number of seconds between jobs to check for ACL changes and reindex collections."
  3600)

(def CLEANUP_EXPIRED_COLLECTIONS_INTERVAL
  "The number of seconds between jobs to cleanup expired collections"
  3600)

(defn acls->provider-id-hashes
  "Converts acls to a map of provider-ids to hashes of the ACLs."
  [acls]
  (let [provider-id-to-acls (group-by (comp :provider-id :catalog-item-identity) acls)]
    (into {}
          (for [[provider-id provider-acls] provider-id-to-acls]
            ;; Convert them to a set so hash is consistent without order
            [provider-id (hash (set provider-acls))]))))

(defn reindex-all-collections
  "Reindexes all collections in all providers"
  [context force-version?]

  ;; Refresh the acls
  ;; This is done because we want to make sure we have the latest acls cached. This will update
  ;; the hash code stored in the consistent cache. Both ingest and the indexer use the consistent
  ;; cache for acls so they are kept in sync. This removes the need for the indexer to refresh
  ;; the cache on each message that it processes.
  (acl-fetcher/refresh-acl-cache context)

  (let [providers (map :provider-id (mdb/get-providers context))
        current-provider-id-acl-hashes (acls->provider-id-hashes
                                         (acl-fetcher/get-acls context [:catalog-item]))]
    (info "Sending events to reindex collections in all providers:" (pr-str providers))
    (doseq [provider providers]
      (ingest-events/publish-provider-event
        context
        (ingest-events/provider-collections-require-reindexing-event provider force-version?)))

    (debug "Reindexing all collection events submitted. Saving provider acl hashes")
    (pah/save-provider-id-acl-hashes context current-provider-id-acl-hashes)
    (debug "Saving provider acl hashes complete")))

(defn reindex-collection-permitted-groups
  "Reindexes all collections in a provider if the acls have changed. This is necessary because
  the groups that have permission to find collections are indexed with the collections."
  [context]

  ;; Refresh the acls
  ;; This is done because we want to make sure we have the latest acls cached. This will update
  ;; the hash code stored in the consistent cache. Both ingest and the indexer use the consistent
  ;; cache for acls so they are kept in sync. This removes the need for the indexer to refresh
  ;; the cache on each message that it processes.
  (acl-fetcher/refresh-acl-cache context)

  (let [providers (map :provider-id (mdb/get-providers context))
        provider-id-acl-hashes (or (pah/get-provider-id-acl-hashes context) {})
        current-provider-id-acl-hashes (acls->provider-id-hashes
                                         (acl-fetcher/get-acls context [:catalog-item]))
        providers-requiring-reindex (filter (fn [provider-id]
                                              (not= (get current-provider-id-acl-hashes provider-id)
                                                    (get provider-id-acl-hashes provider-id)))
                                            providers)]
    (when (seq providers-requiring-reindex)
      (info "Providers" (pr-str providers-requiring-reindex)
            "ACLs have changed. Reindexing collections")
      (doseq [provider providers-requiring-reindex]
        (ingest-events/publish-provider-event
          context
          (ingest-events/provider-collections-require-reindexing-event provider false))))

    (pah/save-provider-id-acl-hashes context current-provider-id-acl-hashes)))


;; Periodically checks the acls for a provider. When they change reindexes all the collections in a
;; provider.
(def-stateful-job ReindexCollectionPermittedGroups
  [ctx system]
  (let [context {:system system}]
    (reindex-collection-permitted-groups context)))

;; Reindexes all collections for providers regardless of whether the ACLs have changed or not.
;; This is done as a temporary fix for CMR-1311 but we may keep it around to help temper other race
;; conditions that may occur.
(def-stateful-job ReindexAllCollections
  [ctx system]
  (let [context {:system system}]
    (reindex-all-collections context false)))

(defn cleanup-expired-collections
  "Finds collections that have expired (have a delete date in the past) and removes them from
  metadata db and the index"
  [context]
  (doseq [{:keys [provider-id]} (mdb/get-providers context)]
    (info "Cleaning up expired collections for provider" provider-id)
    (when-let [concept-ids (mdb/get-expired-collection-concept-ids context provider-id)]
      (info "Removing expired collections:" (pr-str concept-ids))
      (doseq [concept-id concept-ids]
        (mdb/save-concept context {:concept-id concept-id :deleted true})))))

(def-stateful-job CleanupExpiredCollections
  [ctx system]
  (let [context {:system system}]
    (cleanup-expired-collections context)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Jobs for refreshing the collection granule aggregation cache in the indexer. This is a singleton job
;; and the indexer does not have a database so it's triggered from Ingest and sent via message.
;; Only one node needs to refresh the cache because we're using the  fallback cache with cubby cache.
;; The value stored in cubby will be available to all the nodes.

(defconfig partial-refresh-collection-granule-aggregation-cache-interval
  "Number of seconds between partial refreshes of the collection granule aggregation cache."
  {:default 3600
   :type Long})

(defn trigger-full-refresh-collection-granule-aggregation-cache
  "Triggers a refresh of the collection granule aggregation cache in the Indexer."
  [context]
  (ingest-events/publish-provider-event
    context
    (ingest-events/trigger-collection-granule-aggregation-cache-refresh nil)))

(defn trigger-partial-refresh-collection-granule-aggregation-cache
  "Triggers a partial refresh of the collection granule aggregation cache in the Indexer."
  [context]
  (ingest-events/publish-provider-event
    context
    (ingest-events/trigger-collection-granule-aggregation-cache-refresh
     ;; include a 5 minute buffer
     (+ 300 (partial-refresh-collection-granule-aggregation-cache-interval)))))

;; Refreshes collections updated in past interval time period.
(def-stateful-job TriggerPartialRefreshCollectionGranuleAggregationCacheJob
  [_ system]
  (trigger-partial-refresh-collection-granule-aggregation-cache
   {:system system}))

;; Fully refreshes the cache
(def-stateful-job TriggerFullRefreshCollectionGranuleAggregationCacheJob
  [_ system]
  (trigger-full-refresh-collection-granule-aggregation-cache
   {:system system}))

(defn jobs
  "A list of jobs for ingest"
  []
  [{:job-type ReindexCollectionPermittedGroups
    :interval REINDEX_COLLECTION_PERMITTED_GROUPS_INTERVAL}

   {:job-type CleanupExpiredCollections
    :interval CLEANUP_EXPIRED_COLLECTIONS_INTERVAL}

   {:job-type TriggerPartialRefreshCollectionGranuleAggregationCacheJob
    :interval (partial-refresh-collection-granule-aggregation-cache-interval)}

   {:job-type TriggerFullRefreshCollectionGranuleAggregationCacheJob
    ;; Everyday at 11:20 am so it's before the reindex all collections job
    :daily-at-hour-and-minute [11 20]}

   {:job-type ReindexAllCollections
    ;; Run everyday at 12:20 pm. Chosen because it's least busy time for indexer historically and also
    ;; during business hours when people can debug issues. It's offset from the top of the hour so as
    ;; not to be at the same time as EDSC fetches all the collection metadata.
    :daily-at-hour-and-minute [12 20]}])
