package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.verification.VerificationModule;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
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

        if (!plugin.getConfigManager().isFeatureEnabled("verification")) {
            plugin.getMessageManager().sendMessage(player, "errors.feature-disabled");
            return true;
        }

        if (plugin.getAdminUtils().isVerified(player)) {
            plugin.getMessageManager().sendMessage(player, "verification.already-verified");
            return true;
        }

        VerificationModule verificationModule = plugin.getModuleManager().getModule(VerificationModule.class);
        if (verificationModule == null || !verificationModule.isEnabled()) {
            plugin.getMessageManager().sendError(player, "errors.module-not-available");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtils.parse("<gradient:#FF5555:#FFAA00>Usage:</gradient> &e/verify <code>"));
            player.sendMessage(ColorUtils.parse("&7To get a verification code:"));
            player.sendMessage(ColorUtils.parse("&e1. &7Use &b/verify &7in Discord"));
            player.sendMessage(ColorUtils.parse("&e2. &7Copy the code you receive"));
            player.sendMessage(ColorUtils.parse("&e3. &7Use &b/verify <code> &7here"));
            return true;
        }

        String code = args[0].toUpperCase();

        verificationModule.processMinecraftVerification(player, code);

        return true;
    }
}