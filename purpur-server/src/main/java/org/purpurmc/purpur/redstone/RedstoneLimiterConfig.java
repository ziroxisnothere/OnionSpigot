package org.purpurmc.purpur.redstone;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * OnionSpigot start - Redstone Limiter
 * Configuration for the redstone limiter, stored in {@code redstonelimiter.yml}
 * in the server root directory (next to purpur.yml).
 *
 * <p>The limiter is disabled by default: creating this file alone never changes
 * redstone behavior. All values are validated and clamped on load; invalid
 * values fall back to documented defaults with a console warning instead of
 * failing server startup. The file is only written once (when missing) so user
 * edits are always preserved.</p>
 * OnionSpigot end - Redstone Limiter
 */
public final class RedstoneLimiterConfig {

    public enum Mode { SKIP_EXCESS, DELAY_EXCESS }

    /** Per-world limit overrides (unset values inherit the global defaults). */
    public static final class WorldOverride {
        public final Integer maxUpdatesPerChunk;
        public final Integer maxUpdatesPerWorld;
        public final Integer maxEventsPerCheck;
        public final Integer maxUpdatesPerSource;

        WorldOverride(Integer chunk, Integer world, Integer events, Integer source) {
            this.maxUpdatesPerChunk = chunk;
            this.maxUpdatesPerWorld = world;
            this.maxEventsPerCheck = events;
            this.maxUpdatesPerSource = source;
        }
    }

    // settings
    public final boolean enabled;
    public final boolean debug;
    public final int checkIntervalTicks;
    // limits (global defaults)
    public final int maxUpdatesPerChunk;
    public final int maxUpdatesPerWorld;
    public final int maxEventsPerCheck;
    public final int maxUpdatesPerSource;
    public final boolean limitRepeaters;
    public final boolean limitComparators;
    public final boolean limitPistons;
    public final boolean limitObservers;
    public final boolean limitDispensersDroppers;
    public final boolean limitNoteBlocks;
    // behavior
    public final Mode mode;
    public final int delayTicks;
    public final boolean logLimitEvents;
    public final int warningCooldownSeconds;
    public final boolean preventDuplicateProcessing;
    public final boolean resetCountersEveryInterval;
    // worlds
    public final Set<String> bypassWorlds;
    public final Set<String> disabledWorlds;
    public final boolean applyToAllWorlds;
    public final Map<String, WorldOverride> overrides;
    // performance
    public final boolean cleanupUnloadedChunks;
    public final int counterExpirationSeconds;
    public final int debugLogCooldownMs;

    private static volatile RedstoneLimiterConfig instance = disabled();
    private static File configFile;

    private RedstoneLimiterConfig(boolean enabled, boolean debug, int checkIntervalTicks,
            int maxUpdatesPerChunk, int maxUpdatesPerWorld, int maxEventsPerCheck, int maxUpdatesPerSource,
            boolean limitRepeaters, boolean limitComparators, boolean limitPistons, boolean limitObservers,
            boolean limitDispensersDroppers, boolean limitNoteBlocks,
            Mode mode, int delayTicks, boolean logLimitEvents, int warningCooldownSeconds,
            boolean preventDuplicateProcessing, boolean resetCountersEveryInterval,
            Set<String> bypassWorlds, Set<String> disabledWorlds, boolean applyToAllWorlds,
            Map<String, WorldOverride> overrides,
            boolean cleanupUnloadedChunks, int counterExpirationSeconds, int debugLogCooldownMs) {
        this.enabled = enabled;
        this.debug = debug;
        this.checkIntervalTicks = checkIntervalTicks;
        this.maxUpdatesPerChunk = maxUpdatesPerChunk;
        this.maxUpdatesPerWorld = maxUpdatesPerWorld;
        this.maxEventsPerCheck = maxEventsPerCheck;
        this.maxUpdatesPerSource = maxUpdatesPerSource;
        this.limitRepeaters = limitRepeaters;
        this.limitComparators = limitComparators;
        this.limitPistons = limitPistons;
        this.limitObservers = limitObservers;
        this.limitDispensersDroppers = limitDispensersDroppers;
        this.limitNoteBlocks = limitNoteBlocks;
        this.mode = mode;
        this.delayTicks = delayTicks;
        this.logLimitEvents = logLimitEvents;
        this.warningCooldownSeconds = warningCooldownSeconds;
        this.preventDuplicateProcessing = preventDuplicateProcessing;
        this.resetCountersEveryInterval = resetCountersEveryInterval;
        this.bypassWorlds = bypassWorlds;
        this.disabledWorlds = disabledWorlds;
        this.applyToAllWorlds = applyToAllWorlds;
        this.overrides = overrides;
        this.cleanupUnloadedChunks = cleanupUnloadedChunks;
        this.counterExpirationSeconds = counterExpirationSeconds;
        this.debugLogCooldownMs = debugLogCooldownMs;
    }

