# AGENTS.md

Fabric mod for Minecraft 26.3 (Java 25) adding a Mining Golem that digs an endless ring
strip-mine: collects loot, places torches, bridges holes. Entry point:
`src/main/java/com/michnhokn/mininggolem/MiningGolem.java`. Behavior spec: `README.md`.

## Workflow

- **Features go on new branches**, never on the main branch.
  Branch name: `feat/<short-name>` (fixes: `fix/<short-name>`).
- **Conventional commits**: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:` —
  e.g. `feat: add vein mining for exposed ores`.
- **PRs** into `main`, one feature/fix per PR, PR title = conventional commit style.
- **Semantic versioning** in `gradle.properties` (`version=…`), Minecraft-mod style:
  - **MAJOR** — new Minecraft version support / breaking behavior changes
  - **MINOR** — new features
  - **PATCH** — bug fixes
  Bump the version in the release commit (`chore: release x.y.z`), tag `v x.y.z`.

## Before opening a PR

```
./gradlew build        # must pass (includes compileClientJava)
```

`README.md` documents player-facing behavior — update it when behavior changes.
