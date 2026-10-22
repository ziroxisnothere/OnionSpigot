package org.purpurmc.purpur.command;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class OnionCommand extends Command {
    public OnionCommand(String name) {
        super(name);
        this.description = "Display OnionSpigot software information and manage OnionSpigot features";
        this.usageMessage = "/onion [redstonelimiter status|reload]";
        this.setPermission("bukkit.command.onion");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) return true;

        if (args.length > 0 && args[0].equalsIgnoreCase("redstonelimiter")) {
            // OnionSpigot start - Redstone Limiter subcommands
            if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
                for (final String line : org.purpurmc.purpur.redstone.RedstoneLimiter.statusReport()) {
                    sender.sendMessage(org.bukkit.ChatColor.DARK_GREEN + "[Onion] " + org.bukkit.ChatColor.RESET + line);
                }
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                sender.sendMessage(org.bukkit.ChatColor.DARK_GREEN + "[Onion] " + org.bukkit.ChatColor.RESET + "Reloading redstonelimiter.yml...");
                for (final String line : org.purpurmc.purpur.redstone.RedstoneLimiter.reloadFromCommand()) {
                    sender.sendMessage(org.bukkit.ChatColor.DARK_GREEN + "[Onion] " + org.bukkit.ChatColor.RESET + line);
                }
                return true;
            }
            sender.sendMessage(org.bukkit.ChatColor.RED + "Usage: /onion redstonelimiter <status|reload>");
            return true;
            // OnionSpigot end - Redstone Limiter subcommands
        }
        if (args.length > 0) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "Usage: " + this.usageMessage);
            return true;
        }

        OnionSpigotInfo.sendBriefInfo(sender);
        return true;
    }

    // OnionSpigot start - Redstone Limiter tab completion
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, org.bukkit.Location location) throws IllegalArgumentException {
        if (!testPermission(sender)) return new ArrayList<>();
        if (args.length == 1) {
            final List<String> result = new ArrayList<>();
            if ("redstonelimiter".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                result.add("redstonelimiter");
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("redstonelimiter")) {
            final List<String> result = new ArrayList<>();
            for (final String option : new String[]{"status", "reload"}) {
                if (option.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    result.add(option);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
    // OnionSpigot end - Redstone Limiter tab completion
}
