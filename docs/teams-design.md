# Teams Design Document

## Overview

Replace per-player chat storage with team-based storage. All players belong to a team. Chat history, flags, and action state are shared across team members.

---

## Core Concepts

- Players **always** belong to exactly one team
- Auto-generated team created on first server join
- Chat history is fully shared across team members
- Player replies show the clicking player's name/avatar with team name as subtitle
- Flags are team-scoped but queried via player
- Read receipts remain per-player
- Action buttons are consumed for whole team, but conditions evaluated per-player

---

## Decisions

### 1. Auto-Team Creation

**Decision:** On first server join.

Team created immediately when player first joins server.

---

### 2. Team Data Storage

**Decision:** Per-team JSON files.

Location: `/world/data/simchat/teams/<team_id>.json`

Human-readable, easy to debug and manually edit.

---

### 3. Team ID Format

**Decision:** Hash-based 7-character string.

Generated from creation timestamp + random seed, hashed to 7 alphanumeric characters.

---

### 4. Invite System

**Decision:** Clickable chat message.

`/simchat team invite <player>` sends a clickable vanilla chat message with pre-filled join command. Player clicks to accept, ignores to decline. No expiration.

---

### 5. Admin Permissions

**Decision:** Permission node.

Use permission node `simchat.admin.team` for mod compatibility (LuckPerms, etc.).

Admins can:
- Rename other teams
- Run team commands targeting other players/teams

---

### 6. Player Avatars

**Decision:** Player skin head.

When player clicks action and reply is shown, use their Minecraft skin (head portion) like vanilla does.

---

### 7. Team Title Uniqueness

**Decision:** Allow duplicates.

Titles are display names only. Team ID (7-char hash) is the real identifier.

---

### 8. Migration

**Decision:** No migration.

Early development - break existing PlayerChatData. All players start fresh with new team system.

---

### 9. Teammate Notifications

**Decision:** Toast + sound, client-configurable.

When teammate responds to conversation:
- Show toast notification
- Play notification sound
- Both settings configurable in client config

---

### 10. Team Color

**Decision:** MC 16 colors from title hash.

Hash team title to select from Minecraft's 16 built-in team colors. Compatible with vanilla team sync.

---

### 11. Vanilla Team Sync

**Decision:** SimChat is master.

When enabled in config:
- SimChat pushes team changes to vanilla scoreboard teams
- External vanilla team changes are ignored
- SimChat commands are the only way to manage teams

---

### 12. Empty Team Cleanup

**Decision:** Never cleanup.

Teams persist forever, even when empty. Accept potential world bloat. Preserves flags and history for potential future members.

---

### 13. Team List Visibility

**Decision:** All teams visible.

`/simchat team list` shows all teams on server. Anyone can see and join any team.

---

## Data Structures

### TeamData (per-team JSON file)

```java
public class TeamData {
    String id;                                    // 7-char hash
    String title;                                 // Display name
    int color;                                    // MC color index (0-15)
    Set<UUID> members;                            // Player UUIDs
    Map<String, List<ChatMessage>> conversations; // entityId -> messages
    Map<String, String> flags;                    // Shared flags
}
```

### PlayerChatData (player capability - minimal)

```java
public class PlayerChatData {
    Map<String, Integer> readMessageCounts;  // entityId -> read count
    // Everything else is team-level
}
```

### SimChatTeamManager (world-level)

```java
public class SimChatTeamManager {
    Map<String, TeamData> teams;       // teamId -> TeamData
    Map<UUID, String> playerToTeam;    // playerUUID -> teamId
}
```

---

## Team Lifecycle

### Player First Join
1. Generate 7-char team ID (hash of timestamp + random)
2. Create TeamData with player as sole member
3. Generate title: "Team [ID]" or random name
4. Calculate color from title hash
5. Save to `/world/data/simchat/teams/<id>.json`
6. Register player -> team mapping

### Player Changes Team
1. Remove player from old team's member set
2. Add player to new team's member set
3. Update player -> team mapping
4. Clear player's read receipts (fresh start in new team)
5. Sync new team data to player
6. If vanilla sync enabled: update vanilla team membership

