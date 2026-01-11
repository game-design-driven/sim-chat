# Sim Chat

A Minecraft Forge mod that adds a slack/discord-style chat interface for dialogues. Designed for modpack creators to script conversations via datapacks and commands.

## Features

- **Slack/Discord-style UI**: Sidebar with conversation list, chat history panel with message bubbles
- **Datapack-driven dialogues**: Define dialogue trees in JSON, loaded as datapack resources
- **Action buttons**: Clickable response options that trigger commands or continue dialogue
- **Typing indicators**: Simulated typing delay before messages appear
- **Toast notifications**: Non-intrusive popups when new messages arrive
- **Custom avatars**: Load PNG images from config folder for sender portraits
- **Per-player chat history**: Conversations persist per-player via capabilities

## Commands

All commands require permission level 4 by default (configurable).

```
/simchat send <player> <entityId> <dialogueId>
```
Send a dialogue message to a player. The `dialogueId` references a datapack resource.

## Datapack Format

Dialogues are loaded from: `data/<namespace>/simchat/<path>.json`

Example: `data/mypack/simchat/merchant/greeting.json`

```json
{
  "sender": "Merchant Bob",
  "image": "merchant",
  "text": "Welcome! What can I help you with?",
  "actions": [
    {
      "label": "Buy items",
      "command": "simchat send @s merchant mypack:merchant/shop",
      "reply": "Show me what you have."
    },
    {
      "label": "Just browsing",
      "command": "",
      "reply": "Just looking around."
    }
  ]
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `sender` | string | Display name shown in chat |
| `image` | string | Avatar image ID (loads from `config/simchat/avatars/<image>.png`) |
| `text` | string | Message content |
| `actions` | array | Response buttons (optional) |
| `actions[].label` | string | Button text |
| `actions[].command` | string | Command to run when clicked (empty = just show reply) |
| `actions[].reply` | string | Player's reply text shown in chat |

## Custom Avatars

Place PNG images in: `config/simchat/avatars/`

Filename (without extension) becomes the image ID. Example:
- `config/simchat/avatars/merchant.png` → use `"image": "merchant"`

Images hot-reload when modified. Any size works, displayed at 36x36.

## Configuration

### Client Config (`simchat-client.toml`)

| Option | Default | Description |
|--------|---------|-------------|
| `notificationSound` | `minecraft:block.note_block.bell` | Sound on new message (empty to disable) |
| `notificationVolume` | `0.5` | Sound volume (0.0-1.0) |
| `showToasts` | `true` | Show toast popups |
| `toastDuration` | `5.0` | Toast display time (seconds) |
| `sidebarWidth` | `240` | Conversation list width (draggable in-game) |

### Server Config (`simchat-common.toml`)

| Option | Default | Description |
|--------|---------|-------------|
| `minDelay` | `0.3` | Minimum typing indicator delay (seconds) |
| `maxDelay` | `3.0` | Maximum typing indicator delay (seconds) |
| `charsPerSecond` | `100.0` | Simulated typing speed |
| `commandPermissionLevel` | `4` | Permission level for /simchat commands (0-4) |

## Keybinds

| Key | Action |
|-----|--------|
| `;` (semicolon) | Open chat screen |

## License

MIT
