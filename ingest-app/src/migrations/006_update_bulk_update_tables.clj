(ns migrations.006-update-bulk-update-tables
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 6."
  []
  (println "migrations.006-update-bulk-update-tables up...")
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.bulk_update_task_status_new (
                              TASK_ID VARCHAR(255) NOT NULL,
                              PROVIDER_ID  VARCHAR(10) NOT NULL,
                              REQUEST_JSON_BODY  BLOB NOT NULL,
                              STATUS VARCHAR(20),
                              STATUS_MESSAGE   VARCHAR(255),
                              CREATED_AT TIMESTAMP(9) WITH TIME ZONE DEFAULT sysdate NOT NULL
                              )")
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.bulk_update_coll_status_new (
                              TASK_ID VARCHAR(255) NOT NULL,
                              CONCEPT_ID VARCHAR(255) NOT NULL,
                              STATUS VARCHAR(20),
                              STATUS_MESSAGE  VARCHAR(500),
                              ACTION_TAKEN VARCHAR2(1) DEFAULT 'F' NOT NULL
                              )")
  (j/db-do-commands (config/db) "INSERT INTO CMR_INGEST.bulk_update_task_status_new(
                              TASK_ID, PROVIDER_ID, REQUEST_JSON_BODY, STATUS, STATUS_MESSAGE)
                              SELECT to_char(TASK_ID), PROVIDER_ID, REQUEST_JSON_BODY, STATUS, STATUS_MESSAGE
                              FROM CMR_INGEST.bulk_update_task_status")
  (j/db-do-commands (config/db) "INSERT INTO CMR_INGEST.bulk_update_coll_status_new(
                              TASK_ID, CONCEPT_ID, STATUS, STATUS_MESSAGE)
                              SELECT to_char(TASK_ID), CONCEPT_ID, STATUS, STATUS_MESSAGE
                              FROM CMR_INGEST.bulk_update_coll_status")
  (j/db-do-commands (config/db) "DROP TABLE CMR_INGEST.bulk_update_coll_status")
  (j/db-do-commands (config/db) "DROP TABLE CMR_INGEST.bulk_update_task_status")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_task_status_new
                                 RENAME TO bulk_update_task_status")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status_new
                                 RENAME TO bulk_update_coll_status")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_task_status
                                 ADD CONSTRAINT BULK_UPDATE_TASK_STATUS_PK PRIMARY KEY (TASK_ID)")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status
                                 ADD CONSTRAINT BULK_UPDATE_COLL_STATUS_PK PRIMARY KEY (TASK_ID,CONCEPT_ID)") 
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status
                                 ADD CONSTRAINT BULK_UPDATE_COLL_STATUS_FK FOREIGN KEY (TASK_ID)
                                 REFERENCES BULK_UPDATE_TASK_STATUS(TASK_ID)")
  (j/db-do-commands (config/db) "CREATE INDEX idx_blk_upd_tsk_pi ON CMR_INGEST.bulk_update_task_status(PROVIDER_ID)"))

(defn down
  "Migrates the database down from version 6."
  []
  (println "migrations.006-update-bulk-update-tables down...")
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.bulk_update_task_status_old (
                              TASK_ID NUMBER NOT NULL,
                              PROVIDER_ID  VARCHAR(10) NOT NULL,
                              REQUEST_JSON_BODY  BLOB NOT NULL,
                              STATUS VARCHAR(20),
                              STATUS_MESSAGE   VARCHAR(255)
                              )")
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.bulk_update_coll_status_old (
                              TASK_ID NUMBER NOT NULL,
                              CONCEPT_ID VARCHAR(255) NOT NULL,
                              STATUS VARCHAR(20),
                              STATUS_MESSAGE  VARCHAR(255)
                              )")
  (j/db-do-commands (config/db) "INSERT INTO CMR_INGEST.bulk_update_task_status_old(
                              TASK_ID, PROVIDER_ID, REQUEST_JSON_BODY, STATUS, STATUS_MESSAGE)
                              SELECT to_number(TASK_ID), PROVIDER_ID, REQUEST_JSON_BODY, STATUS, STATUS_MESSAGE
                              FROM CMR_INGEST.bulk_update_task_status")
  (j/db-do-commands (config/db) "INSERT INTO CMR_INGEST.bulk_update_coll_status_old(
                              TASK_ID, CONCEPT_ID, STATUS, STATUS_MESSAGE)
                              SELECT to_number(TASK_ID), CONCEPT_ID, STATUS, STATUS_MESSAGE
                              FROM CMR_INGEST.bulk_update_coll_status")
  (j/db-do-commands (config/db) "DROP TABLE CMR_INGEST.bulk_update_coll_status")
  (j/db-do-commands (config/db) "DROP TABLE CMR_INGEST.bulk_update_task_status")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_task_status_old
                                 RENAME TO bulk_update_task_status")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status_old
                                 RENAME TO bulk_update_coll_status")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_task_status
                                 ADD CONSTRAINT BULK_UPDATE_TASK_STATUS_PK PRIMARY KEY (TASK_ID)")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status
                                 ADD CONSTRAINT BULK_UPDATE_COLL_STATUS_PK PRIMARY KEY (TASK_ID,CONCEPT_ID)")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status
                                 ADD CONSTRAINT BULK_UPDATE_COLL_STATUS_FK FOREIGN KEY (TASK_ID)
                                 REFERENCES BULK_UPDATE_TASK_STATUS(TASK_ID)")
  (j/db-do-commands (config/db) "CREATE INDEX idx_blk_upd_tsk_pi ON CMR_INGEST.bulk_update_task_status(PROVIDER_ID)")) 
