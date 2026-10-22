package org.purpurmc.purpur.redstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * OnionSpigot start - Redstone Limiter tests
 * Covers configuration defaults, invalid value clamping, behavior-mode
 * resolution, world overrides/bypasses and the packed counter helpers.
 * These tests intentionally avoid booting a server.
 * OnionSpigot end - Redstone Limiter tests
 */
class RedstoneLimiterConfigTest {

    private static RedstoneLimiterConfig parse(String yaml) {
        final YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (final org.bukkit.configuration.InvalidConfigurationException ex) {
            throw new IllegalArgumentException(ex);
        }
        return RedstoneLimiterConfig.parse(config, new ArrayList<>());
    }

    @Test
    void defaultsWhenFileIsEmpty() {
        final RedstoneLimiterConfig config = parse("");
        assertFalse(config.enabled, "limiter must be disabled by default");
        assertFalse(config.debug);
        assertEquals(20, config.checkIntervalTicks);
        assertEquals(1200, config.maxUpdatesPerChunk);
        assertEquals(12_000, config.maxUpdatesPerWorld);
        assertEquals(4000, config.maxEventsPerCheck);
        assertEquals(800, config.maxUpdatesPerSource);
        assertTrue(config.limitRepeaters);
        assertTrue(config.limitComparators);
        assertTrue(config.limitPistons);
        assertTrue(config.limitObservers);
        assertFalse(config.limitDispensersDroppers, "dispensers/droppers are opt-in");
        assertFalse(config.limitNoteBlocks, "note blocks are opt-in");
        assertEquals(RedstoneLimiterConfig.Mode.SKIP_EXCESS, config.mode);
        assertEquals(2, config.delayTicks);
        assertTrue(config.resetCountersEveryInterval);
        assertTrue(config.applyToAllWorlds);
    }

    @Test
    void negativeAndInvalidLimitsAreClampedWithWarnings() {
        final List<String> warnings = new ArrayList<>();
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("settings.check-interval-ticks", -5);
        yaml.set("limits.max-updates-per-chunk", -100);
        yaml.set("limits.max-updates-per-source", 999_999_999);
        final RedstoneLimiterConfig config = RedstoneLimiterConfig.parse(yaml, warnings);
        assertEquals(1, config.checkIntervalTicks, "interval clamped to minimum");
        assertEquals(1, config.maxUpdatesPerChunk, "negative limit clamped to minimum");
        assertEquals(100_000_000, config.maxUpdatesPerSource, "oversized limit clamped to maximum");
        assertFalse(warnings.isEmpty(), "clamping must produce warnings");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("check-interval-ticks")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("max-updates-per-chunk")));
    }

    @Test
    void missingKeysFallBackToDefaults() {
        final RedstoneLimiterConfig config = parse("settings:\n  enabled: true\n");
        assertTrue(config.enabled);
        assertEquals(20, config.checkIntervalTicks, "missing interval uses default");
        assertEquals(1200, config.maxUpdatesPerChunk, "missing limit uses default");
        assertEquals(RedstoneLimiterConfig.Mode.SKIP_EXCESS, config.mode, "missing mode uses default");
    }

    @Test
    void unknownModeFallsBackToSkipExcessWithWarning() {
        final List<String> warnings = new ArrayList<>();
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("behavior.mode", "EXPLODE_EVERYTHING");
        final RedstoneLimiterConfig config = RedstoneLimiterConfig.parse(yaml, warnings);
        assertEquals(RedstoneLimiterConfig.Mode.SKIP_EXCESS, config.mode);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("behavior.mode")));
    }

    @Test
    void modeIsCaseInsensitive() {
        assertEquals(RedstoneLimiterConfig.Mode.DELAY_EXCESS, parse("behavior:\n  mode: delay_excess\n").mode);
    }

    @Test
    void worldOverridesAreAppliedPerWorld() {
        final RedstoneLimiterConfig config = parse("""
                worlds:
                  overrides:
                    world_nether:
                      max-updates-per-chunk: 800
                      max-updates-per-world: 8000
                """);
        final RedstoneLimiterConfig.WorldSettings nether = config.settingsFor("world_nether");
        assertNotNull(nether);
        assertEquals(800, nether.maxUpdatesPerChunk);
        assertEquals(8000, nether.maxUpdatesPerWorld);
        assertEquals(4000, nether.maxEventsPerCheck, "unset override inherits global default");
        final RedstoneLimiterConfig.WorldSettings overworld = config.settingsFor("world");
        assertNotNull(overworld);
        assertEquals(1200, overworld.maxUpdatesPerChunk, "world without override uses global default");
    }

    @Test
    void bypassAndDisabledWorldsAreUnmanaged() {
        final RedstoneLimiterConfig config = parse("""
                worlds:
                  bypass:
                    - example_world
                  disabled:
                    - creative_world
                """);
        assertNull(config.settingsFor("example_world"));
        assertNull(config.settingsFor("creative_world"));
        assertNotNull(config.settingsFor("world"));
    }

    @Test
    void applyToAllWorldsFalseLeavesUnlistedWorldsUnmanaged() {
        final RedstoneLimiterConfig config = parse("""
                worlds:
                  apply-to-all-worlds: false
                  overrides:
                    world_nether:
                      max-updates-per-chunk: 800
                """);
        assertNull(config.settingsFor("world"), "world without override is not managed");
        assertNotNull(config.settingsFor("world_nether"), "overridden world is managed");
    }

    @Test
    void counterPackingRoundTrips() {
        assertEquals(1234, RedstoneLimiter.countOf(RedstoneLimiter.pack(7L, 1234)));
        assertEquals(7L, RedstoneLimiter.pack(7L, 1234) >>> 32);
        assertEquals(Integer.MAX_VALUE, RedstoneLimiter.countOf(RedstoneLimiter.pack(1L, Integer.MAX_VALUE)));
    }

    @Test
    void defaultFileIsCreatedOnFirstInitAndReloadKeepsUserValues(@TempDir Path tempDir) throws Exception {
        final File dir = tempDir.toFile();
        RedstoneLimiterConfig.init(dir);
        final File file = new File(dir, "redstonelimiter.yml");
        assertTrue(file.exists(), "redstonelimiter.yml must be generated on first startup");
        final String original = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(original.contains("enabled: false"), "generated file must keep the limiter disabled");

        // user edits the file, then /onion redstonelimiter reload re-reads it
        final String edited = original.replace("enabled: false", "enabled: true")
                .replace("check-interval-ticks: 20", "check-interval-ticks: 40");
        Files.write(file.toPath(), edited.getBytes(StandardCharsets.UTF_8));
        final List<String> messages = RedstoneLimiterConfig.reload();
        assertTrue(messages.get(0).contains("reloaded"));
        assertTrue(RedstoneLimiterConfig.get().enabled);
        assertEquals(40, RedstoneLimiterConfig.get().checkIntervalTicks);

        // restore the static state so other tests are unaffected
        Files.write(file.toPath(), original.getBytes(StandardCharsets.UTF_8));
        RedstoneLimiterConfig.reload();
        assertFalse(RedstoneLimiterConfig.get().enabled);
    }
}
