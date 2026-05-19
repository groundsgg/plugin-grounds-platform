# plugin-grounds-platform

In-pod Paper plugin that connects a user-deployed Minecraft server to
the Grounds Platform. Bundled into the official `paper` baseImage and
loaded automatically alongside the user's own plugin.

## What it does

Reads env vars set by [grounds-forge][forge]'s deploy renderer:

| Env | Purpose |
|---|---|
| `GROUNDS_PROJECT_ID` | Project the workload belongs to. |
| `GROUNDS_PROJECT_NAME` | Used for the project-aware MOTD. |
| `GROUNDS_APP_NAME` | Deployment name used for app-scoped platform APIs. |
| `GROUNDS_PUSH_ID` | Push identity used to scope command leases to the running workload version. |
| `GROUNDS_FORGE_URL` | In-cluster URL the plugin polls for platform updates. |
| `GROUNDS_TOKEN` | Workload-scoped service-account token. Mounted from a K8s Secret, never logged. |

When the required platform context is set the plugin:

1. Replaces the default Paper MOTD with the project name + `via Grounds`.
2. Enables the server whitelist (idempotent — `whitelist=true` only when off).
3. Polls `GET /v1/projects/<id>/whitelist` every 30s and reconciles the
   local whitelist against the snapshot. Mojang UUIDs from the forge
   side are the source of truth; usernames are display-only.
4. Long-polls Forge for queued console commands, executes each command as
   the Paper console, and posts the execution result back to Forge.

When the base platform env vars are missing the plugin logs one WARN line and
stays inert — the user's gameplay is unaffected. Command polling is disabled
separately when `GROUNDS_PUSH_ID` or `GROUNDS_TOKEN` is missing.

## Build

```bash
./gradlew shadowJar
```

The resulting fat-jar lives at `build/libs/plugin-grounds-platform-<version>-all.jar`.

## Test

```bash
./gradlew test
```

## Release

`release-please` opens release PRs as commits land on `main`. Merging a
release PR tags `vX.Y.Z`, which triggers `.github/workflows/release.yml`
to build the shadow JAR and attach it to the GitHub release.

[forge]: https://github.com/groundsgg/grounds-forge
