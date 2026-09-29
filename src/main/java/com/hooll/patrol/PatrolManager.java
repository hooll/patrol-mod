package com.hooll.patrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PatrolManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("patrol-points.json");

    private static final int MOB_SCAN_INTERVAL = 5;
    private static final double ARRIVE_Y_TOLERANCE = 4;
    private static final int EVENT_SHOW_TICKS = 80;

    private static PatrolConfig config = new PatrolConfig();

    // ---------- 巡逻状态 ----------
    private static boolean active = false;
    private static List<PatrolPoint> route = new ArrayList<>();
    private static int routeIndex = 0;
    private static int retries = 0;
    private static int stuckTicks = 0;
    private static int pointTicks = 0;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static double initialDist = -1;
    private static String routeDimension = null;

    private static int scanCounter = 0;
    private static boolean mobsNear = false;
    private static int combatTicks = 0;

    // ---------- 找怪状态 ----------
    public enum HuntMode { NONE, SINGLE, AUTO }

    private static HuntMode huntMode = HuntMode.NONE;
    private static Entity huntTarget = null;
    private static BlockPos huntLastGoto = null;
    private static boolean huntArrived = false;

    private static String lastEvent = null;
    private static int lastEventTicks = 0;

    private static long lastConfigMtime = -1;
    private static int mtimeCheckCounter = 0;

    // ---------- 配置读写 ----------

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            PatrolConfig loaded = GSON.fromJson(reader, PatrolConfig.class);
            if (loaded != null) {
                config = loaded;
                if (config.points == null) config.points = new ArrayList<>();
                if (config.baritonePrefix == null || config.baritonePrefix.isEmpty()) config.baritonePrefix = "#";
                if (config.arriveRadius <= 0) config.arriveRadius = 3.0;
                if (config.stuckSeconds <= 0) config.stuckSeconds = 20;
                if (config.pointTimeoutSeconds <= 0) config.pointTimeoutSeconds = 300;
                if (config.mobRadius <= 0) config.mobRadius = 12.0;
                if (config.combatFreezeSeconds <= 0) config.combatFreezeSeconds = 240;
                if (config.maxRetries < 0) config.maxRetries = 1;
                if (config.hudPosition == null) config.hudPosition = "top-center";
                if (config.huntRange <= 0) config.huntRange = 48.0;
                if (config.huntRetargetDistance <= 0) config.huntRetargetDistance = 2.5;
                if (config.huntTargets == null) config.huntTargets = "hostile";
                String mode = config.huntTargets.trim().toLowerCase(Locale.ROOT);
                if (!mode.equals("mob") && !mode.equals("all")) mode = "hostile";
                config.huntTargets = mode;
                if (config.huntIgnoreTypes == null) config.huntIgnoreTypes = new ArrayList<>();
            }
        } catch (IOException | RuntimeException e) {
            PatrolMod.LOG.error("failed to load patrol config", e);
        }
        lastConfigMtime = currentMtime();
    }

    private static long currentMtime() {
        try {
            return Files.exists(CONFIG_PATH) ? Files.getLastModifiedTime(CONFIG_PATH).toMillis() : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            PatrolMod.LOG.error("failed to save patrol config", e);
        }
        lastConfigMtime = currentMtime();
    }

    // ---------- 聊天命令 ----------

    public static boolean handleChat(String raw) {
        String message = raw.trim();
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.equals("!patrol") && !lower.startsWith("!patrol ")) return false;

        String[] parts = message.split("\\s+");
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "add" -> cmdAdd(parts);
            case "del", "remove", "rm" -> cmdDel(parts);
            case "list", "ls" -> cmdList();
            case "clear" -> cmdClear();
            case "start", "go" -> cmdStart(parts);
            case "stop" -> cmdStop();
            case "hunt", "find", "target" -> cmdHunt(parts);
            case "scan", "entities", "near" -> cmdScan();
            case "reload" -> cmdReload();
            case "status" -> cmdStatus();
            default -> cmdHelp();
        }
        return true;
    }

    private static void cmdAdd(String[] parts) {
        if (mc.player == null || mc.world == null) return;
        if (parts.length < 3) {
            msg("用法: !patrol add <名字>（站在要巡逻的位置上执行）");
            return;
        }
        String name = parts[2];
        String dim = currentDimension();
        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());
        config.points.removeIf(p -> p.name.equalsIgnoreCase(name) && p.dimension.equals(dim));
        config.points.add(new PatrolPoint(name, dim, x, y, z));
        save();
        msg("已记录 " + name + " (" + shortDim(dim) + " " + x + " " + y + " " + z + ")");
    }

    private static void cmdDel(String[] parts) {
        if (parts.length < 3) {
            msg("用法: !patrol del <名字>");
            return;
        }
        String name = parts[2];
        String dim = currentDimension();
        boolean removed = config.points.removeIf(p -> p.name.equalsIgnoreCase(name) && p.dimension.equals(dim));
        if (removed) {
            save();
            msg("已删除 " + name);
        } else {
            msg("当前世界没有叫 " + name + " 的点");
        }
    }

    private static void cmdList() {
        if (config.points.isEmpty()) {
            msg("还没有点位。站到位置上输入 !patrol add <名字>");
            return;
        }
        String cur = currentDimension();
        msg("共 " + config.points.size() + " 个点:");
        String lastDim = null;
        for (PatrolPoint p : config.points) {
            if (!p.dimension.equals(lastDim)) {
                lastDim = p.dimension;
                send("§7— " + shortDim(lastDim) + (lastDim.equals(cur) ? " §a(当前世界)" : ""));
            }
            send((p.dimension.equals(cur) ? "§a* " : "§7  ") + "§f" + p.name + " §7(" + p.x + ", " + p.y + ", " + p.z + ")");
        }
    }

    private static void cmdClear() {
        String dim = currentDimension();
        int before = config.points.size();
        config.points.removeIf(p -> p.dimension.equals(dim));
        save();
        msg("已清空当前世界(" + shortDim(dim) + ")的 " + (before - config.points.size()) + " 个点");
    }

    private static void cmdStart(String[] parts) {
        String dim = currentDimension();
        if (dim == null) return;
        List<PatrolPoint> candidates = pointsIn(dim);
        if (candidates.isEmpty()) {
            msg("当前世界没有点位，先 !patrol add <名字>");
            return;
        }

        List<PatrolPoint> newRoute = new ArrayList<>();
        if (parts.length > 2) {
            for (int i = 2; i < parts.length; i++) {
                String name = parts[i];
                PatrolPoint found = null;
                for (PatrolPoint p : candidates) {
                    if (p.name.equalsIgnoreCase(name)) {
                        found = p;
                        break;
                    }
                }
                if (found == null) {
                    msg("当前世界没有点 " + name + "，已忽略");
                } else {
                    newRoute.add(found);
                }
            }
            if (newRoute.isEmpty()) {
                msg("指定的点都不在当前世界");
                return;
            }
        } else {
            newRoute = candidates;
        }

        endHunt(false);
        route = newRoute;
        routeIndex = 0;
        routeDimension = dim;
        retries = 0;
        combatTicks = 0;
        active = true;

        StringBuilder sb = new StringBuilder();
        for (PatrolPoint p : route) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(p.name);
        }
        msg("开始巡逻(" + route.size() + " 个点): " + sb);
        gotoCurrent();
    }

    private static void cmdStop() {
        if (huntMode != HuntMode.NONE) {
            endHunt(true);
            msg("已停止找怪");
            return;
        }
        if (!active) {
            msg("当前没有在巡逻");
            return;
        }
        stop();
        msg("已停止巡逻");
    }

    private static void cmdHunt(String[] parts) {
        String arg = parts.length > 2 ? parts[2].toLowerCase(Locale.ROOT) : "";

        if (arg.equals("stop") || arg.equals("off")) {
            if (huntMode == HuntMode.NONE) {
                msg("当前没有在找怪");
                return;
            }
            endHunt(true);
            msg("已停止找怪");
            return;
        }

        if (arg.equals("targets") || arg.equals("filter")) {
            if (parts.length < 4) {
                msg("目标过滤: " + config.huntTargets + "（hostile=原版敌对 / mob=所有生物 / all=除玩家外所有活物）");
                return;
            }
            String mode = parts[3].toLowerCase(Locale.ROOT);
            if (!mode.equals("hostile") && !mode.equals("mob") && !mode.equals("all")) {
                msg("可选值: hostile / mob / all");
                return;
            }
            config.huntTargets = mode;
            save();
            msg("目标过滤 = " + mode + "（已保存，立即生效）");
            return;
        }

        if (arg.equals("auto")) {
            if (huntMode == HuntMode.AUTO) {
                endHunt(true);
                msg("自动找怪：关");
                return;
            }
            clearPatrolState();
            if (baritoneLoaded()) sendBaritone("cancel");
            huntMode = HuntMode.AUTO;
            huntTarget = null;
            huntLastGoto = null;
            huntArrived = false;
            msg("自动找怪：开（半径 " + (int) config.huntRange + " 格，过滤 " + config.huntTargets + "，找到就过去，KillAura 负责打）");
            if (config.huntTargets.equals("hostile")) {
                event("提示: 自定义怪若不算目标，改 huntTargets 或用 !patrol scan 看类型");
            }
            return;
        }

        if (mc.player == null || mc.world == null) return;
        Entity target = findInView(config.huntRange);
        if (target == null) {
            msg("视角前方 " + (int) config.huntRange + " 格内没找到活物（准星对着它再执行，!patrol scan 可看附近有哪些实体）");
            return;
        }
        clearPatrolState();
        if (baritoneLoaded()) sendBaritone("cancel");
        huntMode = HuntMode.SINGLE;
        huntTarget = target;
        huntLastGoto = null;
        huntArrived = false;
        msg("锁定 " + target.getName().getString() + " §7[" + typeId(target) + "]§f，距离 "
                + (int) mc.player.distanceTo(target) + "m，走过去");
    }

    private static void cmdScan() {
        if (mc.player == null || mc.world == null) return;
        double range = config.huntRange;
        Map<String, int[]> stats = new LinkedHashMap<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity) || e == mc.player) continue;
            if (mc.player.distanceTo(e) > range) continue;
            int[] s = stats.computeIfAbsent(typeId(e), k -> new int[2]);
            if (matchesTargetFilter(e)) {
                s[0]++;
            } else {
                s[1]++;
            }
        }
        if (stats.isEmpty()) {
            msg("附近 " + (int) range + " 格内没有活物");
            return;
        }
        List<Map.Entry<String, int[]>> list = new ArrayList<>(stats.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        msg("附近 " + (int) range + " 格活物（过滤 " + config.huntTargets + "，§a✔§f=会自动锁定）:");
        int shown = 0;
        for (Map.Entry<String, int[]> en : list) {
            if (shown >= 15) {
                send("§7  …还有 " + (list.size() - shown) + " 种");
                break;
            }
            shown++;
            int[] s = en.getValue();
            if (s[0] > 0) {
                send("§a✔ §f" + en.getKey() + " §7×" + (s[0] + s[1]) + (s[1] > 0 ? " §7(其中 " + s[0] + " 个算目标)" : ""));
            } else {
                send("§7✘ " + en.getKey() + " ×" + s[1] + " §8(不算目标)");
            }
        }
    }

    private static void cmdReload() {
        load();
        msg("配置已重载：点 " + config.points.size() + " 个 | 卡住 " + config.stuckSeconds + "s | 单点上限 "
                + config.pointTimeoutSeconds + "s | 怪物半径 " + (int) config.mobRadius + " | 战斗暂停上限 "
                + config.combatFreezeSeconds + "s | 找怪半径 " + (int) config.huntRange
                + " | 目标过滤 " + config.huntTargets + " | 排除 " + config.huntIgnoreTypes.size() + " 种");
    }

    private static void cmdStatus() {
        String ver = version();
        if (huntMode == HuntMode.AUTO) {
            msg("v" + ver + " 自动找怪中" + (huntTarget != null && huntTarget.isAlive()
                    ? " → " + huntTarget.getName().getString() + " (" + (int) mc.player.distanceTo(huntTarget) + "m)" : " (扫描中)"));
            return;
        }
        if (huntMode == HuntMode.SINGLE) {
            msg("v" + ver + " 锁定目标" + (huntTarget != null && huntTarget.isAlive()
                    ? " " + huntTarget.getName().getString() + " (" + (int) mc.player.distanceTo(huntTarget) + "m)" : " (已消失)"));
            return;
        }
        if (!active) {
            msg("v" + ver + " 未在巡逻。当前世界 " + shortDim(currentDimension()) + " 有 " + pointsIn(currentDimension()).size() + " 个点");
            return;
        }
        PatrolPoint p = route.get(routeIndex);
        msg("v" + ver + " 巡逻中 " + (routeIndex + 1) + "/" + route.size() + " → " + p.name + " (" + p.x + ", " + p.y + ", " + p.z + ")"
                + (mobsNear ? " §e[附近有怪,计时暂停]" : ""));
    }

    private static void cmdHelp() {
        send("§b[巡逻] §f命令:");
        send("§7  !patrol add <名字>   §f在当前位置记一个点(自动带当前世界)");
        send("§7  !patrol del <名字>   §f删除当前世界的点");
        send("§7  !patrol list          §f列出所有点(按世界分组)");
        send("§7  !patrol clear         §f清空当前世界的点");
        send("§7  !patrol start [名字...] §f开始循环巡逻(不带名字=当前世界全部点)");
        send("§7  !patrol hunt          §f记住视角前方那只怪(不限类型),走过去");
        send("§7  !patrol hunt auto     §f开关自动找怪(最近的怪→过去→下一只)");
        send("§7  !patrol hunt targets <hostile|mob|all> §f自动找怪认哪些实体");
        send("§7  !patrol hunt stop     §f停止找怪");
        send("§7  !patrol scan          §f列出附近活物的实体类型,排查自定义怪");
        send("§7  !patrol stop          §f停止巡逻/找怪");
        send("§7  !patrol reload        §f重新读取配置文件(改文件后 1 秒内也会自动重载)");
        send("§7聊天栏输入 §f!pat§7 按 §fTab§7 可补全命令和点位名;参数在 config/patrol-points.json");
    }

    // ---------- 主循环 ----------

    public static void tick(MinecraftClient client) {
        if (lastEventTicks > 0) lastEventTicks--;

        // 配置文件被改动就自动重载(每秒查一次 mtime)
        mtimeCheckCounter++;
        if (mtimeCheckCounter >= 20) {
            mtimeCheckCounter = 0;
            long m = currentMtime();
            if (m != lastConfigMtime) {
                load();
                if (active || huntMode != HuntMode.NONE) event("配置已重载");
            }
        }

        if (huntMode != HuntMode.NONE) {
            tickHunt(client);
            return;
        }

        if (!active) return;
        if (client.player == null || client.world == null) return;

        String dim = currentDimension();
        if (dim == null) return;

        if (!dim.equals(routeDimension)) {
            List<PatrolPoint> next = pointsIn(dim);
            if (next.isEmpty()) {
                msg("已进入 " + shortDim(dim) + "，该世界没有点位，巡逻停止");
                stop();
                return;
            }
            route = next;
            routeIndex = 0;
            retries = 0;
            combatTicks = 0;
            routeDimension = dim;
            event("已切换到 " + shortDim(dim) + " 的路线(" + next.size() + " 个点)");
            gotoCurrent();
            return;
        }

        if (route.isEmpty()) {
            stop();
            return;
        }

        scanCounter++;
        if (scanCounter >= MOB_SCAN_INTERVAL) {
            scanCounter = 0;
            mobsNear = config.pauseNearMobs && targetsNearby(client, config.mobRadius);
        }

        PatrolPoint p = route.get(routeIndex);
        double dx = client.player.getX() - (p.x + 0.5);
        double dz = client.player.getZ() - (p.z + 0.5);
        double dy = client.player.getY() - p.y;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal <= config.arriveRadius && Math.abs(dy) <= ARRIVE_Y_TOLERANCE) {
            advance(false);
            return;
        }

        double moved = Math.abs(client.player.getX() - lastX)
                + Math.abs(client.player.getY() - lastY)
                + Math.abs(client.player.getZ() - lastZ);
        lastX = client.player.getX();
        lastY = client.player.getY();
        lastZ = client.player.getZ();

        if (mobsNear) {
            combatTicks++;
        } else {
            combatTicks = 0;
        }
        boolean combatPause = mobsNear && combatTicks <= config.combatFreezeSeconds * 20;

        if (!combatPause) {
            pointTicks++;
            if (moved < 0.02) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
        } else if (moved >= 0.02) {
            stuckTicks = 0;
        }

        if (stuckTicks >= config.stuckSeconds * 20) {
            stuckTicks = 0;
            if (retries < config.maxRetries) {
                retries++;
                event("在 " + p.name + " 卡住，重试 " + retries + "/" + config.maxRetries);
                gotoCurrent();
            } else {
                advance(true);
            }
            return;
        }

        if (pointTicks >= config.pointTimeoutSeconds * 20) {
            advance(true);
        }
    }

    private static void tickHunt(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        if (huntMode == HuntMode.AUTO) {
            if (huntTarget == null || !huntTarget.isAlive()) {
                huntTarget = findNearestTarget(client, config.huntRange);
                huntLastGoto = null;
                huntArrived = false;
                if (huntTarget == null) return;
                event("新目标 " + huntTarget.getName().getString() + " [" + typeId(huntTarget) + "]");
            }
        } else {
            if (huntTarget == null || !huntTarget.isAlive()) {
                event(huntTarget == null ? "目标已消失" : "目标已被击杀");
                endHunt(false);
                return;
            }
        }

        double dist = client.player.distanceTo(huntTarget);
        if (dist > config.huntRange * 2) {
            if (huntMode == HuntMode.SINGLE) {
                event("目标跑太远，放弃");
                endHunt(true);
            } else {
                huntTarget = null;
            }
            return;
        }

        if (dist <= config.arriveRadius) {
            if (!huntArrived) {
                huntArrived = true;
                if (baritoneLoaded()) sendBaritone("cancel");
                event("已到达 " + huntTarget.getName().getString());
            }
            return;
        }

        huntArrived = false;
        int tx = (int) Math.floor(huntTarget.getX());
        int ty = (int) Math.floor(huntTarget.getY());
        int tz = (int) Math.floor(huntTarget.getZ());
        boolean needGoto = huntLastGoto == null;
        if (!needGoto) {
            double mdx = tx - huntLastGoto.getX();
            double mdy = ty - huntLastGoto.getY();
            double mdz = tz - huntLastGoto.getZ();
            needGoto = mdx * mdx + mdy * mdy + mdz * mdz >= config.huntRetargetDistance * config.huntRetargetDistance;
        }
        if (needGoto) {
            huntLastGoto = new BlockPos(tx, ty, tz);
            baritone("goto " + tx + " " + ty + " " + tz);
        }
    }

    private static void gotoCurrent() {
        if (mc.player == null) return;
        PatrolPoint p = route.get(routeIndex);
        baritone("goto " + p.x + " " + p.y + " " + p.z);
        lastX = mc.player.getX();
        lastY = mc.player.getY();
        lastZ = mc.player.getZ();
        initialDist = distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ(), p);
        stuckTicks = 0;
        pointTicks = 0;
        combatTicks = 0;
    }

    private static void advance(boolean skipped) {
        if (skipped) event("跳过 " + route.get(routeIndex).name);
        routeIndex = (routeIndex + 1) % route.size();
        retries = 0;
        gotoCurrent();
    }

    private static void clearPatrolState() {
        active = false;
        route = new ArrayList<>();
        routeIndex = 0;
        retries = 0;
        stuckTicks = 0;
        pointTicks = 0;
        combatTicks = 0;
        mobsNear = false;
        routeDimension = null;
    }

    private static void stop() {
        clearPatrolState();
        if (baritoneLoaded()) sendBaritone("cancel");
    }

    private static void endHunt(boolean cancelPath) {
        huntMode = HuntMode.NONE;
        huntTarget = null;
        huntLastGoto = null;
        huntArrived = false;
        if (cancelPath && baritoneLoaded()) sendBaritone("cancel");
    }

    // ---------- 找怪 ----------

    /** 视角射线命中的最近活物（先按碰撞箱求交，没命中再放宽成一个锥形容差） */
    private static Entity findInView(double range) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = mc.player.getRotationVec(1.0F);
        Vec3d end = eye.add(dir.multiply(range));

        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        Entity fallback = null;
        double fallbackDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive()) continue;

            var hit = e.getBoundingBox().raycast(eye, end);
            if (hit.isPresent()) {
                double d = hit.get().subtract(eye).length();
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
                continue;
            }

            // 容差：射线到实体中心的垂直距离
            Vec3d center = e.getBoundingBox().getCenter();
            Vec3d toEntity = center.subtract(eye);
            double t = toEntity.dotProduct(dir);
            if (t <= 0 || t > range) continue;
            Vec3d closest = eye.add(dir.multiply(t));
            double perp = closest.subtract(center).length();
            double tolerance = Math.max(0.7, e.getWidth() * 0.7 + 0.3);
            if (perp <= tolerance && t < fallbackDist) {
                fallbackDist = t;
                fallback = e;
            }
        }
        return best != null ? best : fallback;
    }

    private static Entity findNearestTarget(MinecraftClient client, double range) {
        if (client.world == null || client.player == null) return null;
        Entity best = null;
        double bestDist = range;
        for (Entity e : client.world.getEntities()) {
            if (!matchesTargetFilter(e)) continue;
            double d = client.player.distanceTo(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * 自动找怪/战斗暂停共用的目标判定。
     * hostile=原版敌对生物；mob=所有生物(含豹猫这类被动、中立)；all=除玩家外所有活物(含盔甲架挂件)。
     * 插件服的自定义怪常拿豹猫/狼/村民套模型，本体不是 HostileEntity，所以要能放宽。
     */
    private static boolean matchesTargetFilter(Entity e) {
        if (!(e instanceof LivingEntity) || !e.isAlive() || e == mc.player) return false;
        if (isIgnoredType(e)) return false;
        return switch (config.huntTargets) {
            case "all" -> !(e instanceof PlayerEntity);
            case "mob" -> e instanceof MobEntity;
            default -> e instanceof HostileEntity;
        };
    }

    private static boolean isIgnoredType(Entity e) {
        String id = typeId(e);
        for (String raw : config.huntIgnoreTypes) {
            if (raw == null) continue;
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            if (id.equals(s) || id.equals("minecraft:" + s)) return true;
        }
        return false;
    }

    private static String typeId(Entity e) {
        var id = Registries.ENTITY_TYPE.getId(e.getType());
        return id == null ? "?" : id.toString();
    }

    // ---------- HUD 数据 ----------

    public static class HudState {
        public boolean idle;
        public String extra = "";
        public int index;
        public int total;
        public String name = "";
        public double distance;
        public double partial;
        public HudStatus status = HudStatus.WALKING;
        public String event;

        public boolean hunting;
        public boolean huntAuto;
        public boolean huntHasTarget;
        public boolean huntArrived;
        public String huntName = "";
        public int huntDistance;
        public int huntHealth = -1;
    }

    public enum HudStatus {
        WALKING,
        COMBAT,
        RETRY
    }

    public static String hudPosition() {
        return config.hudPosition;
    }

    /** 当前世界的点位名，供 Tab 补全用 */
    public static List<String> pointNames() {
        String dim = currentDimension();
        List<String> names = new ArrayList<>();
        for (PatrolPoint p : config.points) {
            if (dim == null || dim.equals(p.dimension)) names.add(p.name);
        }
        return names;
    }

    public static HudState hudState() {
        if (!config.hud) return null;
        if (mc.player == null || mc.world == null) return null;

        if (huntMode != HuntMode.NONE) {
            HudState s = new HudState();
            s.hunting = true;
            s.huntAuto = huntMode == HuntMode.AUTO;
            s.huntArrived = huntArrived;
            if (huntTarget != null && huntTarget.isAlive()) {
                s.huntHasTarget = true;
                s.huntName = huntTarget.getName().getString();
                s.huntDistance = (int) Math.round(mc.player.distanceTo(huntTarget));
                if (huntTarget instanceof LivingEntity living) {
                    s.huntHealth = (int) Math.ceil(living.getHealth());
                }
            }
            if (lastEventTicks > 0) s.event = lastEvent;
            return s;
        }

        if (!active) {
            if (!config.hudWhenIdle) return null;
            HudState s = new HudState();
            s.idle = true;
            s.extra = String.valueOf(pointsIn(currentDimension()).size());
            return s;
        }
        if (route.isEmpty()) return null;

        PatrolPoint p = route.get(routeIndex);
        HudState s = new HudState();
        s.index = routeIndex;
        s.total = route.size();
        s.name = p.name;
        s.distance = distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ(), p);
        s.status = mobsNear ? HudStatus.COMBAT : (retries > 0 ? HudStatus.RETRY : HudStatus.WALKING);
        if (initialDist > 0) {
            double partial = 1 - s.distance / initialDist;
            s.partial = Math.max(0, Math.min(1, partial));
        }
        if (lastEventTicks > 0) s.event = lastEvent;
        return s;
    }

    private static double distanceTo(double px, double py, double pz, PatrolPoint p) {
        double dx = px - (p.x + 0.5);
        double dy = py - p.y;
        double dz = pz - (p.z + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---------- 工具 ----------

    private static boolean targetsNearby(MinecraftClient client, double radius) {
        if (client.world == null || client.player == null) return false;
        for (Entity e : client.world.getEntities()) {
            if (matchesTargetFilter(e) && e.distanceTo(client.player) <= radius) {
                return true;
            }
        }
        return false;
    }

    private static boolean baritoneLoaded() {
        return FabricLoader.getInstance().isModLoaded("baritone-meteor")
                || FabricLoader.getInstance().isModLoaded("baritone");
    }

    private static void sendBaritone(String command) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.sendChatMessage(config.baritonePrefix + command);
    }

    private static void baritone(String command) {
        if (!baritoneLoaded()) {
            msg("§c未检测到 Baritone(baritone-meteor)，无法寻路");
            stop();
            endHunt(false);
            return;
        }
        sendBaritone(command);
    }

    private static List<PatrolPoint> pointsIn(String dim) {
        List<PatrolPoint> list = new ArrayList<>();
        if (dim == null) return list;
        for (PatrolPoint p : config.points) {
            if (dim.equals(p.dimension)) list.add(p);
        }
        return list;
    }

    private static String currentDimension() {
        if (mc.world == null) return null;
        return mc.world.getRegistryKey().getValue().toString();
    }

    private static String shortDim(String dim) {
        if (dim == null) return "?";
        int i = dim.indexOf(':');
        return i >= 0 ? dim.substring(i + 1) : dim;
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(PatrolMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    private static void send(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    private static void msg(String s) {
        send("§b[巡逻] §f" + s);
    }

    /** 自动事件：默认只进 HUD 顶部提示，不发聊天框 */
    private static void event(String s) {
        lastEvent = s;
        lastEventTicks = EVENT_SHOW_TICKS;
        if (config.chatEvents) msg(s);
    }
}
