package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.verification.VerificationModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VerifyCommand implements CommandExecutor {
    
    private final XDiscordUltimate plugin;
    
    public VerifyCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "general.player-only");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if verification feature is enabled
        if (!plugin.getConfigManager().isFeatureEnabled("verification")) {
            plugin.getMessageManager().sendMessage(player, "errors.feature-disabled");
            return true;
        }
        
        // Check if player is already verified
        if (plugin.getAdminUtils().isVerified(player)) {
            plugin.getMessageManager().sendMessage(player, "verification.already-verified");
            return true;
        }
        
        // Get the verification module
        VerificationModule verificationModule = plugin.getModuleManager().getModule(VerificationModule.class);
        if (verificationModule == null || !verificationModule.isEnabled()) {
            plugin.getMessageManager().sendError(player, "Verification module is not available!");
            return true;
        }
        
        // Check if code is provided
        if (args.length == 0) {
            player.sendMessage("§cUsage: /verify <code>");
            player.sendMessage("§7To get a verification code:");
            player.sendMessage("§e1. §7Use §b/verify §7in Discord");
            player.sendMessage("§e2. §7Copy the code you receive");
            player.sendMessage("§e3. §7Use §b/verify <code> §7here");
            return true;
        }
        
        // Get the verification code
        String code = args[0].toUpperCase();
        
        // Process verification with the code
        verificationModule.processMinecraftVerification(player, code);
        
        return true;
    }
}