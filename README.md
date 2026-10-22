<div align="center">

# OnionSpigot

**High-performance Minecraft server software**

*Dark. Sharp. Fast.*

</div>

---

## About

OnionSpigot is a drop-in replacement for [Paper](https://github.com/PaperMC/Paper) and
[Purpur](https://github.com/PurpurMC/Purpur) servers. It inherits the full Purpur feature set
(deep configurability, gameplay toggles, and performance work built on top of Paper) and layers
a clean, branded identity and native software commands on top:

* `/onion` — displays a compact OnionSpigot information panel in chat (software version,
  Minecraft version, Java runtime, and project links).
* `/onionspigot` — displays the same identity with a more detailed panel (build number or
  development-build metadata, git branch/commit, brand id, repository and documentation links).
* A FIGlet `Onion` startup banner printed to the console on every server start.
* **Console-only startup** — the Swing server GUI has been removed; the server always runs
  headless from Windows CMD, PowerShell and Linux terminals (`--nogui` is accepted but no
  longer necessary).
* OnionSpigot branding for server identification (manifest metadata, `/version` output,
  watchdog messages, console app name, and bStats platform name).
* **Redstone Limiter** — an optional, configuration-driven system that limits redstone device
  activity per chunk, per world, per source position and globally (see below).
* **Automatic plugin downloader** — optionally downloads required plugins (PacketEvents,
  spark) into the plugins directory before plugin discovery (see below).

Both commands are native server commands registered through the standard Bukkit command map -
no plugins, no extra dependencies. They work from the console and for players with the
`bukkit.command.onion` / `bukkit.command.onionspigot` permissions (server operators by default),
and remain available after restarts. The links shown by these commands can be customized in
`purpur.yml` under `settings.onion.repo-url` and `settings.onion.docs-url`.

OnionSpigot is fully compatible with existing Paper/Purpur setups: existing worlds, plugins,
and configuration files (including `purpur.yml`) continue to load unchanged.

## Redstone Limiter

On first startup OnionSpigot generates `redstonelimiter.yml` next to `purpur.yml`. The limiter
is **disabled by default** — creating the file never changes redstone behavior; set
`settings.enabled: true` to activate it.

When enabled, the limiter tracks redstone *device* events (repeaters, comparators, pistons,
observers, dispensers/droppers and note blocks) with interval-windowed counters and enforces:

* `limits.max-updates-per-chunk` / `max-updates-per-world` / `max-events-per-check` (global
  per interval) / `max-updates-per-source`
* per-world `bypass`, `disabled` and `overrides` lists, and `worlds.apply-to-all-worlds`
* `behavior.mode`:
  * **`SKIP_EXCESS`** — once a limit is reached, further eligible device events are skipped
    until the next interval (a piston stalls instead of moving; a repeater keeps its state).
  * **`DELAY_EXCESS`** — tick-driven devices (repeaters, comparators, observers, dispensers,
    droppers) re-schedule themselves `behavior.delay-ticks` later and retry when the limit
    has recovered. Pistons and note blocks are block-event driven and **always skip** —
    their events cannot be re-queued safely.

Warnings are rate-limited (`behavior.log-limit-events`, `warning-cooldown-seconds`) and debug
logging is throttled (`performance.debug-log-cooldown-ms`). Counters reset every interval
(`behavior.reset-counters-every-interval`) or expire after inactivity
(`performance.counter-expiration-seconds`). Invalid values are clamped to documented defaults
with a console warning; a malformed file never prevents startup.

Manage it at runtime with `/onion redstonelimiter status` (effective limits and current
interval usage) and `/onion redstonelimiter reload` (re-reads the file, resets counters,
applies everything live — no restart required).

> [!NOTE]
> Known trade-offs: redstone *wire* signal propagation is never limited (skipping wire
> updates can leave ghost signals), so wire spam is not throttled — only device events are
> counted. Skipping device events intentionally stalls the affected machine; clocks keep
> retrying and recover automatically once below the limits.

## Automatic plugin downloader

On first startup OnionSpigot generates `onion-plugins.yml` in the server root. It downloads
missing required plugins into the `plugins` directory **before plugin discovery**, so they
load during the same startup without a second restart. Defaults ship with PacketEvents and
spark; each entry can be disabled or pointed at any HTTPS URL.

Safety behavior: valid existing jars are never replaced (a download only happens when the
destination file is missing or invalid), downloads go through a temporary file and are
validated (HTTP status, no HTML error page, jar archive containing `plugin.yml` or
`paper-plugin.yml`) before being moved into place, HTTPS is enforced, timeouts and retries
are bounded, and a failed download is reported while startup continues. Temporary files are
always cleaned up. Set `settings.enabled: false` to turn the downloader off.

## Requirements

* **Java 25** (or a JRE 11+ with a provisioned JDK toolchain) to build
* Java 21+ to run the server

## Building and setting up

#### Initial setup

First, <u>clone</u> this repository. Do not download it.

```console
git clone https://github.com/ziroxisnothere/OnionSpigot.git
cd OnionSpigot
```

Then run the following command in the root directory:

```console
./gradlew applyAllPatches
```

The project is now ready for use in your IDE.

#### Compiling

Use the command `./gradlew build` to build the API and server. Compiled JARs
will be placed under `purpur-api/build/libs` and `purpur-server/build/libs`.
**These JARs are not used to start a server.**

To compile a server-ready onionclip jar, run:

```console
./gradlew createPaperclipJar
```

The runnable server jar will be produced in `purpur-server/build/libs`.

To install the `purpur-api` and `purpur` dependencies to your local Maven repo, run
`./gradlew publishToMavenLocal`.

> [!NOTE]
> Building OnionSpigot decompiles Minecraft and compiles the full server source.
> Expect the first build to take a while, and make sure enough memory and disk
> space are available.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the patch-based workflow used by this project
(the same patch/gradle tooling as upstream Purpur).

## License

All patches are licensed under the MIT license, unless otherwise noted in the patch headers.

See [PaperMC/Paper](https://github.com/PaperMC/Paper), [PurpurMC/Purpur](https://github.com/PurpurMC/Purpur),
and [PaperMC/Paperweight](https://github.com/PaperMC/paperweight) for the license of material
used by this project. Upstream attribution is preserved intentionally.

## Acknowledgements

OnionSpigot is built on the shoulders of giants:

* [PurpurMC/Purpur](https://github.com/PurpurMC/Purpur) - configurability and gameplay features
* [PaperMC/Paper](https://github.com/PaperMC/Paper) - performance and API foundation
* [SpigotMC](https://www.spigotmc.org/) / [Bukkit](https://bukkit.org) - plugin API
* [PaperMC/paperweight](https://github.com/PaperMC/paperweight) - build tooling