    /** Config used when the file is missing or unreadable: limiter off. */
    public static RedstoneLimiterConfig disabled() {
        return new RedstoneLimiterConfig(false, false, 20,
                1200, 12_000, 4000, 800,
                true, true, true, true, false, false,
                Mode.SKIP_EXCESS, 2, true, 10, true, true,
                Set.of(), Set.of(), true, Map.of(),
                true, 120, 1000);
    }

    /** Current config; always non-null (disabled defaults until first init). */
    public static RedstoneLimiterConfig get() {
        return instance;
    }

    /** Config file path, or null before the first init. */
    public static File file() {
        return configFile;
    }

    /**
     * Creates {@code redstonelimiter.yml} in the given directory when missing
     * and loads it. Called once during server startup.
     */
    public static void init(File directory) {
        if (directory == null) {
            directory = new File(".");
        }
        final File file = new File(directory, "redstonelimiter.yml");
        configFile = file;
        if (!file.exists()) {
            createDefaultFile(file);
        }
        try {
            instance = load(file);
            RedstoneLimiter.onConfigReloaded();
        } catch (final InvalidConfigurationException ex) {
            // a malformed optional config must never break server startup:
            // keep the previous (or disabled default) settings and warn
            log(Level.SEVERE, "[RedstoneLimiter] " + file.getName() + " is malformed, keeping previous settings: " + ex.getMessage());
        }
    }

    /** Re-reads the config file; keeps the previous config if it fails. */
    public static synchronized List<String> reload() {
        final List<String> messages = new ArrayList<>();
        if (configFile == null || !configFile.exists()) {
            messages.add("redstonelimiter.yml not found; limiter stays disabled");
            return messages;
        }
        try {
            instance = load(configFile);
            RedstoneLimiter.onConfigReloaded();
            messages.add("redstonelimiter.yml reloaded from " + configFile.getPath());
        } catch (final Exception ex) {
            messages.add("Reload failed, keeping previous settings: " + ex.getMessage());
        }
        return messages;
    }

