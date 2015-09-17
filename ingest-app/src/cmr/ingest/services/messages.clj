(ns cmr.ingest.services.messages
  (:require [cmr.common.util :as util]
            [cmr.common.validations.core :as vc]
            [clojure.string :as str]))

(defn parent-collection-does-not-exist
  [provider-id granule-ur collection-ref]
  (let [collection-ref-fields (util/remove-nil-keys (into {} collection-ref))
        coll-ref-humanized-fields (for [[field value] collection-ref-fields]
                                    (format "%s [%s]" (vc/humanize-field field) value))]
    (format "Collection with %s referenced in granule [%s] provider [%s] does not exist."
            (str/join ", " coll-ref-humanized-fields)
            granule-ur provider-id)))

(defn invalid-multipart-params
  [expected-params actual-params]
  (format "Unexpected multipart parameters: [%s]. Expected the multipart parameters [%s]."
          (str/join ", " actual-params)
          (str/join ", " expected-params)))

(defn invalid-revision-id
  [revision-id]
  (format "Invalid revision-id [%s]. Cmr-Revision-id in the header must be a positive integer." revision-id))

(defn invalid-parent-collection-for-validation
  [collection-validation-error]
  (str "The collection given for validating the granule was invalid: " collection-validation-error))

(defn platform-not-matches-kms-keywords
  [platform]
  (format "Platform short name [%s] and long name [%s] were not a valid keyword combination."
          (:short-name platform) (:long-name platform)))