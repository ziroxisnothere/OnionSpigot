package org.purpurmc.purpur.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * OnionSpigot start - Automatic plugin downloader tests
 * Covers configuration defaults/validation, filename safety, URL validation
 * and plugin jar validation. These tests intentionally avoid booting a server.
 * OnionSpigot end - Automatic plugin downloader tests
 */
class OnionPluginDownloaderTest {

    private static OnionPluginDownloader.DownloaderConfig parse(String yaml) {
        final YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (final org.bukkit.configuration.InvalidConfigurationException ex) {
            throw new IllegalArgumentException(ex);
        }
        return OnionPluginDownloader.parse(config);
    }

    private static Path buildPluginJar(Path path, String descriptor) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry(descriptor));
            zip.write("name: FakePlugin\nversion: 1.0.0\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }

    @Test
    void defaultConfigEnablesBothPlugins() {
        final OnionPluginDownloader.DownloaderConfig config = parse(OnionPluginDownloader.DEFAULT_YAML);
        assertTrue(config.enabled);
        assertTrue(config.downloadOnStartup);
        assertEquals(15, config.timeoutSeconds);
        assertEquals(2, config.retryCount);
        assertTrue(config.continueOnFailure);
        assertEquals(2, config.plugins.size());
        final OnionPluginDownloader.PluginEntry packetevents = config.plugins.get("packetevents");
        assertNotNull(packetevents);
        assertTrue(packetevents.enabled);
        assertTrue(packetevents.url.startsWith("https://"));
        assertEquals("packetevents-spigot-2.14.0.jar", packetevents.file);
        assertNotNull(config.plugins.get("spark"));
    }

    @Test
    void invalidSettingsAreClamped() {
        final List<String> problems = new ArrayList<>();
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("settings.timeout-seconds", 0);
        yaml.set("settings.retry-count", 99);
        final OnionPluginDownloader.DownloaderConfig config = OnionPluginDownloader.parse(yaml);
        assertEquals(1, config.timeoutSeconds, "timeout clamped to minimum");
        assertEquals(5, config.retryCount, "retry count clamped to maximum");
    }

    @Test
    void pluginWithUnsafeFilenameIsRejected() {
        final OnionPluginDownloader.DownloaderConfig config = parse("""
                plugins:
                  evil:
                    enabled: true
                    url: "https://example.com/plugin.jar"
                    file: "../outside/evil.jar"
                  ok:
                    enabled: true
                    url: "https://example.com/plugin.jar"
                    file: "plugin.jar"
                """);
        assertNull(config.plugins.get("evil"), "path traversal filename must be rejected");
        assertNotNull(config.plugins.get("ok"));
    }

    @Test
    void pluginWithHttpUrlIsRejected() {
        final OnionPluginDownloader.DownloaderConfig config = parse("""
                plugins:
                  insecure:
                    enabled: true
                    url: "http://example.com/plugin.jar"
                    file: "plugin.jar"
                """);
        assertNull(config.plugins.get("insecure"), "plain HTTP URLs must be rejected");
    }

    @Test
    void sanitizeFileNameRejectsTraversalAndNonJars() {
        final List<String> problems = new ArrayList<>();
        assertNull(OnionPluginDownloader.sanitizeFileName("../evil.jar", problems));
        assertNull(OnionPluginDownloader.sanitizeFileName("sub/dir.jar", problems));
        assertNull(OnionPluginDownloader.sanitizeFileName("plugin.zip", problems));
        assertNull(OnionPluginDownloader.sanitizeFileName("", problems));
        assertEquals("plugin.jar", OnionPluginDownloader.sanitizeFileName("plugin.jar", problems));
    }

    @Test
    void validateUrlRequiresHttps() {
        final List<String> problems = new ArrayList<>();
        assertNotNull(OnionPluginDownloader.validateUrl("https://cdn.example.com/a.jar", problems));
        assertNull(OnionPluginDownloader.validateUrl("ftp://cdn.example.com/a.jar", problems));
        assertNull(OnionPluginDownloader.validateUrl("https://", problems));
        assertNull(OnionPluginDownloader.validateUrl(null, problems));
    }

    @Test
    void validPluginJarWithPluginYmlIsAccepted(@TempDir Path tempDir) throws IOException {
        final Path jar = buildPluginJar(tempDir.resolve("plugin.jar"), "plugin.yml");
        assertTrue(OnionPluginDownloader.isValidPluginJar(jar));
    }

    @Test
    void validPluginJarWithPaperPluginYmlIsAccepted(@TempDir Path tempDir) throws IOException {
        final Path jar = buildPluginJar(tempDir.resolve("paper.jar"), "paper-plugin.yml");
        assertTrue(OnionPluginDownloader.isValidPluginJar(jar));
    }

    @Test
    void jarWithoutDescriptorAndGarbageFilesAreRejected(@TempDir Path tempDir) throws IOException {
        final Path noDescriptor = tempDir.resolve("nodescriptor.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(noDescriptor))) {
            zip.putNextEntry(new ZipEntry("something.txt"));
            zip.write("hi".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertFalse(OnionPluginDownloader.isValidPluginJar(noDescriptor));

        final Path garbage = tempDir.resolve("garbage.jar");
        Files.write(garbage, "<html>502 Bad Gateway</html>".getBytes(StandardCharsets.UTF_8));
        assertFalse(OnionPluginDownloader.isValidPluginJar(garbage));

        assertFalse(OnionPluginDownloader.isValidPluginJar(tempDir.resolve("missing.jar")));
    }

    @Test
    void looksLikeHtmlDetectsErrorPages(@TempDir Path tempDir) throws IOException {
        final Path html = tempDir.resolve("page.html");
        Files.write(html, "<!DOCTYPE html><html><body>error</body></html>".getBytes(StandardCharsets.UTF_8));
        assertTrue(OnionPluginDownloader.looksLikeHtml(html));
        final Path jar = buildPluginJar(tempDir.resolve("real.jar"), "plugin.yml");
        assertFalse(OnionPluginDownloader.looksLikeHtml(jar));
    }

    @Test
    void configGenerationCreatesFileOnce(@TempDir Path tempDir) {
        final Path configPath = tempDir.resolve("onion-plugins.yml");
        final OnionPluginDownloader.DownloaderConfig first = OnionPluginDownloader.loadConfig(configPath);
        assertTrue(Files.exists(configPath), "config must be generated when missing");
        assertTrue(first.enabled);
        final String original = read(configPath);
        OnionPluginDownloader.loadConfig(configPath);
        assertEquals(original, read(configPath), "existing config must be preserved");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
