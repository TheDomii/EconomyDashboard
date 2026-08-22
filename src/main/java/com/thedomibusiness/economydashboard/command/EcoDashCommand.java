package com.thedomibusiness.economydashboard.command;

import com.thedomibusiness.economydashboard.EconomyDashboardPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /ecodash - console/admin command. Was declared in plugin.yml but had no executor registered
 *  (ran as a silent no-op). "status" mirrors the console startup block; "reload" re-reads
 *  config.yml and restarts just the web server (see EconomyDashboardPlugin#reloadWebServer). */
public class EcoDashCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("status", "reload");

    private final EconomyDashboardPlugin plugin;

    public EcoDashCommand(EconomyDashboardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("economydashboard.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Berechtigung.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.GRAY + "Nutzung: /ecodash <status|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                sender.sendMessage(ChatColor.GRAY + "=== EconomyDashboard v" + plugin.getDescription().getVersion() + " ===");
                for (String line : plugin.statusLines()) {
                    sender.sendMessage(ChatColor.GRAY + line);
                }
                return true;
            case "reload":
                sender.sendMessage(ChatColor.GRAY + "Lade config.yml neu und starte den Webserver neu...");
                sender.sendMessage(ChatColor.GRAY + "(Modul-Umschalter und das Aktualisierungsintervall brauchen weiterhin einen vollen Serverneustart.)");
                String result = plugin.reloadWebServer();
                sender.sendMessage(ChatColor.GRAY + result);
                return true;
            default:
                sender.sendMessage(ChatColor.GRAY + "Nutzung: /ecodash <status|reload>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS;
        }
        return Collections.emptyList();
    }
}
