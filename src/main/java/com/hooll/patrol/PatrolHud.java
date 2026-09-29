package com.hooll.patrol;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class PatrolHud implements HudElement {
    private static final Identifier ID = Identifier.of("patrol-mod", "status");

    public static void register() {
        HudElementRegistry.addLast(ID, new PatrolHud());
    }

    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        PatrolManager.HudState st = PatrolManager.hudState();
        if (st == null) return;

        TextRenderer tr = mc.textRenderer;

        MutableText line1;
        MutableText line2;
        boolean showBar;

        if (st.idle) {
            line1 = Text.literal("巡逻待机").formatted(Formatting.WHITE)
                    .append(Text.literal(" · " + st.extra + " 个点").formatted(Formatting.GRAY));
            line2 = Text.literal("!patrol start 开始").formatted(Formatting.DARK_GRAY);
            showBar = false;
        } else if (st.hunting) {
            line1 = Text.literal("找怪").formatted(Formatting.WHITE)
                    .append(Text.literal(st.huntAuto ? " (自动)" : "").formatted(Formatting.GRAY));
            if (st.huntHasTarget) {
                line1.append(Text.literal("  →  ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal(st.huntName).formatted(Formatting.YELLOW));
                line2 = Text.literal("距离 " + st.huntDistance + "m").formatted(Formatting.GRAY)
                        .append(Text.literal("  |  ").formatted(Formatting.DARK_GRAY))
                        .append(st.huntArrived
                                ? Text.literal("已到达 · 交给 KillAura").formatted(Formatting.GREEN)
                                : Text.literal("追踪中").formatted(Formatting.AQUA));
                if (st.huntHealth >= 0) {
                    line2.append(Text.literal("  |  ").formatted(Formatting.DARK_GRAY))
                            .append(Text.literal("♥ " + st.huntHealth).formatted(Formatting.RED));
                }
            } else {
                line2 = Text.literal("附近没有敌对怪，扫描中…").formatted(Formatting.DARK_GRAY);
            }
            showBar = false;
        } else {
            line1 = Text.literal("巡逻 ").formatted(Formatting.WHITE)
                    .append(Text.literal((st.index + 1) + "/" + st.total).formatted(Formatting.AQUA))
                    .append(Text.literal(" → ").formatted(Formatting.GRAY))
                    .append(Text.literal(st.name).formatted(Formatting.WHITE));
            line2 = Text.literal("距离 " + (int) Math.ceil(st.distance) + "m").formatted(Formatting.GRAY)
                    .append(Text.literal("  |  ").formatted(Formatting.DARK_GRAY))
                    .append(statusText(st.status));
            showBar = true;
        }

        int textW = Math.max(tr.getWidth(line1), tr.getWidth(line2));
        int panelW = textW + 12;
        int barH = showBar ? 5 : 0;
        int panelH = 6 + 10 + 3 + 10 + 4 + barH + 4;

        int screenW = ctx.getScaledWindowWidth();
        int x;
        int y;
        if ("top-left".equalsIgnoreCase(PatrolManager.hudPosition())) {
            x = 4;
            y = 4;
        } else {
            x = (screenW - panelW) / 2;
            y = 6;
        }
        int right = x + panelW;
        int bottom = y + panelH;

        ctx.fill(x, y, right, bottom, 0x8C000000);
        ctx.fill(x, y, right, y + 1, 0x5035D0FF);

        int white = 0xFFFFFFFF;
        ctx.drawText(tr, line1, x + 6, y + 5, white, true);
        ctx.drawText(tr, line2, x + 6, y + 16, white, true);

        if (showBar) {
            int barX = x + 6;
            int barY = bottom - barH - 4;
            int barW = panelW - 12;
            ctx.fill(barX, barY, barX + barW, barY + barH, 0x40FFFFFF);
            double progress = ((double) st.index + st.partial) / Math.max(1, st.total);
            if (progress > 1) progress = 1;
            if (progress < 0) progress = 0;
            ctx.fill(barX, barY, barX + (int) (barW * progress), barY + barH, 0xFF35D0FF);
        }

        if (st.event != null) {
            Text ev = Text.literal(st.event).formatted(Formatting.YELLOW);
            int evW = tr.getWidth(ev) + 12;
            int evX = "top-left".equalsIgnoreCase(PatrolManager.hudPosition()) ? 4 : (screenW - evW) / 2;
            int evY = bottom + 2;
            ctx.fill(evX, evY, evX + evW, evY + 14, 0x8C000000);
            ctx.drawText(tr, ev, evX + 6, evY + 3, white, true);
        }
    }

    private static Text statusText(PatrolManager.HudStatus status) {
        return switch (status) {
            case COMBAT -> Text.literal("附近有怪 · 计时暂停").formatted(Formatting.YELLOW);
            case RETRY -> Text.literal("卡住重试中").formatted(Formatting.YELLOW);
            default -> Text.literal("行走中").formatted(Formatting.GREEN);
        };
    }
}
