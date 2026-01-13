# AGENTS.md - Sim Chat Mod

This document provides guidelines for AI coding agents working on this Minecraft Forge mod codebase.

## Project Overview

- **Type**: Minecraft Forge Mod (Java 17)
- **Minecraft Version**: 1.20.1
- **Forge Version**: 47.1.47
- **Build System**: Gradle with NeoForged ModDev LegacyForge plugin

## Build Commands

```bash
# Build the mod JAR
./gradlew build

# Run Minecraft client with the mod loaded (for testing)
./gradlew runClient

# Run Minecraft server with the mod loaded
./gradlew runServer

# Clean build artifacts
./gradlew clean

# Update version (used by CI)
./update_version.sh <version>
```

## Testing

**No unit test framework is configured.** The project relies on:
- Manual testing via `./gradlew runClient`
- CI validation of JSON/YAML files only

When adding new features, test by running the client and verifying in-game behavior.

## Project Structure

```
src/main/java/com/yardenzamir/simchat/
├── SimChatMod.java              # Main mod entry point (@Mod annotation)
├── capability/                  # Forge capability system for player data
├── client/                      # Client-side code (screens, widgets, caches)
│   ├── screen/                  # GUI screens (extends Minecraft Screen)
│   └── widget/                  # UI components and rendering helpers
├── command/                     # Brigadier command registration
├── condition/                   # Template engine and condition evaluation
├── config/                      # ForgeConfigSpec client/server configs
├── data/                        # Data models (ChatMessage, DialogueData, etc.)
├── integration/kubejs/          # KubeJS scripting integration (soft dependency)
├── network/                     # Network packets (Forge SimpleChannel)
└── team/                        # Team management system
```

## Code Style Guidelines

### Imports

Order imports as follows (no wildcards):
1. Standard Java imports (`java.*`)
2. Minecraft/Forge imports (`net.minecraft.*`, `net.minecraftforge.*`)
3. Third-party libraries (`com.google.*`, `org.jetbrains.*`)
4. Internal package imports (`com.yardenzamir.simchat.*`)

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Classes | PascalCase | `ChatMessage`, `SimChatTeamManager` |
| Methods | camelCase | `getEntityDisplayName()`, `syncToPlayer()` |
| Constants | UPPER_SNAKE_CASE | `MOD_ID`, `PROTOCOL_VERSION` |
| Private fields | camelCase (no prefix) | `sidebarWidth`, `lastTeamId` |
| NBT tag keys | Static final String constants | `TAG_ENTITY_ID = "entityId"` |

### Data Classes

- Use **Java records** for immutable data containers:
  ```java
  public record DialogueData(String entityId, String text, List<DialogueAction> actions) {}
  ```
- Use regular classes with explicit getters/setters for mutable state
- Return immutable collections via `List.copyOf()` or `Collections.unmodifiableSet()`

### Null Handling

- Always use `@Nullable` annotation from `org.jetbrains.annotations`
- Perform explicit null checks before operations
- Use `Optional.ifPresent()` for Forge capabilities:
  ```java
  ChatCapability.get(player).ifPresent(data -> { ... });
  ```

### Error Handling

- Use try-catch for number parsing with sensible defaults
- Use `Component.translatable()` for user-facing error messages
- Command handlers: return `0` for failure, `1` for success

### Serialization Patterns

- **NBT** for network packets and runtime data transfer:
  ```java
  public CompoundTag toNbt() { ... }
  public static MyClass fromNbt(CompoundTag tag) { ... }
  ```
- **JSON** for datapack-loaded content:
  ```java
  public static MyClass fromJson(JsonObject json) { ... }
  ```
- Use `GsonHelper` utilities from Minecraft for JSON parsing

### GUI/Rendering

- Extend Minecraft's `Screen` class for GUI screens
- Use `GuiGraphics` for all rendering operations
- Extract widgets into separate classes in `client/widget/`
- Define layout constants at class top:
  ```java
  public static final int PADDING = 8;
  public static final int AVATAR_SIZE = 32;
  ```

### Network Packets

Follow this pattern for packet classes:
```java
public class MyPacket {
    // Fields
    
    public static void encode(MyPacket msg, FriendlyByteBuf buf) { ... }
    public static MyPacket decode(FriendlyByteBuf buf) { ... }
    public static void handle(MyPacket msg, Supplier<NetworkEvent.Context> ctx) { ... }
}
```

### Forge Patterns

- Use `@Mod.EventBusSubscriber` for event handlers
- Register to correct bus: `Mod.EventBusSubscriber.Bus.MOD` vs `Bus.FORGE`
- Use `DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)` for client-only code
- Use `ForgeConfigSpec.Builder` for configuration

## Common Gotchas

1. **Side safety**: Client-only code must be guarded with `DistExecutor` or `@OnlyIn(Dist.CLIENT)`
2. **Network thread**: Packet handlers run on network thread; use `ctx.get().enqueueWork()` for main thread access
3. **Resource locations**: Always use `new ResourceLocation(MOD_ID, path)` or the helper `SimChatMod.id(path)`
4. **Capability access**: Check `ifPresent()` - capabilities may not be attached in all contexts

## CI/CD Pipeline

The GitHub Actions pipeline (`.github/workflows/push.yaml`):
1. Validates JSON/YAML files
2. Auto-bumps version via git tags (semantic versioning)
3. Builds with Gradle
4. Publishes to Modrinth and CurseForge
5. Creates PR in downstream modpack repo

Commit message prefixes for version bumping:
- `feat:` -> minor bump
- `fix:`, `refactor:`, `chore:`, `docs:` -> patch bump

## Dependencies

- `org.jetbrains:annotations:24.1.0` - Null annotations (compile-only)
- `dev.latvian.mods:kubejs-forge` - KubeJS integration (compile-only, soft dependency)

## Key Architectural Notes

1. **Server-authoritative**: All game state lives on server, synced to clients via NBT packets
2. **Team-based state**: Conversation history is shared per team, read counts are per-player
3. **Datapack dialogues**: Dialogue JSON files loaded via `ReloadListener` (DialogueManager)
4. **Template engine**: Custom `{resolver:key}` syntax for dynamic text in dialogues
5. **Condition system**: Prefix-based conditions (`kjs:`, `data:`, `score:`, `permission:`)
