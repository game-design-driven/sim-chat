package com.yardenzamir.simchat.command;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.config.ServerConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.data.DialogueData;
import com.yardenzamir.simchat.data.DialogueManager;
import com.yardenzamir.simchat.data.PlayerChatData;
import com.yardenzamir.simchat.network.NetworkHandler;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import java.util.concurrent.CompletableFuture;
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

/**
 * Registers and handles all /simchat commands.
 */
@Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SimChatCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("simchat")
                .requires(source -> source.hasPermission(ServerConfig.COMMAND_PERMISSION_LEVEL.get()))
                // send <player> <dialogue_id> - uses entityId from dialogue
                .then(Commands.literal("send")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dialogue_id", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(DialogueManager.getDialogueIds(), builder))
                                        .executes(SimChatCommands::sendDialogue))))
                // system <player> <entity_id> <message> - send a system message
                .then(Commands.literal("system")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("entity_id", StringArgumentType.word())
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(SimChatCommands::sendSystemMessage)))))
                // clear <player> [entity_id]
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SimChatCommands::clearAll)
                                .then(Commands.argument("entity_id", StringArgumentType.word())
                                        .executes(SimChatCommands::clearEntity))))
                // open <player> [entity_id]
                .then(Commands.literal("open")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SimChatCommands::openChat)
                                .then(Commands.argument("entity_id", StringArgumentType.word())
                                        .executes(SimChatCommands::openChatEntity))))
                // team subcommands
                .then(Commands.literal("team")
                        // team create <title>
                        .then(Commands.literal("create")
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(SimChatCommands::teamCreate)))
                        // team invite <player>
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(SimChatCommands::teamInvite)))
                        // team join <id_or_name>
                        .then(Commands.literal("join")
                                .then(Commands.argument("team", StringArgumentType.greedyString())
                                        .suggests(SimChatCommands::suggestTeams)
                                        .executes(SimChatCommands::teamJoin)))
                        // team list
                        .then(Commands.literal("list")
                                .executes(SimChatCommands::teamList))
                        // team info
                        .then(Commands.literal("info")
                                .executes(SimChatCommands::teamInfo))
                        // team title <title> (own team) or team title <player> <title> (admin)
                        .then(Commands.literal("title")
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(SimChatCommands::teamTitleSelf))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                                .executes(SimChatCommands::teamTitleAdmin)))))
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
        ChatMessage message = dialogue.toMessage(dialogue.entityId(), worldDay);

        float delay = calculateDelay(dialogue.text());
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
        ChatMessage message = ChatMessage.systemMessage(entityId, messageText, worldDay);
        team.addMessage(message);
        manager.saveTeam(team);
        NetworkHandler.syncTeamToAllMembers(team, player.server);

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

        team.clearAll();
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

        team.clearConversation(entityId);
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
        NetworkHandler.openChatScreen(player, "");
        return 1;
    }

    private static int openChatEntity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String entityId = StringArgumentType.getString(ctx, "entity_id");
        NetworkHandler.openChatScreen(player, entityId);
        return 1;
    }

    // === Team Commands ===

    private static int teamCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String title = StringArgumentType.getString(ctx, "title");

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.createTeam(player, title);

        NetworkHandler.syncTeamToPlayer(player, team);

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

        // Build clickable message for invitee
        Component joinButton = Component.translatable("simchat.command.team.invite_button")
                .withStyle(Style.EMPTY
                        .withColor(0x55FF55)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/simchat team join " + team.getId()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("simchat.command.team.invite_hover", team.getTitle()))));

        Component message = Component.translatable("simchat.command.team.invite_message", inviter.getName().getString(), team.getTitle())
                .append(joinButton);

        invitee.sendSystemMessage(message);
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.invited", invitee.getName().getString()), false);
        return 1;
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

        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.list_header"), false);
        for (TeamData team : manager.getAllTeams()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.list_entry",
                    team.getTitle(), team.getId(), team.getMemberCount()), false);
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

        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.info_header"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.info_title", team.getTitle()), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.info_id", team.getId()), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.info_members", team.getMemberCount()), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("simchat.command.team.info_conversations", team.getEntityIds().size()), false);
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
}
