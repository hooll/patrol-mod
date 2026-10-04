package com.hooll.patrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** 宏编辑器：改字段和步骤，保存写到 config/patrol-macro/<名字>.json */
public class PatrolMacroEditScreen extends Screen {
    private static final String[] TYPES = {"wait", "goto", "cmd", "click", "respawn"};
    private static final String[] TYPE_LABELS = {"等", "走", "命令", "点击", "复活"};
    private static final int ROW_H = 22;
    private static final int CTRL_H = 18;

    private final Screen parent;
    private final PatrolMacro work;
    private final List<StepRow> rows = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private int scroll = 0;
    private int viewTop = 106;
    private int viewH = 100;
    private int listX = 40;
    private String status = "";
    private boolean confirmDelete = false;

    private static final class StepRow {
        PatrolMacro.Step step;
        int typeIdx;
        ButtonWidget typeBtn;
        TextFieldWidget field;
        ButtonWidget keyBtn;
        ButtonWidget up;
        ButtonWidget down;
        ButtonWidget del;
        int labelY;
        boolean visibleRow;
    }

    private record Label(Text text, int x, int y) {
    }

    public PatrolMacroEditScreen(Screen parent, PatrolMacro macro) {
        super(Text.literal("宏编辑"));
        this.parent = parent;
        this.work = macro;
        for (PatrolMacro.Step s : work.steps) {
            StepRow r = new StepRow();
            r.step = s;
            r.typeIdx = Math.max(0, indexOfType(s.type));
            rows.add(r);
        }
    }

