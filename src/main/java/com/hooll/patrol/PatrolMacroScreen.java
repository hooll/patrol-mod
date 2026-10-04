package com.hooll.patrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** 宏列表：开关 / 编辑 / 跑一次 / 新建。!patrol macro gui 或配置界面底部进来 */
public class PatrolMacroScreen extends Screen {
    private static final int ROW_H = 24;

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private String status = "";

    private record Label(Text text, int x, int y) {
    }

    public PatrolMacroScreen(Screen parent) {
        super(Text.literal("Patrol 宏"));
        this.parent = parent;
    }

    private List<PatrolMacro> macros() {
        return PatrolManager.macroList();
    }

    @Override
    protected void init() {
        labels.clear();
        List<PatrolMacro> macros = macros();
        int blockLeft = Math.max(20, (this.width - 560) / 2);
        int top = 44;
        int maxRows = Math.max(1, (this.height - 130) / ROW_H);
        int shown = 0;

        for (PatrolMacro m : macros) {
            if (shown >= maxRows) break;
            shown++;
            int y = top + ROW_H * (shown - 1);
            String match = m.trigger.equals("screen") && m.screenMatch != null && !m.screenMatch.isEmpty()
                    ? ":" + m.screenMatch : "";
            MutableText label = Text.literal(m.name).formatted(m.enabled ? Formatting.WHITE : Formatting.DARK_GRAY)
                    .append(Text.literal(" [" + m.trigger + match + " | " + m.when + " | " + m.steps.size() + " 步]")
                            .formatted(Formatting.GRAY));
            labels.add(new Label(label, blockLeft, y + 6));

            addDrawableChild(ButtonWidget.builder(onOff(m.enabled), b -> {
                m.enabled = !m.enabled;
                b.setMessage(onOff(m.enabled));
                PatrolManager.saveMacro(m);
                status = "已保存：" + m.name + " = " + (m.enabled ? "开" : "关");
            }).dimensions(blockLeft + 256, y, 56, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("编辑"), b ->
                    MinecraftClient.getInstance().setScreen(new PatrolMacroEditScreen(this, PatrolManager.copyMacro(m)))
            ).dimensions(blockLeft + 318, y, 64, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("跑一次"), b -> {
                if (PatrolManager.runMacroByName(m.name)) {
                    MinecraftClient.getInstance().setScreen(parent);
                } else {
                    status = "没法启动（已有宏在跑，或这条没有步骤）";
                }
            }).dimensions(blockLeft + 388, y, 72, 18).build());
        }

        if (macros.size() > maxRows) {
            labels.add(new Label(Text.literal("还有 " + (macros.size() - maxRows) + " 条没显示，用 !patrol macro 命令操作")
                    .formatted(Formatting.YELLOW), blockLeft, top + ROW_H * maxRows + 2));
        }

        int by = this.height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("新建宏"), b ->
                MinecraftClient.getInstance().setScreen(new PatrolMacroEditScreen(this, PatrolManager.newTemplateMacro("新宏")))
        ).dimensions(this.width / 2 - 160, by, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("重载配置"), b -> {
            PatrolManager.load();
            MinecraftClient.getInstance().setScreen(new PatrolMacroScreen(parent));
        }).dimensions(this.width / 2 - 60, by, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(this.width / 2 + 40, by, 90, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(getTextRenderer(), getTitle(), this.width / 2, 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(getTextRenderer(),
                Text.literal("一条宏 = 一个 JSON 文件(config/patrol-macro/)；这里改的开关直接存进对应文件"),
                this.width / 2, 22, 0xFFA0A0A0);
        if (macros().isEmpty()) {
            ctx.drawCenteredTextWithShadow(getTextRenderer(),
                    Text.literal("还没有宏——点下面「新建宏」做一个，或者把 JSON 放进 config/patrol-macro/"),
                    this.width / 2, this.height / 2 - 10, 0xFFFFAA00);
        }
        for (Label l : labels) {
            ctx.drawText(getTextRenderer(), l.text(), l.x(), l.y(), 0xFFFFFFFF, false);
        }
        if (PatrolManager.macroRunning()) {
            PatrolMacro m = PatrolManager.runningMacroInfo();
            ctx.drawCenteredTextWithShadow(getTextRenderer(),
                    Text.literal("正在跑：" + (m == null ? "?" : m.name) + " · !patrol macro stop 可停"),
                    this.width / 2, this.height - 40, 0xFF55FF55);
        } else if (!status.isEmpty()) {
            ctx.drawCenteredTextWithShadow(getTextRenderer(), Text.literal(status),
                    this.width / 2, this.height - 40, 0xFFFFFF55);
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static Text onOff(boolean b) {
        return Text.literal(b ? "开" : "关");
    }
}
