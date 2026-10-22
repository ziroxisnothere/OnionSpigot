package org.purpurmc.purpur.redstone;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;

/**
 * OnionSpigot start - Redstone Limiter
 * Runtime side of the redstone limiter. Redstone device hooks call
 * {@link #check} before doing work; the limiter keeps per-chunk, per-source,
 * per-world and global counters for the current interval window and returns a
 * {@link Verdict} that tells the hook to process, skip or defer the event.
 *
 * <p>All calls happen on the server main thread (redstone device updates are
 * main-thread), so plain hash maps are used. When the limiter is disabled the
 * first check is a single volatile boolean read and does no counting.</p>
 * OnionSpigot end - Redstone Limiter
 */
public final class RedstoneLimiter {

    /** Redstone device kinds the limiter can track. */
    public enum DeviceType {
        REPEATER(true),
        COMPARATOR(true),
        OBSERVER(true),
        DISPENSER(true),
        PISTON(false),   // block-event driven: cannot be re-queued with a delay
        NOTE_BLOCK(false); // block-event driven: cannot be re-queued with a delay

        /** Whether a DEFER verdict can safely re-schedule this device. */
        public final boolean deferrable;

        DeviceType(boolean deferrable) {
            this.deferrable = deferrable;
        }
    }

    /** What the device hook should do with the event. */
    public enum Verdict { PROCESS, SKIP, DEFER }

    /** Per-world counter state. Values are packed longs: (window << 32) | count. */
    static final class WorldCounters {
        final Long2LongOpenHashMap chunks = new Long2LongOpenHashMap();
        final Long2LongOpenHashMap sources = new Long2LongOpenHashMap();
        long worldTotal;
        long window = -1L; // window this data belongs to
    }

    private static volatile boolean active = false;
    private static final Map<String, WorldCounters> worlds = new HashMap<>();
    private static long globalTotal;
    private static long globalWindow = -1L;
    private static final LongOpenHashSet seenThisTick = new LongOpenHashSet();
    private static int seenTick = -1;
    private static final Map<String, Long> warningCooldowns = new HashMap<>();
    private static long lastDebugLogMs;

    private RedstoneLimiter() {
    }

    /** Called after config init/reload; resets counters and the fast gate. */
    public static void onConfigReloaded() {
        final RedstoneLimiterConfig cfg = RedstoneLimiterConfig.get();
        synchronized (RedstoneLimiter.class) {
            worlds.clear();
            warningCooldowns.clear();
            globalTotal = 0L;
            globalWindow = -1L;
            seenThisTick.clear();
            seenTick = -1;
            active = cfg.enabled;
        }
        if (cfg.enabled) {
            RedstoneLimiterConfig.log(Level.INFO, "[RedstoneLimiter] Enabled (" + cfg.mode + ", interval "
                    + cfg.checkIntervalTicks + " ticks). Use /onion redstonelimiter status for details.");
        }
    }

    /** Clears all counters (used by reload so no stale data survives). */
    public static void resetCounters() {
        synchronized (RedstoneLimiter.class) {
            worlds.clear();
            globalTotal = 0L;
            globalWindow = -1L;
            seenThisTick.clear();
            seenTick = -1;
            warningCooldowns.clear();
        }
    }

    /** Current interval window id. */
    static long currentWindow(RedstoneLimiterConfig cfg) {
        final int interval = Math.max(1, cfg.checkIntervalTicks);
        return Math.floorDiv(MinecraftServer.currentTick, interval);
    }

    static int countOf(long packed) {
        return (int) (packed & 0xFFFF_FFFFL);
    }

    static long pack(long window, int count) {
        return (window << 32) | (count & 0xFFFF_FFFFL);
    }

