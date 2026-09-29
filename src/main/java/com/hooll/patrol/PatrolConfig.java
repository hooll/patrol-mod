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
    public double huntRetargetDistance = 2.5;

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
