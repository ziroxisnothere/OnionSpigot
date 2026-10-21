package org.purpurmc.purpur.command;

import io.papermc.paper.ServerBuildInfo;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.purpurmc.purpur.PurpurConfig;

import static io.papermc.paper.ServerBuildInfo.StringRepresentation.VERSION_SIMPLE;

/**
 * OnionSpigot start - Centralized branding
 * Single source of truth for OnionSpigot identity output: version metadata,
 * the /onion and /onionspigot information panels and the startup banner all
 * live here so every user-facing surface stays consistent and real build
 * metadata is resolved in one place (missing metadata degrades gracefully).
 * OnionSpigot end - Centralized branding
 */
public final class OnionSpigotInfo {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String DESCRIPTION = "High-performance Minecraft server software";

    private OnionSpigotInfo() {
    }

    public static String softwareVersion() {
        return ServerBuildInfo.buildInfo().asString(VERSION_SIMPLE);
    }

    public static String minecraftVersion() {
        return ServerBuildInfo.buildInfo().minecraftVersionName();
    }

    public static String javaVersion() {
        return System.getProperty("java.version", "unknown");
    }

    public static void sendBriefInfo(CommandSender sender) {
        final ServerBuildInfo info = ServerBuildInfo.buildInfo();
        sender.sendMessage(separator());
        sender.sendMessage(header());
        sender.sendMessage(Component.text("  " + DESCRIPTION, NamedTextColor.GRAY));
        sender.sendMessage(Component.text()
                .append(Component.text("  Minecraft: ", NamedTextColor.WHITE))
                .append(Component.text(info.minecraftVersionName(), NamedTextColor.GREEN))
                .append(Component.text("   Java: ", NamedTextColor.WHITE))
                .append(Component.text(javaVersion(), NamedTextColor.GREEN))
                .build());
        sender.sendMessage(Component.text("  ", NamedTextColor.WHITE).append(link(PurpurConfig.onionRepoUrl)));
        sender.sendMessage(separator());
    }

    public static void sendDetailedInfo(CommandSender sender) {
        final ServerBuildInfo info = ServerBuildInfo.buildInfo();
        sender.sendMessage(separator());
        sender.sendMessage(header());
        sender.sendMessage(Component.text("  " + DESCRIPTION, NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());
        sender.sendMessage(keyValue("Software version", softwareVersion()));
        sender.sendMessage(keyValue("Build", buildDescription(info)));
        sender.sendMessage(keyValue("Minecraft", info.minecraftVersionName()));
        sender.sendMessage(keyValue("Java", javaVersion()));
        sender.sendMessage(keyValue("Brand", info.brandId().asString()));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  Repository: ", NamedTextColor.WHITE).append(link(PurpurConfig.onionRepoUrl)));
        sender.sendMessage(Component.text("  Documentation: ", NamedTextColor.WHITE).append(link(PurpurConfig.onionDocsUrl)));
        sender.sendMessage(separator());
        sender.sendMessage(Component.text("  Running OnionSpigot - a Purpur fork with full Paper plugin support", NamedTextColor.DARK_GRAY));
    }

    public static void printStartupBanner() {
        final ServerBuildInfo info = ServerBuildInfo.buildInfo();
        Bukkit.getLogger().info("------------------------------------------------------------");
        Bukkit.getLogger().info(" OnionSpigot " + softwareVersion() + " (Minecraft " + info.minecraftVersionName() + ")");
        Bukkit.getLogger().info(" " + DESCRIPTION);
        Bukkit.getLogger().info(" Java " + javaVersion() + " | " + PurpurConfig.onionRepoUrl);
        Bukkit.getLogger().info("------------------------------------------------------------");
    }

    private static Component header() {
        return Component.text()
                .append(Component.text("  OnionSpigot", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
                .append(Component.text(" v", NamedTextColor.DARK_GRAY))
                .append(Component.text(softwareVersion(), NamedTextColor.GREEN))
                .build();
    }

    private static Component separator() {
        return Component.text("  ----------------------------------------", NamedTextColor.DARK_GRAY);
    }

    private static Component keyValue(String label, String value) {
        return Component.text()
                .append(Component.text("  " + label + ": ", NamedTextColor.WHITE))
                .append(Component.text(value, NamedTextColor.GREEN))
                .build();
    }

    private static Component link(String url) {
        return Component.text(url, NamedTextColor.GOLD)
                .hoverEvent(Component.text("Open link", NamedTextColor.WHITE))
                .clickEvent(ClickEvent.openUrl(url));
    }

    private static String buildDescription(ServerBuildInfo info) {
        if (info.buildNumber().isPresent()) {
            final String base = "build #" + info.buildNumber().getAsInt();
            final String time = formatBuildTime(info.buildTime());
            return time.isEmpty() ? base : base + " (" + time + ")";
        }
        final Optional<String> branch = info.gitBranch();
        final Optional<String> commit = info.gitCommit();
        if (commit.isPresent()) {
            final String base = "dev build " + commit.get();
            return branch.isPresent() ? base + " on " + branch.get() : base;
        }
        return "development build";
    }

    private static String formatBuildTime(Instant time) {
        if (time == null || time.equals(Instant.EPOCH)) {
            return "";
        }
        return DATE_FORMAT.format(time.atZone(ZoneOffset.UTC));
    }
}
