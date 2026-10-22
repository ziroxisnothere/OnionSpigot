package org.purpurmc.purpur.plugin;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * OnionSpigot start - Automatic plugin downloader tests
 * Aggregates the automatic plugin downloader unit tests (config defaults,
 * validation, filename/URL safety and plugin jar validation).
 * OnionSpigot end - Automatic plugin downloader tests
 */
@Suite(failIfNoTests = false)
@SuiteDisplayName("OnionSpigot Automatic Plugin Downloader tests")
@SelectPackages("org.purpurmc.purpur.plugin")
public class OnionPluginDownloaderTestSuite {
}
