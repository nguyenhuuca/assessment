# Sequence Diagrams: Frontend Dependency Upgrade

- **PRD:** [PRD-frontend-deps-upgrade](../prd/PRD-frontend-deps-upgrade.md)
- **ADR:** [0034-frontend-deps-upgrade](../adr/0034-frontend-deps-upgrade.md)
- **Spec:** [spec-frontend-deps-upgrade](../specs/spec-frontend-deps-upgrade.md)
- **Status:** Draft

This change has no runtime API flow of its own. The diagrams show the upgrade workflow, the CI delivery path it changes, and the two runtime behaviours the upgrade must preserve (lazy `hls.js` playback and the PWA new-version check).

## 1. Upgrade and verify in groups, land as one commit

The builder upgrades one package group at a time and runs the gates after each; only when every group is green are the changes committed as one commit (spec Rule 7).

```mermaid
sequenceDiagram
    autonumber
    actor B as Builder
    participant NPM as npm (webapp/)
    participant G as Gates (lint, vitest, build)
    participant Git as git (main)

    loop group in [in-range, build/test toolchain, lint toolchain, FontAwesome removal]
        B->>NPM: bump versions in package.json (hls.js exact 1.7.4)
        B->>NPM: rm -rf node_modules package-lock.json && npm install
        alt ERESOLVE peer conflict (EC-2)
            NPM-->>B: conflict
            B-->>B: stop, report package, no --force
        else installed
            NPM-->>B: lockfile regenerated
        end
        B->>G: npm run lint && npx vitest run --root . && npm run build
        G-->>B: results
        opt new react-hooks 7 errors (Rule 6)
            alt <= 10 and local fixes
                B->>B: fix code
            else > 10 or needs refactor
                B->>B: rule -> warn in eslint.config.js + TASKS.md follow-up
            end
        end
    end
    B->>NPM: npm outdated
    NPM-->>B: empty
    B->>NPM: npm ci (from committed lockfile)
    B->>Git: one commit (deps, configs, CI Node 22, docs, measurements in body)
```

## 2. Delivery: push to main builds on Node 22

```mermaid
sequenceDiagram
    autonumber
    actor O as Owner
    participant GH as GitHub Actions (deploy-web.yml)
    participant N as setup-node@v4 (node 22)
    participant V as Vite 8 build
    participant Host as Static hosting

    O->>GH: approve push to main
    GH->>N: node-version '22', npm cache
    N->>GH: npm ci (webapp/)
    GH->>V: npm run build
    V-->>GH: dist/ (index.html, assets/index-*.js, assets/hls-*.js, sw.js, manifest.json, version.json)
    alt build fails only on Node 22 (EC-6)
        GH-->>O: red run
        O->>O: reproduce under Node 22 locally
    else green
        GH->>Host: deploy dist/
        O->>Host: prod smoke (login, feed, HLS + Range, admin, i18n, PWA banner)
    end
```

## 3. Preserved: lazy hls.js playback

`hls.js` must remain a separate chunk fetched only when a video has `hlsUrl` (ADR-0031); Vite 8's bundler must not merge it into the main chunk (EC-3).

```mermaid
sequenceDiagram
    autonumber
    participant P as VideoPlayer.jsx
    participant H as useHlsSource (src/hooks/useHlsSource.js)
    participant Br as Browser
    participant API as /v1/funny-app/video-stream

    P->>H: useHlsSource(videoRef, { hlsUrl, rangeUrl })
    alt no hlsUrl
        H-->>P: src = rangeUrl
        P->>API: GET /stream/{fileId} (Range)
    else native HLS
        H-->>P: src = hlsUrl
        P->>API: GET /hls/{fileId}/playlist.m3u8
    else needs hls.js
        H->>Br: import('hls.js') -> fetch assets/hls-*.js
        Br-->>H: Hls
        H->>API: hls.loadSource(playlist.m3u8)
        opt fatal error
            H-->>P: fallback to rangeUrl
        end
    end
```

## 4. Preserved: PWA new-version check

The custom `version-file` Vite plugin and the `__APP_BUILD__` define must keep producing a matching pair after the upgrade.

```mermaid
sequenceDiagram
    autonumber
    participant V as vite.config.js (version-file plugin)
    participant D as dist/version.json
    participant U as useNewVersion (src/pwa/useNewVersion.js)
    participant S as Static hosting

    V->>D: emit {"build": GITHUB_SHA[0:7]}
    V->>V: define __APP_BUILD__ = same sha7
    U->>S: GET /version.json?t=now (no-store)
    S-->>U: {"build": sha7}
    alt build differs from __APP_BUILD__
        U-->>U: show new-version banner
    else same
        U-->>U: no banner
    end
```
