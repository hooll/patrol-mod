package com.hooll.patrol.mixin;

import com.hooll.patrol.PatrolSuggestions;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Shadow
    @Final
    private TextFieldWidget textField;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private ChatInputSuggestor.SuggestionWindow window;

    @Shadow
    private boolean completingSuggestions;

    @Shadow
    protected abstract void showCommandSuggestions();

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void patrol$refresh(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String text = textField.getText();
        if (!text.startsWith("!") || mc.player == null || mc.player.networkHandler == null) return;

        int cursor = Math.min(textField.getCursor(), text.length());
        StringReader reader = new StringReader(text);
        reader.setCursor(1);

        ParseResults<CommandSource> parse = PatrolSuggestions.DISPATCHER.parse(reader, mc.player.networkHandler.getCommandSource());

        if (this.window == null || !this.completingSuggestions) {
            this.pendingSuggestions = PatrolSuggestions.DISPATCHER.getCompletionSuggestions(parse, cursor);
            CompletableFuture<Suggestions> pending = this.pendingSuggestions;
            pending.thenRun(() -> {
                if (pending.isDone()) {
                    this.showCommandSuggestions();
                }
            });
        }

        ci.cancel();
    }
}
