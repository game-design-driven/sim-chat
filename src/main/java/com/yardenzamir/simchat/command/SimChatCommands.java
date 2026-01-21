package com.yardenzamir.simchat.command;

import java.util.List;
import java.util.UUID;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.condition.CallbackContext;
import com.yardenzamir.simchat.condition.TemplateEngine;
import com.yardenzamir.simchat.config.ServerConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.data.DialogueData;
import com.yardenzamir.simchat.data.DialogueManager;
import com.yardenzamir.simchat.data.PlayerChatData;
import com.yardenzamir.simchat.integration.kubejs.KubeJSIntegration;
import com.yardenzamir.simchat.network.NetworkHandler;
import com.yardenzamir.simchat.storage.SimChatDatabase;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Registers and handles all /simchat commands.
 */
@Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SimChatCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("simchat")
                // send <player> <dialogue_id> - uses entityId from dialogue
                .then(Commands.literal("send")
                        .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("send")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dialogue_id", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(DialogueManager.getDialogueIds(), builder))
                                        .executes(SimChatCommands::sendDialogue))))
                // system <player> <entity_id> <message> - send a system message
                .then(Commands.literal("system")
                        .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("system")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("entity_id", StringArgumentType.word())
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(SimChatCommands::sendSystemMessage)))))
                // clear <player> [entity_id]
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("clear")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SimChatCommands::clearAll)
                                .then(Commands.argument("entity_id", StringArgumentType.word())
                                        .executes(SimChatCommands::clearEntity))))
                // open <player> [entity_id]
                .then(Commands.literal("open")
                        .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("open")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SimChatCommands::openChat)
                                .then(Commands.argument("entity_id", StringArgumentType.word())
                                        .executes(SimChatCommands::openChatEntity))))
                // openmessage <message_id>
                .then(Commands.literal("openmessage")
                        .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("openmessage")))
                        .then(Commands.argument("message_id", StringArgumentType.word())
                                .executes(SimChatCommands::openChatMessage)))
                // team subcommands
                .then(Commands.literal("team")
                        // team create <title>
                        .then(Commands.literal("create")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("team.create")))
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(SimChatCommands::teamCreate)))
                        // team invite [player] - if no player, broadcast to all
                        .then(Commands.literal("invite")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("team.invite")))
                                .executes(SimChatCommands::teamInviteBroadcast)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(SimChatCommands::teamInvite)))
                        // team join <id_or_name>
                        .then(Commands.literal("join")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("team.join")))
                                .then(Commands.argument("team", StringArgumentType.greedyString())
                                        .suggests(SimChatCommands::suggestTeams)
                                        .executes(SimChatCommands::teamJoin)))
                        // team list
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("team.list")))
                                .executes(SimChatCommands::teamList))
                        // team info
                        .then(Commands.literal("info")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("team.info")))
                                .executes(SimChatCommands::teamInfo))
                        // team title <title> (own team) or team title <player> <title> (admin)
                        .then(Commands.literal("title")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("team.title")))
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(SimChatCommands::teamTitleSelf))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                                .executes(SimChatCommands::teamTitleAdmin))))
                        // team color <color>
                        .then(Commands.literal("color")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("team.color")))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests(SimChatCommands::suggestColors)
                                        .executes(SimChatCommands::teamColor))))
                // reload - re-fire KubeJS callback registration
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("callback.reload")))
                        .executes(SimChatCommands::reloadCallbacks))
                // callback subcommands for KubeJS callbacks
                .then(Commands.literal("callback")
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("callback.list")))
                                .executes(SimChatCommands::callbackList))
                        // callback run <name> [player] - run a callback and show result
                        .then(Commands.literal("run")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("callback.run")))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                com.yardenzamir.simchat.condition.CallbackRegistry.getCallbackNames(), builder))
                                        .executes(SimChatCommands::callbackRunSelf)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(SimChatCommands::callbackRunPlayer)))))
                // data subcommands for team data - all accept optional [target] (player or team)
                .then(Commands.literal("data")
                        .then(Commands.literal("get")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("data.get")))
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(SimChatCommands::dataGetSelf)
                                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                                .suggests(SimChatCommands::suggestTargets)
                                                .executes(SimChatCommands::dataGetTarget))))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("data.set")))
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.string())
                                                .executes(SimChatCommands::dataSetSelf)
                                                .then(Commands.argument("target", StringArgumentType.greedyString())
                                                        .suggests(SimChatCommands::suggestTargets)
                                                        .executes(SimChatCommands::dataSetTarget)))))
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("data.add")))
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                .executes(SimChatCommands::dataAddSelf)
                                                .then(Commands.argument("target", StringArgumentType.greedyString())
                                                        .suggests(SimChatCommands::suggestTargets)
                                                        .executes(SimChatCommands::dataAddTarget)))))
                        .then(Commands.literal("remove")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("data.remove")))
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(SimChatCommands::dataRemoveSelf)
                                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                                .suggests(SimChatCommands::suggestTargets)
                                                .executes(SimChatCommands::dataRemoveTarget))))
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(ServerConfig.getCommandPermission("data.list")))
                                .executes(SimChatCommands::dataListSelf)
                                .then(Commands.argument("target", StringArgumentType.greedyString())
                                        .suggests(SimChatCommands::suggestTargets)
                                        .executes(SimChatCommands::dataListTarget))))
        );
    }

    private static long getWorldDay(ServerPlayer player) {
        return player.level().getDayTime() / 24000L;
    }

    private static float calculateDelay(String text) {
        float charsPerSecond = ServerConfig.TYPING_CHARS_PER_SECOND.get().floatValue();
        float minDelay = ServerConfig.TYPING_DELAY_MIN.get().floatValue();
        float maxDelay = ServerConfig.TYPING_DELAY_MAX.get().floatValue();

        float seconds = text.length() / charsPerSecond;
        return Math.max(minDelay, Math.min(maxDelay, seconds));
    }

    private static int sendDialogue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation dialogueId = ResourceLocationArgument.getId(ctx, "dialogue_id");

        DialogueData dialogue = DialogueManager.get(dialogueId);
        if (dialogue == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.unknown_dialogue", dialogueId.toString()));
            return 0;
        }

        if (dialogue.entityId() == null || dialogue.entityId().isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.dialogue_no_entity", dialogueId.toString()));
            return 0;
        }

        long worldDay = getWorldDay(player);
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        CallbackContext callbackCtx = new CallbackContext(player, team, dialogue.entityId());
        ChatMessage message = dialogue.toMessage(dialogue.entityId(), worldDay, callbackCtx, dialogueId);

        float delay = calculateDelay(message.content());
        int delayTicks = (int) (delay * 20);
        DelayedMessageScheduler.schedule(player, message, delayTicks);

        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.send.success", dialogueId.toString(), player.getName().getString()), false);
        return 1;
    }

    private static int sendSystemMessage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String entityId = StringArgumentType.getString(ctx, "entity_id");
        String messageText = StringArgumentType.getString(ctx, "message");

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.no_team"));
            return 0;
        }

        long worldDay = getWorldDay(player);
        CallbackContext callbackCtx = new CallbackContext(player, team, entityId);
        TemplateEngine.TemplateCompilation compilation = TemplateEngine.compile(messageText, callbackCtx);
        ChatMessage message = ChatMessage.systemMessage(entityId, compilation.compiledText(), compilation.runtimeTemplate(), worldDay);
        int messageIndex = manager.appendMessage(team, message);
        if (messageIndex < 0) {
            return 0;
        }
        manager.saveTeam(team);
        int totalCount = manager.getMessageCount(team, entityId);
        NetworkHandler.sendMessageToTeam(team, message, messageIndex, totalCount, player.server, true);

        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.no_team"));
            return 0;
        }

        manager.clearAllConversations(team);
        manager.saveTeam(team);
        NetworkHandler.syncTeamToAllMembers(team, player.server);

        // Also clear read counts for all team members
        for (ServerPlayer member : manager.getOnlineTeamMembers(team)) {
            PlayerChatData readData = ChatCapability.getOrThrow(member);
            readData.clearAll();
            NetworkHandler.syncToPlayer(member);
        }

        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.clear.all", team.getTitle()), false);
        return 1;
    }

    private static int clearEntity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String entityId = StringArgumentType.getString(ctx, "entity_id");

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.no_team"));
            return 0;
        }

        manager.clearConversation(team, entityId);
        manager.saveTeam(team);
        NetworkHandler.syncTeamToAllMembers(team, player.server);

        // Also clear read counts for this entity for all team members
        for (ServerPlayer member : manager.getOnlineTeamMembers(team)) {
            PlayerChatData readData = ChatCapability.getOrThrow(member);
            readData.clearReadCount(entityId);
            NetworkHandler.syncToPlayer(member);
        }

        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.clear.entity", entityId), false);
        return 1;
    }

    private static int openChat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        NetworkHandler.openChatScreen(player, "", null, -1);
        return 1;
    }

    private static int openChatEntity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String entityId = StringArgumentType.getString(ctx, "entity_id");
        NetworkHandler.openChatScreen(player, entityId, null, -1);
        return 1;
    }

    private static int openChatMessage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String messageIdText = StringArgumentType.getString(ctx, "message_id");
        UUID messageId;
        try {
            messageId = UUID.fromString(messageIdText);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid message id: " + messageIdText));
            return 0;
        }

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.no_team"));
            return 0;
        }

        SimChatDatabase.StoredMessage stored = manager.getMessageById(team, messageId);
        if (stored == null) {
            ctx.getSource().sendFailure(Component.literal("Message not found in team."));
            return 0;
        }

        int totalCount = manager.getMessageCount(team, stored.entityId());
        int windowSize = Math.max(1, ServerConfig.INITIAL_SYNC_MESSAGE_COUNT.get());
        int before = windowSize / 2;
        int startIndex = Math.max(0, stored.messageIndex() - before);
        int count = Math.min(windowSize, totalCount - startIndex);

        if (count > 0) {
            List<ChatMessage> messages = manager.loadMessages(team, stored.entityId(), startIndex, count);
            NetworkHandler.sendMessages(player, stored.entityId(), messages, totalCount, startIndex);
        }

        PlayerChatData readData = ChatCapability.getOrThrow(player);
        readData.setFocusedMessage(stored.entityId(), stored.message().messageId(), stored.messageIndex());
        NetworkHandler.syncToPlayer(player);

        NetworkHandler.openChatScreen(player, stored.entityId(), stored.message().messageId(), stored.messageIndex());
        return 1;
    }

    // === Team Commands ===

    private static int teamCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String title = StringArgumentType.getString(ctx, "title");

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.createTeam(player, title);

        NetworkHandler.syncTeamWithLazyLoad(player, team);

        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.created", title, team.getId()), false);
        return 1;
    }

    private static int teamInvite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer inviter = ctx.getSource().getPlayerOrException();
        ServerPlayer invitee = EntityArgument.getPlayer(ctx, "player");

        SimChatTeamManager manager = SimChatTeamManager.get(inviter.server);
        TeamData team = manager.getPlayerTeam(inviter);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.not_in_team"));
            return 0;
        }

        Component message = buildInviteMessage(inviter, team);
        invitee.sendSystemMessage(message);
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.invited", invitee.getName().getString()), false);
        return 1;
    }

    private static int teamInviteBroadcast(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer inviter = ctx.getSource().getPlayerOrException();

        SimChatTeamManager manager = SimChatTeamManager.get(inviter.server);
        TeamData team = manager.getPlayerTeam(inviter);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.not_in_team"));
            return 0;
        }

        Component message = buildInviteMessage(inviter, team);

        // Send to all players except the inviter
        int count = 0;
        for (ServerPlayer player : inviter.server.getPlayerList().getPlayers()) {
            if (!player.equals(inviter)) {
                player.sendSystemMessage(message);
                count++;
            }
        }

        int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.invited_all", finalCount), false);
        return 1;
    }

    private static Component buildInviteMessage(ServerPlayer inviter, TeamData team) {
        Component joinButton = Component.translatable("simchat.command.team.invite_button")
                .withStyle(Style.EMPTY
                        .withColor(0x55FF55)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/simchat team join " + team.getId()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("simchat.command.team.invite_hover", team.getTitle()))));

        return Component.translatable("simchat.command.team.invite_message", inviter.getName().getString(), team.getTitle())
                .append(joinButton);
    }

    private static CompletableFuture<Suggestions> suggestTeams(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        SimChatTeamManager manager = SimChatTeamManager.get(ctx.getSource().getServer());
        for (TeamData team : manager.getAllTeams()) {
            // Suggest both ID and title
            builder.suggest(team.getId());
            // Quote titles with spaces
            String title = team.getTitle();
            if (title.contains(" ")) {
                builder.suggest("\"" + title + "\"");
            } else {
                builder.suggest(title);
            }
        }
        return builder.buildFuture();
    }

    private static int teamJoin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String input = StringArgumentType.getString(ctx, "team");

        // Remove quotes if present
        if (input.startsWith("\"") && input.endsWith("\"")) {
            input = input.substring(1, input.length() - 1);
        }

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.findTeam(input);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.team_not_found", input));
            return 0;
        }

        manager.changeTeam(player, team.getId());
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.joined", team.getTitle()), false);
        return 1;
    }

    private static int teamList(CommandContext<CommandSourceStack> ctx) {
        SimChatTeamManager manager = SimChatTeamManager.get(ctx.getSource().getServer());
        var teams = manager.getAllTeams();

        ctx.getSource().sendSuccess(() -> Component.literal("Teams (" + teams.size() + ")")
                .withStyle(Style.EMPTY.withColor(0x55FFFF).withBold(true)), false);

        for (TeamData team : teams) {
            Component titleComp = Component.literal(team.getTitle()).withStyle(Style.EMPTY.withColor(0xFFFFFF));
            Component idComp = Component.literal(team.getId()).withStyle(Style.EMPTY.withColor(0xAAAAAA));
            Component membersComp = Component.literal(String.valueOf(team.getMemberCount()))
                    .withStyle(Style.EMPTY.withColor(0x55FF55));
            ctx.getSource().sendSuccess(() -> Component.literal("  ")
                    .append(titleComp)
                    .append(Component.literal(" (").withStyle(Style.EMPTY.withColor(0x555555)))
                    .append(idComp)
                    .append(Component.literal(") - ").withStyle(Style.EMPTY.withColor(0x555555)))
                    .append(membersComp)
                    .append(Component.literal(" members").withStyle(Style.EMPTY.withColor(0x555555))), false);
        }
        return 1;
    }

    private static int teamInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.not_in_team"));
            return 0;
        }

        Component titleComp = Component.literal(team.getTitle()).withStyle(Style.EMPTY.withColor(0x55FFFF).withBold(true));
        ctx.getSource().sendSuccess(() -> titleComp, false);

        Component idLabel = Component.literal("  ID: ").withStyle(Style.EMPTY.withColor(0xAAAAAA));
        Component idVal = Component.literal(team.getId()).withStyle(Style.EMPTY.withColor(0xFFFFFF));
        ctx.getSource().sendSuccess(() -> idLabel.copy().append(idVal), false);

        Component membersLabel = Component.literal("  Members: ").withStyle(Style.EMPTY.withColor(0xAAAAAA));
        Component membersVal = Component.literal(String.valueOf(team.getMemberCount())).withStyle(Style.EMPTY.withColor(0x55FF55));
        ctx.getSource().sendSuccess(() -> membersLabel.copy().append(membersVal), false);

        Component colorLabel = Component.literal("  Color: ").withStyle(Style.EMPTY.withColor(0xAAAAAA));
        int colorIndex = Math.max(0, Math.min(15, team.getColor()));
        Component colorVal = Component.literal(TeamData.getColorName(colorIndex))
                .withStyle(Style.EMPTY.withColor(TeamData.getColorValue(colorIndex)));
        ctx.getSource().sendSuccess(() -> colorLabel.copy().append(colorVal), false);

        Component convsLabel = Component.literal("  Conversations: ").withStyle(Style.EMPTY.withColor(0xAAAAAA));
        Component convsVal = Component.literal(String.valueOf(team.getEntityIds().size())).withStyle(Style.EMPTY.withColor(0x55FF55));
        ctx.getSource().sendSuccess(() -> convsLabel.copy().append(convsVal), false);

        int dataCount = team.getAllData().size();
        Component dataLabel = Component.literal("  Data keys: ").withStyle(Style.EMPTY.withColor(0xAAAAAA));
        Component dataVal = Component.literal(String.valueOf(dataCount)).withStyle(Style.EMPTY.withColor(0x55FF55));
        ctx.getSource().sendSuccess(() -> dataLabel.copy().append(dataVal), false);

        return 1;
    }

    private static int teamTitleSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String newTitle = StringArgumentType.getString(ctx, "title");

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.not_in_team"));
            return 0;
        }

        team.setTitle(newTitle);
        manager.saveTeam(team);
        manager.updateVanillaTeamColor(team);
        NetworkHandler.syncTeamToAllMembers(team, player.server);

        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.title_changed", newTitle), false);
        return 1;
    }

    private static int teamTitleAdmin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(ctx, "player");
        String newTitle = StringArgumentType.getString(ctx, "title");

        SimChatTeamManager manager = SimChatTeamManager.get(targetPlayer.server);
        TeamData team = manager.getPlayerTeam(targetPlayer);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.no_team"));
            return 0;
        }

        team.setTitle(newTitle);
        manager.saveTeam(team);
        manager.updateVanillaTeamColor(team);
        NetworkHandler.syncTeamToAllMembers(team, targetPlayer.server);

        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.title_changed_admin",
                targetPlayer.getName().getString(), newTitle), false);
        return 1;
    }



    private static CompletableFuture<Suggestions> suggestColors(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String color : TeamData.COLOR_NAMES) {
            builder.suggest(color);
        }
        return builder.buildFuture();
    }

    private static int parseColor(String input) {
        String normalized = input.toLowerCase().replace('-', '_');
        for (int i = 0; i < TeamData.COLOR_NAMES.length; i++) {
            if (TeamData.COLOR_NAMES[i].equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private static int teamColor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorInput = StringArgumentType.getString(ctx, "color");

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.not_in_team"));
            return 0;
        }

        int colorIndex = parseColor(colorInput);
        if (colorIndex < 0) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.invalid_color", colorInput));
            return 0;
        }

        team.setColor(colorIndex);
        manager.saveTeam(team);
        manager.updateVanillaTeamColor(team);
        NetworkHandler.syncTeamToAllMembers(team, player.server);

        String colorName = TeamData.getColorName(colorIndex);
        Component coloredName = Component.literal(colorName)
                .withStyle(Style.EMPTY.withColor(TeamData.getColorValue(colorIndex)));
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.color_changed", coloredName), false);
        return 1;
    }

    private static int reloadCallbacks(CommandContext<CommandSourceStack> ctx) {
        KubeJSIntegration.fireRegisterCallbacksEvent();
        int count = com.yardenzamir.simchat.condition.CallbackRegistry.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded ")
                .withStyle(Style.EMPTY.withColor(0x55FF55))
                .append(Component.literal(String.valueOf(count)).withStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(true)))
                .append(Component.literal(" callbacks").withStyle(Style.EMPTY.withColor(0x55FF55))), false);
        return 1;
    }

    private static int callbackList(CommandContext<CommandSourceStack> ctx) {
        var names = com.yardenzamir.simchat.condition.CallbackRegistry.getCallbackNames();

        ctx.getSource().sendSuccess(() -> Component.literal("Callbacks (" + names.size() + ")")
                .withStyle(Style.EMPTY.withColor(0x55FFFF).withBold(true)), false);

        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  (none registered)")
                    .withStyle(Style.EMPTY.withColor(0x555555)), false);
        } else {
            for (String name : names) {
                ctx.getSource().sendSuccess(() -> Component.literal("  ")
                        .append(Component.literal(name).withStyle(Style.EMPTY.withColor(0xFFAA00))), false);
            }
        }
        return 1;
    }

    private static int callbackRunSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return runCallback(ctx, player);
    }

    private static int callbackRunPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        return runCallback(ctx, player);
    }

    private static int runCallback(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        String callbackName = StringArgumentType.getString(ctx, "name");

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);

        CallbackContext callbackCtx = new CallbackContext(player, team, null);
        Object result = com.yardenzamir.simchat.condition.CallbackRegistry.evaluate(callbackName, callbackCtx);

        Component nameComp = Component.literal(callbackName).withStyle(Style.EMPTY.withColor(0xFFAA00));
        Component playerComp = Component.literal(player.getName().getString()).withStyle(Style.EMPTY.withColor(0x55FFFF));

        if (result == null) {
            ctx.getSource().sendSuccess(() -> nameComp.copy()
                    .append(Component.literal("(").withStyle(Style.EMPTY.withColor(0x555555)))
                    .append(playerComp)
                    .append(Component.literal(") = ").withStyle(Style.EMPTY.withColor(0x555555)))
                    .append(Component.literal("null").withStyle(Style.EMPTY.withColor(0xAAAAAA))), false);
            return 1;
        }

        String resultStr = result.toString();
        int color = result instanceof Boolean b ? (b ? 0x55FF55 : 0xFF5555) : 0x55FF55;
        ctx.getSource().sendSuccess(() -> nameComp.copy()
                .append(Component.literal("(").withStyle(Style.EMPTY.withColor(0x555555)))
                .append(playerComp)
                .append(Component.literal(") = ").withStyle(Style.EMPTY.withColor(0x555555)))
                .append(Component.literal(resultStr).withStyle(Style.EMPTY.withColor(color))), false);
        return 1;
    }

    // === Data Commands ===

    private static CompletableFuture<Suggestions> suggestTargets(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        var server = ctx.getSource().getServer();
        // Suggest online players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            builder.suggest(player.getName().getString());
        }
        // Suggest teams
        SimChatTeamManager manager = SimChatTeamManager.get(server);
        for (TeamData team : manager.getAllTeams()) {
            builder.suggest(team.getId());
            String title = team.getTitle();
            if (title.contains(" ")) {
                builder.suggest("\"" + title + "\"");
            } else {
                builder.suggest(title);
            }
        }
        return builder.buildFuture();
    }

    /**
     * Resolves a target string to a TeamData.
     * Tries: online player by name -> team by ID -> team by title
     */
    private static TeamData resolveTarget(CommandSourceStack source, String target) {
        var server = source.getServer();
        SimChatTeamManager manager = SimChatTeamManager.get(server);

        // Remove quotes if present
        if (target.startsWith("\"") && target.endsWith("\"")) {
            target = target.substring(1, target.length() - 1);
        }

        // Try as online player name
        ServerPlayer player = server.getPlayerList().getPlayerByName(target);
        if (player != null) {
            return manager.getPlayerTeam(player);
        }

        // Try as team ID or title
        return manager.findTeam(target);
    }

    private static int dataGetSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        return dataGetImpl(ctx, team, null);
    }

    private static int dataGetTarget(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "target");
        TeamData team = resolveTarget(ctx.getSource(), target);
        return dataGetImpl(ctx, team, target);
    }

    private static int dataGetImpl(CommandContext<CommandSourceStack> ctx, TeamData team, String targetName) {
        String key = StringArgumentType.getString(ctx, "key");

        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.target_no_team", targetName != null ? targetName : "You"));
            return 0;
        }

        Object val = team.getData(key);
        String valStr = val != null ? (val instanceof Double d && d == Math.floor(d) ? String.valueOf(d.longValue()) : val.toString()) : "null";
        ctx.getSource().sendSuccess(() -> Component.literal(valStr), false);
        return 1;
    }

    private static int dataSetSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        return dataSetImpl(ctx, team, null);
    }

    private static int dataSetTarget(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "target");
        TeamData team = resolveTarget(ctx.getSource(), target);
        return dataSetImpl(ctx, team, target);
    }

    private static int dataSetImpl(CommandContext<CommandSourceStack> ctx, TeamData team, String targetName) {
        String key = StringArgumentType.getString(ctx, "key");
        String valueStr = StringArgumentType.getString(ctx, "value");

        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.target_no_team", targetName != null ? targetName : "You"));
            return 0;
        }

        // Auto-detect: try parsing as number first
        String displayVal;
        try {
            double numVal = Double.parseDouble(valueStr);
            team.setData(key, numVal);
            displayVal = numVal == Math.floor(numVal) ? String.valueOf((long) numVal) : String.valueOf(numVal);
        } catch (NumberFormatException e) {
            team.setData(key, valueStr);
            displayVal = valueStr;
        }

        SimChatTeamManager.get(ctx.getSource().getServer()).saveTeam(team);

        String finalDisplayVal = displayVal;
        ctx.getSource().sendSuccess(() -> Component.literal(finalDisplayVal), false);
        return 1;
    }

    private static int dataAddSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        return dataAddImpl(ctx, team, null);
    }

    private static int dataAddTarget(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "target");
        TeamData team = resolveTarget(ctx.getSource(), target);
        return dataAddImpl(ctx, team, target);
    }

    private static int dataAddImpl(CommandContext<CommandSourceStack> ctx, TeamData team, String targetName) {
        String key = StringArgumentType.getString(ctx, "key");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.target_no_team", targetName != null ? targetName : "You"));
            return 0;
        }

        team.addData(key, amount);
        SimChatTeamManager.get(ctx.getSource().getServer()).saveTeam(team);
        double newVal = team.getDataNumber(key, 0);

        String newStr = newVal == Math.floor(newVal) ? String.valueOf((long) newVal) : String.valueOf(newVal);
        ctx.getSource().sendSuccess(() -> Component.literal(newStr), false);
        return 1;
    }

    private static int dataRemoveSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        return dataRemoveImpl(ctx, team, null);
    }

    private static int dataRemoveTarget(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "target");
        TeamData team = resolveTarget(ctx.getSource(), target);
        return dataRemoveImpl(ctx, team, target);
    }

    private static int dataRemoveImpl(CommandContext<CommandSourceStack> ctx, TeamData team, String targetName) {
        String key = StringArgumentType.getString(ctx, "key");

        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.target_no_team", targetName != null ? targetName : "You"));
            return 0;
        }

        team.removeData(key);
        SimChatTeamManager.get(ctx.getSource().getServer()).saveTeam(team);
        return 1;
    }

    private static int dataListSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        return dataListImpl(ctx, team, null);
    }

    private static int dataListTarget(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "target");
        TeamData team = resolveTarget(ctx.getSource(), target);
        return dataListImpl(ctx, team, target);
    }

    private static int dataListImpl(CommandContext<CommandSourceStack> ctx, TeamData team, String targetName) {
        if (team == null) {
            ctx.getSource().sendFailure(Component.translatable("simchat.command.error.target_no_team", targetName != null ? targetName : "You"));
            return 0;
        }

        var data = team.getAllData();
        if (data.isEmpty()) {
            return 1;
        }

        for (var entry : data.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            String vStr = v instanceof Double d && d == Math.floor(d) ? String.valueOf(d.longValue()) : v.toString();
            ctx.getSource().sendSuccess(() -> Component.literal(k + "=" + vStr), false);
        }
        return 1;
    }
}
