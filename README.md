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
/simchat send <player> <dialogueId>
```
Send a dialogue message to a player. Uses `entityId` from the dialogue file.

## Datapack Format

Dialogues are loaded from: `data/<namespace>/simchat/<path>.json`

Example: `data/mypack/simchat/contractor/contract.json`

```json
{
  "entityId": "contractor",
  "entityName": "Director Vex",
  "entitySubtitle": "Helion Industries",
  "text": "We need 200 plasma conduits. Military-grade shielding. The Frontier Militia is buying and they don't ask questions. 5,000 credits on delivery, plus a bonus for discretion.",
  "actions": [
    {
      "label": "I'll take it",
      "commands": ["simchat send @s mypack:contractor/contract_accept"],
      "reply": "Send the specifications.",
      "items": [
        {"id": "minecraft:copper_ingot", "count": 64},
        {"id": "minecraft:redstone", "count": 32}
      ]
    },
    {
      "label": "Need time to decide",
      "commands": ["simchat send @s mypack:contractor/contract_later"],
      "reply": "Let me review my production queue."
    }
  ]
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `entityId` | string | Unique entity identifier (also used for avatar lookup) |
| `entityName` | string | Display name shown in chat |
| `entitySubtitle` | string | Subtitle (company, title) shown next to name (optional) |
| `text` | string | Message content |
| `actions` | array | Response buttons (optional) |
| `actions[].label` | string | Button text |
| `actions[].commands` | array | Commands to run when clicked (array of strings) |
| `actions[].reply` | string | Player's reply text shown in chat |
| `actions[].items` | array | Items to display on button (optional) |
| `actions[].items[].id` | string | Item ID (e.g., "minecraft:diamond") |
| `actions[].items[].count` | int | Item count (default: 1) |

### JSON Schema

Validate your dialogues with the JSON schema at [`schemas/dialogue.schema.json`](schemas/dialogue.schema.json).

To use in VS Code, add to your dialogue files:
```json
{
  "$schema": "https://raw.githubusercontent.com/YardenZamir/sim-chat/main/schemas/dialogue.schema.json",
  "entityId": "...",
  ...
}
```

## Custom Avatars

Place PNG images in: `config/simchat/avatars/`

Filename (without extension) becomes the avatar ID. Example:
- `config/simchat/avatars/merchant.png` → entity with `entityId: "merchant"` uses this avatar

Images hot-reload when modified. Any size works, displayed at 36x36.

## Entity Config Files

Define default entity info in: `config/simchat/entities/<entityId>.json`

These provide fallback values when not specified in dialogue files.

Example: `config/simchat/entities/merchant.json`

```json
{
  "name": "Merchant Bob",
  "subtitle": "Ironwood Trading Co.",
  "avatar": "merchant"
}
```

| Field | Description |
|-------|-------------|
| `name` | Default display name for this entity |
| `subtitle` | Default subtitle (company, title) |
| `avatar` | Avatar ID to use (defaults to entityId) |

Entity configs hot-reload when modified.

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
