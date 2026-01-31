Design Doc: SimChat Web API + SSE

Goal
- Expose SimChat team/player data and actions over HTTP while the world is running.
- Enable an external client (React) to mirror the in-game chat behavior.
- SSE provides live updates; REST provides initial load and pagination.
- No auth; API disabled by default.

Non-Goals
- No authentication/authorization.
- No rate limiting.
- No offline inventory validation or offline command execution.
- No nullable-player support in core chat flow.
- No client runtime template engine shipped in this phase (client reimplements).

Scope
- HTTP server embedded in the dedicated server process.
- REST endpoints for teams, players, messages, and template resolution.
- POST actions mirroring ActionClickPacket behavior (restricted to nextState-only actions).
- SSE events for new messages, message updates (actions consumed), typing state,
  read/focus updates, and team metadata changes.
- Persist read/focus to DB for offline updates and sync on login.

Architecture

1) Server lifecycle
- Start HTTP server on ServerStartingEvent.
- Stop HTTP server on ServerStoppingEvent.
- HTTP handlers run on a dedicated executor.
- All reads/writes of game state executed on the main server thread via
  server.execute(...).

2) Packages
- com.yardenzamir.simchat.web
  - WebServer (start/stop, routing)
  - SseHub (client registry per team, broadcasting)
  - ApiHandlers (REST endpoints)
  - Json (DTO mapping utilities)
  - ActionProcessor (shared action validation/execution)

3) Data sources
- Teams/messages: SimChatTeamManager, SimChatDatabase.
- Player <-> team mapping: SimChatDatabase.loadPlayerTeams.
- Player name <-> uuid mapping: MinecraftServer.getProfileCache().
- Read/focus counts: ChatCapability + DB persistence for offline writes.

REST Endpoints

GET /teams
- Returns list of team IDs and minimal metadata.

GET /teams/{teamId}
- Returns:
  - team metadata (title, color, members, entity order)
  - messageCountPerEntity
  - team data key/value map
  - per-entity metadata (display name/template, subtitle/template, image id,
    last message, last entity message, typing state)

GET /players/{uuid}/team
- Returns team id + metadata.
- If player not mapped, 404.

GET /teams/{teamId}/messages
- Query: entityId, beforeIndex, limit
- Behavior mirrors RequestOlderMessagesPacket:
  - totalCount
  - startIndex
  - hasOlder
  - messages[]

POST /actions/click
- Body:
  {
    "teamId": "abc1234",
    "entityId": "npc_vendor",
    "messageId": "uuid",
    "actionIndex": 0,
    "playerId": "optional uuid or name",
    "inputValue": "optional"
  }
- Validation + execution mirrors ActionClickPacket with restrictions:
  - action must have nextState
  - commands must be empty
  - itemsInput/itemsOutput must be empty
  - no kjs: usage in action condition or templates

POST /read
- Body:
  { "playerId": "uuid or name", "entityId": "npc_vendor" }
- Online only unless offline persistence is added.

POST /focus
- Body:
  { "playerId": "uuid or name", "entityId": "npc_vendor", "messageId": "uuid", "messageIndex": 12 }
- Stored in DB for offline updates; synced into capability on login.

POST /templates/resolve
- Body:
  { "teamId": "abc1234", "playerId": "optional uuid or name", "entityId": "npc_vendor",
    "fieldKey": "content", "template": "{world:day}" }
- Uses ResolveTemplateRequestPacket logic on server.
- If resolution fails, returns the original template string.

SSE

GET /teams/{teamId}/events
- Content-Type: text/event-stream
- Cache-Control: no-cache
- Connection: keep-alive
- Periodic heartbeat event.

Event types
- message:new -> new message appended
- message:update -> message actions consumed
- typing -> entity typing state
- read -> player read counts updated (online players only)
- focus -> player focus updated (online only)
- team:update -> title/color/members/data changes

Event payloads
- Always include teamId, entityId when relevant.
- Messages include messageId, messageIndex, totalCount, message DTO.
- Read/focus include playerId, readCount or focus details.

Broadcast hooks
- NetworkHandler.sendMessageToTeam -> message:new
- SimChatTeamManager.consumeActions path -> message:update
- NetworkHandler.sendTypingToTeam -> typing
- MarkAsReadPacket.handle -> read
- FocusMessagePacket.handle -> focus
- Team mutations (create/title/color/invite) -> team:update

DTOs

Team DTO
{
  "id": "abc1234",
  "title": "Team Alpha",
  "color": 12,
  "members": ["uuid1", "uuid2"],
  "entityOrder": ["npc_a", "npc_b"],
  "messageCounts": { "npc_a": 120, "npc_b": 9 },
  "data": { "key": "value", "count": 3 }
}

