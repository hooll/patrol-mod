package com.hooll.patrol;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条宏：某个事件发生时按顺序执行一串动作。
 * 定义写在 config/patrol-points.json 的 macros 数组里（GUI !patrol macro gui 只做开关/手动跑）。
 */
public class PatrolMacro {
    public String name = "";
    public boolean enabled = true;
    /** 触发事件: death(自己死了) / respawn(复活后) / screen(界面打开) */
    public String trigger = "death";
    /** trigger=screen 时匹配界面标题(子串, 如 "副本"); 填 "container"=任意容器界面; 留空=任意界面 */
    public String screenMatch = "";
    /** 什么模式下才触发: always / hunt(找怪中) / patrol(巡逻中) */
    public String when = "always";
    /** 两次触发的最小间隔(秒) */
    public double cooldownSeconds = 15;
    /** 跑完是否恢复刚开始时中断的巡逻/找怪 */
    public boolean resumeAfter = true;
    public List<Step> steps = new ArrayList<>();

    /** config/patrol-macro/ 里的文件名（运行时用，不写进 JSON）；null = 还写在 patrol-points.json 里 */
    public transient String file = null;

    public static class Step {
        /** wait / goto / cmd / click / respawn */
        public String type = "";
        // wait: 等几秒
        public double seconds = 1;
        // goto: 点位名(当前世界)；或直接写坐标 x/y/z
        public String point = "";
        public Integer x;
        public Integer y;
        public Integer z;
        // cmd: 原样发送(服务器命令/聊天)；以 # 开头会被 Baritone 拦下不发到服务器
        public String text = "";
        // click: slot=容器槽位号(容器界面开着时)；或 cx/cy=界面坐标
        public Integer slot;
        public Integer cx;
        public Integer cy;
        /** click 用哪个键: 0=左键(默认) 1=右键 */
        public Integer button;
        /** goto 走到位的超时(秒) */
        public double timeoutSeconds = 120;
        /** 编辑器里那格文本的原始内容（运行时用，不写进 JSON） */
        public transient String raw = null;
    }
}
