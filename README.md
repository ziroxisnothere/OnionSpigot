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
* A clean startup banner printed to the console on every server start.
* OnionSpigot branding for server identification (manifest metadata, `/version` output,
  watchdog messages, server GUI title, and bStats platform name).

Both commands are native server commands registered through the standard Bukkit command map -
no plugins, no extra dependencies. They work from the console and for players with the
`bukkit.command.onion` / `bukkit.command.onionspigot` permissions (server operators by default),
and remain available after restarts. The links shown by these commands can be customized in
`purpur.yml` under `settings.onion.repo-url` and `settings.onion.docs-url`.

OnionSpigot is fully compatible with existing Paper/Purpur setups: existing worlds, plugins,
and configuration files (including `purpur.yml`) continue to load unchanged.

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