Message DTO
{
  "type": "ENTITY|PLAYER|SYSTEM",
  "messageId": "uuid",
  "entityId": "npc_vendor",
  "senderName": "Vendor",
  "senderNameTemplate": null,
  "senderSubtitle": "Shopkeep",
  "senderSubtitleTemplate": null,
  "senderImageId": "vendor",
  "content": "Hello",
  "contentTemplate": "{team:title}",
  "worldDay": 12,
  "playerUuid": "uuid or null",
  "actions": [ ... ],
  "transactionInput": [ ... ],
  "transactionOutput": [ ... ]
}

Action DTO mirrors ChatAction:
- label, labelTemplate
- replyText
- commands
- itemsVisual/input/output
- nextState
- condition
- playerInput

Action Execution and Offline Rules

Player selection
- If playerId provided:
  - If online: use it.
  - If offline: resolve team from stored mapping and pick any online member
    of that team as the execution player.
- If playerId missing:
  - Use any online team member.
  - If none online, return 409 (no execution player available).

Allowed actions
- Actions must have nextState and no itemsInput/itemsOutput.
- Commands must be empty.
- No kjs: usage in action condition, action label/reply templates, or in
  the target nextState dialogue templates.

Required refactors
- Add ActionProcessor that reuses ActionClickPacket logic with the new
  action restrictions.
- Add a team-based scheduling path for nextState when the execution player
  is not the playerId that initiated the request.

Threading and Consistency
- REST handlers call CompletableFuture that schedules on server thread; response
  waits with timeout.
- SSE broadcast called from server thread to enqueue events only.
- Per-client writer threads drain bounded queues; slow clients are dropped.
- Avoid direct access to TeamData/SimChatDatabase off thread.

Config
- webApi.enabled = false
- webApi.bind = "127.0.0.1"
- webApi.port = 25580
- webApi.allowOrigins = "*"
- webApi.sseHeartbeatSeconds = 15
- webApi.maxSseClients = 100 (optional)

DB Schema (Read/Focus Persistence)
- Table: simchat_player_state
  - player_id TEXT PRIMARY KEY (uuid)
  - read_counts TEXT (json map entityId -> count)
  - focus_entries TEXT (json map entityId -> {messageId, messageIndex})
  - last_focused_entity TEXT
  - updated_at INTEGER (unix millis)
- Login sync rules:
  - Load DB row if present, merge with capability state by newest updated_at.
  - On write from web or in-game, update DB and update capability if online.
  - On logout, flush capability to DB with updated_at.

Player Lookup Rules
- Accept UUID string or player name in playerId fields.
- UUID parsing first; if not UUID, lookup name via MinecraftServer.getProfileCache().
- If name not found, return 404.
- If multiple profiles with same name (rare), return 409 with error detail.

Error Model
- JSON error body:
  { "error": "code", "message": "human readable", "details": { ... } }
- Common status codes:
  - 400 invalid input
  - 404 not found
  - 409 conflict (no execution player, ambiguous name)
  - 422 action not allowed
  - 500 unexpected error

Pagination Defaults
- limit default: 50
- limit max: ServerConfig.MAX_LAZY_LOAD_BATCH_SIZE
- beforeIndex default: totalCount

CORS
- Allow all origins by default; include Access-Control-Allow-Origin in all responses.
- Handle OPTIONS preflight for POST endpoints.

SSE Ordering
- Maintain per-team monotonically increasing eventId.
- Include id: <eventId> in SSE events.
- Optional: support Last-Event-ID header to replay missed events (bounded buffer).

SSE Limits and Shutdown
- Enforce per-team and global SSE client caps (webApi.maxSseClients).
- On server stop, close all SSE connections and stop executor threads.

Logging
- Log: method, path, status, duration, and error codes.
- Log SSE connect/disconnect and queue overflow drops.

Pitfalls / Caveats
- No auth: anyone with network access can read/act on chats.
- Actions are restricted to nextState-only with no commands or items.
- KJS and command-based flows are explicitly blocked from web execution.
- Read/focus updates require DB persistence and login sync to keep parity.
- SSE must handle disconnects and slow clients; write failures should remove
  clients.
- Template compatibility: web client must resolve contentTemplate,
  senderNameTemplate, etc. same way as ClientTemplateEngine.

Complexities
- Enforcing action restrictions by inspecting action and nextState dialogue
  templates and conditions for kjs: usage.
- Maintaining read/focus parity across web and in-game clients.
- Ensuring message order consistency between REST pagination and SSE events.
- Synchronizing read/focus state across web and in-game without auth.

Implementation Sequence
1) Add config block for webApi.
2) Add WebServer + ApiHandlers + SseHub.
3) Add JSON DTO mappers for team/message/action and per-entity metadata.
4) Build REST endpoints for teams/messages/templates.
5) Refactor ActionClickPacket logic into ActionProcessor with action restrictions.
6) Add DB persistence for read/focus and login sync into capability.
7) Add SSE events and hooks in server flow with bounded queues.
8) Update README with API usage and caveats.
