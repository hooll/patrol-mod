package com.hooll.patrol;

import java.util.ArrayList;
import java.util.List;

public class PatrolConfig {
    public String baritonePrefix = "#";

    // 到达判定：水平距离 <= arriveRadius 且 |dy| <= 4
    public double arriveRadius = 3.0;
    // 连续多少秒没位移算卡住（附近有怪时暂停累计）
    public int stuckSeconds = 20;
    // 单个点最长走多少秒（附近有怪时暂停累计）
    public int pointTimeoutSeconds = 300;
    // 附近有敌对怪时暂停"卡住/超时"计时——打怪时 Baritone 本来就会停下
    public boolean pauseNearMobs = true;
    public double mobRadius = 12.0;
    // 战斗中最多暂停计时多少秒（防止被围住永远不走）
    public int combatFreezeSeconds = 240;
    // 每个点卡住后的重试次数，用完才跳过
    public int maxRetries = 1;

    // 找怪
    public double huntRange = 48.0;
    // 目标移动超过这个距离才重新下发 goto（调大能减少 Baritone 重复报路的声音）
    public double huntRetargetDistance = 4.0;
    // 两次重发 goto 之间的最小间隔(秒)，防止怪物走动导致刷屏
    public double huntMinRetargetIntervalSeconds = 3.0;
    // 距目标多少格算"到了"，交给 KillAura
    public double huntArriveRadius = 3.0;
    // 多少秒完全没位移算"过不去"(Baritone 算不出路径时会停着不动)
    public int huntStuckSeconds = 10;
    // 每个目标最多重发几次 goto，用完就取消并跳过(自动模式换下一只)
    public int huntMaxAttempts = 3;
    // 目标至少要在客户端"存活"这么多秒才会被选
    // 技能特效/模型通常一瞬即逝，这条能把它们挡在门外
    public double huntMinAliveSeconds = 1.0;
    // 自动找怪认哪些实体：hostile=原版敌对生物 / mob=所有生物(含豹猫这类被动、中立) / all=除玩家外所有活物(含盔甲架)
    // 插件服的自定义怪常拿豹猫、狼、村民这类实体套模型，这种情况用 mob 或 all
    public String huntTargets = "hostile";
    // 任何模式下都排除的实体类型，写 "minecraft:villager" 或简写 "villager"
    public List<String> huntIgnoreTypes = new ArrayList<>();

    // 屏幕状态面板（只有自己可见）
    public boolean hud = true;
    // "top-center" 或 "top-left"
    public String hudPosition = "top-center";
    // 没在巡逻时也显示面板
    public boolean hudWhenIdle = false;
    // 自动事件(跳过/切世界等)是否也发到聊天框
    public boolean chatEvents = false;

    public List<PatrolPoint> points = new ArrayList<>();
}
