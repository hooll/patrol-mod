package com.hooll.patrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/** !patrol gui 打开的配置界面：改完点"保存并关闭"写回 config/patrol-points.json */
public class PatrolConfigScreen extends Screen {
    private static final int COLS = 3;
    private static final int ROW_H = 22;
    private static final int CTRL_W = 76;
    private static final int CTRL_H = 18;

    private final Screen parent;
    private final List<BooleanSupplier> savers = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private int[] colRow = new int[COLS];
    private int top;
    private int left;
    private int colW;
    private int invalid;

    private record Label(Text text, int x, int y) {
    }

    public PatrolConfigScreen(Screen parent) {
        super(Text.literal("Patrol 配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        savers.clear();
        labels.clear();
        colRow = new int[COLS];
        invalid = 0;
        colW = Math.max(150, Math.min(210, (this.width - 20) / COLS));
        left = (this.width - colW * COLS) / 2;
        top = 36;

        PatrolConfig c = PatrolManager.config;

        // ---- 第1列: 巡逻 ----
        numRow(0, "到达半径(格)", c.arriveRadius, 0.5, v -> c.arriveRadius = v);
        numRow(0, "卡住秒数", c.stuckSeconds, 1, v -> c.stuckSeconds = (int) Math.round(v));
        numRow(0, "单点上限秒", c.pointTimeoutSeconds, 5, v -> c.pointTimeoutSeconds = (int) Math.round(v));
        boolRow(0, "遇怪暂停计时", c.pauseNearMobs, v -> c.pauseNearMobs = v);
        numRow(0, "怪物半径(格)", c.mobRadius, 1, v -> c.mobRadius = v);
        numRow(0, "战斗暂停上限秒", c.combatFreezeSeconds, 5, v -> c.combatFreezeSeconds = (int) Math.round(v));
        numRow(0, "卡住重试次数", c.maxRetries, 0, v -> c.maxRetries = (int) Math.round(v));
        boolRow(0, "禁挖/禁放(副本)", c.baritoneNoBreak, v -> c.baritoneNoBreak = v);
        boolRow(0, "困住自动退回", c.retreatWhenTrapped, v -> c.retreatWhenTrapped = v);

        // ---- 第2列: 找怪 ----
        numRow(1, "找怪半径(格)", c.huntRange, 4, v -> c.huntRange = v);
        numRow(1, "到达判定(格)", c.huntArriveRadius, 1, v -> c.huntArriveRadius = v);
        numRow(1, "没动秒数", c.huntStuckSeconds, 2, v -> c.huntStuckSeconds = (int) Math.round(v));
        numRow(1, "重试次数", c.huntMaxAttempts, 0, v -> c.huntMaxAttempts = (int) Math.round(v));
        numRow(1, "连败停止数", c.huntMaxGiveUps, 1, v -> c.huntMaxGiveUps = (int) Math.round(v));
        numRow(1, "目标存活门槛秒", c.huntMinAliveSeconds, 0, v -> c.huntMinAliveSeconds = v);
        numRow(1, "重发距离(格)", c.huntRetargetDistance, 1, v -> c.huntRetargetDistance = v);
        numRow(1, "重发间隔(秒)", c.huntMinRetargetIntervalSeconds, 0.5, v -> c.huntMinRetargetIntervalSeconds = v);
        boolRow(1, "目标死时停寻路", c.cancelOnTargetDeath, v -> c.cancelOnTargetDeath = v);

        // ---- 第3列: 过滤 / 界面 ----
        cycleRow(2, "目标过滤", new String[]{"hostile", "mob", "all"}, c.huntTargets, v -> c.huntTargets = v);
        listRow(2, "只打(白名单)", c.huntTypes, v -> {
            c.huntTypes.clear();
            c.huntTypes.addAll(v);
        });
        listRow(2, "黑名单", c.huntIgnoreTypes, v -> {
            c.huntIgnoreTypes.clear();
            c.huntIgnoreTypes.addAll(v);
        });
        boolRow(2, "状态面板(HUD)", c.hud, v -> c.hud = v);
        cycleRow(2, "面板位置", new String[]{"top-center", "top-left"}, c.hudPosition, v -> c.hudPosition = v);
        boolRow(2, "待机也显示HUD", c.hudWhenIdle, v -> c.hudWhenIdle = v);
        boolRow(2, "事件发聊天框", c.chatEvents, v -> c.chatEvents = v);
        boolRow(2, "重连续跑", c.autoResume, v -> c.autoResume = v);

        // ---- 底部按钮 ----
        int by = this.height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("保存并关闭"), b -> saveAll())
                .dimensions(this.width / 2 - 105, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> close())
                .dimensions(this.width / 2 + 5, by, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(getTextRenderer(), getTitle(), this.width / 2, 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(getTextRenderer(),
                Text.literal("数字留空=不改；填错会保留原值。列表用逗号分隔，如 ocelot, villager"),
                this.width / 2, 22, 0xFFA0A0A0);
        for (Label l : labels) {
            ctx.drawText(getTextRenderer(), l.text(), l.x(), l.y(), 0xFFE0E0E0, false);
        }
        if (invalid > 0) {
            ctx.drawCenteredTextWithShadow(getTextRenderer(),
                    Text.literal("有 " + invalid + " 个数字填得不对（已保留原值），改好再保存"),
                    this.width / 2, this.height - 40, 0xFFFF5555);
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void saveAll() {
        invalid = 0;
        for (BooleanSupplier s : savers) {
            if (!s.getAsBoolean()) invalid++;
        }
        if (invalid > 0) return;
        PatrolManager.onConfigEdited();
        close();
    }

    // ---------- 行构件 ----------

    /** 给某列占一行：记下标签位置，返回控件应放的位置 {x, y} */
    private int[] slot(int col, String label) {
        int y = top + ROW_H * colRow[col]++;
        labels.add(new Label(Text.literal(label), left + colW * col + 8, y + 5));
        return new int[]{left + colW * col + colW - 14 - CTRL_W, y};
    }

    private void boolRow(int col, String label, boolean value, Consumer<Boolean> apply) {
        boolean[] state = {value};
        int[] p = slot(col, label);
        ButtonWidget b = ButtonWidget.builder(onOff(state[0]), btn -> {
            state[0] = !state[0];
            btn.setMessage(onOff(state[0]));
        }).dimensions(p[0], p[1], CTRL_W, CTRL_H).build();
        addDrawableChild(b);
        savers.add(() -> {
            apply.accept(state[0]);
            return true;
        });
    }

    private void cycleRow(int col, String label, String[] values, String current, Consumer<String> apply) {
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) idx = i;
        }
        int[] sel = {idx};
        int[] p = slot(col, label);
        ButtonWidget b = ButtonWidget.builder(Text.literal(values[idx]), btn -> {
            sel[0] = (sel[0] + 1) % values.length;
            btn.setMessage(Text.literal(values[sel[0]]));
        }).dimensions(p[0], p[1], CTRL_W, CTRL_H).build();
        addDrawableChild(b);
        savers.add(() -> {
            apply.accept(values[sel[0]]);
            return true;
        });
    }

    private void numRow(int col, String label, double value, double min, DoubleConsumer apply) {
        int[] p = slot(col, label);
        TextFieldWidget tf = new TextFieldWidget(getTextRenderer(), p[0], p[1], CTRL_W, CTRL_H, Text.empty());
        tf.setText(fmt(value));
        tf.setMaxLength(10);
        addDrawableChild(tf);
        savers.add(() -> {
            String s = tf.getText().trim();
            if (s.isEmpty()) return true;
            double v;
            try {
                v = Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return false;
            }
            if (v < min) v = min;
            apply.accept(v);
            return true;
        });
    }

    private void listRow(int col, String label, List<String> current, Consumer<List<String>> apply) {
        int[] p = slot(col, label);
        TextFieldWidget tf = new TextFieldWidget(getTextRenderer(), p[0], p[1], CTRL_W, CTRL_H, Text.empty());
        tf.setText(String.join(", ", current));
        tf.setMaxLength(180);
        addDrawableChild(tf);
        savers.add(() -> {
            List<String> out = new ArrayList<>();
            for (String raw : tf.getText().split("[,\\s]+")) {
                String id = PatrolManager.normalizeTypeId(raw);
                if (id != null && !out.contains(id)) out.add(id);
            }
            apply.accept(out);
            return true;
        });
    }

    private static Text onOff(boolean b) {
        return Text.literal(b ? "开" : "关");
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
