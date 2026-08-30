# nginx crash/traffic alerting (systemd + fail2ban, provider-agnostic)

Alerts when `nginx.service` fails, when traffic looks abnormal, and
auto-bans IPs that hit vulnerability-scan paths or send excessive request
volume. Files in this directory are templates to install on the server —
nothing here runs as part of the app build/deploy pipeline.

Notifications go through a provider abstraction (`lib-notify.sh` +
`providers/<name>.sh`) so the alert/ban scripts never talk to a specific
service directly — see "Switching notification provider" below. Telegram
is the only provider implemented today.

## Quick deploy: run-all.sh

Sections 2-6 below can be done manually, but `run-all.sh` automates all of
it over SSH from your local machine in one shot — deploy + verify,
idempotent (safe to re-run; never overwrites existing credential files).

```bash
cd scripts/monitoring/nginx-alert
cp server.config.example server.config   # git-ignored, fill in your server
./run-all.sh
```

`server.config`:
```bash
SSH_HOST=203.0.113.10
SSH_USER=root
SSH_PORT=22
# Windows-style D:\... paths are auto-converted to Git-Bash form, but MUST
# be single-quoted here or bash eats the backslashes while sourcing this file.
SSH_KEY_PATH='/path/to/your/private_key'
```

First run creates `/etc/nginx-alert/telegram.env` on the server with
`REPLACE_ME` placeholders (it will warn you to fill it in and re-run) —
after that, it prints a pass/fail checklist and, once real credentials are
in place, fires a real end-to-end delivery test. The step-by-step sections
below are what `run-all.sh` does under the hood — read them if you want to
understand or debug a specific piece, or to add fail2ban (section 6) if
you skipped it initially since `run-all.sh` deploys everything together.

## Repo → server file map

Every path below is created by the `sudo cp`/`sudo tee` commands in the
install steps further down — nothing deploys itself. Use this as a
checklist of what should exist on the box once everything is installed.

```
/usr/local/bin/
├── lib-notify.sh                 ← lib-notify.sh
├── nginx-alert-notify.sh         ← nginx-alert-notify.sh
├── nginx-traffic-alert.sh        ← nginx-traffic-alert.sh
├── fail2ban-notify.sh            ← fail2ban/fail2ban-notify.sh
└── providers/
    └── telegram.sh               ← providers/telegram.sh
    (slack.sh, etc. — see providers/slack.sh.example)

/etc/nginx-alert/                 (created by install steps, not in repo)
├── notify.env                    → ALERT_PROVIDER=telegram
├── telegram.env                  → TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID (secret, 600)
└── traffic.env                   → ACCESS_LOG / THRESHOLD_* / ALERT_COOLDOWN_SEC

/var/lib/nginx-alert/             (runtime state, auto-created by the scripts)
├── access.offset
├── last-total-alert
└── last-ip-alert

/etc/systemd/system/
├── nginx-alert.service           ← nginx-alert.service
├── nginx-traffic-alert.service   ← nginx-traffic-alert.service
├── nginx-traffic-alert.timer     ← nginx-traffic-alert.timer
└── nginx.service.d/
    └── override.conf             ← nginx-override.conf

/etc/fail2ban/
├── filter.d/
│   ├── nginx-exploit-probe.conf  ← fail2ban/nginx-exploit-probe.filter
│   └── nginx-req-limit.conf      ← fail2ban/nginx-req-limit.filter
├── action.d/
│   └── notify.conf               ← fail2ban/notify.action
└── jail.d/
    └── nginx-custom.conf         ← fail2ban/nginx-custom.jail
```

## How it behaves

- `Restart=always` (in `nginx-override.conf`) lets nginx self-heal from a
  single crash with no alert — that's expected/normal restart behavior.
- `OnFailure=nginx-alert.service` only fires once systemd gives up
  restarting within the burst window (`StartLimitBurst=3` crashes in
  `StartLimitIntervalSec=60` seconds by default) — i.e. a real crash loop,
  not a one-off blip. Tune those two values if you want a more/less
  sensitive trigger.

## 1. Create a Telegram bot and get your chat ID

