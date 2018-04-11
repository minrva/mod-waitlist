# Mod-Waitlist

Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

Mod-Waitlist is an Okapi module that provides the webservices for a course reserves waitlist.

## Configure Mod-Waitlist

In order for mod-waitlist to send e-mails, you will need to configure smtp settings.

1. Open NotifyEmail.java
    ```bash
    code ~/Desktop/folio/bl/mod-waitlist/src/main/java/org/folio/rest/utils/NotifyEmail.java
    ```
1. Provide your smtp username, password, host, and port.
    ```java
    private final String SMTP_USERNAME = "username";
    private final String SMTP_PASSWORD = "password";
    private final String SMTP_HOST = "smtp.gmail.com";
    private final String SMTP_PORT = "587";
    ```
1. Customize the notification e-mail. `NOTIFY_EXPIRE_TIME` is the amount of minutes that the patron has to collect a reserved item after being notified that the item has become available.
    ```java
    private final String FROM_ADDRESS = "techcirc@folio.org";
    private final int NOTIFY_EXPIRE_TIME = 2;
    ```
1. Open TimerManager.java
    ```bash
    code ~/Desktop/folio/bl/mod-waitlist/src/main/java/org/folio/rest/utils/TimerManager.java
    ```
1. Decide how long to wait for a patron to pick up a reserved item before deleting them from the waitlist and e-mailing the next patron in the waitlist. `NOTIFY_EXPIRE_TIME` is the amount of time in milliseconds.
    ```java 
    private static final long NOTIFY_EXPIRE_TIME = 2 * 60 * 1000;
    ```
1. Open WaitlistAPI.java
    ```bash
    code ~/Desktop/folio/bl/mod-waitlist/src/main/java/org/folio/rest/impl/WaitlistAPI.java
    ```
1. Optionally, you can change the rate at which mod-waitlist updates its waitlist timers. `TICK_INTERVAL` is the amount of time in milliseconds.
    ```java
    private static final long TICK_INTERVAL = 5000;
    ```

## Install Mod-Waitlist

```bash
mvn clean install
```

## Install Mod-Waitlist Docker Image

```bash
docker container prune
docker rmi mod-waitlist
docker build -t mod-waitlist .
```

## Deploy as Stand-Alone Application

### Java Verticle

```bash
cd ~/Desktop/folio/bl/mod-waitlist
java -jar target/mod-waitlist-fat.jar embed_postgres=true
```

### Docker Container

```bash
cd ~/Desktop/folio/bl/mod-waitlist
docker run -t -i -p 8081:8081 mod-waitlist embed_postgres=true
```

### cURL Usage

1. Create tenant-module databasae (i.e. enable module).
    ```bash
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' http://localhost:8081/_/tenant
    ```
1. POST: waitlist, queuer, course
    ```bash
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' \
        -d @ramls/examples/waitlist.json http://localhost:8081/waitlists
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' \
        -d @ramls/examples/queuer.json http://localhost:8081/queuers
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' \
        -d @ramls/examples/course.json http://localhost:8081/courses
    ```
1. GET: waitlist, queuer, course
    ```bash
    curl -i -w '\n' -X GET \
        -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' http://localhost:8081/courses
    curl -i -w '\n' -X GET \
        -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' http://localhost:8081/waitlists
    curl -i -w '\n' -X GET \
        -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' http://localhost:8081/queuers
    ```
1. GET FILTER: queuer
    ```bash
    curl -i -w '\n' -X GET \
        -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' 'http://localhost:8081/queuers?limit=30&query=waitlistId%3D%22c760b623-9490-476a-bad1-c8739100e27b%22'
    ```

### RAML API Documentation

