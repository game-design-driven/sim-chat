# Quest & Story Writing Guide

A comprehensive guide for creating interactive stories and quests using SimChat, with examples themed around a Create mod factory automation setting.

---

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [Dialogue JSON Format](#dialogue-json-format)
3. [Actions & Buttons](#actions--buttons)
4. [Conditions](#conditions)
5. [Templates](#templates)
6. [Team Data System](#team-data-system)
7. [KubeJS Callbacks](#kubejs-callbacks)
8. [Commands Reference](#commands-reference)
9. [Complete Quest Example](#complete-quest-example)
10. [Best Practices](#best-practices)

---

## Core Concepts

SimChat conversations are **team-based** - all players on the same team share conversation history and progress. This is ideal for cooperative quest systems.

**Key components:**
- **Dialogues**: JSON files defining what NPCs say and player response options
- **Actions**: Clickable buttons that execute commands, trade items, and advance dialogue
- **Conditions**: Show/hide actions based on game state
- **Templates**: Dynamic text that pulls in player/world data
- **Team Data**: Persistent key-value storage for tracking quest progress

---

## Dialogue JSON Format

Dialogues are stored in datapacks at:
```
data/<namespace>/simchat/<path>.json
```

### Basic Structure

```json
{
  "entityId": "engineer_foreman",
  "entityName": "Chief Engineer Marcus",
  "entitySubtitle": "Factory Overseer",
  "text": "Welcome to the factory floor, engineer. We've got work to do.",
  "actions": []
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `entityId` | Yes | Unique identifier for this NPC (used for conversation grouping) |
| `entityName` | Yes | Display name shown in chat |
| `entitySubtitle` | No | Gray subtitle under name (role, title, etc.) |
| `text` | Yes | The message content |
| `actions` | No | Array of clickable response buttons |

### File Naming Convention

Organize dialogues by NPC and quest:
```
data/factory/simchat/
├── marcus/
│   ├── intro.json
│   ├── quest_gears.json
│   ├── quest_gears_complete.json
│   └── shop.json
├── elena/
│   ├── intro.json
│   └── steam_engine_tutorial.json
└── system/
    └── factory_alerts.json
```

Trigger with: `/simchat send @p factory:marcus/intro`

---

## Actions & Buttons

Actions define interactive buttons players can click.

### Basic Action

```json
{
  "entityId": "engineer_foreman",
  "entityName": "Chief Engineer Marcus",
  "text": "The conveyor belts are jammed. Can you help clear them?",
  "actions": [
    {
      "label": "I'll handle it",
      "reply": "Consider it done, Chief.",
      "commands": [
        "simchat data set quest_conveyors 1"
      ],
      "nextState": "factory:marcus/quest_conveyors_accepted"
    },
    {
      "label": "What's in it for me?",
      "reply": "What's the pay for this job?",
      "nextState": "factory:marcus/quest_conveyors_negotiate"
    }
  ]
}
```

### Action Fields

| Field | Description |
|-------|-------------|
| `label` | Button text |
| `reply` | Player's spoken response (shown as player message) |
| `commands` | Array of commands to execute (as server, targeting player) |
| `nextState` | Dialogue to automatically send after this action |
| `condition` | Condition string to show/hide this action |
| `itemsVisual` | Items displayed on button (visual only) |
| `itemsInput` | Items taken from player inventory on click |
| `itemsOutput` | Items given to player on click |

### Item Trading

```json
{
  "entityId": "merchant_cogs",
  "entityName": "Cogsworth the Merchant",
  "entitySubtitle": "Mechanical Parts Dealer",
  "text": "I've got brass gears, iron shafts, and precision mechanisms. What'll it be?",
  "actions": [
    {
      "label": "Buy Brass Gears",
      "itemsInput": [
        { "id": "minecraft:iron_ingot", "count": 4 }
      ],
      "itemsOutput": [
        { "id": "create:brass_casing", "count": 8 }
      ],
      "commands": [
        "playsound minecraft:block.anvil.use master @s ~ ~ ~ 0.5"
      ]
    },
    {
      "label": "Buy Precision Mechanism",
      "itemsInput": [
        { "id": "create:brass_ingot", "count": 8 },
        { "id": "create:cogwheel", "count": 4 }
      ],
      "itemsOutput": [
        { "id": "create:precision_mechanism", "count": 1 }
      ],
      "condition": "data:unlocked_precision"
    }
  ]
}
```

**Item display colors:**
- Input items: Blue background (items you give)
- Output items: Orange background (items you receive)
- Visual items: Gray background (informational only)

### Advanced Item Syntax

Items support full Minecraft syntax including NBT:

```json
{
  "itemsOutput": [
    {
      "id": "minecraft:diamond_pickaxe{display:{Name:'\"Factory Drill\"',Lore:['\"Property of Marcus\"']},Enchantments:[{id:efficiency,lvl:5}]}",
      "count": 1
    }
  ]
}
```

---

## Conditions

Conditions control when actions appear. Syntax: `prefix:name` or `!prefix:name` (negated).

### Built-in Condition Types

#### `data:` - Team Data Check

Shows action only if team has truthy data value:

```json
{
  "label": "Tell me about the steam engines",
  "condition": "data:met_elena",
  "nextState": "factory:marcus/steam_info"
}
```

```json
{
  "label": "I don't trust machines",
  "condition": "!data:completed_tutorial",
  "reply": "These contraptions make me nervous...",
  "nextState": "factory:marcus/reassurance"
}
```

#### `score:` - Scoreboard Check

Shows if player's score in objective > 0:

```json
{
  "label": "Claim Engineer Rank II",
  "condition": "score:engineer_xp",
  "commands": [
    "scoreboard players set @s engineer_rank 2",
    "scoreboard players set @s engineer_xp 0"
  ]
}
```

#### `permission:` - Permission Level

Shows only to operators/admins:

```json
{
  "label": "[ADMIN] Reset Factory",
  "condition": "permission:2",
  "commands": [
    "function factory:reset_all"
  ]
}
```

#### `kjs:` - KubeJS Callback

For complex logic (see KubeJS section):

```json
{
  "label": "Access the Master Control",
  "condition": "kjs:hasAllBlueprints",
  "nextState": "factory:master_control/menu"
}
```

### Combining Conditions

Currently conditions are single expressions. For complex logic, use KubeJS callbacks:

```javascript
// kubejs/server_scripts/factory_conditions.js
SimChat.registerCallback('canAccessAdvancedShop', ctx => {
  let team = ctx.getTeam()
  return team.hasData('completed_tutorial') &&
         team.getDataNumber('reputation', 0) >= 50
})
```

---

## Templates

Templates insert dynamic values into dialogue text. Syntax: `{prefix:name}`

### Built-in Template Types

#### `{player:*}` - Player Data

```json
{
  "text": "Ah, {player:name}! Back from the mines, I see. Your health is at {player:health}/{player:maxHealth}."
}
```

Available: `name`, `uuid`, `health`, `maxHealth`, `level`, `x`, `y`, `z`

#### `{team:*}` - Team Data

```json
{
  "text": "The {team:title} crew has {team:memberCount} engineers on duty."
}
```

Available: `id`, `title`, `memberCount`, `color`

#### `{data:*}` - Team Data Values

```json
{
  "text": "Factory efficiency: {data:efficiency}%. Gears produced: {data:total_gears}."
}
```

#### `{world:*}` - World State

```json
{
  "text": "Day {world:day} of operations. Current conditions: {world:weather}."
}
```

Available: `day`, `time`, `dimension`, `weather`

#### `{kjs:*}` - KubeJS Callback

```json
{
  "text": "Your current rank: {kjs:getEngineerRank}. Next promotion in {kjs:xpToNextRank} XP."
}
```

### Template Example

```json
{
  "entityId": "engineer_foreman",
  "entityName": "Chief Engineer Marcus",
  "text": "Status report for {team:title}:\n\n• Day {world:day} of operations\n• Gears manufactured: {data:total_gears}\n• Current efficiency: {data:efficiency}%\n• Engineers on shift: {team:memberCount}\n\nKeep up the good work, {player:name}!",
  "actions": [
    {
      "label": "View detailed stats",
      "nextState": "factory:marcus/detailed_stats"
    }
  ]
}
```

---

## Team Data System

Team data is persistent key-value storage shared by all team members. Perfect for quest progress, reputation, counters, and flags.

### Commands

```bash
# Set a value (auto-detects string vs number)
/simchat data set quest_stage 3
/simchat data set current_objective "repair_generator"

# Add to numeric value
/simchat data add total_gears 50
/simchat data add reputation 10

# Get current value
/simchat data get reputation

# Remove a key
/simchat data remove temp_flag

# List all data
/simchat data list
```

### In Dialogue Actions

```json
{
  "label": "Accept the job",
  "commands": [
    "simchat data set quest_generator active",
    "simchat data set quest_generator_stage 1"
  ],
  "nextState": "factory:elena/generator_stage1"
}
```

### Quest Stage Pattern

Track multi-stage quests with numbered stages:

```json
// quest_generator_start.json
{
  "entityId": "engineer_elena",
  "entityName": "Elena Steamwright",
  "text": "The main generator is failing. We need copper coils to repair it.",
  "actions": [
    {
      "label": "I'll find the copper",
      "commands": [
        "simchat data set quest_gen 1"
      ],
      "nextState": "factory:elena/gen_stage1"
    }
  ]
}

// gen_stage1.json (triggered when player returns)
{
  "entityId": "engineer_elena",
  "entityName": "Elena Steamwright",
  "text": "Did you find the copper coils?",
  "actions": [
    {
      "label": "Here they are",
      "condition": "kjs:hasCoils",
      "itemsInput": [
        { "id": "create:copper_sheet", "count": 16 }
      ],
      "commands": [
        "simchat data set quest_gen 2",
        "simchat data add reputation 25"
      ],
      "nextState": "factory:elena/gen_stage2"
    },
    {
      "label": "Still looking...",
      "condition": "!kjs:hasCoils",
      "reply": "Not yet, give me more time."
    }
  ]
}
```

### Truthiness Rules

The `data:` condition checks truthiness:
- `null` / missing → false
- `0` → false
- `""` (empty string) → false
- Any other value → true

This means you can use simple flags:
```bash
/simchat data set met_elena 1        # truthy
/simchat data set met_elena 0        # falsy (hides condition)
/simchat data remove met_elena       # falsy (removes entirely)
```

---

## KubeJS Callbacks

For complex logic beyond simple data checks, use KubeJS callbacks.

### Registration

Create `kubejs/server_scripts/simchat_callbacks.js`:

```javascript
// Called when KubeJS scripts load
SimChat.registerCallback('hasCoils', ctx => {
  // ctx.getPlayer() returns ServerPlayer
  let player = ctx.getPlayer()
  let inv = player.inventory

  let coilCount = 0
  for (let i = 0; i < inv.containerSize; i++) {
    let stack = inv.getItem(i)
    if (stack.id == 'create:copper_sheet') {
      coilCount += stack.count
    }
  }
  return coilCount >= 16
})

SimChat.registerCallback('getEngineerRank', ctx => {
  let team = ctx.getTeam()
  if (!team) return 'Unranked'

  let rep = team.getDataNumber('reputation', 0)
  if (rep >= 500) return 'Master Engineer'
  if (rep >= 200) return 'Senior Engineer'
  if (rep >= 50) return 'Engineer'
  return 'Apprentice'
})

SimChat.registerCallback('xpToNextRank', ctx => {
  let team = ctx.getTeam()
  if (!team) return '???'

  let rep = team.getDataNumber('reputation', 0)
  if (rep >= 500) return 'MAX'
  if (rep >= 200) return (500 - rep).toString()
  if (rep >= 50) return (200 - rep).toString()
  return (50 - rep).toString()
})

SimChat.registerCallback('hasAllBlueprints', ctx => {
  let team = ctx.getTeam()
  if (!team) return false

  return team.hasData('blueprint_gears') &&
         team.hasData('blueprint_steam') &&
         team.hasData('blueprint_press')
})

SimChat.registerCallback('factoryEfficiency', ctx => {
  let team = ctx.getTeam()
  if (!team) return '0'

  let gears = team.getDataNumber('machines_gears', 0)
  let steam = team.getDataNumber('machines_steam', 0)
  let press = team.getDataNumber('machines_press', 0)

  let efficiency = Math.min(100, Math.floor((gears + steam + press) / 3 * 10))
  return efficiency.toString() + '%'
})
```

### Callback Context

The callback receives a `CallbackContext` with these methods:

```javascript
ctx.getPlayer()     // ServerPlayer - the player who triggered the dialogue
ctx.getTeam()       // TeamData - the player's team (can be null)
ctx.getEntityId()   // String - the NPC's entityId (can be null)
```

### TeamData Methods

```javascript
let team = ctx.getTeam()

// Data access
team.hasData('key')                    // boolean - truthy check
team.getData('key')                    // Object - raw value or null
team.getDataString('key', 'default')   // String
team.getDataNumber('key', 0)           // double
team.getDataInt('key', 0)              // int

// Team info
team.getId()          // String - 7-char team ID
team.getTitle()       // String - team display name
team.getMemberCount() // int
team.getColor()       // int (0-15)
```

### Reloading Callbacks

After editing KubeJS scripts:
```bash
/reload                    # Reloads datapacks + KubeJS
/simchat reload            # Re-fires callback registration
/simchat callback list     # Verify callbacks registered
/simchat callback run hasCoils    # Test a callback
```

---

## Commands Reference

### Sending Dialogues

```bash
# Send dialogue to player
/simchat send <player> <dialogue_id>
/simchat send @p factory:marcus/intro
/simchat send @a factory:system/announcement

# Send system message (no NPC, just text)
/simchat system <player> <entityId> <message>
/simchat system @a factory_alerts "WARNING: Steam pressure critical!"
```

### Managing Conversations

```bash
# Clear all conversations for a player's team
/simchat clear <player>

# Clear specific NPC conversation
/simchat clear <player> <entityId>
/simchat clear @p engineer_foreman
```

### Opening Chat Screen

```bash
# Open chat screen
/simchat open <player>

# Open to specific NPC
/simchat open <player> <entityId>
/simchat open @p merchant_cogs
```

### Team Management

```bash
# Create team
/simchat team create <title>
/simchat team create "Factory Alpha"

# Invite player
/simchat team invite <player>
/simchat team invite          # Broadcast to all

# Join team
/simchat team join <id_or_name>

# Team info
/simchat team list
/simchat team info

# Customize
/simchat team title "New Name"
/simchat team color gold
```

### Triggering from Other Systems

Call dialogues from:

**Command blocks:**
```
/execute as @p[distance=..5] run simchat send @s factory:terminal/access
```

**Functions:**
```mcfunction
# data/factory/functions/start_quest.mcfunction
simchat data set quest_main 1
simchat send @s factory:marcus/quest_start
playsound minecraft:entity.experience_orb.pickup master @s
```

**Create mod contraptions** (via command blocks or sequenced gearbox + command):
```
/execute if block ~ ~-1 ~ create:mechanical_press run simchat send @a[distance=..10] factory:system/press_activated
```

---

## Complete Quest Example

A multi-stage quest for building your first mechanical press.

### Quest Flow

```
[Marcus Intro] → Accept quest
       ↓
[Stage 1] → Gather iron + andesite
       ↓
[Stage 2] → Craft andesite alloy
       ↓
[Stage 3] → Build the press
       ↓
[Complete] → Unlock advanced shop
```

### Dialogue Files

**`data/factory/simchat/marcus/quest_press_intro.json`**
```json
{
  "entityId": "engineer_foreman",
  "entityName": "Chief Engineer Marcus",
  "entitySubtitle": "Factory Overseer",
  "text": "{player:name}, we need to expand production capacity. I need you to build our first Mechanical Press.\n\nIt's a complex machine, but I'll guide you through it. Are you ready?",
  "actions": [
    {
      "label": "I'm ready to learn",
      "reply": "Tell me what I need to do, Chief.",
      "commands": [
        "simchat data set quest_press 1",
        "simchat data set quest_press_stage 1"
      ],
      "nextState": "factory:marcus/quest_press_s1"
    },
    {
      "label": "Maybe later",
      "reply": "I've got other things to handle first."
    }
  ]
}
```

**`data/factory/simchat/marcus/quest_press_s1.json`**
```json
{
  "entityId": "engineer_foreman",
  "entityName": "Chief Engineer Marcus",
  "text": "First, we need materials. Gather these:\n\n• 16 Iron Ingots\n• 32 Andesite\n\nYou'll find iron in the mines below. Andesite is common in the quarry. Come back when you have them.",
  "actions": [
    {
      "label": "Hand over materials",
      "condition": "kjs:hasPressStage1Materials",
      "itemsInput": [
        { "id": "minecraft:iron_ingot", "count": 16 },
        { "id": "minecraft:andesite", "count": 32 }
      ],
      "reply": "Here are the materials.",
      "commands": [
        "simchat data set quest_press_stage 2",
        "simchat data add reputation 10"
      ],
      "nextState": "factory:marcus/quest_press_s2"
    },
    {
      "label": "Where's the mine?",
      "reply": "Which way to the mines?",
      "nextState": "factory:marcus/directions_mine"
    },
    {
      "label": "Where's the quarry?",
      "reply": "Point me to the quarry.",
      "nextState": "factory:marcus/directions_quarry"
    }
  ]
}
```

**`data/factory/simchat/marcus/quest_press_s2.json`**
```json
{
  "entityId": "engineer_foreman",
  "entityName": "Chief Engineer Marcus",
  "text": "Good work! Now we need to process these into Andesite Alloy.\n\nTake the materials to Elena at the Mixing Station. She'll show you how to combine them properly.",
  "actions": [
    {
      "label": "I'll find Elena",
      "reply": "On my way.",
      "commands": [
        "simchat data set met_elena_hint 1"
      ]
    }
  ]
}
```

**`data/factory/simchat/elena/mixing_tutorial.json`**
```json
{
  "entityId": "engineer_elena",
  "entityName": "Elena Steamwright",
  "entitySubtitle": "Mixing Specialist",
  "text": "Marcus sent you? Perfect timing - the mixer is warmed up.\n\nAndesite Alloy is simple: combine iron nuggets with andesite in a 1:1 ratio. The mechanical mixer does the rest.\n\nHere, I'll process your first batch.",
  "actions": [
    {
      "label": "Process my materials",
      "condition": "kjs:hasMixingMaterials",
      "itemsInput": [
        { "id": "minecraft:iron_nugget", "count": 16 },
        { "id": "minecraft:andesite", "count": 16 }
      ],
      "itemsOutput": [
        { "id": "create:andesite_alloy", "count": 16 }
      ],
      "reply": "Let's see this machine work.",
      "commands": [
        "simchat data set quest_press_stage 3",
        "simchat data set met_elena 1",
        "simchat data add reputation 15"
      ],
      "nextState": "factory:elena/mixing_complete"
    },
    {
      "label": "I need iron nuggets",
      "reply": "I have ingots, not nuggets.",
      "nextState": "factory:elena/nugget_hint"
    }
  ]
}
```

**`data/factory/simchat/elena/mixing_complete.json`**
```json
{
  "entityId": "engineer_elena",
  "entityName": "Elena Steamwright",
  "text": "Beautiful! See how the alloy has that distinctive gray-blue sheen?\n\nNow take this back to Marcus. He'll show you the final assembly.\n\nOh, and {player:name}? Come back anytime - I can teach you more advanced recipes once you've proven yourself.",
  "actions": [
    {
      "label": "Thanks, Elena",
      "reply": "I appreciate the help."
    }
  ]
}
```

**`data/factory/simchat/marcus/quest_press_s3.json`**
```json
{
  "entityId": "engineer_foreman",
  "entityName": "Chief Engineer Marcus",
  "text": "Elena's work is impeccable as always. Now for the final step.\n\nYou'll need:\n• 1 Andesite Casing (craft from alloy + logs)\n• 1 Shaft (from andesite alloy)\n• 1 Iron Block\n\nAssemble them at the Assembly Table in Bay 3. The schematic is posted there.",
  "actions": [
    {
      "label": "Press is built!",
      "condition": "kjs:hasBuiltPress",
      "reply": "The Mechanical Press is operational!",
      "commands": [
        "simchat data set quest_press complete",
        "simchat data remove quest_press_stage",
        "simchat data set unlocked_precision 1",
        "simchat data add reputation 50",
        "simchat data add total_machines 1"
      ],
      "nextState": "factory:marcus/quest_press_complete"
    },
    {
      "label": "How do I make a casing?",
      "reply": "What's the casing recipe?",
      "nextState": "factory:marcus/recipe_casing"
    },
    {
      "label": "Still working on it",
      "reply": "Give me a bit more time."
    }
  ]
}
```

**`data/factory/simchat/marcus/quest_press_complete.json`**
```json
{
  "entityId": "engineer_foreman",
  "entityName": "Chief Engineer Marcus",
  "text": "Outstanding work, Engineer {player:name}!\n\nThe {team:title} crew now has a functional Mechanical Press. This opens up advanced manufacturing.\n\nAs promised, I've unlocked access to Precision Mechanisms in Cogsworth's shop. You'll need those for the next tier of machines.\n\nCurrent Status:\n• Reputation: {data:reputation}\n• Machines Built: {data:total_machines}\n• Rank: {kjs:getEngineerRank}",
  "actions": [
    {
      "label": "What's next?",
      "reply": "What should I build next?",
      "nextState": "factory:marcus/quest_list"
    },
    {
      "label": "Visit Cogsworth's shop",
      "commands": [
        "simchat open @s merchant_cogs"
      ]
    }
  ]
}
```

### KubeJS Callbacks

**`kubejs/server_scripts/factory_quests.js`**
```javascript
// Stage 1: Check for raw materials
SimChat.registerCallback('hasPressStage1Materials', ctx => {
  let player = ctx.getPlayer()
  return hasItems(player, 'minecraft:iron_ingot', 16) &&
         hasItems(player, 'minecraft:andesite', 32)
})

// Stage 2: Check for mixing materials
SimChat.registerCallback('hasMixingMaterials', ctx => {
  let player = ctx.getPlayer()
  return hasItems(player, 'minecraft:iron_nugget', 16) &&
         hasItems(player, 'minecraft:andesite', 16)
})

// Stage 3: Check if press exists in world (simplified - checks data flag)
// In practice, you might scan for the block or use an advancement
SimChat.registerCallback('hasBuiltPress', ctx => {
  let team = ctx.getTeam()
  // Player must manually confirm, or use advancement trigger
  // For demo, we check if they have the output item as proof
  let player = ctx.getPlayer()
  return hasItems(player, 'create:mechanical_press', 1)
})

// Helper function
function hasItems(player, itemId, count) {
  let total = 0
  let inv = player.inventory
  for (let i = 0; i < inv.containerSize; i++) {
    let stack = inv.getItem(i)
    if (stack.id == itemId) {
      total += stack.count
    }
  }
  return total >= count
}

// Rank calculation
SimChat.registerCallback('getEngineerRank', ctx => {
  let team = ctx.getTeam()
  if (!team) return 'Unranked'

  let rep = team.getDataNumber('reputation', 0)
  if (rep >= 500) return '§6Master Engineer§r'
  if (rep >= 200) return '§eSenior Engineer§r'
  if (rep >= 50) return '§aEngineer§r'
  return '§7Apprentice§r'
})
```

### Trigger Function

**`data/factory/functions/check_player_progress.mcfunction`**
```mcfunction
# Call this periodically or on certain triggers
# Routes player to appropriate quest stage

execute if score @s quest_press matches 0 run simchat send @s factory:marcus/quest_press_intro
execute if score @s quest_press matches 1 run simchat send @s factory:marcus/quest_press_s1
execute if score @s quest_press matches 2 if entity @s[nbt={Inventory:[{id:"create:andesite_alloy",Count:16b}]}] run simchat send @s factory:marcus/quest_press_s3
```

---

## Best Practices

### 1. Organize by NPC

Keep all dialogues for one NPC in a subfolder:
```
simchat/marcus/intro.json
simchat/marcus/shop.json
simchat/marcus/quest_*.json
```

### 2. Use Consistent Entity IDs

The `entityId` groups conversations. Use the same ID across all dialogues for one NPC:
```json
"entityId": "engineer_foreman"  // Always this for Marcus
```

### 3. Provide Multiple Response Options

Give players agency with 2-4 choices when possible:
```json
"actions": [
  { "label": "Accept quest", ... },
  { "label": "Ask for more details", ... },
  { "label": "Decline politely", ... }
]
```

### 4. Use Conditions to Show Relevant Options

Hide options that don't apply:
```json
{
  "label": "Return the artifact",
  "condition": "kjs:hasArtifact",
  ...
},
{
  "label": "I'm still searching",
  "condition": "!kjs:hasArtifact",
  ...
}
```

### 5. Track State Granularly

Use descriptive data keys:
```bash
simchat data set quest_press_stage 2    # Better than just "2"
simchat data set met_elena 1            # Flag for meeting NPC
simchat data set unlocked_precision 1   # Unlock flag
```

### 6. Give Feedback on Progress

Use templates to show players their status:
```json
"text": "Progress Report:\n• Reputation: {data:reputation}\n• Machines: {data:total_machines}\n• Rank: {kjs:getEngineerRank}"
```

### 7. Chain Dialogues with nextState

Avoid manual `/simchat send` in commands when possible:
```json
{
  "label": "Continue",
  "nextState": "factory:marcus/next_dialogue"  // Automatic chaining
}
```

### 8. Use System Messages for Alerts

For non-NPC communications:
```bash
/simchat system @a factory_alerts "⚠ Steam pressure critical in Sector 7!"
```

### 9. Test Incrementally

Test each dialogue as you create it:
```bash
/simchat send @s factory:marcus/quest_press_s1
/simchat callback run hasPressStage1Materials
/simchat data list
```

### 10. Handle Edge Cases

Add fallback options for players who get stuck:
```json
{
  "label": "I need a reminder",
  "reply": "What was I supposed to do again?",
  "nextState": "factory:marcus/quest_press_reminder"
}
```

---

## Quick Reference Card

### Dialogue JSON
```json
{
  "entityId": "npc_id",
  "entityName": "Display Name",
  "entitySubtitle": "Optional Subtitle",
  "text": "Message with {player:name} templates",
  "actions": [
    {
      "label": "Button Text",
      "reply": "Player says this",
      "commands": ["command1", "command2"],
      "nextState": "namespace:path/to/next",
      "condition": "data:flag_name",
      "itemsInput": [{"id": "minecraft:diamond", "count": 1}],
      "itemsOutput": [{"id": "minecraft:emerald", "count": 5}]
    }
  ]
}
```

### Condition Prefixes
| Prefix | Example | Checks |
|--------|---------|--------|
| `data:` | `data:quest_done` | Team data truthiness |
| `!data:` | `!data:quest_done` | Team data falsy |
| `kjs:` | `kjs:hasItem` | KubeJS callback returns truthy |
| `score:` | `score:points` | Scoreboard > 0 |
| `permission:` | `permission:2` | Op level >= N |

### Template Prefixes
| Prefix | Examples |
|--------|----------|
| `{player:*}` | name, uuid, health, level, x, y, z |
| `{team:*}` | id, title, memberCount, color |
| `{data:*}` | Any team data key |
| `{world:*}` | day, time, dimension, weather |
| `{kjs:*}` | Any registered callback |

### Essential Commands
```bash
/simchat send <player> <dialogue>
/simchat data set <key> <value>
/simchat data add <key> <amount>
/simchat data get <key>
/simchat open <player> [entityId]
/simchat clear <player> [entityId]
/simchat callback list
/simchat reload
```

