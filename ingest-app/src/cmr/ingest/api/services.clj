(ns cmr.ingest.api.services
  "Service ingest functions in support of the ingest API."
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.validation.validation :as v]))

(defn- validate-and-prepare-service-concept
  "Validate service concept, set the concept format and returns the concept;
  throws error if the metadata is not a valid against the UMM service JSON schema."
  [concept]
  (let [concept (update-in concept [:format] (partial ingest/fix-ingest-concept-format :service))]
    (v/validate-concept-request concept)
    (v/validate-concept-metadata concept)
    concept))

(defn ingest-service
  "Processes a request to create or update a service."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        concept (api-core/body->concept!
                 :service provider-id native-id body content-type headers)]
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission
      request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (validate-and-prepare-service-concept concept)]
      (->> (api-core/set-user-id concept request-context headers)
           (ingest/save-service request-context)
           (api-core/generate-ingest-response headers)))))

(defn delete-service
  "Deletes the service with the given provider id and native id."
  [provider-id native-id request]
  (api-core/delete-concept :service provider-id native-id request))
