# nginx crash alert (systemd + Telegram)

Alerts a Telegram chat when `nginx.service` fails on the VM. Files in this
directory are templates to install on the server — nothing here runs as
part of the app build/deploy pipeline.

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
# 1. Scripts
sudo cp lib-telegram.sh nginx-alert-telegram.sh /usr/local/bin/
sudo chmod 755 /usr/local/bin/lib-telegram.sh /usr/local/bin/nginx-alert-telegram.sh

# 2. Secrets — NOT committed to git, root-only readable
sudo mkdir -p /etc/nginx-alert
sudo tee /etc/nginx-alert/telegram.env >/dev/null <<'EOF'
TELEGRAM_BOT_TOKEN=<your-bot-token>
TELEGRAM_CHAT_ID=<your-chat-id>
EOF
sudo chmod 600 /etc/nginx-alert/telegram.env

# 3. Alert unit
sudo cp nginx-alert.service /etc/systemd/system/nginx-alert.service

# 4. nginx.service override
sudo mkdir -p /etc/systemd/system/nginx.service.d
sudo cp nginx-override.conf /etc/systemd/system/nginx.service.d/override.conf

# 5. Reload systemd
sudo systemctl daemon-reload
sudo systemctl restart nginx
```

## 3. Test it

```bash
# Fire the alert unit directly to confirm Telegram delivery works
sudo systemctl start nginx-alert.service

# Simulate a real crash loop (nginx will restart 3x within 60s, then alert)
for i in 1 2 3; do sudo pkill -9 -f "nginx: master"; sleep 1; done
```

You should receive a Telegram message within a few seconds of the third
kill. Check `systemctl status nginx` and `systemctl status nginx-alert`
if nothing arrives — the script logs curl errors to the unit's journal
(`journalctl -u nginx-alert`).

## 4. Verification checklist

Run each command; expected output noted after `→`.

```bash
# Script exists and is executable
test -x /usr/local/bin/nginx-alert-telegram.sh && echo OK-script
# → OK-script

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

# Manual Telegram delivery check (bypasses systemd, shows the raw API response)
source /etc/nginx-alert/telegram.env
curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  --data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" --data-urlencode "text=verify"
unset TELEGRAM_BOT_TOKEN TELEGRAM_CHAT_ID
# → {"ok":true, ...}

# End-to-end: fire the alert unit through systemd and confirm it exits clean
sudo systemctl start nginx-alert.service
systemctl show nginx-alert.service -p Result
# → Result=success
# (also confirm the Telegram message actually arrived in the app)
```

If any step deviates from the expected output, see the Troubleshooting
notes above (`journalctl -u nginx-alert`, `journalctl -u nginx`) before
moving on.

## 5. Traffic anomaly alerts (optional)

Separate from the crash alert above: `nginx-traffic-alert.timer` runs every
minute, diffs `access.log` growth since the last run, and alerts on Telegram
when either:

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
# 1. Script (reuses lib-telegram.sh installed in step 2 above)
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
# → Telegram message "⚠️ nginx traffic spike..." should arrive
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
using the existing Telegram bot for ban/unban notifications. Assumes
fail2ban is already installed (`fail2ban-client --version`).

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

Both jails run an extra `telegram` action (ban/unban) alongside fail2ban's
normal iptables action — see `fail2ban-telegram-notify.sh` /
`telegram.action`.

### Install

```bash
# 1. Filters
sudo cp fail2ban/nginx-exploit-probe.filter /etc/fail2ban/filter.d/nginx-exploit-probe.conf
sudo cp fail2ban/nginx-req-limit.filter /etc/fail2ban/filter.d/nginx-req-limit.conf

# 2. Telegram notify action + its helper script (reuses lib-telegram.sh from step 1)
sudo cp fail2ban/fail2ban-telegram-notify.sh /usr/local/bin/fail2ban-telegram-notify.sh
sudo chmod 755 /usr/local/bin/fail2ban-telegram-notify.sh
sudo cp fail2ban/telegram.action /etc/fail2ban/action.d/telegram.conf

# 3. Jail definitions
sudo cp fail2ban/nginx-custom.jail /etc/fail2ban/jail.d/nginx-custom.conf

# 4. Validate + reload
sudo fail2ban-client -t
sudo systemctl reload fail2ban
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
