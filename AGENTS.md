# AGENTS.md

Fabric mod for Minecraft 26.3 (Java 25) adding a Mining Golem that digs an endless ring
strip-mine: collects loot, places torches, bridges holes. Entry point:
`src/main/java/com/michnhokn/mininggolem/MiningGolem.java`. Behavior spec: `README.md`.

## Ground rules

- **Every new feature goes through a PR** — never commit feature work directly to
  `main`, no matter how small. Fixes should go through PRs too.
- **Check the version before merging to `main`**: if a branch changes
  `version=` in `gradle.properties`, the new number must be strictly *newer* than
  the one currently on `main` (`git show origin/main:gradle.properties`). Never
  merge a downgrade, and never merge a release with an unchanged version number.
- **`README.md` is written for Modrinth readers**: a release syncs it to the
  Modrinth project description, so it must read as if the mod was downloaded from
  Modrinth — describe in-game behavior only, no "drop the jar into your `mods`
  folder" install steps, and use absolute image URLs (e.g.
  `https://raw.githubusercontent.com/…`) so images also render on Modrinth.

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
  Bump the version in the release commit (`chore: release x.y.z`). Tags (`v x.y.z`)
  go on `main` only when a version is actually released — never on feature branches.

## Before opening a PR

```
./gradlew build        # must pass (includes compileClientJava)
```

`README.md` documents player-facing behavior — update it when behavior changes.
