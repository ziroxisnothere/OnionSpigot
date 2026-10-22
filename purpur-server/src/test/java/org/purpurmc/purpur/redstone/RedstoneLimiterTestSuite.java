package org.purpurmc.purpur.redstone;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * OnionSpigot start - Redstone Limiter tests
 * Aggregates the redstone limiter unit tests (configuration defaults,
 * validation/clamping, world overrides and counter helpers).
 * OnionSpigot end - Redstone Limiter tests
 */
@Suite(failIfNoTests = false)
@SuiteDisplayName("OnionSpigot Redstone Limiter tests")
@SelectPackages("org.purpurmc.purpur.redstone")
public class RedstoneLimiterTestSuite {
}