### Player Logs In
1. Look up player's team ID
2. Load TeamData
3. Sync full team state to player
4. All messages since last login appear as unread

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/simchat team create <title>` | 0 | Create new team, join it, set title |
| `/simchat team invite <player>` | 0 | Send clickable chat invite |
| `/simchat team join <id>` | 0 | Leave current team, join target |
| `/simchat team list` | 0 | Show all teams (id, title, member count) |
| `/simchat team title <title>` | 0 | Rename own team |
| `/simchat team title <player> <title>` | `simchat.admin.team` | Rename player's team |

---

## Network Packets

| Packet | Direction | Purpose |
|--------|-----------|---------|
| `SyncTeamDataPacket` | S→C | Full team state sync (replaces SyncChatDataPacket) |
| `TeamMessagePacket` | S→C | Single new message + notification flag |
| `TeamMembershipPacket` | S→C | Team join/leave notification to other members |

---

## Vanilla Team Sync (Config-Enabled)

When `syncVanillaTeams = true`:

| SimChat Action | Vanilla Result |
|----------------|----------------|
| Team created | Create vanilla team with same name, set color |
| Team renamed | Delete old vanilla team, create new, move members |
| Player joins team | Add to vanilla team |
| Player leaves team | Remove from vanilla team |

Vanilla team name = SimChat team title (truncated to 16 chars if needed).

---

## Client Config Additions

```toml
# Notify when teammate responds to conversation
notifyTeammateActions = true

# Play sound for teammate actions
teammateActionSound = true
```

---

## Files to Modify/Create

| File | Changes |
|------|---------|
| `PlayerChatData.java` | Reduce to read receipts only |
| New: `TeamData.java` | Team state record |
| New: `SimChatTeamManager.java` | World-level team registry |
| New: `TeamDataStorage.java` | JSON serialization for teams |
| `ChatCapability.java` | Adapt to new PlayerChatData |
| New: `TeamCapability.java` | World capability for team manager |
| `SyncChatDataPacket.java` → `SyncTeamDataPacket.java` | Sync team data |
| `NewMessagePacket.java` → `TeamMessagePacket.java` | Team-aware messaging |
| `SimChatCommands.java` | Add team subcommands |
| `ChatScreen.java` | Show team subtitle on player messages |
| `ChatHistoryWidget.java` | Render player skin for replies |
| `EntityListWidget.java` | No changes (entities still keyed by entityId) |
| `ClientConfig.java` | Add teammate notification settings |

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Two players click same action simultaneously | Server serializes. First succeeds, second fails silently (action already consumed). |
| Player joins team with existing history | Sees all history, all marked unread |
| Player offline when teammate acts | Sees changes on next login as unread |
| Team file corrupted/missing | Recreate empty team for affected players on login |
| Server has no teams (fresh world) | Teams created on-demand as players join |

---

## Flag Interface

Flags are queried/set via player but stored in team:

```java
// Setting a flag (goes to player's team)
SimChatFlags.set(player, "quest_started", "true");

// Getting a flag (reads from player's team)
String value = SimChatFlags.get(player, "quest_started");

// In KubeJS
SimChatEvents.registerPredicate('hasQuest', (player, ctx) => {
    return SimChat.getFlag(player, 'quest_started') === 'true';
});
```

Internally:
1. Look up player's team ID
2. Read/write flag in TeamData.flags
3. Sync to all online team members

---

## Example Team JSON

`/world/data/simchat/teams/a3f82b1.json`:

```json
{
  "id": "a3f82b1",
  "title": "The Adventurers",
  "color": 11,
  "members": [
    "550e8400-e29b-41d4-a716-446655440000",
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
  ],
  "conversations": {
    "merchant": [
      {
        "isPlayer": false,
        "isSystem": false,
        "entityId": "merchant",
        "senderName": "Trader Bob",
        "content": "What can I get you?",
        "worldDay": 142,
        "actions": []
      },
      {
        "isPlayer": true,
        "entityId": "merchant",
        "senderName": "Alice",
        "senderSubtitle": "The Adventurers",
        "content": "I'll take the sword.",
        "worldDay": 142
      }
    ]
  },
  "flags": {
    "quest_started": "true",
    "merchant_trust": "5"
  }
}
```