    /**
     * Main entry point for the device hooks. Must be called on the main thread
     * before the device processes its redstone event.
     */
    public static Verdict check(net.minecraft.world.level.Level level, BlockPos pos, DeviceType type) {
        final RedstoneLimiterConfig cfg = RedstoneLimiterConfig.get();
        if (!active || !cfg.enabled) {
            return Verdict.PROCESS;
        }
        final String worldName = level.getWorld().getName();
        final RedstoneLimiterConfig.WorldSettings settings = cfg.settingsFor(worldName);
        if (settings == null) {
            return Verdict.PROCESS; // bypassed, disabled or not managed world
        }
        if (!typeIncluded(cfg, type)) {
            return Verdict.PROCESS;
        }

        final long window = currentWindow(cfg);
        final WorldCounters counters;
        final int chunkCount;
        final int sourceCount;
        final int worldCount;
        final int globalCount;
        synchronized (RedstoneLimiter.class) {
            if (cfg.preventDuplicateProcessing) {
                if (seenTick != MinecraftServer.currentTick) {
                    seenThisTick.clear();
                    seenTick = MinecraftServer.currentTick;
                }
                if (!seenThisTick.add(pos.asLong())) {
                    return Verdict.PROCESS; // already counted this source this tick
                }
            }
            counters = worlds.computeIfAbsent(worldName, k -> new WorldCounters());
            if (counters.window != window) {
                counters.window = window;
                counters.worldTotal = 0L;
                counters.chunks.clear();
                counters.sources.clear();
            }
            if (globalWindow != window) {
                globalWindow = window;
                globalTotal = 0L;
                expireStaleEntries(cfg); // reentrant; no-op when counters reset every interval
            }
            final long chunkKey = ChunkPos.pack(pos);
            final long posKey = pos.asLong();
            chunkCount = countOf(counters.chunks.get(chunkKey));
            sourceCount = countOf(counters.sources.get(posKey));
            worldCount = countOf(counters.worldTotal);
            globalCount = countOf(globalTotal);

            if (chunkCount >= settings.maxUpdatesPerChunk || worldCount >= settings.maxUpdatesPerWorld
                    || sourceCount >= settings.maxUpdatesPerSource || globalCount >= settings.maxEventsPerCheck) {
                final String reason = chunkCount >= settings.maxUpdatesPerChunk ? "chunk"
                        : worldCount >= settings.maxUpdatesPerWorld ? "world"
                        : sourceCount >= settings.maxUpdatesPerSource ? "source" : "global";
                onLimitExceeded(cfg, worldName, pos, type, reason);
                // tick-driven devices may defer in DELAY_EXCESS; block-event devices always skip
                if (cfg.mode == RedstoneLimiterConfig.Mode.DELAY_EXCESS && type.deferrable) {
                    return Verdict.DEFER;
                }
                return Verdict.SKIP;
            }

            counters.chunks.put(chunkKey, pack(window, chunkCount + 1));
            counters.sources.put(posKey, pack(window, sourceCount + 1));
            counters.worldTotal = pack(window, worldCount + 1);
            globalTotal = pack(window, globalCount + 1);
        }
        return Verdict.PROCESS;
    }

    private static boolean typeIncluded(RedstoneLimiterConfig cfg, DeviceType type) {
        return switch (type) {
            case REPEATER -> cfg.limitRepeaters;
            case COMPARATOR -> cfg.limitComparators;
            case OBSERVER -> cfg.limitObservers;
            case PISTON -> cfg.limitPistons;
            case DISPENSER -> cfg.limitDispensersDroppers;
            case NOTE_BLOCK -> cfg.limitNoteBlocks;
        };
    }

    private static void onLimitExceeded(RedstoneLimiterConfig cfg, String worldName, BlockPos pos,
            DeviceType type, String reason) {
        if (!cfg.logLimitEvents && !cfg.debug) {
            return;
        }
        final long now = System.nanoTime();
        final long cooldownNanos = TimeUnit.SECONDS.toNanos(cfg.warningCooldownSeconds);
        final String key = worldName + ":" + reason;
        if (cfg.logLimitEvents) {
            final Long next = warningCooldowns.get(key);
            if (next == null || now >= next) {
                warningCooldowns.put(key, now + cooldownNanos);
                RedstoneLimiterConfig.log(Level.WARNING, "[RedstoneLimiter] " + reason + " limit reached in world '"
                        + worldName + "' near " + pos.toShortString() + " (" + type + "); excess events are being "
                        + (cfg.mode == RedstoneLimiterConfig.Mode.DELAY_EXCESS && type.deferrable ? "delayed" : "skipped"));
            }
        }
        if (cfg.debug) {
            final long debugCooldownMs = cfg.debugLogCooldownMs;
            if (now - lastDebugLogMs >= debugCooldownMs * 1_000_000L) {
                lastDebugLogMs = now;
                RedstoneLimiterConfig.log(Level.INFO, "[RedstoneLimiter] [debug] " + type + " at "
                        + worldName + " " + pos.toShortString() + " exceeded " + reason + " limit (mode=" + cfg.mode + ")");
            }
        }
    }

