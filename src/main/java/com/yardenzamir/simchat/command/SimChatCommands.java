package com.yardenzamir.simchat.command;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.config.ServerConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.data.DialogueData;
import com.yardenzamir.simchat.data.DialogueManager;
import com.yardenzamir.simchat.network.NetworkHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
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
            ctx.getSource().sendFailure(Component.literal("Unknown dialogue: " + dialogueId));
            return 0;
        }

        if (dialogue.entityId() == null || dialogue.entityId().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Dialogue " + dialogueId + " has no entityId"));
            return 0;
        }

        long worldDay = getWorldDay(player);
        ChatMessage message = dialogue.toMessage(dialogue.entityId(), worldDay);

        float delay = calculateDelay(dialogue.text());
        int delayTicks = (int) (delay * 20);
        DelayedMessageScheduler.schedule(player, message, delayTicks);

        ctx.getSource().sendSuccess(() -> Component.literal("Sending dialogue " + dialogueId + " to " + player.getName().getString()), false);
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

        ChatCapability.get(player).ifPresent(data -> {
            data.clearAll();
            NetworkHandler.syncToPlayer(player);
        });

        ctx.getSource().sendSuccess(() -> Component.literal("Cleared all conversations for " + player.getName().getString()), false);
        return 1;
    }

    private static int clearEntity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String entityId = StringArgumentType.getString(ctx, "entity_id");

        ChatCapability.get(player).ifPresent(data -> {
            data.clearConversation(entityId);
            NetworkHandler.syncToPlayer(player);
        });

        ctx.getSource().sendSuccess(() -> Component.literal("Cleared conversation with " + entityId), false);
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
}
