# XDiscordUltimate

The most comprehensive Discord integration plugin for Minecraft servers. Features modern slash commands, chat bridge, verification system, tickets, moderation, and much more.

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Minecraft](https://img.shields.io/badge/minecraft-1.16.5+-green)
![Java](https://img.shields.io/badge/java-17+-orange)
![Discord](https://img.shields.io/badge/discord-JDA_5.0-7289da)
![License](https://img.shields.io/badge/license-MIT-purple)

## Features

- **Chat Bridge** - Seamless chat sync between Minecraft and Discord
- **Verification System** - Secure account linking with verification codes
- **Support Tickets** - Full ticket system with Discord integration
- **Player Stats** - Track and display player statistics
- **Server Status** - Auto-updating status embed in Discord
- **Moderation** - Ban sync, chat filtering, and report system
- **Admin Console** - Execute server commands from Discord
- **Announcements** - Broadcast to both Discord and Minecraft
- **Booster Perks** - Reward Discord server boosters
- **Admin Alerts** - Server health monitoring
- **Server Logging** - Log all events to Discord

## Quick Start

1. Download the latest release
2. Place in your `plugins` folder
3. Start server to generate config
4. Configure `config.yml` with your bot token and guild ID
5. Restart server

```yaml
discord:
  bot-token: "YOUR_BOT_TOKEN"
  guild-id: "YOUR_GUILD_ID"
  channels:
    chat: "CHANNEL_ID"
    logs: "CHANNEL_ID"
```

## Documentation

### Getting Started

**New to XDiscordUltimate?** Start here:

| Guide | Description |
|-------|-------------|
| [**Getting Started Guide**](docs/GETTING-STARTED.md) | Step-by-step setup from scratch |
| [Installation Guide](docs/INSTALLATION.md) | Quick installation reference |

### Core Documentation

| Document | Description |
|----------|-------------|
| [Installation Guide](docs/INSTALLATION.md) | Complete setup instructions |
| [Configuration Guide](docs/CONFIGURATION.md) | All configuration options |
| [Commands Reference](docs/COMMANDS.md) | All commands and permissions |
| [Features Guide](docs/FEATURES.md) | Detailed feature explanations |
| [Permissions Reference](docs/PERMISSIONS.md) | All permission nodes |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Common issues and solutions |
| [Messages Configuration](docs/MESSAGES.md) | Customize all messages |
| [Developer API](docs/API.md) | API for developers |
| [Database Reference](docs/DATABASE.md) | Database schema and configuration |

### Module Documentation

| Module | Description |
|--------|-------------|
| [Chat Bridge](docs/modules/CHAT-BRIDGE.md) | Minecraft-Discord chat sync |
| [Verification](docs/modules/VERIFICATION.md) | Account linking system |
| [Server Logging](docs/modules/SERVER-LOGGING.md) | Event logging to Discord |
| [Admin Alerts](docs/modules/ADMIN-ALERTS.md) | Server health monitoring |
| [Tickets](docs/modules/TICKETS.md) | Support ticket system |
| [Moderation](docs/modules/MODERATION.md) | Ban sync, filter, reports |
| [Server Control](docs/modules/SERVER-CONTROL.md) | Remote server management |
| [Player Stats](docs/modules/PLAYER-STATS.md) | Statistics tracking |
| [Server Status](docs/modules/SERVER-STATUS.md) | Auto-updating status embed |
| [Booster Perks](docs/modules/BOOSTER-PERKS.md) | Discord booster rewards |
| [Bot Console](docs/modules/BOT-CONSOLE.md) | Discord console access |
| [Announcements](docs/modules/ANNOUNCEMENTS.md) | Cross-platform broadcasts |
| [Module Manager](docs/modules/MODULE-MANAGER.md) | Managing modules |

## Commands

### Player Commands
| Command | Description |
|---------|-------------|
| `/verify <code>` | Link your Discord account |
| `/support <message>` | Create a support ticket |
| `/report <player> <reason>` | Report a player |
| `/stats [player]` | View player statistics |
| `/help` | Open help menu |

### Admin Commands
| Command | Description |
|---------|-------------|
| `/xdiscord reload` | Reload configuration |
| `/xdiscord status` | Show plugin status |
| `/xdiscord modules` | List all modules |
| `/announce <message>` | Make an announcement |

### Discord Slash Commands
| Command | Description |
|---------|-------------|
| `/verify` | Get verification code |
| `/status` | Show server status |
| `/players` | List online players |
| `/stats [player]` | View player statistics |
| `/console <command>` | Execute server command |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `xdiscord.verify` | Use verification | true |
| `xdiscord.support` | Create tickets | true |
| `xdiscord.report` | Report players | true |
| `xdiscord.admin` | Admin commands | op |
| `xdiscord.console` | Console access | false |
| `xdiscord.announce` | Make announcements | op |
| `xdiscord.bypass.verification` | Bypass verification | false |
| `xdiscord.bypass.filter` | Bypass chat filter | false |

## Requirements

- **Minecraft Server**: Spigot/Paper 1.16.5+
- **Java**: 17 or higher
- **Discord Bot**: With MESSAGE_CONTENT intent enabled

### Optional Dependencies
- LuckPerms - For permission groups
- PlaceholderAPI - For placeholders
- Vault - For economy (future)

## Configuration Example

```yaml
# Admin Discord IDs
adminIDs:
  - "123456789012345678"

# Enable features
features:
  chat-bridge:
    enabled: true
    chat-channel-id: "1234567890"
    minecraft-to-discord: true
    discord-to-minecraft: true
  
  verification:
    enabled: true
    verified-role: "Verified"
    code-expiry-minutes: 5
  
  server-logging:
    enabled: true
    logs-channel-id: "1234567890"
    log-join-leave: true
    log-commands: true
  
  tickets:
    enabled: true
    auto-close-hours: 48
    save-transcript: true
  
  player-stats:
    enabled: true
  
  server-status:
    enabled: true
    channel-id: "1234567890"
    update-interval: 60
  
  moderation:
    enabled: true
    sync-bans: true
    auto-moderate: true
  
  admin-alerts:
    enabled: true
    tps-threshold: 15.0
    ram-threshold: 90
  
  booster-perks:
    enabled: true
    permission-group: "booster"

# Discord settings
discord:
  bot-token: "YOUR_TOKEN"
  guild-id: "YOUR_GUILD_ID"
  channels:
    chat: ""
    logs: ""
    tickets: ""
    moderation: ""

# Database
database:
  type: "sqlite"
```

## Support

- **Issues**: [GitHub Issues](https://github.com/xreatlabs/XDiscordUltimate/issues)
- **Discord**: [Join our Discord](https://discord.gg/xreatlabs)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

Made with ❤️ by XreatLabs
