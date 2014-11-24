# cmr-ingest-app

This is the ingest component of the CMR system. It is responsible for collaborating with metadata db and indexer components of the CMR system to maintain the lifecycle of concepts coming into the system.

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## Setting up the database

There are two ways database operations can be done. It can happen through leiningen commands for local development or using the built uberjar.

### leiningen commands

1. Create the user

```
lein create-user
```

2. Run the migration scripts

```
lein migrate
```

You can use `lein migrate -version version` to restore the database to
a given version. `lein migrate -version 0` will clean the datbase
completely.

3. Remove the user

```
lein drop-user
```

### java commands through uberjar

1. Create the user

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db create-user
```

2. Run db migration

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db migrate
```

You can provider additional arguments to migrate the database to a given version as in lein migrate.

3. Remove the user

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db drop-user
```

## Curl statements
- ensure Metadata db, ES, Indexer, Ingest are functioning

### Create provider

    curl -v -XPOST -H "Content-Type: application/json" -H "Echo-Token: mock-echo-system-token" -d '{"provider-id": "PROV1"}' http://localhost:3002/providers

### Delete provider

    curl -v -XDELETE -H "Echo-Token: mock-echo-system-token" http://localhost:3002/providers/PROV1

### Get providers

    curl http://localhost:3002/providers

### Create concept

    curl -i -v  -X PUT -H "Content-Type: application/echo10+xml" -H "Accept:application/json" --data \
"<Collection> <ShortName>ShortName_Larc</ShortName> <VersionId>Version01</VersionId> <InsertTime>1999-12-31T19:00:00-05:00</InsertTime> <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate> <DeleteTime>2015-05-23T22:30:59</DeleteTime><LongName>LarcLongName</LongName> <DataSetId>LarcDatasetId</DataSetId> <Description>A minimal valid collection</Description> <Orderable>true</Orderable> <Visible>true</Visible> </Collection>"  \
http://localhost:3002/providers/PROV1/collections/nativeId8

sample output:
{"concept-id":"C12-CurlPROV009","revision-id":0}

### Delete concept

    curl -i -v -XDELETE -H "Content-Type: application/json" http://localhost:3002/providers/CurlPROV009/collections/nativeId8

sample output:
{"concept-id":"C12-CurlPROV009","revision-id":1}

### Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their healthes in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET "http://localhost:3002/health?pretty=true"

Example healthy response body:

```
{
  "oracle" : {
    "ok?" : true
  },
  "echo" : {
    "ok?" : true
  },
  "metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "indexer" : {
    "ok?" : true,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      },
      "metadata-db" : {
        "ok?" : true,
        "dependencies" : {
          "oracle" : {
            "ok?" : true
          },
          "echo" : {
            "ok?" : true
          }
        }
      },
      "index-set" : {
        "ok?" : true,
        "dependencies" : {
          "elastic_search" : {
            "ok?" : true
          },
          "echo" : {
            "ok?" : true
          }
        }
      }
    }
  }
}
```

Example un-healthy response body:

```
{
  "oracle" : {
    "ok?" : false,
    "problem" : "Exception occurred while getting connection: oracle.ucp.UniversalConnectionPoolException: Cannot get Connection from Datasource: java.sql.SQLRecoverableException: IO Error: The Network Adapter could not establish the connection"
  },
  "echo" : {
    "ok?" : true
  },
  "metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "indexer" : {
    "ok?" : true,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      },
      "metadata-db" : {
        "ok?" : true,
        "dependencies" : {
          "oracle" : {
            "ok?" : true
          },
          "echo" : {
            "ok?" : true
          }
        }
      },
      "index-set" : {
        "ok?" : true,
        "dependencies" : {
          "elastic_search" : {
            "ok?" : true
          },
          "echo" : {
            "ok?" : true
          }
        }
      }
    }
  }
}
```

### Pause ingest scheduled jobs

Requires token with UPDATE ingest management permission.

    curl -v -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3002/jobs/pause

### Resume ingest scheduled jobs

Requires token with UPDATE ingest management permission.

    curl -v -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3002/jobs/resume

## License

Copyright © 2014 NASA
