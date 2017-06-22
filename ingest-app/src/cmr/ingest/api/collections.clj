(ns cmr.ingest.api.collections
  "Collection ingest functions in support of the ingest API."
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as util]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]))

(def VALIDATE_KEYWORDS_HEADER "cmr-validate-keywords")
(def ENABLE_UMM_C_VALIDATION_HEADER "cmr-validate-umm-c")

(defn get-validation-options
  "Returns a map of validation options with boolean values"
  [headers]
  {:validate-keywords? (= "true" (get headers VALIDATE_KEYWORDS_HEADER))
   :validate-umm? (= "true" (get headers ENABLE_UMM_C_VALIDATION_HEADER))})

(defn validate-collection
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request
        concept (api-core/body->concept! :collection provider-id native-id body content-type headers)
        validation-options (get-validation-options headers)]
    (api-core/verify-provider-exists request-context provider-id)
    (info (format "Validating Collection %s from client %s"
                  (api-core/concept->loggable-string concept) (:client-id request-context)))
    (let [validate-response (ingest/validate-and-prepare-collection request-context
                                                                    concept
                                                                    validation-options)]
      (api-core/generate-validate-response
       headers
       (util/remove-nil-keys
        (select-keys (api-core/contextualize-warnings validate-response)
                     [:warnings]))))))

(defn ingest-collection
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request]
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (api-core/body->concept! :collection provider-id native-id body content-type headers)
          validation-options (get-validation-options headers)
          save-collection-result (ingest/save-collection
                                  request-context
                                  (api-core/set-user-id concept request-context headers)
                                  validation-options)]
      (info (format "Ingesting collection %s from client %s"
              (api-core/concept->loggable-string (assoc concept :entry-title (:entry-title save-collection-result)))
              (:client-id request-context)))
      (api-core/generate-ingest-response headers
                                         (api-core/contextualize-warnings
                                          ;; entry-title is added just for the logging above.
                                          ;; dissoc it so that it remains the same as the
                                          ;; original code.
                                          (dissoc save-collection-result :entry-title))))))

(defn delete-collection
  [provider-id native-id request]
  (let [{:keys [request-context params headers]} request
        concept-attribs (-> {:provider-id provider-id
                             :native-id native-id
                             :concept-type :collection}
                            (api-core/set-revision-id headers)
                            (api-core/set-user-id request-context headers))]
    (common-enabled/validate-write-enabled request-context "ingest")
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (info (format "Deleting collection %s from client %s"
                  (pr-str concept-attribs) (:client-id request-context)))
    (api-core/generate-ingest-response headers
                                       (api-core/contextualize-warnings
                                        (ingest/delete-concept
                                         request-context
                                         concept-attribs)))))
