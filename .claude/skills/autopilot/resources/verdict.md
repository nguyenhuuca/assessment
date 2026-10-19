# Autopilot Verdict Contract (D2)

Every review stage (plan review and code review) on either path must end its reply with exactly one verdict block. The orchestrator reads only this block to decide the next step. The review prose above it goes to the user as is.

## Block

````
```verdict
VERDICT: approve | revise
BLOCKING: <n>
ADVISORY: <n>
- [B] <file:line> — <finding> — <remediation>
- [A] <file:line> — <finding> — <suggestion>
```
````

- **[B] Blocking** = what `swarm-review` lists as Critical or High: a security issue, a broken requirement, missing tests for new logic, a violated tech-strategy or architecture rule, a breaking change with no migration. For a plan, it also covers a task without acceptance criteria, a fact with no `file:line`, and scope beyond the request.
- **[A] Advisory** = Medium and Low: naming, small refactors, follow-ups. Advisories never trigger another round.
- `BLOCKING` / `ADVISORY` must equal the number of `[B]` / `[A]` lines.

## Mapping From `swarm-review`

| `swarm-review` verdict | Block |
|------------------------|-------|
| ✅ Approved | `VERDICT: approve` |
| ⚠️ Needs Work with only Medium/Low items | `VERDICT: approve`, items as `[A]` |
| ⚠️ Needs Work with any Critical/High item, ❌ Request Changes | `VERDICT: revise`, those items as `[B]` |

## Parsing Rules (orchestrator)

1. Take the **last** fenced block tagged `verdict` in the reply.
2. Read `VERDICT`, `BLOCKING` and `ADVISORY` as `key: value` lines, ignoring case and spaces. Count the `[B]` and `[A]` lines.
3. **Normalize:**
   - `revise` with 0 `[B]` lines → `approve`, noted as "revise without blockers" in the ledger.
   - `approve` with any `[B]` line → `revise`. The findings win over the label.
   - Counts that disagree with the lines → use the line counts and add a note in the ledger.
4. **Missing or unreadable block:**
   - Ask the same reviewer once: "End with the verdict block from `.claude/skills/autopilot/resources/verdict.md`".
   - Still missing → stop the run with `status: blocked`, reason "review returned no verdict".
5. Pass **only the `[B]` lines** to the next Plan or Fix round. Collect the `[A]` lines for the gate summary.

## Example

````
```verdict
VERDICT: revise
BLOCKING: 1
ADVISORY: 2
- [B] api/src/main/java/com/canhlabs/funnyapp/web/AdminController.java:142 — PATCH accepts any int — reject bits outside Permission.ALL_BITS with 400
- [A] webapp/src/components/admin/AdminAccountTable.jsx:88 — chip label not translated — use the admin.json key
- [A] TASKS.md:270 — commit hash missing — add it when the task lands
```
````

This parses to `{verdict: revise, blocking: 1, advisory: 2}`, and only the `[B]` line goes to the Fix round.
