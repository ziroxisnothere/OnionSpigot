package org.purpurmc.purpur.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import joptsimple.OptionSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * OnionSpigot start - Automatic plugin downloader
 * Downloads required plugins into the server's plugins directory during
 * startup, before Bukkit plugin discovery runs, so they are loaded during the
 * same startup without a second restart.
 *
 * <p>Behavior:</p>
 * <ul>
 *   <li>controlled by {@code onion-plugins.yml} in the server root; the file is
 *       generated once when missing and user edits are never overwritten</li>
 *   <li>a download only happens when the destination file is missing or not a
 *       valid plugin jar; a valid user-installed jar is never replaced</li>
 *   <li>downloads go to a temporary file, are validated (HTTP status, no HTML
 *       error page, plausible jar archive with a plugin descriptor) and are then
 *       moved into place; temporary files are removed after failures</li>
 *   <li>HTTPS only, bounded connection/read timeouts and bounded retries; a
 *       failed download is reported and (by default) startup continues</li>
 * </ul>
 * OnionSpigot end - Automatic plugin downloader
 */
public final class OnionPluginDownloader {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("OnionSpigot");

    /** One configured plugin entry. */
    public static final class PluginEntry {
        public final String name;
        public final boolean enabled;
        public final String url;
        public final String file;

        PluginEntry(String name, boolean enabled, String url, String file) {
            this.name = name;
            this.enabled = enabled;
            this.url = url;
            this.file = file;
        }
    }

    /** Parsed onion-plugins.yml settings. */
    public static final class DownloaderConfig {
        public final boolean enabled;
        public final boolean downloadOnStartup;
        public final int timeoutSeconds;
        public final int retryCount;
        public final boolean continueOnFailure;
        public final Map<String, PluginEntry> plugins;

        DownloaderConfig(boolean enabled, boolean downloadOnStartup, int timeoutSeconds,
                int retryCount, boolean continueOnFailure, Map<String, PluginEntry> plugins) {
            this.enabled = enabled;
            this.downloadOnStartup = downloadOnStartup;
            this.timeoutSeconds = timeoutSeconds;
            this.retryCount = retryCount;
            this.continueOnFailure = continueOnFailure;
            this.plugins = plugins;
        }
    }

    private static final AtomicBoolean ALREADY_RAN = new AtomicBoolean(false);
    private static volatile DownloaderConfig config;

    private OnionPluginDownloader() {
    }

    /** Current config (for tests and status output). */
    public static DownloaderConfig config() {
        return config;
    }

    /** True when a valid plugin jar with a plugin descriptor exists at the path. */
    public static boolean isValidPluginJar(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try {
            if (Files.size(path) <= 0) {
                return false;
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                final ZipEntry pluginYml = zip.getEntry("plugin.yml");
                final ZipEntry paperYml = zip.getEntry("paper-plugin.yml");
                return (pluginYml != null && !pluginYml.isDirectory())
                        || (paperYml != null && !paperYml.isDirectory());
            }
        } catch (final IOException ex) {
            return false;
        }
    }

    /** Rejects filenames that escape the plugins directory or are not jar names. */
    public static String sanitizeFileName(String name, List<String> problems) {
        if (name == null || name.isBlank()) {
            problems.add("filename is empty");
            return null;
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            problems.add("filename must not contain path separators or '..'");
            return null;
        }
        if (name.startsWith(".")) {
            problems.add("filename must not start with a dot");
            return null;
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            problems.add("filename must end with .jar");
            return null;
        }
        return name;
    }

