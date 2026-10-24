# Postmortem: API hang after deploy (virtual-thread class-init deadlock)

- **Date:** 2026-10-24
- **Severity:** High. The whole API was unavailable until a manual restart.
- **Status:** Resolved. Code fix `7c1239b9` is deployed. The extracted-jar hardening was considered and not adopted (owner, 2026-10-24).
- **Related:** first occurrence 2026-10-15 (no report; root cause and JVM-flag fix recorded in the project notes).

## Summary

After the deploy of `ef87ed84` (yt-dlp H.264 format + HLS codec log), the backend started normally (`Started FunnyApp in 82.78 seconds`). About 20 s later it stopped answering anything, including `localhost:8081/actuator/health`. The process stayed alive, listening on 8081 at near-zero CPU, and its log stopped at 05:15:05 UTC. Browsers saw timeouts. Because nginx error pages carry no CORS headers, a hang like this can also look like CORS errors.

The cause was a deadlock between **class initialisation on virtual threads** and the **Spring Boot nested-jar class loader**. It was triggered by the first burst of authenticated requests after startup. The code in the deploy itself was not involved.

## Impact

- The API was down for every user from about 05:15 UTC until the manual restart. The frontend could not load videos or log in.
- No data loss. Requests were never processed, so nothing was half-written.
- `mock-test.sh` after the deploy timed out. That is how the hang was found.

## Timeline (UTC, from `/opt/funnyapp/logs/funny-app.log` and CI)

| Time | Event |
|------|-------|
| ~05:13 | CI "Funny App Build and Deploy" for `ef87ed84` stops the old app and starts the new jar |
| 05:14:48 | `Started FunnyApp in 82.78 seconds`. Tomcat accepts requests on 8081 |
| 05:14:59 | First request initialises the `DispatcherServlet` |
| 05:15:05 | Last log line (`getTopVideos … isVirtual: true`). Nothing is logged after this |
| ~05:20 | Smoke test (`mock-test.sh`) and a direct `curl` time out after 15 s |
| 05:22–05:24 | Diagnosis over SSH: process alive, load 0.02, `localhost:8081` times out, thread dump taken (`/tmp/td-052331.txt`) |
| later | Owner restarts the app (`stopServer.sh` + `startServer.sh`). API back to normal |
| later | Fix `7c1239b9` deployed. CI green, API answered 200 within about 2 min of the deploy, smoke test 22/22 |

## Root cause

The JVM runs Tomcat request handlers on virtual threads, with 4 carrier threads (`-Djdk.virtualThreadScheduler.parallelism=4`, set after the 2026-10-15 incident).

The thread dump (`jcmd <pid> Thread.dump_to_file -format=text`) showed all 4 carriers busy and 91 `tomcat-handler` virtual threads. 86 of those had empty stacks: they were created but never got a carrier.

The threads that did run:

| Virtual thread | Top of stack | Meaning |
|----------------|--------------|---------|
| `tomcat-handler-7`, `-9`, `-10` | `DefaultJwtParserBuilder.<init>` ← `Jwts.parser()` ← `JwtProvider.verifyToken:65` | Waiting for another thread to finish the class init of `io.jsonwebtoken.Jwts$ENC`. A thread waiting on class init **pins its carrier**. |
| `tomcat-handler-8` | `NestedJarFile.hasEntry` ← … ← `Jwts$ENC.<clinit>` | The initialiser. It needs a class from the fat jar and is blocked in the Spring Boot nested-jar loader |
| `tomcat-handler-11` | `FileDataBlock$FileAccess.ensureOpen` ← `NestedJarFile.hasEntry` | Holds or waits for the loader's file-access lock |

What happened, step by step:
1. Right after startup, several authenticated requests arrive together. Each calls `Jwts.parser()` for the first time in the JVM.
2. One thread starts the jjwt class init. The others wait for it, and each waiting thread pins a carrier, which took 3 of the 4.
3. The initialiser needs the Spring Boot loader's locks (`NestedJarFile` / `FileDataBlock`), which are contended by another virtual thread. Progress needs a free carrier and the lock at the same time, and neither comes free.
4. No carrier is left for the remaining virtual threads, including the ones that would release the lock. The JVM hangs for good. Even `/actuator/health` never runs.

### Why the 2026-10-15 fix was not enough

