# NoMorePlaytime

Server-side playtime limit for Fabric, NeoForge and Forge, Minecraft 1.21.1 to 26.3, by SagMCU and Claude. Players have a time budget shown in a boss bar; at zero they are kicked and cannot rejoin until an admin gives them time or resets them. Everything players see or hear is in `config/nomoreplaytime/config.json`.

## Storage

`database.type` in the config picks where the times are kept: `H2` (default, `config/nomoreplaytime/data.mv.db`), `MYSQL` or `MARIADB` (fill in `host`, `port`, `name`, `user`, `password`; the database must already exist). Times are saved every `saveIntervalMinutes` (5), when a player leaves, after every admin command and when the server stops. Changing the type needs a restart and does not copy the existing data.

## Daily reset

Off by default. `dailyReset.time` is a 24-hour time and an optional zone, for example `"00:00 UTC"` (default), `"16:30 UTC+5"`, `"08:00 UTC-3"` or `"20:15 Europe/Madrid"`. Once a day, at that time, every player gets the maximum time back (paused timers stay paused).

## Permissions

With LuckPerms installed (all three loaders), every command has a node; without it, or when a node is not set, operators can use the admin commands and everybody can use `/timer on|off`. Setting a node to `false` denies it even to an operator.

| Node | Command | Default |
|---|---|---|
| `nomoreplaytime.admin` | every command below | - |
| `nomoreplaytime.help` / `set` / `add` / `remove` / `max` / `pause` / `resume` / `reset` / `check` / `reload` | `/nmp <subcommand>` | operators |
| `nomoreplaytime.toggletimer` | `/timer on\|off` | everybody |
| `nomoreplaytime.toggletimer.others` | `/timer on\|off <player>` | operators |

Minecraft sends the command list to a player when they join, so a changed permission shows up in their tab completion after they rejoin.

## Building

```
.\gradlew.bat -p loaders\fabric build -Ptarget=1.21.1
.\build-all.ps1            # every target, jars collected in dist\
.\build-all.ps1 forge      # only one loader
```

A target is one file in `targets/<loader>/`. One jar covers a whole range of Minecraft versions where the code is identical:

| Loader | Target | Jar runs on |
|---|---|---|
| fabric | 1.21.1 | 1.21.1 - 1.21.10 |
| fabric | 1.21.11 | 1.21.11 |
| fabric-unobf | 26.1 | 26.1 - 26.3 |
| neoforge | 1.21.1 | 1.21.1 - 1.21.10 |
| neoforge | 1.21.11 | 1.21.11 |
| neoforge | 26.1 | 26.1 - 26.3 |
| forge | 1.21.1 | 1.21.1 - 1.21.5 |
| forge | 1.21.6 | 1.21.6 - 1.21.10 |
| forge | 1.21.11 | 1.21.11 |
| forge | 26.1 | 26.1 - 26.3 |

Java 21 builds the 1.21.x targets, Java 25 the 26.x ones (Forge downloads the JDK it needs).

## Layout

- `core/` - the whole mod, with no Minecraft classes: config, texts, database, timers, commands, permissions.
- `mc/common/` - the one class that talks to Minecraft (`McPlatform`).
- `mc/compat/` - the few calls that changed between Minecraft versions: `legacy` (1.21.1-1.21.10), `identifier` (1.21.11), `unobfuscated` (26.x).
- `loaders/` - per loader: the entrypoint, the metadata file and the Gradle build.
- `targets/` - one properties file per build.

## Supporting a new Minecraft version

1. Copy the newest file in `targets/<loader>/` to the new version and update the versions in it.
2. If the Minecraft version is inside an existing range, instead widen that target's `mc_depends` / `mc_range` / `forge_loader_range` / `jar_suffix`.
3. If it does not compile, the API changed: add a folder under `mc/compat/` with its own `Compat` class (and point `compat=` at it) for Minecraft changes, or a new shell under `loaders/forge/src/` (and `shell=`) for Forge event changes.