    private static int indexOfType(String type) {
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(type)) return i;
        }
        return 0;
    }

    @Override
    protected void init() {
        labels.clear();
        int left = Math.max(20, (this.width - 660) / 2);
        listX = Math.max(20, (this.width - 400) / 2);
        viewTop = 106;
        viewH = Math.max(44, this.height - 40 - viewTop);

        // ---- 头部：左列 ----
        addLabel("名字", left, 34);
        TextFieldWidget nameField = textField(left + 70, 34, 150, work.name == null ? "" : work.name, 24,
                "宏的名字", v -> work.name = v);
        addLabel("事件", left, 56);
        ButtonWidget triggerBtn = ButtonWidget.builder(Text.literal(work.trigger), b -> {
            String[] triggers = {"death", "respawn", "screen"};
            int idx = 0;
            for (int i = 0; i < triggers.length; i++) if (triggers[i].equals(work.trigger)) idx = i;
            work.trigger = triggers[(idx + 1) % triggers.length];
            b.setMessage(Text.literal(work.trigger));
        }).dimensions(left + 70, 56, 90, CTRL_H).build();
        addDrawableChild(triggerBtn);
        addLabel("匹配界面", left, 78);
        textField(left + 70, 78, 150, work.screenMatch == null ? "" : work.screenMatch, 40,
                "标题子串 / container / 留空", v -> work.screenMatch = v);

        // ---- 头部：右列 ----
        int rx = left + 280;
        addLabel("模式", rx, 34);
        ButtonWidget whenBtn = ButtonWidget.builder(Text.literal(work.when), b -> {
            String[] modes = {"always", "hunt", "patrol"};
            int idx = 0;
            for (int i = 0; i < modes.length; i++) if (modes[i].equals(work.when)) idx = i;
            work.when = modes[(idx + 1) % modes.length];
            b.setMessage(Text.literal(work.when));
        }).dimensions(rx + 70, 34, 90, CTRL_H).build();
        addDrawableChild(whenBtn);
        addLabel("冷却(秒)", rx, 56);
        textField(rx + 70, 56, 90, trimNum(work.cooldownSeconds), 8,
                "15", v -> {
                    try {
                        work.cooldownSeconds = Double.parseDouble(v.trim());
                    } catch (NumberFormatException ignored) {
                    }
                });
        addLabel("启用", rx, 78);
        ButtonWidget enabledBtn = ButtonWidget.builder(onOff(work.enabled), b -> {
            work.enabled = !work.enabled;
            b.setMessage(onOff(work.enabled));
        }).dimensions(rx + 70, 78, 70, CTRL_H).build();
        addDrawableChild(enabledBtn);
        addLabel("跑完恢复", rx + 150, 78);
        ButtonWidget resumeBtn = ButtonWidget.builder(onOff(work.resumeAfter), b -> {
            work.resumeAfter = !work.resumeAfter;
            b.setMessage(onOff(work.resumeAfter));
        }).dimensions(rx + 224, 78, 70, CTRL_H).build();
        addDrawableChild(resumeBtn);

        // ---- 步骤 ----
        for (StepRow r : rows) {
            buildRow(r);
        }
        layout();

        // ---- 底部按钮 ----
        int by = this.height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), b -> save())
                .dimensions(this.width / 2 - 210, by, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ 加步骤"), b -> addStep())
                .dimensions(this.width / 2 - 126, by, 80, 20).build());
        ButtonWidget delBtn = ButtonWidget.builder(Text.literal("删除宏"), b -> {
            if (!confirmDelete) {
                confirmDelete = true;
                b.setMessage(Text.literal("再点一次删除"));
                return;
            }
            PatrolManager.deleteMacro(work);
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(this.width / 2 - 42, by, 80, 20).build();
        addDrawableChild(delBtn);
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(this.width / 2 + 42, by, 80, 20).build());
    }

    private void addLabel(String s, int x, int y) {
        labels.add(new Label(Text.literal(s).formatted(Formatting.GRAY), x, y + 5));
    }

    private TextFieldWidget textField(int x, int y, int w, String value, int maxLen, String placeholder, java.util.function.Consumer<String> onChange) {
        TextFieldWidget tf = new TextFieldWidget(getTextRenderer(), x, y, w, CTRL_H, Text.empty());
        tf.setText(value == null ? "" : value);
        tf.setMaxLength(maxLen);
        if (placeholder != null) tf.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
        tf.setChangedListener(onChange);
        addDrawableChild(tf);
        return tf;
    }

    private void buildRow(StepRow r) {
        r.typeBtn = ButtonWidget.builder(Text.literal(TYPE_LABELS[r.typeIdx]), b -> {
            r.typeIdx = (r.typeIdx + 1) % TYPES.length;
            r.step.type = TYPES[r.typeIdx];
            b.setMessage(Text.literal(TYPE_LABELS[r.typeIdx]));
            r.field.setText(defaultRaw(r.step));
            layout();
        }).dimensions(listX + 20, 0, 64, CTRL_H).build();
        addDrawableChild(r.typeBtn);

        r.field = new TextFieldWidget(getTextRenderer(), listX + 88, 0, 150, CTRL_H, Text.empty());
        r.field.setText(rawOf(r.step));
        r.field.setMaxLength(200);
        r.field.setChangedListener(v -> r.step.raw = v);
        addDrawableChild(r.field);

        r.keyBtn = ButtonWidget.builder(Text.literal(keyLabel(r.step)), b -> {
            r.step.button = (r.step.button == null || r.step.button == 0) ? 1 : 0;
            b.setMessage(Text.literal(keyLabel(r.step)));
        }).dimensions(listX + 242, 0, 52, CTRL_H).build();
        addDrawableChild(r.keyBtn);

        r.up = ButtonWidget.builder(Text.literal("▲"), b -> move(r, -1)).dimensions(listX + 298, 0, 22, CTRL_H).build();
        r.down = ButtonWidget.builder(Text.literal("▼"), b -> move(r, 1)).dimensions(listX + 322, 0, 22, CTRL_H).build();
        r.del = ButtonWidget.builder(Text.literal("×"), b -> removeRow(r)).dimensions(listX + 346, 0, 22, CTRL_H).build();
        addDrawableChild(r.up);
        addDrawableChild(r.down);
        addDrawableChild(r.del);
    }

    private void move(StepRow r, int dir) {
        int i = rows.indexOf(r);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= rows.size()) return;
        java.util.Collections.swap(rows, i, j);
        java.util.Collections.swap(work.steps, i, j);
        layout();
    }

    private void removeRow(StepRow r) {
        int i = rows.indexOf(r);
        if (i < 0) return;
        rows.remove(i);
        work.steps.remove(i);
        remove(r.typeBtn);
        remove(r.field);
        remove(r.keyBtn);
        remove(r.up);
        remove(r.down);
        remove(r.del);
        layout();
    }

    private void addStep() {
        PatrolMacro.Step s = new PatrolMacro.Step();
        s.type = "wait";
        s.seconds = 1;
        work.steps.add(s);
        StepRow r = new StepRow();
        r.step = s;
        r.typeIdx = 0;
        rows.add(r);
        buildRow(r);
        layout();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, rows.size() * ROW_H - viewH);
        if (max > 0) {
            scroll = (int) Math.max(0, Math.min(max, scroll - verticalAmount * 20));
            layout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void layout() {
        int y0 = viewTop - scroll;
        for (int i = 0; i < rows.size(); i++) {
            StepRow r = rows.get(i);
            int y = y0 + i * ROW_H;
            boolean vis = y + ROW_H > viewTop && y < viewTop + viewH;
            place(r.typeBtn, listX + 20, y, 64, vis);
            place(r.field, listX + 88, y, 150, vis && !isRespawn(r.step));
            place(r.keyBtn, listX + 242, y, 52, vis && isClick(r.step));
            place(r.up, listX + 298, y, 22, vis);
            place(r.down, listX + 322, y, 22, vis);
            place(r.del, listX + 346, y, 22, vis);
            r.labelY = y + 5;
            r.visibleRow = vis;
        }
    }

    private static void place(ClickableWidget w, int x, int y, int width, boolean vis) {
        w.setX(x);
        w.setY(y);
        w.setWidth(width);
        w.visible = vis;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(getTextRenderer(), getTitle(), this.width / 2, 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(getTextRenderer(),
                Text.literal("步骤从上到下依次执行：等秒数 / 走到点位或坐标 / 发命令 / 点容器槽位或界面坐标 / 复活"),
                this.width / 2, 22, 0xFFA0A0A0);
        for (Label l : labels) {
            ctx.drawText(getTextRenderer(), l.text(), l.x(), l.y(), 0xFFE0E0E0, false);
        }
        for (int i = 0; i < rows.size(); i++) {
            StepRow r = rows.get(i);
            if (!r.visibleRow) continue;
            ctx.drawText(getTextRenderer(), Text.literal("#" + (i + 1)).formatted(Formatting.AQUA),
                    listX, r.labelY, 0xFFFFFFFF, false);
            ctx.drawText(getTextRenderer(), Text.literal(hintFor(r.step)).formatted(Formatting.DARK_GRAY),
                    listX + 88, r.labelY + 11, 0xFF808080, false);
        }
        int max = Math.max(0, rows.size() * ROW_H - viewH);
        if (max > 0) {
            ctx.drawText(getTextRenderer(), Text.literal("滚轮翻页 ↕").formatted(Formatting.DARK_GRAY),
                    listX + 346, viewTop - 12, 0xFF808080, false);
        }
        String line = status.isEmpty()
                ? (work.file == null ? "新宏：点保存会创建 " + PatrolManager.suggestedFile(work) : "文件：" + work.file)
                : status;
        ctx.drawCenteredTextWithShadow(getTextRenderer(), Text.literal(line),
                this.width / 2, this.height - 40, status.isEmpty() ? 0xFFA0A0A0 : 0xFFFFFF55);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void save() {
        for (StepRow r : rows) commitStep(r);
        if (work.name == null || work.name.trim().isEmpty()) work.name = "新宏";
        PatrolManager.saveEditedMacro(work);
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void commitStep(StepRow r) {
        PatrolMacro.Step s = r.step;
        s.type = TYPES[r.typeIdx];
        String raw = r.field.getText().trim();
        s.raw = raw.isEmpty() ? null : raw;
        switch (s.type) {
            case "wait" -> s.seconds = parseOr(raw, 1);
            case "goto" -> {
                int[] xyz = parseN(raw, 3);
                if (xyz != null) {
                    s.x = xyz[0];
                    s.y = xyz[1];
                    s.z = xyz[2];
                    s.point = "";
                } else {
                    s.point = raw;
                    s.x = null;
                    s.y = null;
                    s.z = null;
                }
            }
            case "cmd" -> s.text = raw;
            case "click" -> {
                int[] xy = parseN(raw, 2);
                if (xy != null) {
                    s.cx = xy[0];
                    s.cy = xy[1];
                    s.slot = null;
                } else {
                    s.cx = null;
                    s.cy = null;
                    s.slot = parseIntOrNull(raw);
                }
            }
            default -> {
            }
        }
    }

    private static boolean isClick(PatrolMacro.Step s) {
        return "click".equals(s.type);
    }

    private static boolean isRespawn(PatrolMacro.Step s) {
        return "respawn".equals(s.type);
    }

    private static String keyLabel(PatrolMacro.Step s) {
        return (s.button != null && s.button == 1) ? "右键" : "左键";
    }

    /** 步骤那格文本的初始内容 */
    private static String rawOf(PatrolMacro.Step s) {
        if (s.raw != null) return s.raw;
        return defaultRaw(s);
    }

    private static String defaultRaw(PatrolMacro.Step s) {
        String type = s.type == null ? "wait" : s.type;
        return switch (type) {
            case "wait" -> trimNum(s.seconds);
            case "goto" -> (s.point != null && !s.point.isEmpty()) ? s.point
                    : (s.x != null ? s.x + " " + s.y + " " + s.z : "");
            case "cmd" -> s.text == null ? "" : s.text;
            case "click" -> s.slot != null ? String.valueOf(s.slot)
                    : (s.cx != null ? s.cx + " " + (s.cy == null ? 0 : s.cy) : "");
            default -> "";
        };
    }

    /** 每格该填什么的一句话提示 */
    private static String hintFor(PatrolMacro.Step s) {
        String type = s.type == null ? "" : s.type;
        return switch (type) {
            case "wait" -> "等几秒";
            case "goto" -> "点位名 或 x y z";
            case "cmd" -> "命令/聊天原文(# 开头给 Baritone)";
            case "click" -> "容器槽位号 或 cx cy";
            case "respawn" -> "点复活(不用填)";
            default -> "";
        };
    }

    private static double parseOr(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static int[] parseN(String s, int n) {
        String[] p = s.trim().split("\\s+");
        if (p.length != n) return null;
        int[] out = new int[n];
        try {
            for (int i = 0; i < n; i++) out[i] = Integer.parseInt(p[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Text onOff(boolean b) {
        return Text.literal(b ? "开" : "关");
    }

    private static String trimNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
