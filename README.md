# plugin-grounds-platform

In-pod Paper plugin that connects a user-deployed Minecraft server to
the Grounds Platform. Bundled into the official `paper` baseImage and
loaded automatically alongside the user's own plugin.

## What it does

Reads four env vars set by [grounds-forge][forge]'s deploy renderer:

| Env | Purpose |
|---|---|
| `GROUNDS_PROJECT_ID` | Project the workload belongs to. |
| `GROUNDS_PROJECT_NAME` | Used for the project-aware MOTD. |
| `GROUNDS_FORGE_URL` | In-cluster URL the plugin polls for whitelist updates. |
| `GROUNDS_TOKEN` | Workload-scoped service-account token. Mounted from a K8s Secret, never logged. |

When all four are set the plugin:

1. Replaces the default Paper MOTD with the project name + `via Grounds`.
2. Enables the server whitelist (idempotent — `whitelist=true` only when off).
3. Polls `GET /v1/projects/<id>/whitelist` every 30s and reconciles the
   local whitelist against the snapshot. Mojang UUIDs from the forge
   side are the source of truth; usernames are display-only.

When any of the env vars is missing the plugin logs one WARN line and
stays inert — the user's gameplay is unaffected.

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
