package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class XDiscordSetupCommand implements CommandExecutor {

    private final XDiscordUltimate plugin;
    private final Map<CommandSender, SetupSession> activeSessions;

    public XDiscordSetupCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xdiscord.admin")) {
            plugin.getMessageManager().sendMessage(sender, "errors.no-permission");
            return true;
        }
        startSetup(sender);
        return true;
    }

    private void startSetup(CommandSender sender) {
        sender.sendMessage(color("<gradient:#55FFFF:#5555FF>━━━ XDiscordUltimate Setup ━━━</gradient>"));
        sender.sendMessage(color("&7This will guide you through the initial setup."));
        sender.sendMessage(color("&7Please have the following information ready:"));
        sender.sendMessage(color("  &e- Bot Token (from Discord Developer Portal)"));
        sender.sendMessage(color("  &e- Guild ID (your server ID)"));
        sender.sendMessage(color("  &e- Chat Channel ID (main chat channel)"));
        sender.sendMessage("");
        sender.sendMessage(color("&eTo cancel at any time, type 'cancel'"));
        sender.sendMessage(color("<gradient:#55FFFF:#5555FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        sender.sendMessage("");

        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(color("&eStep 1/3: Enter your Discord Bot Token:"));
            activeSessions.put(sender, new SetupSession(sender, SetupStep.BOT_TOKEN));
        } else if (sender instanceof Player) {
            ConversationFactory factory = new ConversationFactory(plugin)
                .withModality(true)
                .withPrefix(new SetupPrefix())
                .withFirstPrompt(new BotTokenPrompt())
                .withEscapeSequence("cancel")
                .withLocalEcho(false)
                .withTimeout(60)
                .addConversationAbandonedListener(event -> {
                    if (!event.gracefulExit()) {
                        sender.sendMessage(color("&cSetup cancelled or timed out."));
                    }
                });
            factory.buildConversation((Player) sender).begin();
        }
    }

    public boolean handleConsoleInput(CommandSender sender, String input) {
        SetupSession session = activeSessions.get(sender);
        if (session == null) {
            return false;
        }

        if (input.equalsIgnoreCase("cancel")) {
            sender.sendMessage(color("&cSetup cancelled."));
            activeSessions.remove(sender);
            return true;
        }

        switch (session.getCurrentStep()) {
            case BOT_TOKEN:
                if (validateBotToken(sender, input)) {
                    session.setBotToken(input);
                    sender.sendMessage(color("&eStep 2/3: Enter your Guild ID (Server ID):"));
                    session.setCurrentStep(SetupStep.GUILD_ID);
                }
                break;

            case GUILD_ID:
                if (validateSnowflakeId(sender, input, "Guild ID")) {
                    session.setGuildId(input);
                    sender.sendMessage(color("&eStep 3/3: Enter your Chat Channel ID:"));
                    session.setCurrentStep(SetupStep.CHAT_CHANNEL_ID);
                }
                break;

            case CHAT_CHANNEL_ID:
                if (validateSnowflakeId(sender, input, "Chat Channel ID")) {
                    session.setChatChannelId(input);
                    saveConfiguration(sender, session);
                    activeSessions.remove(sender);
                }
                break;
        }
        return true;
    }

    private boolean validateBotToken(CommandSender sender, String token) {
        if (token == null || token.trim().isEmpty()) {
            sender.sendMessage(color("&cBot token cannot be empty! Please try again."));
            sender.sendMessage(color("&eEnter your Discord Bot Token:"));
            return false;
        }
        return true;
    }

    private boolean validateSnowflakeId(CommandSender sender, String id, String fieldName) {
        if (id == null || id.trim().isEmpty()) {
            sender.sendMessage(color("&c" + fieldName + " cannot be empty! Please try again."));
            sender.sendMessage(color("&eEnter your " + fieldName + ":"));
            return false;
        }
        if (!id.matches("\\d{17,20}")) {
            sender.sendMessage(color("&cInvalid " + fieldName + "! Must be a numeric ID (17-20 digits)."));
            sender.sendMessage(color("&7Tip: Enable Developer Mode in Discord Settings, then right-click and Copy ID."));
            sender.sendMessage(color("&eEnter your " + fieldName + ":"));
            return false;
        }
        return true;
    }

    private void saveConfiguration(CommandSender sender, SetupSession session) {
        try {
            plugin.getConfig().set("discord.bot-token", session.getBotToken());
            plugin.getConfig().set("discord.guild-id", session.getGuildId());
            plugin.getConfig().set("discord.channels.chat", session.getChatChannelId());
            plugin.getConfig().set("features.chat-bridge.chat-channel-id", session.getChatChannelId());
            plugin.saveConfig();

            sender.sendMessage(color("<gradient:#55FF55:#00AA00>━━━ Setup Complete! ━━━</gradient>"));
            sender.sendMessage(color("&aConfiguration saved successfully!"));
            sender.sendMessage(color("&7Bot Token: &f***" + (session.getBotToken().length() > 10 ? session.getBotToken().substring(session.getBotToken().length() - 6) : "***")));
            sender.sendMessage(color("&7Guild ID: &f" + session.getGuildId()));
            sender.sendMessage(color("&7Chat Channel ID: &f" + session.getChatChannelId()));
            sender.sendMessage("");
            sender.sendMessage(color("&eNext steps:"));
            sender.sendMessage(color("  1. Restart the server or use /xdiscord reload"));
            sender.sendMessage(color("  2. Verify the bot connects successfully"));
            sender.sendMessage(color("  3. Use /xdiscord status to check connection"));
            sender.sendMessage(color("<gradient:#55FF55:#00AA00>━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        } catch (Exception e) {
            sender.sendMessage(color("&cFailed to save configuration: " + e.getMessage()));
            plugin.getLogger().severe("Failed to save setup configuration: " + e.getMessage());
        }
    }

    private String color(String message) {
        return ColorUtils.parse(message);
    }

    private static final String SNOWFLAKE_PATTERN = "\\d{17,20}";

    private static class SetupSession {
        private final CommandSender sender;
        private SetupStep currentStep;
        private String botToken;
        private String guildId;
        private String chatChannelId;

        SetupSession(CommandSender sender, SetupStep initialStep) {
            this.sender = sender;
            this.currentStep = initialStep;
        }

        SetupStep getCurrentStep() {
            return currentStep;
        }

        void setCurrentStep(SetupStep step) {
            this.currentStep = step;
        }

        String getBotToken() {
            return botToken;
        }

        void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        String getGuildId() {
            return guildId;
        }

        void setGuildId(String guildId) {
            this.guildId = guildId;
        }

        String getChatChannelId() {
            return chatChannelId;
        }

        void setChatChannelId(String chatChannelId) {
            this.chatChannelId = chatChannelId;
        }
    }

    private enum SetupStep {
        BOT_TOKEN,
        GUILD_ID,
        CHAT_CHANNEL_ID
    }

    private static class SetupPrefix implements ConversationPrefix {
        @Override
        public String getPrefix(ConversationContext context) {
            return ColorUtils.parse("&e[Setup] ");
        }
    }

    private class BotTokenPrompt extends ValidatingPrompt {

        @Override
        public String getPromptText(ConversationContext context) {
            return ColorUtils.parse("\n&eEnter your Discord Bot Token:") +
                   ColorUtils.parse("\n&7Get it from: https://discord.com/developers/applications");
        }

        @Override
        protected boolean isInputValid(ConversationContext context, String input) {
            return input != null && !input.trim().isEmpty();
        }

        @Override
        protected String getFailedValidationText(ConversationContext context, String invalidInput) {
            return ColorUtils.parse("&cBot token cannot be empty! Please try again.");
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, String input) {
            context.setSessionData("botToken", input);
            return new GuildIdPrompt();
        }
    }

    private class GuildIdPrompt extends ValidatingPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ColorUtils.parse("\n&eEnter your Guild ID (Server ID):") +
                   ColorUtils.parse("\n&7Right-click your server in Discord -> Copy ID");
        }

        @Override
        protected boolean isInputValid(ConversationContext context, String input) {
            return input != null && !input.trim().isEmpty() && input.matches(SNOWFLAKE_PATTERN);
        }

        @Override
        protected String getFailedValidationText(ConversationContext context, String invalidInput) {
            return ColorUtils.parse("&cInvalid Guild ID! Must be a numeric ID (17-20 digits)." +
                   "\n&7Tip: Enable Developer Mode in Discord Settings, then right-click and Copy ID.");
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, String input) {
            context.setSessionData("guildId", input);
            return new ChatChannelIdPrompt();
        }
    }

    private class ChatChannelIdPrompt extends ValidatingPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ColorUtils.parse("\n&eEnter your Chat Channel ID:") +
                   ColorUtils.parse("\n&7Right-click the channel in Discord -> Copy ID");
        }

        @Override
        protected boolean isInputValid(ConversationContext context, String input) {
            return input != null && !input.trim().isEmpty() && input.matches(SNOWFLAKE_PATTERN);
        }

        @Override
        protected String getFailedValidationText(ConversationContext context, String invalidInput) {
            return ColorUtils.parse("&cInvalid Channel ID! Must be a numeric ID (17-20 digits)." +
                   "\n&7Tip: Enable Developer Mode in Discord Settings, then right-click and Copy ID.");
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, String input) {
            context.setSessionData("chatChannelId", input);
            return new CompletionPrompt();
        }
    }

    private class CompletionPrompt implements Prompt {

        @Override
        public String getPromptText(ConversationContext context) {
            return "";
        }

        @Override
        public boolean blocksForInput(ConversationContext context) {
            return false;
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            try {
                String botToken = (String) context.getSessionData("botToken");
                String guildId = (String) context.getSessionData("guildId");
                String chatChannelId = (String) context.getSessionData("chatChannelId");

                plugin.getConfig().set("discord.bot-token", botToken);
                plugin.getConfig().set("discord.guild-id", guildId);
                plugin.getConfig().set("discord.channels.chat", chatChannelId);
                plugin.getConfig().set("features.chat-bridge.chat-channel-id", chatChannelId);
                plugin.saveConfig();

                context.getForWhom().sendRawMessage(color("<gradient:#55FF55:#00AA00>━━━ Setup Complete! ━━━</gradient>"));
                context.getForWhom().sendRawMessage(color("&aConfiguration saved successfully!"));
                context.getForWhom().sendRawMessage(color("&7Bot Token: &f***" +
                    (botToken.length() > 10 ? botToken.substring(botToken.length() - 6) : "***")));
                context.getForWhom().sendRawMessage(color("&7Guild ID: &f" + guildId));
                context.getForWhom().sendRawMessage(color("&7Chat Channel ID: &f" + chatChannelId));
                context.getForWhom().sendRawMessage(color(""));
                context.getForWhom().sendRawMessage(color("&eNext steps:"));
                context.getForWhom().sendRawMessage(color("  1. Restart the server or use /xdiscord reload"));
                context.getForWhom().sendRawMessage(color("  2. Verify the bot connects successfully"));
                context.getForWhom().sendRawMessage(color("  3. Use /xdiscord status to check connection"));
                context.getForWhom().sendRawMessage(color("<gradient:#55FF55:#00AA00>━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));

            } catch (Exception e) {
                context.getForWhom().sendRawMessage(color("&cFailed to save configuration: " + e.getMessage()));
                plugin.getLogger().severe("Failed to save setup configuration: " + e.getMessage());
            }
            return Prompt.END_OF_CONVERSATION;
        }
    }
}
