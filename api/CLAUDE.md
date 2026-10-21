# CLAUDE.md — api (Spring Boot backend)

**Database:**
```bash
createdb funnyapp
psql -d funnyapp -f db/dump.sql
```

**Docker:**
```bash
cd api
mvn clean package
docker build -t funny-app .
docker run -p 8081:8081 --env-file env.local funny-app
```