    /** Parses and validates the file; throws on malformed YAML. */
    public static RedstoneLimiterConfig load(File file) throws InvalidConfigurationException {
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException ex) {
            throw new InvalidConfigurationException("Cannot read " + file.getName() + ": " + ex.getMessage(), ex);
        }
        final List<String> warnings = new ArrayList<>();
        final RedstoneLimiterConfig config = parse(yaml, warnings);
        for (final String warning : warnings) {
            log(Level.WARNING, "[RedstoneLimiter] " + warning);
        }
        if (config.enabled && config.debug) {
            log(Level.INFO, "[RedstoneLimiter] effective settings: " + config.describe());
        }
        return config;
    }

    /** Parses an in-memory YAML tree (used by tests as well as {@link #load}). */
    public static RedstoneLimiterConfig parse(YamlConfiguration yaml, List<String> warnings) {
        // settings
        final boolean enabled = yaml.getBoolean("settings.enabled", false);
        final boolean debug = yaml.getBoolean("settings.debug", false);
        final int interval = clamp(yaml, "settings.check-interval-ticks", 20, 1, 20_000, warnings);

        // limits
        final int maxChunk = clamp(yaml, "limits.max-updates-per-chunk", 1200, 1, 100_000_000, warnings);
        final int maxWorld = clamp(yaml, "limits.max-updates-per-world", 12_000, 1, 100_000_000, warnings);
        final int maxEvents = clamp(yaml, "limits.max-events-per-check", 4000, 1, 100_000_000, warnings);
        final int maxSource = clamp(yaml, "limits.max-updates-per-source", 800, 1, 100_000_000, warnings);
        final boolean limitRepeaters = yaml.getBoolean("limits.limit-repeaters", true);
        final boolean limitComparators = yaml.getBoolean("limits.limit-comparators", true);
        final boolean limitPistons = yaml.getBoolean("limits.limit-pistons", true);
        final boolean limitObservers = yaml.getBoolean("limits.limit-observers", true);
        final boolean limitDispensersDroppers = yaml.getBoolean("limits.limit-dispensers-droppers", false);
        final boolean limitNoteBlocks = yaml.getBoolean("limits.limit-note-blocks", false);

        // behavior
        final String modeRaw = yaml.getString("behavior.mode", Mode.SKIP_EXCESS.name());
        Mode mode = Mode.SKIP_EXCESS;
        if (modeRaw != null) {
            try {
                mode = Mode.valueOf(modeRaw.trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ex) {
                warnings.add("behavior.mode '" + modeRaw + "' is not a valid mode (use SKIP_EXCESS or DELAY_EXCESS); using SKIP_EXCESS");
                mode = Mode.SKIP_EXCESS;
            }
        }
        final int delayTicks = clamp(yaml, "behavior.delay-ticks", 2, 1, 1200, warnings);
        final boolean logLimitEvents = yaml.getBoolean("behavior.log-limit-events", true);
        final int warningCooldown = clamp(yaml, "behavior.warning-cooldown-seconds", 10, 0, 86_400, warnings);
        final boolean preventDuplicates = yaml.getBoolean("behavior.prevent-duplicate-processing", true);
        final boolean resetCounters = yaml.getBoolean("behavior.reset-counters-every-interval", true);

        // worlds
        final Set<String> bypass = new LinkedHashSet<>(yaml.getStringList("worlds.bypass"));
        final Set<String> disabled = new LinkedHashSet<>(yaml.getStringList("worlds.disabled"));
        final boolean applyToAll = yaml.getBoolean("worlds.apply-to-all-worlds", true);
        final Map<String, WorldOverride> overrides = new HashMap<>();
        final ConfigurationSection worldsSection = yaml.getConfigurationSection("worlds.overrides");
        if (worldsSection != null) {
            for (final String worldName : worldsSection.getKeys(false)) {
                final ConfigurationSection section = worldsSection.getConfigurationSection(worldName);
                if (section == null) {
                    warnings.add("worlds.overrides." + worldName + " must be a mapping of settings; entry ignored");
                    continue;
                }
                overrides.put(worldName, new WorldOverride(
                        section.contains("max-updates-per-chunk")
                                ? clamp(section, "max-updates-per-chunk", 1200, 1, 100_000_000, warnings) : null,
                        section.contains("max-updates-per-world")
                                ? clamp(section, "max-updates-per-world", 12_000, 1, 100_000_000, warnings) : null,
                        section.contains("max-events-per-check")
                                ? clamp(section, "max-events-per-check", 4000, 1, 100_000_000, warnings) : null,
                        section.contains("max-updates-per-source")
                                ? clamp(section, "max-updates-per-source", 800, 1, 100_000_000, warnings) : null));
            }
        }

        // performance
        final boolean cleanup = yaml.getBoolean("performance.cleanup-unloaded-chunks", true);
        final int expiration = clamp(yaml, "performance.counter-expiration-seconds", 120, 1, 86_400, warnings);
        final int debugCooldown = clamp(yaml, "performance.debug-log-cooldown-ms", 1000, 0, 3_600_000, warnings);

        return new RedstoneLimiterConfig(enabled, debug, interval,
                maxChunk, maxWorld, maxEvents, maxSource,
                limitRepeaters, limitComparators, limitPistons, limitObservers, limitDispensersDroppers, limitNoteBlocks,
                mode, delayTicks, logLimitEvents, warningCooldown, preventDuplicates, resetCounters,
                bypass, disabled, applyToAll, overrides,
                cleanup, expiration, debugCooldown);
    }

    /** Human-readable summary of the effective settings (debug + /onion status). */
    public String describe() {
        return "enabled=" + enabled
                + ", mode=" + mode
                + ", check-interval-ticks=" + checkIntervalTicks
                + ", max-updates-per-chunk=" + maxUpdatesPerChunk
                + ", max-updates-per-world=" + maxUpdatesPerWorld
                + ", max-events-per-check=" + maxEventsPerCheck
                + ", max-updates-per-source=" + maxUpdatesPerSource
                + ", devices=[repeaters=" + limitRepeaters
                + ", comparators=" + limitComparators
                + ", pistons=" + limitPistons
                + ", observers=" + limitObservers
                + ", dispensers-droppers=" + limitDispensersDroppers
                + ", note-blocks=" + limitNoteBlocks + "]"
                + ", delay-ticks=" + delayTicks
                + ", worlds: bypass=" + bypassWorlds
                + ", disabled=" + disabledWorlds
                + ", apply-to-all=" + applyToAllWorlds
                + ", overrides=" + overrides.keySet()
                + ", reset-counters-every-interval=" + resetCountersEveryInterval
                + ", counter-expiration-seconds=" + counterExpirationSeconds;
    }

    /** Resolves the effective per-world settings: null means the world is not managed. */
    public WorldSettings settingsFor(String worldName) {
        if (disabledWorlds.contains(worldName) || bypassWorlds.contains(worldName)) {
            return null;
        }
        final WorldOverride override = overrides.get(worldName);
        if (override == null && !applyToAllWorlds) {
            return null;
        }
        return new WorldSettings(this, override);
    }

    /** Immutable, override-aware effective limits for one world. */
    public static final class WorldSettings {
        public final int maxUpdatesPerChunk;
        public final int maxUpdatesPerWorld;
        public final int maxEventsPerCheck;
        public final int maxUpdatesPerSource;
        public final RedstoneLimiterConfig config;

        WorldSettings(RedstoneLimiterConfig config, WorldOverride override) {
            this.config = config;
            this.maxUpdatesPerChunk = override != null && override.maxUpdatesPerChunk != null
                    ? override.maxUpdatesPerChunk : config.maxUpdatesPerChunk;
            this.maxUpdatesPerWorld = override != null && override.maxUpdatesPerWorld != null
                    ? override.maxUpdatesPerWorld : config.maxUpdatesPerWorld;
            this.maxEventsPerCheck = override != null && override.maxEventsPerCheck != null
                    ? override.maxEventsPerCheck : config.maxEventsPerCheck;
            this.maxUpdatesPerSource = override != null && override.maxUpdatesPerSource != null
                    ? override.maxUpdatesPerSource : config.maxUpdatesPerSource;
        }
    }

    private static int clamp(YamlConfiguration yaml, String path, int def, int min, int max, List<String> warnings) {
        if (!yaml.contains(path)) {
            return def;
        }
        final int value = yaml.getInt(path, def);
        return clampValue(path, value, def, min, max, warnings);
    }

    private static int clamp(ConfigurationSection section, String path, int def, int min, int max, List<String> warnings) {
        final int value = section.getInt(path, def);
        return clampValue(section.getCurrentPath() + "." + path, value, def, min, max, warnings);
    }

    private static int clampValue(String path, int value, int def, int min, int max, List<String> warnings) {
        if (value < min) {
            warnings.add(path + " = " + value + " is below the minimum " + min + "; using " + min);
            return min;
        }
        if (value > max) {
            warnings.add(path + " = " + value + " is above the maximum " + max + "; using " + max);
            return max;
        }
        return value;
    }

    /** Writes the documented default file (only called when the file is missing). */
    private static void createDefaultFile(File file) {
        try {
            Files.write(file.toPath(), DEFAULT_YAML.getBytes(StandardCharsets.UTF_8));
            log(Level.INFO, "[RedstoneLimiter] Created default configuration " + file.getPath()
                    + " (limiter is disabled by default)");
        } catch (final IOException ex) {
            log(Level.WARNING, "[RedstoneLimiter] Could not write default " + file.getPath() + ": " + ex.getMessage());
        }
    }

    static void log(Level level, String message) {
        if (Bukkit.getServer() != null) {
            Bukkit.getLogger().log(level, message);
        } else {
            java.util.logging.Logger.getLogger("OnionSpigot").log(level, message);
        }
    }

    /** Default content written when redstonelimiter.yml does not exist yet. */
    public static final String DEFAULT_YAML = """
            # OnionSpigot Redstone Limiter
            # The limiter is DISABLED by default - enabling it changes how redstone
            # devices are processed once their configured limits are reached.
            # Restart the server if a setting requires a full reload.

            settings:
              enabled: false
              debug: false
              check-interval-ticks: 20

            limits:
              # Maximum redstone device events allowed per chunk per interval.
              max-updates-per-chunk: 1200

              # Maximum redstone device events allowed per world per interval.
              max-updates-per-world: 12000

              # Maximum redstone device events allowed globally per interval.
              max-events-per-check: 4000

              # Maximum redstone events allowed near a single source position.
              max-updates-per-source: 800

              # Whether repeaters are included in limiting.
              limit-repeaters: true

              # Whether comparators are included in limiting.
              limit-comparators: true

              # Whether pistons and their redstone-triggered actions are included.
              limit-pistons: true

              # Whether observers are included in limiting.
              limit-observers: true

              # Whether dispensers and droppers are included.
              limit-dispensers-droppers: false

              # Whether note blocks are included.
              limit-note-blocks: false

            behavior:
              # How the limiter reacts after reaching a configured limit.
              # SKIP_EXCESS: excess device events are skipped (pistons/note blocks
              #   always skip - their events cannot be re-queued safely).
              # DELAY_EXCESS: tick-driven devices (repeaters, comparators, observers,
              #   dispensers, droppers) retry after delay-ticks; pistons and note
              #   blocks fall back to skipping.
              mode: SKIP_EXCESS

              # Used only when mode is DELAY_EXCESS.
              delay-ticks: 2

              # Whether to notify the console when limits are exceeded.
              log-limit-events: true

              # Minimum interval between repeated warning messages.
              warning-cooldown-seconds: 10

              # Avoid counting the same source position more than once per server tick.
              prevent-duplicate-processing: true

              # Reset all counters at every interval boundary. If false, counters only
              # expire after performance.counter-expiration-seconds of inactivity.
              reset-counters-every-interval: true

            worlds:
              # Worlds in this list are never limited.
              bypass:
                - example_world

              # If true, worlds not listed above are eligible for limiting.
              apply-to-all-worlds: true

              # Optional per-world limit overrides.
              overrides:
                world_nether:
                  max-updates-per-chunk: 800
                  max-updates-per-world: 8000

              # Worlds where limiting is always disabled.
              disabled:
                - creative_world

            performance:
              # Prune counter entries that stopped being active. (Chunk/world
              # counters are always cached in memory; there is no block-scanning
              # fallback to toggle.)
              cleanup-unloaded-chunks: true

              # Age (in seconds of inactivity) after which counter entries are
              # dropped when reset-counters-every-interval is false.
              counter-expiration-seconds: 120

              # Minimum delay between debug log lines.
              debug-log-cooldown-ms: 1000
            """;
}