    /** Validates the configured URL: must be absolute HTTPS. */
    public static String validateUrl(String url, List<String> problems) {
        if (url == null || url.isBlank()) {
            problems.add("url is empty");
            return null;
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            problems.add("url must use HTTPS");
            return null;
        }
        try {
            final URI uri = URI.create(url.trim());
            if (uri.getHost() == null) {
                problems.add("url has no host");
                return null;
            }
            return url.trim();
        } catch (final IllegalArgumentException ex) {
            problems.add("url is malformed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Startup entry point, called from the server bootstrap before plugin
     * discovery. Never throws: any unexpected problem is logged and startup
     * continues.
     */
    public static void checkRequiredPlugins(OptionSet options) {
        if (!ALREADY_RAN.compareAndSet(false, true)) {
            return; // guard against duplicate invocation from multiple init paths
        }
        try {
            run(options);
        } catch (final Exception ex) {
            LOGGER.error("Unexpected error while checking required plugins, continuing startup", ex);
        }
    }

    private static void run(OptionSet options) {
        LOGGER.info("Checking required plugins...");
        final Path configPath = resolveConfigPath(options);
        final DownloaderConfig cfg = loadConfig(configPath);
        config = cfg;
        if (!cfg.enabled || !cfg.downloadOnStartup) {
            LOGGER.info("Required plugin check completed (downloader disabled).");
            return;
        }
        final Path pluginsDir = resolvePluginsDirectory(options);
        try {
            Files.createDirectories(pluginsDir);
        } catch (final IOException ex) {
            LOGGER.error("Could not create plugins directory {}: {}", pluginsDir, ex.getMessage());
            return;
        }
        for (final PluginEntry entry : cfg.plugins.values()) {
            if (!entry.enabled) {
                continue;
            }
            final Path target = pluginsDir.resolve(entry.file).normalize();
            if (isValidPluginJar(target)) {
                LOGGER.info("{} already exists; skipping download.", entry.name);
                continue;
            }
            if (Files.exists(target)) {
                LOGGER.warn("{} exists but is not a valid plugin jar; replacing it via download.", entry.name);
            }
            final String error = download(entry, target, cfg);
            if (error == null) {
                LOGGER.info("{} downloaded successfully.", entry.name);
            } else {
                LOGGER.error("Failed to download {} from {}: {}", entry.name, entry.url, error);
                if (!cfg.continueOnFailure) {
                    LOGGER.error("continue-on-failure is disabled; aborting remaining downloads.");
                    break;
                }
            }
        }
        LOGGER.info("Required plugin check completed.");
    }

    /** Resolves the plugins directory the same way the server does. */
    static Path resolvePluginsDirectory(OptionSet options) {
        if (options != null && options.has("plugins")) {
            final Object value = options.valueOf("plugins");
            if (value instanceof java.io.File file) {
                return file.toPath().toAbsolutePath().normalize();
            }
        }
        return Path.of("plugins").toAbsolutePath().normalize();
    }

    private static Path resolveConfigPath(OptionSet options) {
        final Path pluginsDir = resolvePluginsDirectory(options);
        return pluginsDir.getParent() == null
                ? Path.of("onion-plugins.yml").toAbsolutePath().normalize()
                : pluginsDir.getParent().resolve("onion-plugins.yml").normalize();
    }

    /** Loads onion-plugins.yml, generating the default file when missing. */
    public static DownloaderConfig loadConfig(Path configPath) {
        if (!Files.exists(configPath)) {
            try {
                Files.write(configPath, DEFAULT_YAML.getBytes(StandardCharsets.UTF_8));
                LOGGER.info("Created default configuration {}", configPath);
            } catch (final IOException ex) {
                LOGGER.error("Could not write default {}: {}", configPath, ex.getMessage());
            }
        }
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(configPath.toFile());
        } catch (final IOException | InvalidConfigurationException ex) {
            LOGGER.error("Could not read {}; downloader disabled for this startup.", configPath, ex);
            return new DownloaderConfig(false, false, 15, 2, true, Map.of());
        }
        return parse(yaml);
    }

    /** Parses and validates the YAML tree (used by tests as well). */
    public static DownloaderConfig parse(YamlConfiguration yaml) {
        final List<String> problems = new ArrayList<>();
        final boolean enabled = yaml.getBoolean("settings.enabled", true);
        final boolean downloadOnStartup = yaml.getBoolean("settings.download-on-startup", true);
        final int timeout = clamp(yaml.getInt("settings.timeout-seconds", 15), 1, 300, 15,
                "settings.timeout-seconds", problems);
        final int retries = clamp(yaml.getInt("settings.retry-count", 2), 0, 5, 2,
                "settings.retry-count", problems);
        final boolean continueOnFailure = yaml.getBoolean("settings.continue-on-failure", true);

        final Map<String, PluginEntry> plugins = new LinkedHashMap<>();
        final ConfigurationSection pluginsSection = yaml.getConfigurationSection("plugins");
        if (pluginsSection != null) {
            for (final String key : pluginsSection.getKeys(false)) {
                final ConfigurationSection section = pluginsSection.getConfigurationSection(key);
                if (section == null) {
                    problems.add("plugins." + key + " must be a mapping");
                    continue;
                }
                final boolean pluginEnabled = section.getBoolean("enabled", true);
                final List<String> entryProblems = new ArrayList<>();
                final String url = validateUrl(section.getString("url"), entryProblems);
                final String file = sanitizeFileName(section.getString("file"), entryProblems);
                if (url == null || file == null) {
                    for (final String problem : entryProblems) {
                        LOGGER.warn("onion-plugins.yml: plugins.{} {}", key, problem);
                    }
                    continue;
                }
                plugins.put(key, new PluginEntry(key, pluginEnabled, url, file));
            }
        }
        for (final String problem : problems) {
            LOGGER.warn("onion-plugins.yml: {}", problem);
        }
        return new DownloaderConfig(enabled, downloadOnStartup, timeout, retries, continueOnFailure, plugins);
    }

    private static int clamp(int value, int min, int max, int def, String path, List<String> problems) {
        if (value < min) {
            problems.add(path + " = " + value + " is below the minimum " + min + "; using " + min);
            return min;
        }
        if (value > max) {
            problems.add(path + " = " + value + " is above the maximum " + max + "; using " + max);
            return max;
        }
        return value;
    }

    /**
     * Downloads the configured URL into a temporary file, validates it and
     * moves it onto the target. Returns null on success or the error reason.
     */
    static String download(PluginEntry entry, Path target, DownloaderConfig cfg) {
        final int attempts = 1 + Math.max(0, cfg.retryCount);
        String lastError = "unknown error";
        for (int attempt = 1; attempt <= attempts; attempt++) {
            lastError = downloadOnce(entry, target, cfg);
            if (lastError == null) {
                return null;
            }
            if (attempt < attempts) {
                LOGGER.warn("Download attempt {}/{} for {} failed: {}", attempt, attempts, entry.name, lastError);
            }
        }
        return lastError;
    }

    private static String downloadOnce(PluginEntry entry, Path target, DownloaderConfig cfg) {
        final Path temp = target.resolveSibling("." + target.getFileName() + ".onion-tmp");
        try {
            final HttpURLConnection connection = (HttpURLConnection) URI.create(entry.url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(cfg.timeoutSeconds));
            connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(cfg.timeoutSeconds));
            connection.setInstanceFollowRedirects(true);
            final int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                return "HTTP status " + status;
            }
            final String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                return "server returned an HTML page instead of a jar";
            }
            try (InputStream input = connection.getInputStream()) {
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            // validate the payload: no HTML error page, plausible jar, plugin descriptor
            if (looksLikeHtml(temp)) {
                return "downloaded file is an HTML page, not a jar";
            }
            if (!isValidPluginJar(temp)) {
                return "downloaded file is not a valid plugin jar (missing plugin.yml/paper-plugin.yml?)";
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (final java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        } catch (final SocketTimeoutException ex) {
            return "timed out (" + cfg.timeoutSeconds + "s)";
        } catch (final IOException ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        } finally {
            deleteQuietly(temp); // no-op when the move already consumed the temp file
        }
    }

    /** Detects HTML error pages saved as the download payload. */
    static boolean looksLikeHtml(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            final byte[] head = input.readNBytes(256);
            final String start = new String(head, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            return start.contains("<!doctype html") || start.contains("<html");
        } catch (final IOException ex) {
            return false;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (final IOException ignored) {
        }
    }

    /** Default onion-plugins.yml written when the file does not exist. */
    public static final String DEFAULT_YAML = """
            # OnionSpigot automatic plugin downloader
            # Downloads required plugins into the plugins directory during startup,
            # before plugin discovery, so they load without a second restart.
            # Missing destination files are downloaded; valid existing files are
            # never replaced. Delete a plugin jar to force a re-download.

            settings:
              enabled: true
              download-on-startup: true
              timeout-seconds: 15
              retry-count: 2
              continue-on-failure: true

            plugins:
              packetevents:
                enabled: true
                url: "https://cdn2.zirox.indevs.in/Lib/packetevents-spigot-2.14.0%20%282%29.jar"
                file: "packetevents-spigot-2.14.0.jar"

              spark:
                enabled: true
                url: "https://cdn2.zirox.indevs.in/Lib/spark-1.10.119-bukkit.jar"
                file: "spark-1.10.119-bukkit.jar"
            """;
}