You can also test the previous examples through RAML generated [apidocs](http://localhost:8081/apidocs/index.html?raml=raml/courses.raml) with the fields:

- port: `8081`
- custom header: `X-Okapi-Tenant: diku`.

## Deploy as Okapi Module

### Full: Postgresql Server, All Modules

1. Set-up environment (see 0-installation docs).
    ```bash
    sudo ifconfig lo0 alias 10.0.2.15
    brew services restart postgresql@9.6
    open -a Docker
    # wait for Docker to start before running next cmd
    docker run -d -v /var/run/docker.sock:/var/run/docker.sock -p 127.0.0.1:4243:4243 bobrik/socat TCP-LISTEN:4243,fork UNIX-CONNECT:/var/run/docker.sock
    ```
1. Deploy Okapi
    ```bash
    cd ~/Desktop/folio
    java \
          -Dstorage=postgres \
          -Dpostgres_host=localhost \
          -Dpostgres_port=5432 \
          -Dpostgres_user=okapi \
          -Dpostgres_password=okapi25 \
          -Dpostgres_database=okapi \
          -Dhost=10.0.2.15 \
          -Dport=9130 \
          -Dport_start=9131 \
          -Dport_end=9151 \
          -DdockerURL=http://localhost:4243 \
          -Dokapiurl=http://10.0.2.15:9130 \
          -jar bl/okapi/okapi-core/target/okapi-core-fat.jar dev
    ```
1. Deploy Okapi modules (necessary for permissions-module-4.0.4).
    ```bash
    source activate folio
    python ~/Desktop/folio/bl/dev-ops/deploy_modules.py
    source deactivate folio
    ```
1. Change to mod-waitlist directory.
    ```bash
    cd ~/Desktop/folio/bl/mod-waitlist
    ```
1. *Only Once*: Register `mod-waitlist`.
    ```bash
    curl -w '\n' -X POST -D -   \
        -H "Content-type: application/json"   \
        -d @target/ModuleDescriptor.json \
        http://localhost:9130/_/proxy/modules
    curl http://localhost:9130/_/proxy/modules
    ```
1. **Either** deploy `mod-waitlist` as a **Docker container**.
    ```bash
    curl -w '\n' -D - -s \
        -X POST \
        -H "Content-type: application/json" \
        -d @target/DockerDeploymentDescriptor.json  \
        http://localhost:9130/_/discovery/modules
    curl -i -w '\n' -X GET http://localhost:9130/_/discovery/modules
    ```
1. **Or** deploy `mod-waitlist` as a **Java application**.
    ```bash
    curl -w '\n' -D - -s \
        -X POST \
        -H "Content-type: application/json" \
        -d @target/DeploymentDescriptor.json  \
        http://localhost:9130/_/discovery/modules
    curl -i -w '\n' -X GET http://localhost:9130/_/discovery/modules
    ```
1. *Only Once*: Enable `mod-waitlist` for `diku` tenant.
    ```bash
    curl -w '\n' -X POST -D -   \
        -H "Content-type: application/json"   \
        -d @target/EnableDescriptor.json \
        http://localhost:9130/_/proxy/tenants/diku/modules
    curl http://localhost:9130/_/proxy/tenants/diku/modules
    ```
1. Request `courses` through `mod-waitlist`.
    ```bash
    # LOGIN and get x-okapi-token and use it for the next requests
    curl -i -w '\n' -X POST -H 'X-Okapi-Tenant: diku' \
        -H "Content-type: application/json" \
        -d '{"username": "diku_admin", "password": "admin"}' \
        http://localhost:9130/authn/login

    # GET waitlists, queuers, courses, instructors, and reserves
    curl -i -w '\n' -X GET -H 'X-Okapi-Tenant: diku' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.MZRXUukzjeGbgfe5U-3s2ElajSrAyC1-su8YighkrELjPpzKuswFcJokAExrZCeHwuWQxDpcENsBTaWXo3-fqA' \
        http://localhost:9130/waitlists
    curl -i -w '\n' -X GET -H 'X-Okapi-Tenant: diku' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.rdFFEMH1wMiuIvWt6Ixka3Xu7eRre4cn83t1fNGua5wRQCzfhcaFfyANPZMFXoTS6yh6lo9XkpxJvtIJpE_mOw' \
        http://localhost:9130/queuers
    curl -i -w '\n' -X GET -H 'X-Okapi-Tenant: diku' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.hIWAMTbBAOQYnyL0CF8fQT7RZQH-afs3vE3TjukshKmRp7Wai9cUCIsCzwD-6cNqa0YEPtnMUXL6y8AtZsAdFQ' \
        http://localhost:9130/courses
    curl -i -w '\n' -X GET -H 'X-Okapi-Tenant: diku' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.hIWAMTbBAOQYnyL0CF8fQT7RZQH-afs3vE3TjukshKmRp7Wai9cUCIsCzwD-6cNqa0YEPtnMUXL6y8AtZsAdFQ' \
        http://localhost:9130/instructors
    curl -i -w '\n' -X GET -H 'X-Okapi-Tenant: diku' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.hIWAMTbBAOQYnyL0CF8fQT7RZQH-afs3vE3TjukshKmRp7Wai9cUCIsCzwD-6cNqa0YEPtnMUXL6y8AtZsAdFQ' \
        http://localhost:9130/reserves

    # POST samples: waitlist, queuer, course
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.xkLqNMOj3S3U3xQhhJtdm_QEB20aVjRa19QqgwnPrGq44QV7OvMI7GpoBqITOS8juMQtNLskSRqz3nrZZ6KgLw' \
        -H 'X-Okapi-Tenant: diku' \
        -d @ramls/examples/waitlist.json http://localhost:9130/waitlists
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.MZRXUukzjeGbgfe5U-3s2ElajSrAyC1-su8YighkrELjPpzKuswFcJokAExrZCeHwuWQxDpcENsBTaWXo3-fqA' \
        -H 'X-Okapi-Tenant: diku' \
        -d @ramls/examples/queuer.json http://localhost:9130/queuers
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.MZRXUukzjeGbgfe5U-3s2ElajSrAyC1-su8YighkrELjPpzKuswFcJokAExrZCeHwuWQxDpcENsBTaWXo3-fqA' \
        -H 'X-Okapi-Tenant: diku' \
        -d @ramls/examples/course.json http://localhost:9130/courses

    # GET Query: waitlist, queuers
    curl -i -w '\n' -X GET -H 'X-Okapi-Tenant: diku' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.rdFFEMH1wMiuIvWt6Ixka3Xu7eRre4cn83t1fNGua5wRQCzfhcaFfyANPZMFXoTS6yh6lo9XkpxJvtIJpE_mOw' \
        'http://localhost:9130/waitlists?limit=30&query=reserveId%3D%22632a8f2a-c290-4b22-b73a-5da832e4b3e3%22'
    curl -i -w '\n' -X GET -H 'X-Okapi-Tenant: diku' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.rdFFEMH1wMiuIvWt6Ixka3Xu7eRre4cn83t1fNGua5wRQCzfhcaFfyANPZMFXoTS6yh6lo9XkpxJvtIJpE_mOw' \
        'http://localhost:9130/queuers?limit=30&query=waitlistId%3D%22c760b623-9490-476a-bad1-c8739100e27b%22'

    # GET Pagination: reserves
    curl -i -w '\n' -X GET -H 'X-Okapi-Tenant: diku' \
        -H 'X-Okapi-Token: eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6IjFhZDczN2IwLWQ4NDctMTFlNi1iZjI2LWNlYzBjOTMyY2UwMSIsInRlbmFudCI6ImRpa3UifQ.68APQ-L-ANM7MvEYVKEE_u8n9UvVS0vcSdWrZu8RFRq8OGD7JSaEylJmhYgN_0HgbQgwzwn8T2pUZ4jIA_4uzA' \
        'http://localhost:9130/reserves?limit=30&offset=60'
    ```
1. *Optional*: Deploy Stripes Platform
    ```bash
    cd ~/Desktop/folio/ui/stripes-demo-platform
    yarn start
    ```
1. Tear-down
    `ctrl + c` out of okapi and stripes.
    ```bash
    docker kill $(docker ps -q)
    docker container prune
    brew services stop postgresql@9.6
    sudo ifconfig lo0 -alias 10.0.2.15
    ```

Note: Launch descriptor has a path relative to the directory that `java -jar okapi-core-fat.jar` was executed in (likely `~/Desktop/folio`).

### Light: Embedded Postgresql, Only Mod-Waitlist

1. Deploy Okapi
    ```bash
    sudo ifconfig lo0 alias 10.0.2.15
    cd ~/Desktop/folio
    java \
          -Dhost=10.0.2.15 \
          -Dport=9130 \
          -Dport_start=9131 \
          -Dport_end=9151 \
          -Dokapiurl=http://10.0.2.15:9130 \
          -jar bl/okapi/okapi-core/target/okapi-core-fat.jar dev
    ```
1. Register `diku` tenant.
    ```bash
    cd ~/Desktop/folio/bl/dev-ops/folio-configs/tenant
    curl -w '\n' -X POST -D -   \
        -H "Content-type: application/json"   \
        -d @diku.json \
        http://localhost:9130/_/proxy/tenants
    curl http://localhost:9130/_/proxy/tenants
    ```
1. Register `mod-waitlist`.
    ```bash
    cd ~/Desktop/folio/bl/mod-waitlist
    curl -w '\n' -X POST -D -   \
        -H "Content-type: application/json"   \
        -d @target/ModuleDescriptor.json \
        http://localhost:9130/_/proxy/modules
    curl http://localhost:9130/_/proxy/modules
    ```
1. Deploy `mod-waitlist`.
    ```bash
    cd ~/Desktop/folio/bl/mod-waitlist
    curl -w '\n' -D - -s \
        -X POST \
        -H "Content-type: application/json" \
        -d @target/DeploymentDescriptor.json  \
        http://localhost:9130/_/discovery/modules
    curl -i -w '\n' -X GET http://localhost:9130/_/discovery/modules
    ```
1. Enable `mod-waitlist` for `diku` tenant.
    ```bash
    cd ~/Desktop/folio/bl/mod-waitlist
    curl -w '\n' -X POST -D -   \
        -H "Content-type: application/json"   \
        -d @target/EnableDescriptor.json \
        http://localhost:9130/_/proxy/tenants/diku/modules
    curl http://localhost:9130/_/proxy/tenants/diku/modules
    ```
1. Request `courses` through `mod-waitlist`.
    ```bash
    curl -i -w '\n' -X GET \
        -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' http://localhost:9130/courses
    ```

## Example Post Data

1. Start Mod-Waitlist
    ```bash
    cd ~/Desktop/folio/bl/mod-waitlist
    mvn clean install
    java -jar target/mod-waitlist-fat.jar embed_postgres=true
    ```
1. Post sample json
    ```bash
    cd ~/Desktop/folio/bl/mod-waitlist
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' http://localhost:8081/_/tenant
    curl -i -w '\n' -X POST -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' \
        -d @ramls/examples/course.json http://localhost:8081/courses
    curl -i -w '\n' -X GET \
        -H 'Content-type: application/json' \
        -H 'X-Okapi-Token: dummyJwt.eyJzdWIiOiJzZWIiLCJ0ZW5hbnQiOm51bGx9.sig' \
        -H 'X-Okapi-Tenant: diku' http://localhost:8081/courses
    ```

## Waitlist Timer

A waitlist object containers a timer mechanism that can be manipulated using a variable called `timerMode`. `timerMode` can be in one of these three states: `stopped`, `started`, `paused`.

## Reference

1. Unregister mod-waitlist from Okapi gateway.
    ```bash
    # undeploy mod-waitlist
    curl -w '\n' -X DELETE  -D - http://localhost:9130/_/discovery/modules/mod-waitlist-1.0.0/10.0.2.15-9143
    # disable mod-waitlist for diku
    curl -w '\n' -X DELETE  -D - http://localhost:9130/_/proxy/tenants/diku/modules/mod-waitlist-1.0.0
    # unregister mod-waitlist
    curl -w '\n' -X DELETE  -D - http://localhost:9130/_/proxy/modules/mod-waitlist-1.0.0
    ```
1. List registered modules.
    ```bash
    # list deployed modules
    curl -i -w '\n' -X GET http://localhost:9130/_/discovery/modules
    # list modules enabled for diku user
    curl -i -w '\n' -X GET http://localhost:9130/_/proxy/tenants/diku/modules
    # list modules registered with okapi
    curl -i -w '\n' -X GET http://localhost:9130/_/proxy/modules
    ```
