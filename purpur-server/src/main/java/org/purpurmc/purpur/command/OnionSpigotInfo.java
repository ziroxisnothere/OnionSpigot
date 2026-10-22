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

    // OnionSpigot start - FIGlet startup banner
    /**
     * ASCII-art rendition of the word "Onion" (FIGlet "big" font style).
     * The art is kept to plain ASCII characters so it renders identically on
     * Windows CMD, PowerShell and Linux terminals without Unicode support.
     * The art is a compile-time constant: no runtime dependency or font files
     * are required to display the banner.
     */
    private static final String[] FIGLET_ART = {
        "  ____        _",
        " / __ \\      (_)",
        "| |  | |_ __  _  ___  _ __",
        "| |  | | '_ \\| |/ _ \\| '_ \\",
        "| |__| | | | | | (_) | | | |",
        " \\____/|_| |_|_|\\___/|_| |_|",
    };

    /** Width of the widest FIGlet art line, used to align the subtitle. */
    private static final int BANNER_ART_WIDTH = 28;

    // Raw ANSI colors for the console. TerminalConsoleAppender renders them on
    // ANSI-capable terminals and strips them everywhere else (Windows CMD
    // without ANSI support, log files, GUI console), so the output stays clean.
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_WHITE_BOLD = "\u001B[1;97m";
    private static final String ANSI_GRAY = "\u001B[90m";

    public static void printStartupBanner() {
        final String versionLine = "Version " + softwareVersion()
                + " | Minecraft " + minecraftVersion()
                + " | Java " + javaVersion();
        final int textWidth = Math.max(BANNER_ART_WIDTH, versionLine.length());

        final StringBuilder banner = new StringBuilder();
        for (final String line : FIGLET_ART) {
            banner.append(ANSI_GREEN).append(line).append(ANSI_RESET).append('\n');
        }
        banner.append(ANSI_WHITE_BOLD).append(center("OnionSpigot", textWidth)).append(ANSI_RESET).append('\n');
        banner.append(ANSI_GRAY).append(center("Custom Minecraft Server", textWidth)).append(ANSI_RESET).append('\n');
        banner.append(ANSI_GRAY).append(center(versionLine, textWidth)).append(ANSI_RESET);
        Bukkit.getLogger().info(banner.toString());
    }

    private static String center(String text, int width) {
        final int padding = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(padding) + text;
    }
    // OnionSpigot end - FIGlet startup banner

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
