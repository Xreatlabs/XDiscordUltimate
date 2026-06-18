package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class ConsoleAppender extends AbstractAppender {

    private static final long RATE_LIMIT_MS = 1000;
    private static final String PLUGIN_NAME = "XDiscordUltimate";

    private final XDiscordUltimate plugin;
    private volatile long lastSentTime = 0;

    public ConsoleAppender(XDiscordUltimate plugin) {
        super("XDiscordUltimateConsoleAppender", null,
                PatternLayout.newBuilder().withPattern("[%d{HH:mm:ss} %level]: %msg%n").build(), false, null);
        this.plugin = plugin;
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void append(LogEvent event) {
        if (!isStarted()) {
            return;
        }

        String message = event.getMessage().getFormattedMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        // Avoid recursion: don't relay our own plugin's logs or this appender's output
        String loggerName = event.getLoggerName();
        if (loggerName != null && (loggerName.startsWith("com.xreatlabs.xdiscordultimate") || loggerName.startsWith(PLUGIN_NAME))) {
            return;
        }
        if (message.contains("[" + PLUGIN_NAME + "]")) {
            return;
        }

        // Simple rate limit to avoid Discord rate limits and logging slowdown
        long now = System.currentTimeMillis();
        if (now - lastSentTime < RATE_LIMIT_MS) {
            return;
        }
        lastSentTime = now;

        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            TextChannel consoleChannel = plugin.getDiscordManager().getConsoleChannel();
            if (consoleChannel != null) {
                consoleChannel.sendMessage("```" + message + "```").queue();
            }
        }
    }
}