package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.verification.VerificationModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnlinkCommand implements CommandExecutor {

    private final XDiscordUltimate plugin;

    public UnlinkCommand(XDiscordUltimate plugin) {
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

        VerificationModule verificationModule = plugin.getModuleManager().getModule(VerificationModule.class);
        if (verificationModule == null) {
            plugin.getMessageManager().sendMessage(player, "errors.module-not-available");
            return true;
        }

        if (!plugin.getAdminUtils().isVerified(player)) {
            plugin.getMessageManager().sendMessage(player, "verification.not-linked");
            return true;
        }

        boolean removed = verificationModule.unlink(player.getUniqueId());
        if (removed) {
            plugin.getMessageManager().sendMessage(player, "verification.unlink-success");
        } else {
            plugin.getMessageManager().sendError(player, "verification.verification-failed");
        }
        return true;
    }
}
