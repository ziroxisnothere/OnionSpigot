package org.purpurmc.purpur.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class OnionSpigotCommand extends Command {
    public OnionSpigotCommand(String name) {
        super(name);
        this.description = "Display detailed OnionSpigot software information";
        this.usageMessage = "/onionspigot";
        this.setPermission("bukkit.command.onionspigot");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) return true;

        OnionSpigotInfo.sendDetailedInfo(sender);
        return true;
    }
}