On 2026-10-15 the property name was wrong and the JVM had a single carrier. Raising the carriers to 4 makes the deadlock **less likely**, not impossible: it only needs as many threads waiting on the class init as there are carriers, and any deploy that gets a burst of authenticated requests right after startup can still hit it.

## Resolution

1. **Immediate:** manual restart by the owner. The class init then happened to run before a concurrent burst.
2. **Code fix, `7c1239b9`** (`api/src/main/java/com/canhlabs/funnyapp/filter/JwtProvider.java`):
   - `JwtProvider.warmUp()` runs in `@PostConstruct`.
   - It signs and parses one token with the configured key, which loads `Jwts`, `Jwts$SIG/ENC/KEY` and the JSON reader.
   - This happens on the main platform thread during bean creation, before Tomcat accepts requests. The first real request no longer runs the jjwt class init on a virtual thread.
   - A failure only logs `JWT warm-up failed: …` and never blocks startup.
   - Tests: `JwtProviderTest.warmUp_signsAndParsesOnceWithTheConfiguredKey` and `warmUp_neverFailsStartup_whenTheKeyIsInvalid`. `mvn verify`: 1792 tests green.
3. **Check after deploy:** API 200 within about 2 min of the deploy, smoke test 22/22. The startup log should show `Started FunnyApp` and no `JWT warm-up failed`.

## Alternative considered: run Spring Boot extracted

Spring Boot recommends running the extracted form in production ([Efficient Deployments → Unpacking the Executable jar](https://docs.spring.io/spring-boot/reference/packaging/efficient.html)):

> "loading the classes from nested jars has a small startup cost … running the application from an exploded structure is faster and recommended in production."

Running `java -Djarmode=tools -jar funny-app-1.0.0.jar extract`, then `java -jar <dir>/funny-app-1.0.0.jar`, would have these effects:
- It loads libraries from plain `lib/*.jar` with the JDK class loader, so the `NestedJarFile` / `FileDataBlock` locks from the dump are gone for every class, not only jjwt.
- It starts faster, and it allows an AOT cache or CDS later. Both work only with the extracted form ([AOT cache](https://docs.spring.io/spring-boot/reference/packaging/aot-cache.html)).

The Spring docs give **startup time** as the reason. That this also removes the deadlock is our inference from the thread dump, and it has not been tested on prod.

**Decision (owner, 2026-10-24):** not adopted for now. It needs changes to `startServer.sh` and the server-side deploy script, outside the repo. Revisit if the hang recurs with a stack that does not involve jjwt.

## What went well

- `Thread.dump_to_file` showed virtual threads, so the exact frames were visible. `jstack` and `Thread.print` show only platform threads.
- The project notes from 2026-10-15 pointed straight to the right diagnosis: process alive, log stopped, localhost timing out.
- The smoke test after every deploy caught the hang within minutes.

## What went wrong

- The 2026-10-15 fix treated a symptom (the carrier count) without removing the deadlock. Nobody wrote a postmortem then, so the "less likely, not impossible" risk was never written down.
- CI reports green as soon as the deploy script finishes. There is no post-deploy health gate, so a hung app looks like a successful deploy.

## Follow-ups

| Action | Owner | Status |
|--------|-------|--------|
| Warm up jjwt at startup (`7c1239b9`) | Claude | Done |
| Watch the next deploys for a recurrence. On a hang, take `jcmd <pid> Thread.dump_to_file -format=text` before restarting | nguyenhuuca | Open |
| If a hang recurs with another class-init stack, switch the VM to the extracted jar (server scripts) | nguyenhuuca | Deferred |
| Consider a post-deploy health check in the deploy script: curl `localhost:8081/actuator/health` with a timeout, restart once on failure | nguyenhuuca | Proposed |

## Appendix: commands used

```bash
# process, ports, load
uptime; ps -eo pid,etime,pcpu,pmem,args --sort=-pcpu | head; ss -ltnp | grep 8081
# does the app answer locally?
curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" --max-time 10 http://localhost:8081/actuator/health
# thread dump including virtual threads
/opt/jdk/jdk-24/bin/jcmd <pid> Thread.dump_to_file -format=text /tmp/td-$(date +%H%M%S).txt
# virtual threads that have a stack (running or blocked), with their top frame
awk 'BEGIN{RS="\n\n"} /virtual/ && NF>4 {split($0,l,"\n"); print l[1] " | " l[2]}' /tmp/td-*.txt
```