    /** Drops counter entries inactive for longer than the configured expiration. */
    static void expireStaleEntries(RedstoneLimiterConfig cfg) {
        if (cfg.resetCountersEveryInterval || !cfg.cleanupUnloadedChunks) {
            return;
        }
        synchronized (RedstoneLimiter.class) {
            final long window = currentWindow(cfg);
            final int expirationWindows = Math.max(1,
                    (int) TimeUnit.SECONDS.toNanos(cfg.counterExpirationSeconds) / Math.max(1, cfg.checkIntervalTicks) / 20
                            + 1);
            for (final WorldCounters counters : worlds.values()) {
                counters.chunks.values().removeIf(packed -> window < (packed >>> 32) + expirationWindows);
                counters.sources.values().removeIf(packed -> window < (packed >>> 32) + expirationWindows);
                if (window >= (counters.worldTotal >>> 32) + expirationWindows) {
                    counters.worldTotal = 0L;
                }
            }
            if (window >= (globalTotal >>> 32) + expirationWindows) {
                globalTotal = 0L;
            }
        }
    }

    /** Multi-line status summary used by /onion redstonelimiter status. */
    public static List<String> statusReport() {
        final RedstoneLimiterConfig cfg = RedstoneLimiterConfig.get();
        final List<String> lines = new ArrayList<>();
        lines.add("Redstone Limiter: " + (cfg.enabled ? "enabled" : "disabled (redstone behaves normally)"));
        lines.add("Mode: " + cfg.mode + " | Interval: " + cfg.checkIntervalTicks + " ticks | Delay: " + cfg.delayTicks + " ticks");
        lines.add("Limits: chunk=" + cfg.maxUpdatesPerChunk + " world=" + cfg.maxUpdatesPerWorld
                + " global=" + cfg.maxEventsPerCheck + " source=" + cfg.maxUpdatesPerSource);
        lines.add("Devices: repeaters=" + cfg.limitRepeaters + " comparators=" + cfg.limitComparators
                + " pistons=" + cfg.limitPistons + " observers=" + cfg.limitObservers
                + " dispensers/droppers=" + cfg.limitDispensersDroppers + " note-blocks=" + cfg.limitNoteBlocks);
        if (!cfg.bypassWorlds.isEmpty() || !cfg.disabledWorlds.isEmpty() || !cfg.overrides.isEmpty()) {
            lines.add("Worlds: bypass=" + cfg.bypassWorlds + " disabled=" + cfg.disabledWorlds
                    + " overrides=" + cfg.overrides.keySet());
        }
        synchronized (RedstoneLimiter.class) {
            final long window = currentWindow(cfg);
            if (cfg.enabled && !worlds.isEmpty()) {
                final List<String> usage = new ArrayList<>();
                for (final Map.Entry<String, WorldCounters> entry : worlds.entrySet()) {
                    final WorldCounters counters = entry.getValue();
                    final long w = counters.window == window ? countOf(counters.worldTotal) : 0L;
                    final long g = globalWindow == window ? countOf(globalTotal) : 0L;
                    usage.add(entry.getKey() + ": " + w + " world / " + g + " global events this interval");
                }
                lines.addAll(usage);
            }
        }
        return lines;
    }

    /** Applies a reload via the console command; returns console-friendly lines. */
    public static List<String> reloadFromCommand() {
        final List<String> messages = RedstoneLimiterConfig.reload();
        if (!messages.isEmpty() && messages.get(0).startsWith("redstonelimiter.yml reloaded")) {
            resetCounters();
            final RedstoneLimiterConfig cfg = RedstoneLimiterConfig.get();
            messages.add("Counters reset. Effective settings: " + cfg.describe());
            messages.add("All settings apply live; no restart required.");
        }
        return messages;
    }
}
