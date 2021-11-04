# Funny Moives

### Description
This is web application to share the Youtube videos

### Set up & Installation
#### Installation:
- OpenJDK/JDK 11 and export JAVA_HOME
- Maven >=3.6 and export MAVEN_HOME
- PostgresSQL.
- Generate the GOOGE API KEY to use their API [link](https://console.cloud.google.com/apis/api/youtube.googleapis.com/credentials)


### Running Appp on LocalHost:
- Change file api/.env.example to api/.env and configure database info for postgres sql
```shell
DB_USER=postgres
DB_NAME=asssement
DB_PASS=[pass]
DB_HOST=[host]
GOOGLE_KEY=[key]
JWT_SECRET=[jwt_secret]
```
- Start app:
```shell
cd api
./startLocal 
```

#### To visit the endpoints running on the LocalHost:
`http://localhost:8081/swagger-ui/`

`http://localhost:8081/actutor/health`

#### To visit the  web LocalHost:
`Change baseURL in webapp/jsassesement.js to http://localhost:8081/v1/assessment`

`Open webapp/index.html`

#### To visit demo:
Access https://assessment.canh-labs.com

#### To run tests:
```shell
cd api
./unittest
```

