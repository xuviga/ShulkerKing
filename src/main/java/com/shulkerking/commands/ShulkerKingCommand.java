package com.shulkerking.commands;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShulkerKingCommand implements CommandExecutor {
    
    private final ShulkerKingPlugin plugin;
    
    public ShulkerKingCommand(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Get player for locale detection (if sender is a player)
        String locale = "en_us";
        if (sender instanceof Player) {
            locale = plugin.getLocaleManager().getPlayerLocale((Player) sender);
        }
        
        if (!sender.hasPermission("shulkerking.admin")) {
            if (sender instanceof Player) {
                sender.sendMessage(plugin.getMessage((Player) sender, "messages.no-permission"));
            } else {
                sender.sendMessage(plugin.getMessage("messages.no-permission"));
            }
            return true;
        }
        
        if (args.length == 0) {
            String version = plugin.getDescription().getVersion();
            sender.sendMessage(plugin.getLocaleManager().getMessage(locale, "messages.plugin-info").replace("{version}", version));
            sender.sendMessage(plugin.getLocaleManager().getMessage(locale, "messages.plugin-description"));
            sender.sendMessage(plugin.getLocaleManager().getMessage(locale, "messages.command-help"));
            sender.sendMessage(plugin.getLocaleManager().getMessage(locale, "messages.command-reload").replace("{label}", label));
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            try {
                plugin.reloadPluginConfig();
                if (sender instanceof Player) {
                    sender.sendMessage(plugin.getMessage((Player) sender, "messages.reload-success"));
                } else {
                    sender.sendMessage(plugin.getMessage("messages.reload-success"));
                }
                plugin.getLogger().info("Configuration reloaded by " + sender.getName());
            } catch (Exception e) {
                if (sender instanceof Player) {
                    sender.sendMessage(plugin.getMessage((Player) sender, "messages.reload-error"));
                } else {
                    sender.sendMessage(plugin.getMessage("messages.reload-error"));
                }
                plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
            }
            return true;
        }
        
        sender.sendMessage(plugin.getLocaleManager().getMessage(locale, "messages.unknown-command").replace("{label}", label));
        return true;
    }
}
