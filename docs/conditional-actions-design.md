# Conditional Actions Design Document

## Overview

Add conditional visibility/enablement to action buttons in dialogue messages.

---

## Decisions

### 1. Hide vs Disable Behavior

**Decision:** Per-action choice via `conditionMode` field.

- `"hide"` - Button doesn't render when condition fails
- `"disable"` - Button renders grayed out with tooltip explaining why

Default TBD. Disabled buttons must show reason for being disabled.

---

### 2. Condition Types

All of the following:

| Type | Description |
|------|-------------|
| `hasItem` | Player has item in inventory (extends existing itemsInput logic) |
| `flag` | Custom key-value store per player |
| `time/weather` | World time of day, day count, rain/thunder |
| `scoreboard` | Check Minecraft scoreboard objectives |
| `kubejs` | Call KubeJS predicate function (primary extensibility point) |

---

### 3. KubeJS Integration

**Decision:** Hard dependency on KubeJS.

- All modpack makers use KubeJS
- Provides maximum flexibility via JS predicates
- Simplifies codebase (no soft-dependency conditionals)

---

### 4. Flag Storage

**Decision:** Store in `PlayerChatData` with team support in mind.

- Add `Map<String, String> flags` to `PlayerChatData`
- Already syncs to client via existing packets
- Design flag system with scope concept for future team support
  - Player-scoped flags: per-player
  - Team-scoped flags: shared across team members (future)

---

### 5. JSON Syntax

**Decision:** Use Minecraft predicate syntax embedded in dialogue JSON, with additional `simchat:kubejs` condition type.

Example structure:
```json
{
  "actions": [
    {
      "label": "Secret Option",
      "conditions": {
        "condition": "minecraft:entity_properties",
        "entity": "this",
        "predicate": {
          "location": {
            "biome": "minecraft:plains"
          }
        }
      },
      "conditionMode": "hide"
    },
    {
      "label": "Buy Sword",
      "conditions": {
        "condition": "simchat:kubejs",
        "function": "canBuySword"
      },
      "conditionMode": "disable"
    }
  ]
}
```

Benefits:
- Familiar syntax for datapack authors
- Reuses MC predicate evaluation where possible
- KubeJS provides escape hatch for complex logic

---

### 6. Client-Side Evaluation

**Decision:** Cache on screen open, recheck on hover.

- When chat screen opens: query server for condition evaluation results
- Cache results for rendering
- On button hover: re-query server to catch state changes
- Server always re-validates on click (security)

This balances UX accuracy with network efficiency.

---

## Open Questions

- [ ] Default `conditionMode` value (hide or disable?)
- [ ] Tooltip generation: auto-generate from failed condition, author-provided, or both?
- [ ] Flag command syntax: `/simchat flag <player> <set|get|remove> <key> [value]`?
- [ ] Team flag implementation details (future)

---

## Files to Modify

| File | Changes |
|------|---------|
| `build.gradle` | Add KubeJS dependency |
| `ChatAction.java` | Add `conditions`, `conditionMode` fields |
| `DialogueData.java` | Parse conditions from JSON |
| `PlayerChatData.java` | Add `Map<String, String> flags`, sync in NBT |
| `dialogue.schema.json` | Add conditions schema |
| `InventoryHelper.java` → `ConditionEvaluator.java` | Unified condition evaluation |
| `ActionButtonRenderer.java` | Check conditions for hide/disable rendering |
| `ChatHistoryWidget.java` | Trigger condition cache/recheck |
| `ActionClickPacket.java` | Server-side condition validation |
| New: `ConditionCheckPacket.java` | Client→Server condition query |
| New: `ConditionResultPacket.java` | Server→Client cached results |
| `SimChatCommands.java` | Add `/simchat flag` subcommand |

---

## Example Dialogue JSON

```json
{
  "entityId": "merchant",
  "entityName": "Trader Bob",
  "entitySubtitle": "Black Market",
  "text": "What can I get you today?",
  "actions": [
    {
      "label": "Buy Iron Sword",
      "conditions": {
        "condition": "simchat:has_item",
        "item": "minecraft:emerald",
        "count": 10
      },
      "conditionMode": "disable",
      "itemsInput": [{"id": "minecraft:emerald", "count": 10}],
      "itemsOutput": [{"id": "minecraft:iron_sword", "count": 1}],
      "reply": "I'll take the sword."
    },
    {
      "label": "Secret Goods",
      "conditions": {
        "condition": "simchat:all_of",
        "conditions": [
          {
            "condition": "simchat:flag",
            "flag": "trusted_customer",
            "value": "true"
          },
          {
            "condition": "simchat:kubejs",
            "function": "isNightTime"
          }
        ]
      },
      "conditionMode": "hide",
      "commands": ["simchat send @s mypack:merchant/secret_inventory"]
    }
  ]
}
```

---

## KubeJS Integration Example

```js
// kubejs/server_scripts/simchat_predicates.js

SimChatEvents.registerPredicate('canBuySword', (player, context) => {
  return player.inventory.count('minecraft:emerald') >= 10;
});

SimChatEvents.registerPredicate('isNightTime', (player, context) => {
  return player.level.dayTime % 24000 >= 12000;
});

SimChatEvents.registerPredicate('hasCompletedQuest', (player, context) => {
  return player.stages.has('quest_1_complete'); // GameStages integration
});
```
