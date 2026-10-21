package org.purpurmc.purpur.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class OnionCommand extends Command {
    public OnionCommand(String name) {
        super(name);
        this.description = "Display OnionSpigot software information";
        this.usageMessage = "/onion";
        this.setPermission("bukkit.command.onion");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) return true;

        OnionSpigotInfo.sendBriefInfo(sender);
        return true;
    }
}
