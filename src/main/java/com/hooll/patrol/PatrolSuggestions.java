package com.hooll.patrol;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;

/** 只用于聊天栏 Tab 补全，不参与实际执行（执行走 PatrolManager.handleChat） */
public final class PatrolSuggestions {
    public static final CommandDispatcher<CommandSource> DISPATCHER = new CommandDispatcher<>();

    private static final SuggestionProvider<CommandSource> POINTS = (ctx, builder) -> {
        for (String name : PatrolManager.pointNames()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSource> MACROS = (ctx, builder) -> {
        for (String name : PatrolManager.macroNames()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private static RequiredArgumentBuilder<CommandSource, String> macroNameArg() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                .suggests(MACROS);
    }

    static {
        DISPATCHER.register(LiteralArgumentBuilder.<CommandSource>literal("patrol")
                .then(LiteralArgumentBuilder.<CommandSource>literal("add")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())))
                .then(LiteralArgumentBuilder.<CommandSource>literal("del")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                                .suggests(POINTS)))
                .then(LiteralArgumentBuilder.<CommandSource>literal("list"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("clear"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("start")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                                .suggests(POINTS)))
                .then(LiteralArgumentBuilder.<CommandSource>literal("stop"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("gui"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("back"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("macro")
                        .then(LiteralArgumentBuilder.<CommandSource>literal("gui"))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("stop"))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("on").then(macroNameArg()))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("off").then(macroNameArg()))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("toggle").then(macroNameArg()))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("run").then(macroNameArg())))
                .then(LiteralArgumentBuilder.<CommandSource>literal("hunt")
                        .then(LiteralArgumentBuilder.<CommandSource>literal("stop"))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("auto"))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("type")
                                .then(LiteralArgumentBuilder.<CommandSource>literal("clear")))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("ignore")
                                .then(LiteralArgumentBuilder.<CommandSource>literal("clear")))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("targets")
                                .then(LiteralArgumentBuilder.<CommandSource>literal("hostile"))
                                .then(LiteralArgumentBuilder.<CommandSource>literal("mob"))
                                .then(LiteralArgumentBuilder.<CommandSource>literal("all"))))
                .then(LiteralArgumentBuilder.<CommandSource>literal("scan"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("status"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("reload"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("help"))
        );
    }

    private PatrolSuggestions() {
    }
}