1. Message [@BotFather](https://t.me/BotFather) on Telegram, run `/newbot`,
   follow the prompts, and copy the bot token it gives you.
2. Send any message to your new bot (search its username, hit Start).
3. Fetch your chat ID:
   ```bash
   curl -s "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates" | grep -o '"chat":{"id":[0-9-]*'
   ```
   The number after `"id":` is `TELEGRAM_CHAT_ID`.

## 2. Install on the server

```bash
# 1. Notify dispatcher + telegram provider
sudo mkdir -p /usr/local/bin/providers
sudo cp lib-notify.sh nginx-alert-notify.sh /usr/local/bin/
sudo cp providers/telegram.sh /usr/local/bin/providers/
sudo chmod 755 /usr/local/bin/lib-notify.sh /usr/local/bin/nginx-alert-notify.sh \
  /usr/local/bin/providers/telegram.sh

# 2. Which provider to use (defaults to telegram if this file is absent)
sudo mkdir -p /etc/nginx-alert
sudo tee /etc/nginx-alert/notify.env >/dev/null <<'EOF'
ALERT_PROVIDER=telegram
EOF

# 3. Telegram credentials — NOT committed to git, root-only readable
sudo tee /etc/nginx-alert/telegram.env >/dev/null <<'EOF'
TELEGRAM_BOT_TOKEN=<your-bot-token>
TELEGRAM_CHAT_ID=<your-chat-id>
EOF
sudo chmod 600 /etc/nginx-alert/telegram.env

# 4. Alert unit
sudo cp nginx-alert.service /etc/systemd/system/nginx-alert.service

# 5. nginx.service override
sudo mkdir -p /etc/systemd/system/nginx.service.d
sudo cp nginx-override.conf /etc/systemd/system/nginx.service.d/override.conf

# 6. Reload systemd
sudo systemctl daemon-reload
sudo systemctl restart nginx
```

## 3. Test it

```bash
# Fire the alert unit directly to confirm delivery works
sudo systemctl start nginx-alert.service

# Simulate a real crash loop (nginx will restart 3x within 60s, then alert)
for i in 1 2 3; do sudo pkill -9 -f "nginx: master"; sleep 1; done
```

You should receive a message within a few seconds of the third kill.
Check `systemctl status nginx` and `systemctl status nginx-alert` if
nothing arrives — the script logs curl errors to the unit's journal
(`journalctl -u nginx-alert`).

## 4. Verification checklist

Run each command; expected output noted after `→`.

```bash
# Scripts exist and are executable
test -x /usr/local/bin/nginx-alert-notify.sh && echo OK-script
test -x /usr/local/bin/providers/telegram.sh && echo OK-provider
# → OK-script / OK-provider

# Secrets file exists, root-only, no leftover placeholders
sudo stat -c '%a %U:%G' /etc/nginx-alert/telegram.env
# → 600 root:root
sudo grep -c REPLACE_ME /etc/nginx-alert/telegram.env
# → 0

# Alert unit + override files are in place
test -f /etc/systemd/system/nginx-alert.service && echo OK-alert-unit
test -f /etc/systemd/system/nginx.service.d/override.conf && echo OK-override
# → OK-alert-unit / OK-override

# systemd has picked up the override (Restart + OnFailure applied)
systemctl show nginx.service -p Restart -p OnFailure
# → Restart=always
# → OnFailure=nginx-alert.service

# nginx itself is healthy right now
sudo systemctl is-active nginx
# → active

# End-to-end: fire the alert unit through systemd and confirm it exits clean
sudo systemctl start nginx-alert.service
systemctl show nginx-alert.service -p Result
# → Result=success
# (also confirm the message actually arrived at your notification provider)
```

If any step deviates from the expected output, see the Troubleshooting
notes above (`journalctl -u nginx-alert`, `journalctl -u nginx`) before
moving on.

## 5. Traffic anomaly alerts (optional)

Separate from the crash alert above: `nginx-traffic-alert.timer` runs every
minute, diffs `access.log` growth since the last run, and alerts when
either:

- **total requests** in the last minute exceed `THRESHOLD_TOTAL` (message
  includes the busiest IP for context), or
- **a single IP** accounts for more than `THRESHOLD_IP` requests in the
  last minute (message includes that IP's top 3 called endpoints, so you
  can tell a login brute-force apart from generic scraping).

Each alert type has its own cooldown (`ALERT_COOLDOWN_SEC`, default 10 min)
so a sustained spike pings once, not every minute. Assumes the default
nginx combined/common log format (client IP is the first field, request
line is the `"$request"` quoted field).

### Install

```bash
# 1. Script (reuses lib-notify.sh + provider installed in step 2 above)
sudo cp nginx-traffic-alert.sh /usr/local/bin/nginx-traffic-alert.sh
sudo chmod 755 /usr/local/bin/nginx-traffic-alert.sh

# 2. Thresholds — tune these to your real traffic before relying on them
sudo tee /etc/nginx-alert/traffic.env >/dev/null <<'EOF'
ACCESS_LOG=/var/log/nginx/access.log
THRESHOLD_TOTAL=600
THRESHOLD_IP=200
ALERT_COOLDOWN_SEC=600
EOF
sudo chmod 600 /etc/nginx-alert/traffic.env

# 3. Timer + service
sudo cp nginx-traffic-alert.service /etc/systemd/system/nginx-traffic-alert.service
sudo cp nginx-traffic-alert.timer /etc/systemd/system/nginx-traffic-alert.timer

# 4. Enable and start the timer
sudo systemctl daemon-reload
sudo systemctl enable --now nginx-traffic-alert.timer
```

### Verify

```bash
sudo systemctl list-timers nginx-traffic-alert.timer
# → shows NEXT run within ~1 minute

# Run one check manually and inspect its output
sudo systemctl start nginx-traffic-alert.service
sudo journalctl -u nginx-traffic-alert.service -n 20 --no-pager

# Force a total-traffic alert to confirm delivery end-to-end
# (lower the threshold temporarily, then generate load)
sudo sed -i 's/THRESHOLD_TOTAL=.*/THRESHOLD_TOTAL=1/' /etc/nginx-alert/traffic.env
for i in $(seq 1 5); do curl -s -o /dev/null https://<your-domain>/; done
sudo systemctl start nginx-traffic-alert.service
# → alert message "⚠️ nginx traffic spike..." should arrive
# revert the threshold afterwards:
sudo sed -i 's/THRESHOLD_TOTAL=.*/THRESHOLD_TOTAL=600/' /etc/nginx-alert/traffic.env
```

### Tuning notes

- Start with generous thresholds and tighten after watching a few days of
  real traffic (`journalctl -u nginx-traffic-alert` logs nothing on quiet
  runs by design — check `wc -l access.log` growth manually if you want a
  baseline).
- `THRESHOLD_IP` will false-positive on shared-NAT clients (corporate
  networks, mobile carriers) or legitimate crawlers/monitoring bots —
  consider an allowlist if that becomes noisy.
- This only *alerts*; it does not block anything. See section 6 below for
  the fail2ban setup that actually auto-bans offending IPs.

## 6. Auto-block with fail2ban (optional)

Two fail2ban jails that firewall-block (not just alert on) abusive IPs,
notifying through the same provider abstraction. Assumes fail2ban is
already installed (`fail2ban-client --version`).

- **`nginx-exploit-probe`**: bans on the 2nd request (within 10 min) for a
  path that only exists in vulnerability-scanner probes (`.env`, `.git`,
  `.sql`, `.php`, `wp-*`, `phpmyadmin`, `adminer`). Since this app is
  Java/Spring Boot and serves none of these, false positives are
  essentially impossible — bantime 7 days.
- **`nginx-req-limit`**: bans any IP exceeding `maxretry` requests/minute
  regardless of path — catches volumetric abuse the path-based jail
  misses. Default `maxretry=1000` (tune in `jail.d/nginx-custom.conf`);
  keep this generous — the app serves video over Range requests, which
  can burst many requests per real user in a short window. bantime 1h
  (shorter than the exploit jail, since this one carries more
  false-positive risk).

Both jails run an extra `notify` action (ban/unban) alongside fail2ban's
normal iptables action — see `fail2ban/fail2ban-notify.sh` /
`fail2ban/notify.action`.

### Install

```bash
# 1. Filters
sudo cp fail2ban/nginx-exploit-probe.filter /etc/fail2ban/filter.d/nginx-exploit-probe.conf
sudo cp fail2ban/nginx-req-limit.filter /etc/fail2ban/filter.d/nginx-req-limit.conf

# 2. Notify action + its helper script (reuses lib-notify.sh from step 1)
sudo cp fail2ban/fail2ban-notify.sh /usr/local/bin/fail2ban-notify.sh
sudo chmod 755 /usr/local/bin/fail2ban-notify.sh
sudo cp fail2ban/notify.action /etc/fail2ban/action.d/notify.conf

# 3. Jail definitions
sudo cp fail2ban/nginx-custom.jail /etc/fail2ban/jail.d/nginx-custom.conf

# 4. Validate + apply
sudo fail2ban-client -t
sudo systemctl restart fail2ban
```

Use `restart`, not `reload` — fail2ban does not reliably pick up an
*action list* change (adding/removing `notify` here) on reload for a jail
that's already running, only on a full restart. Bans persist across the
restart via fail2ban's sqlite ban database, **except** IPs banned purely
via manual `fail2ban-client ... banip` with no matching filter ticket —
those can be dropped; re-ban them afterward if needed:
```bash
fail2ban-client get nginx-exploit-probe actions   # confirm "notify" is listed
fail2ban-client status nginx-exploit-probe        # re-ban any IP missing from here
```

### Verify

```bash
sudo fail2ban-client status
# → nginx-exploit-probe, nginx-req-limit, (sshd, ...)

sudo fail2ban-client status nginx-exploit-probe
sudo fail2ban-client status nginx-req-limit
# → Currently failed / Currently banned counts

# Dry-run a filter's regex against the real log without waiting for a live hit
sudo fail2ban-regex /var/log/nginx/access.log /etc/fail2ban/filter.d/nginx-exploit-probe.conf
```

fail2ban does **not** retroactively scan the existing log on startup — it
only watches for new lines going forward. If you already identified
malicious IPs (e.g. from the traffic-spike alerts above), ban them
immediately instead of waiting for them to re-offend:

```bash
sudo fail2ban-client set nginx-exploit-probe banip <ip>
sudo fail2ban-client status nginx-exploit-probe   # confirm it's listed
sudo iptables -L f2b-nginx-exploit-probe -n        # confirm the real firewall rule
# to undo: sudo fail2ban-client set nginx-exploit-probe unbanip <ip>
```

## 7. Switching notification provider

Every alert/ban script in this toolkit (`nginx-alert-notify.sh`,
`nginx-traffic-alert.sh`, `fail2ban/fail2ban-notify.sh`) only ever calls
one function: `send_alert_message "text"`. None of them know or care what
actually delivers the message — that's resolved at runtime by
`lib-notify.sh`, which reads `ALERT_PROVIDER` from
`/etc/nginx-alert/notify.env` (defaults to `telegram`) and sources
`providers/<name>.sh`.

To add a new provider (e.g. Slack):

```bash
# 1. Copy the example and implement send_alert_message() for the new service
sudo cp providers/slack.sh.example /usr/local/bin/providers/slack.sh
sudo chmod 755 /usr/local/bin/providers/slack.sh
sudo $EDITOR /usr/local/bin/providers/slack.sh   # fill in real logic if the example isn't enough

# 2. Credentials for the new provider, its own file (keeps old provider's
#    creds intact so you can switch back without re-entering anything)
sudo tee /etc/nginx-alert/slack.env >/dev/null <<'EOF'
SLACK_WEBHOOK_URL=<your-webhook-url>
EOF
sudo chmod 600 /etc/nginx-alert/slack.env

# 3. Flip the switch — no restart of alert scripts needed, they read this
#    file fresh on every invocation (crash-alert is event-driven, traffic
#    check runs every minute anyway)
sudo sed -i 's/ALERT_PROVIDER=.*/ALERT_PROVIDER=slack/' /etc/nginx-alert/notify.env

# 4. Verify
sudo systemctl start nginx-alert.service   # should now arrive via Slack
```

`providers/slack.sh.example` in this directory is a minimal, untested
skeleton showing the contract (`send_alert_message()` + its own creds
file) — treat it as a starting point, not a finished integration.
