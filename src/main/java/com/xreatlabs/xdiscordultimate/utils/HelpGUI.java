package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class HelpGUI implements Listener {
    
    private final XDiscordUltimate plugin;
    private final Map<UUID, String> openGUIs = new HashMap<>();
    
    public HelpGUI(XDiscordUltimate plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    public void openMainHelp(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, color("<gradient:#55FFFF:#5555FF>XDiscordUltimate</gradient> &8• &7Help"));
        
        // Fill background with glass
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, glass);
        }
        
        // Top decoration
        ItemStack topDecor = createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, topDecor);
        }
        
        // Discord/Verification section
        inv.setItem(11, createGradientItem(Material.ENDER_PEARL, 
            "<gradient:#55FFFF:#5555FF>Discord & Verification</gradient>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fClick to view Discord commands",
            "",
            "&8▸ &7Link your Minecraft account",
            "&8▸ &7Get Discord server invite",
            "&8▸ &7Check verification status",
            "",
            "&e➤ Click to open"));
        
        // Moderation/Reports section
        inv.setItem(13, createGradientItem(Material.SHIELD, 
            "<gradient:#FF5555:#FFAA00>Moderation & Reports</gradient>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fReport rule-breaking players",
            "",
            "&8▸ &7Submit player reports",
            "&8▸ &7Track report status",
            "&8▸ &7Moderation tools",
            "",
            "&e➤ Click to open"));
        
        // Support/Tickets section
        inv.setItem(15, createGradientItem(Material.WRITABLE_BOOK, 
            "<gradient:#55FF55:#00AA00>Support & Tickets</gradient>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fGet help from our staff team",
            "",
            "&8▸ &7Create support tickets",
            "&8▸ &7Get assistance from staff",
            "&8▸ &7Track ticket progress",
            "",
            "&e➤ Click to open"));
        
        // Voice/TTS section
        inv.setItem(29, createGradientItem(Material.NOTE_BLOCK, 
            "<gradient:#AA00AA:#FF55FF>Voice & TTS</gradient>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fVoice channel features",
            "",
            "&8▸ &7Text-to-speech",
            "&8▸ &7Voice announcements",
            "&8▸ &7Discord voice integration",
            "",
            "&e➤ Click to open"));
        
        // Stats section
        inv.setItem(31, createGradientItem(Material.DIAMOND, 
            "<gradient:#55FFFF:#55FF55>Player Statistics</gradient>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fView your stats and progress",
            "",
            "&8▸ &7Kills, deaths, K/D ratio",
            "&8▸ &7Playtime tracking",
            "&8▸ &7Leaderboards",
            "",
            "&e➤ Use /stats"));
        
        // Admin section (only if admin)
        if (plugin.getAdminUtils().isAdmin(player)) {
            inv.setItem(33, createGradientItem(Material.COMMAND_BLOCK, 
                "<gradient:#FF5555:#AA0000>Admin Commands</gradient>",
                "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "&fAdvanced administration",
                "",
                "&8▸ &7Discord console access",
                "&8▸ &7Plugin management",
                "&8▸ &7Server control",
                "",
                "&c⚠ &7Admin only",
                "&e➤ Click to open"));
        }
        
        // Info item
        inv.setItem(40, createGradientItem(Material.NETHER_STAR, 
            "<rainbow>XDiscordUltimate</rainbow>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fVersion: &e" + plugin.getDescription().getVersion(),
            "&fAuthor: &eXreaLabs",
            "",
            "&7Premium Discord ↔ Minecraft",
            "&7integration plugin",
            "",
            "&e➤ Click for info"));
        
        openGUIs.put(player.getUniqueId(), "main");
        player.openInventory(inv);
    }
    
    public void openDiscordHelp(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, color("<gradient:#55FFFF:#5555FF>Discord & Verification</gradient>"));
        
        // Back button
        inv.setItem(0, createGradientItem(Material.ARROW, "&7← Back to Main Help", "&7Click to go back"));
        
        // Verify command
        inv.setItem(10, createGradientItem(Material.EMERALD, "<gradient:#55FF55:#00AA00>/verify <code></gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fLink your Discord account",
            "&7Usage: &f/verify ABC123",
            "",
            "&7Get your verification code from",
            "&7the Discord bot by typing &b/verify",
            "&7in any Discord channel"));
        
        // Discord command
        inv.setItem(11, createGradientItem(Material.COMPASS, "<gradient:#55FF55:#00AA00>/discord</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fGet Discord server invite",
            "&7Usage: &f/discord",
            "",
            "&7Shows the Discord server invite",
            "&7link and connection status"));
        
        // Status info
        inv.setItem(13, createGradientItem(Material.PLAYER_HEAD, "<gradient:#FFAA00:#FF5500>Verification Status</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fCheck if you're verified",
            "",
            "&7Verified players can:",
            "&8▸ &7Create support tickets",
            "&8▸ &7Submit reports",
            "&8▸ &7Access special features"));
        
        // Benefits
        inv.setItem(15, createGradientItem(Material.DIAMOND, "<gradient:#55FFFF:#5555FF>Verification Benefits</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fWhat you get when verified:",
            "",
            "&a✓ &7Create support tickets",
            "&a✓ &7Report rule breakers",
            "&a✓ &7Access to Discord channels",
            "&a✓ &7Real-time notifications",
            "&a✓ &7Cross-platform chat"));
        
        openGUIs.put(player.getUniqueId(), "discord");
        player.openInventory(inv);
    }
    
    public void openModerationHelp(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, color("<gradient:#FF5555:#FFAA00>Moderation & Reports</gradient>"));
        
        // Back button
        inv.setItem(0, createGradientItem(Material.ARROW, "&7← Back to Main Help", "&7Click to go back"));
        
        // Report command
        inv.setItem(10, createGradientItem(Material.REDSTONE, "<gradient:#55FF55:#00AA00>/report <player> <reason></gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fReport a rule-breaking player",
            "&7Usage: &f/report PlayerName griefing",
            "",
            "&7Examples:",
            "&f/report BadPlayer cheating",
            "&f/report Griefer destroyed my house",
            "",
            "&c⚠ &7Requires Discord verification"));
        
        // Report guidelines
        inv.setItem(12, createGradientItem(Material.PAPER, "<gradient:#FFAA00:#FF5500>Report Guidelines</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fHow to make good reports:",
            "",
            "&8▸ &7Be specific about the issue",
            "&8▸ &7Include evidence if possible",
            "&8▸ &7Don't spam reports",
            "&8▸ &7Use appropriate language",
            "",
            "&7Reports are reviewed by staff"));
        
        // Report status
        inv.setItem(14, createGradientItem(Material.CLOCK, "<gradient:#55FFFF:#5555FF>Report Status</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fTrack your reports:",
            "",
            "&8▸ &7Reports are logged to Discord",
            "&8▸ &7Staff receive notifications",
            "&8▸ &7You'll get updates on actions",
            "&8▸ &7Cooldown: 5 minutes"));
        
        // What to report
        inv.setItem(16, createGradientItem(Material.OAK_SIGN, "<gradient:#FF5555:#AA0000>What to Report</gradient>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fReport these violations:",
            "",
            "&c• &7Cheating/Hacking",
            "&c• &7Griefing",
            "&c• &7Inappropriate chat",
            "&c• &7Harassment",
            "&c• &7Rule breaking",
            "",
            "&7Don't report minor issues"));
        
        openGUIs.put(player.getUniqueId(), "moderation");
        player.openInventory(inv);
    }
    
    public void openSupportHelp(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, color("<gradient:#55FF55:#00AA00>Support & Tickets</gradient>"));
        
        // Back button
        inv.setItem(0, createGradientItem(Material.ARROW, "&7← Back to Main Help", "&7Click to go back"));
        
        // Support command
        inv.setItem(10, createGradientItem(Material.WRITABLE_BOOK, "<gradient:#55FF55:#00AA00>/support <message></gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fCreate a support ticket",
            "&7Usage: &f/support I need help with...",
            "",
            "&7Examples:",
            "&f/support Lost my items in a glitch",
            "&f/support Can't access my base",
            "",
            "&c⚠ &7Requires Discord verification"));
        
        // Ticket process
        inv.setItem(12, createGradientItem(Material.BOOK, "<gradient:#55FFFF:#5555FF>Ticket Process</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fHow tickets work:",
            "",
            "&e1. &7Create ticket with &f/support",
            "&e2. &7Discord channel is created",
            "&e3. &7Staff will respond there",
            "&e4. &7Ticket auto-closes after 24h",
            "&e5. &7You get notifications"));
        
        // Ticket limits
        inv.setItem(14, createGradientItem(Material.BARRIER, "<gradient:#FF5555:#AA0000>Ticket Limits</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fTicket restrictions:",
            "",
            "&8▸ &7Max 3 open tickets per player",
            "&8▸ &7Don't spam tickets",
            "&8▸ &7Be patient for responses",
            "&8▸ &7Provide clear descriptions"));
        
        // When to use
        inv.setItem(16, createGradientItem(Material.MAP, "<gradient:#FFAA00:#FF5500>When to Use Tickets</gradient>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fCreate tickets for:",
            "",
            "&a• &7Technical issues",
            "&a• &7Lost items/progress",
            "&a• &7Account problems",
            "&a• &7General questions",
            "&a• &7Feature requests",
            "",
            "&7For urgent issues, contact staff"));
        
        openGUIs.put(player.getUniqueId(), "support");
        player.openInventory(inv);
    }
    
    public void openVoiceHelp(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, color("<gradient:#AA00AA:#FF55FF>Voice & TTS</gradient>"));
        
        // Back button
        inv.setItem(0, createGradientItem(Material.ARROW, "&7← Back to Main Help", "&7Click to go back"));
        
        // TTS Features
        inv.setItem(10, createGradientItem(Material.NOTE_BLOCK, "<gradient:#AA00AA:#FF55FF>Text-to-Speech</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fVoice announcements:",
            "",
            "&8▸ &7Player join/leave messages",
            "&8▸ &7Chat messages read aloud",
            "&8▸ &7Discord messages in voice",
            "&8▸ &7Important notifications",
            "",
            "&7Automatic in Discord voice"));
        
        // Voice integration
        inv.setItem(12, createGradientItem(Material.JUKEBOX, "<gradient:#55FFFF:#5555FF>Voice Integration</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fDiscord voice features:",
            "",
            "&8▸ &7Real-time chat reading",
            "&8▸ &7Player event announcements",
            "&8▸ &7Cross-platform communication",
            "&8▸ &7Smart message filtering"));
        
        // Settings
        inv.setItem(14, createGradientItem(Material.REDSTONE_TORCH, "<gradient:#FFAA00:#FF5500>Voice Settings</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fConfigurable options:",
            "",
            "&8▸ &7Enable/disable TTS",
            "&8▸ &7Voice speed control",
            "&8▸ &7Message filtering",
            "&8▸ &7Channel restrictions",
            "",
            "&7Contact admin to adjust"));
        
        // Requirements
        inv.setItem(16, createGradientItem(Material.DIAMOND, "<gradient:#55FF55:#00AA00>Requirements</gradient>",
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fTo use voice features:",
            "",
            "&a✓ &7Discord verification required",
            "&a✓ &7Join Discord voice channel",
            "&a✓ &7Bot needs voice permissions",
            "",
            "&7Features work automatically"));
        
        openGUIs.put(player.getUniqueId(), "voice");
        player.openInventory(inv);
    }
    
    public void openAdminHelp(Player player) {
        if (!plugin.getAdminUtils().isAdmin(player)) {
            plugin.getMessageManager().sendError(player, "You don't have permission to view admin commands!");
            return;
        }
        
        Inventory inv = Bukkit.createInventory(null, 36, color("<gradient:#FF5555:#AA0000>Admin Commands</gradient>"));
        
        // Back button
        inv.setItem(0, createGradientItem(Material.ARROW, "&7← Back to Main Help", "&7Click to go back"));
        
        // Console command
        inv.setItem(10, createGradientItem(Material.COMMAND_BLOCK, "<gradient:#55FF55:#00AA00>/dconsole</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fToggle Discord console access",
            "&7Usage: &f/dconsole",
            "",
            "&8▸ &7Enables/disables console commands",
            "&8▸ &7From Discord bot for admins",
            "",
            "&c⚠ &7Admin permission required"));
        
        // XDiscord commands
        inv.setItem(11, createGradientItem(Material.REDSTONE_BLOCK, "<gradient:#55FF55:#00AA00>/xdiscord <action></gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fPlugin management commands",
            "&7Usage: &f/xdiscord reload",
            "",
            "&7Available actions:",
            "&f• reload &7- Reload config",
            "&f• info &7- Show plugin info",
            "&f• status &7- Check bot status"));
        
        // Discord console
        inv.setItem(13, createGradientItem(Material.REPEATING_COMMAND_BLOCK, "<gradient:#55FFFF:#5555FF>Discord Console</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fExecute commands from Discord:",
            "",
            "&7Use &f/console <command> &7in Discord",
            "&7Examples:",
            "&f/console gamemode creative Player",
            "&f/console tp Player1 Player2",
            "",
            "&c⚠ &7Only for verified admins"));
        
        // Admin verification
        inv.setItem(15, createGradientItem(Material.GOLDEN_APPLE, "<gradient:#FFAA00:#FF5500>Admin Verification</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fHow admin access works:",
            "",
            "&8▸ &7Discord ID must be in config",
            "&8▸ &7Must be verified in-game",
            "&8▸ &7Real-time permission checks",
            "&8▸ &7Secure command execution",
            "",
            "&7Contact owner to add IDs"));
        
        // Security info
        inv.setItem(16, createGradientItem(Material.SHIELD, "<gradient:#FF5555:#AA0000>Security Features</gradient>", 
            "&7━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "&fAdmin security measures:",
            "",
            "&8▸ &7Multi-layer verification",
            "&8▸ &7Command logging",
            "&8▸ &7Permission validation",
            "&8▸ &7Rate limiting",
            "&8▸ &7Audit trails"));
        
        openGUIs.put(player.getUniqueId(), "admin");
        player.openInventory(inv);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String guiType = openGUIs.get(player.getUniqueId());
        
        if (guiType == null) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;
        
        String displayName = meta.getDisplayName();
        
        // Handle back button
        if (displayName.contains("← Back to Main Help")) {
            openMainHelp(player);
            return;
        }
        
        // Handle main menu navigation
        if (guiType.equals("main")) {
            String stripped = org.bukkit.ChatColor.stripColor(displayName);
            if (stripped.contains("Discord") && stripped.contains("Verification")) {
                openDiscordHelp(player);
            } else if (stripped.contains("Moderation") && stripped.contains("Reports")) {
                openModerationHelp(player);
            } else if (stripped.contains("Support") && stripped.contains("Tickets")) {
                openSupportHelp(player);
            } else if (stripped.contains("Voice") && stripped.contains("TTS")) {
                openVoiceHelp(player);
            } else if (stripped.contains("Admin") && stripped.contains("Commands")) {
                openAdminHelp(player);
            } else if (stripped.contains("XDiscordUltimate")) {
                showPluginInfo(player);
            }
        }
    }
    
    private void showPluginInfo(Player player) {
        player.closeInventory();
        openGUIs.remove(player.getUniqueId());
        
        plugin.getMessageManager().sendInfo(player, "=== XDiscordUltimate Information ===");
        plugin.getMessageManager().sendInfo(player, "Version: " + plugin.getDescription().getVersion());
        plugin.getMessageManager().sendInfo(player, "Author: XreaLabs");
        plugin.getMessageManager().sendInfo(player, "Description: " + plugin.getDescription().getDescription());
        plugin.getMessageManager().sendInfo(player, "");
        plugin.getMessageManager().sendInfo(player, "Features:");
        plugin.getMessageManager().sendInfo(player, "• Discord-Minecraft integration");
        plugin.getMessageManager().sendInfo(player, "• Player verification system");
        plugin.getMessageManager().sendInfo(player, "• Report and ticket system");
        plugin.getMessageManager().sendInfo(player, "• Voice/TTS features");
        plugin.getMessageManager().sendInfo(player, "• Admin console access");
        plugin.getMessageManager().sendInfo(player, "");
        plugin.getMessageManager().sendInfo(player, "Use &b/help &7to open the help GUI again!");
    }
    
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createGradientItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) {
                List<String> coloredLore = Arrays.stream(lore)
                    .map(this::color)
                    .collect(Collectors.toList());
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private String color(String text) {
        return plugin.getMessageManager().colorize(text);
    }
    
    public void cleanup() {
        openGUIs.clear();
    }
}