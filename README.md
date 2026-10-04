# Patrol Mod

一个 Minecraft Fabric **客户端** mod：**多世界循环巡逻 + 自动找怪**，用 [Baritone](https://github.com/cabaletta/baritone) 自动寻路。走到点位自动切下一个，跑完一圈回到起点；打怪时自动暂停判定，卡住会重试/跳过；还能锁定视角前方那只怪（或自动搜索附近的敌对怪）走过去，击杀交给 KillAura；屏幕上有实时状态面板。

![build](https://github.com/hooll/patrol-mod/actions/workflows/build.yml/badge.svg)

- 目标版本：**Minecraft 1.21.11** + Fabric Loader ≥ 0.18.2
- 依赖：[Fabric API](https://modrinth.com/mod/fabric-api)、[Baritone（Meteor 版）](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.11-SNAPSHOT/)
- 命令全部在客户端处理，**不会发到服务器公聊**（交给 Baritone 的聊天拦截）
- HUD 面板只有自己可见

---

## 功能

- **循环巡逻**：多世界点位，走完一圈回到起点；跨世界自动切换对应路线
- **自动找怪**：`!patrol hunt` 锁定准星前的怪走过去，`!patrol hunt auto` 持续找最近的怪（击杀交给 KillAura）；目标类型、黑白名单可配，兼容插件服的"自定义模型怪"
- **打不误判、卡住自愈**：遇怪暂停计时；算不出路会自动重试 → 取消 goal → 跳过并冷却，不会在原地刷屏
- **副本友好**：寻路时禁挖/禁放方块；连续走不到多个目标就停手；被困会沿面包屑原路退回（`!patrol back`）；目标死了默认停在原地不空追
- **掉线续跑**：重连后自动接着断线前的巡逻/找怪
- **实时 HUD**：点位进度、距离、状态和事件提示（只有自己可见）
- **配置 GUI**：`!patrol gui` 图形界面改参数，也可以直接改 JSON（改完 1 秒热重载）
- **宏（事件→动作）**：死亡/复活/开界面 → 走位/发命令/点界面；一条宏一个文件，游戏内可新建编辑（`!patrol macro gui`）
- **聊天栏 Tab 补全**：`!pat` 按 Tab 补全命令和点位名

## 安装

1. 装好 Fabric Loader（≥ 0.18.2）和 [Fabric API](https://modrinth.com/mod/fabric-api)
2. 装 [Baritone（Meteor 版，1.21.11）](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.11-SNAPSHOT/)
3. 把本 mod 的 jar（可从 [Actions](../../actions) 的 artifact 下载，或自己构建）放进 `mods/`
4. 用 **Java 21** 启动游戏

## 使用

先在各个位置踩点：

```
!patrol add 矿区A      在当前位置记一个点（自动记录当前世界）
!patrol add 矿区B
```

然后开巡逻：

```
!patrol start                       循环跑"当前世界"的全部点
!patrol start 矿区A 矿区B 矿区C      按指定顺序循环
!patrol stop                        停止
```

找怪（击杀交给 KillAura，本 mod 只负责走过去）：

```
!patrol hunt            准星对着怪执行 → 记住它并走过去（之后开 auto 本次就只打这种）
!patrol hunt auto       开关自动找怪：本次只找你视角（或刚锁定）那种，找最近的目标 → 走过去 → 打死后再找下一只
!patrol hunt type       白名单（写配置，持久）：不带参数=用当前/上次锁定的那种，也可写 ocelot
!patrol hunt type clear 取消白名单
!patrol hunt ignore     黑名单（写配置，持久）：不带参数=把当前/上次锁定的那种拉黑，也可写 zombie
!patrol hunt ignore clear 清空黑名单
!patrol hunt targets mob  没有视角/白名单限定时，自动找怪认哪些实体：hostile(默认,原版敌对) / mob(所有生物) / all(除玩家和盔甲架外所有活物)
!patrol scan            列出附近活物的实体类型 + 会不会被锁定，用来排查自定义怪
!patrol hunt stop       停止找怪
```

> **只想打一种怪（不写任何配置）**：准星对着它 → `!patrol hunt auto`（或先 `!patrol hunt` 锁一下再 auto）。
> 本次自动找怪就只认这种类型；想换成别的，视角对着新怪再开一次 auto 即可。`!patrol hunt stop` 结束本次设定。
>
> **想长期固定**（跨登录/跨次都生效）再用黑白名单：`!patrol hunt type ocelot` 只打豹猫，`!patrol hunt ignore creeper` 永不选苦力怕。
> 注意本次视角设定会**压过**黑名单（你是指着它开的），所以拉黑了某种怪之后别再用视角对着它开 auto。

> **自定义模型怪**（插件服用豹猫、狼、村民之类套模型做的怪）：本体不是原版敌对生物，默认过滤认不出来。
> 先 `!patrol scan` 看它在客户端是什么实体（例如 `minecraft:ocelot`），然后 `!patrol hunt targets mob` 即可；
> 若同一场景里还有不想打的（村民 NPC 等），把它们写进配置的 `huntIgnoreTypes`。
> 注意 `all` 不包含玩家和盔甲架（`minecraft:armor_stand`）——后者基本是模型挂件/技能特效；
> 真要追盔甲架就用 `!patrol hunt` 手动锁定（视角模式只认你准星指着的那只，不做类型过滤，优先锁非盔甲架的实体）。

> **怪在墙后 / 地洞里，Baritone 说没路径还在刷屏**：服务器上 Baritone 一般不允许挖方块寻路，这种目标本来就到不了。
> 本 mod 会在 10 秒没位移后自动重发 goto，最多 3 次；仍然不动就取消 goal 并跳过（自动模式换下一只，30 秒内不再选它）。
> 想让"接近到几格就停"更容易达成，把 `huntArriveRadius` 调大（例如 5）。

| 命令 | 说明 |
|---|---|
| `!patrol add <名字>` | 在当前位置记一个点（自动带当前世界） |
| `!patrol del <名字>` | 删除当前世界的点 |
| `!patrol list` | 列出所有点（按世界分组，标出当前世界） |
| `!patrol clear` | 清空当前世界的点 |
| `!patrol start [名字...]` | 开始循环巡逻（不带名字 = 当前世界全部点） |
| `!patrol hunt` | 锁定视角前方的活物并走过去（不限类型），本 auto 会话只打这种 |
| `!patrol hunt auto` | 开关自动找怪（本次只找视角/刚锁定那种，不写配置） |
| `!patrol hunt type [类型\|clear]` | 查看/修改「只打某类」白名单（会存进配置） |
| `!patrol hunt ignore [类型\|clear]` | 查看/修改黑名单，拉黑的类型自动找怪永不选（会存进配置） |
| `!patrol hunt targets <hostile\|mob\|all>` | 没设白名单时，自动找怪认哪些实体（会存进配置） |
| `!patrol scan` | 列出附近活物的实体类型，排查自定义怪 |
| `!patrol hunt stop` | 停止找怪 |
| `!patrol back` | 被困住时按来时记下的路线一小段一小段退回入口（再输一次停）；找怪连续走不到会**自动触发** |
| `!patrol gui` | 打开配置界面（GUI），鼠标改参数，不用手编 JSON |
| `!patrol macro` | 列出宏和开关状态 |
| `!patrol macro on\|off\|toggle <宏名>` | 开关某条宏（存进配置） |
| `!patrol macro run <宏名>` | 立刻手动执行一次（调试用） |
| `!patrol macro stop` | 中止正在跑的宏 |
| `!patrol macro new [名字]` | 新建一条宏并打开编辑器 |
| `!patrol macro gui` | 宏列表界面：新建 / 编辑 / 开关 / 跑一次（也能从配置界面底部的「宏」进） |
| `!patrol stop` | 停止巡逻 / 找怪 / 回退 |
| `!patrol status` | 当前状态（含 mod 版本） |
| `!patrol reload` | 重新读取配置文件 |

聊天栏输入 `!pat` 按 **Tab** 可以补全这些命令；`!patrol del ` 或 `!patrol start ` 后按 Tab 会列出当前世界的点位名。

不想手编 JSON 就 `!patrol gui`：三栏图形界面（巡逻 / 找怪 / 过滤·界面），数字直接输、开关和枚举点一下切换；数字留空或填错会保留原值，点"保存并关闭"写盘并立刻生效。白名单/黑名单用逗号分隔，可写 `ocelot` 或 `minecraft:ocelot`。

## 配置

`config/patrol-points.json`，改完保存 1 秒内自动生效：

| 参数 | 默认 | 说明 |
|---|---|---|
| `arriveRadius` | `3.0` | 距点位多少格算到达 |
| `stuckSeconds` | `20` | 连续多少秒没位移算卡住 |
| `pointTimeoutSeconds` | `300` | 单个点最长走多久 |
| `pauseNearMobs` | `true` | 附近有怪时暂停计时 |
| `mobRadius` | `12.0` | 怪物检测半径 |
| `huntTargets` | `hostile` | 没设白名单时，自动找怪/暂停判定认哪些实体：`hostile` / `mob` / `all` |
| `huntTypes` | `[]` | 只打这些实体类型（白名单）。用 `!patrol hunt type` 写入，或手写 `["ocelot"]`；**视角限定的"本次只打"是运行时的，不写这里** |
| `huntIgnoreTypes` | `[]` | 任何模式下都排除的实体类型，如 `["minecraft:villager", "ocelot"]` |
| `combatFreezeSeconds` | `240` | 战斗中最多暂停计时多久 |
| `maxRetries` | `1` | 卡住后重试次数，用完才跳过 |
| `huntRange` | `48.0` | 找怪的射线长度 / 自动找怪半径 |
| `huntRetargetDistance` | `4.0` | 目标移动超过这个距离才重新下发 goto（调小会更贴怪，但 Baritone 消息更吵） |
| `huntMinRetargetIntervalSeconds` | `3.0` | 两次重发 goto 的最小间隔，防刷屏 |
| `huntArriveRadius` | `3.0` | 距目标多少格算"到了"，交给 KillAura |
| `huntStuckSeconds` | `10` | 多少秒完全没位移算"过不去" |
| `huntMaxAttempts` | `3` | 每个目标最多重试几次，用完就取消并跳过（自动模式换下一只） |
| `huntMaxGiveUps` | `3` | 自动找怪连续这么多个目标都走不到就停掉（防在被困的房间里空转） |
| `huntMinAliveSeconds` | `1.0` | 目标至少存活这么久才会被选（挡掉技能特效那种一闪而过的模型实体） |
| `hud` | `true` | 状态面板开关 |
| `hudPosition` | `top-center` | 也可 `top-left` |
| `hudWhenIdle` | `false` | 没巡逻时也显示面板 |
| `chatEvents` | `false` | 跳过/重试等事件是否也发聊天框 |
| `autoResume` | `true` | 掉线/服务器重启后重连，自动接着断线前的巡逻/找怪 |
| `baritoneNoBreak` | `true` | 寻路时禁掉 Baritone 的挖/放方块（副本里挖不动，开着会卡在原地挖） |
| `retreatWhenTrapped` | `true` | 被困住（找怪连续走不到）时自动沿原路退回入口；`!patrol back` 也能手动触发 |
| `cancelOnTargetDeath` | `true` | 追踪的目标死亡/消失时取消 Baritone 的寻路（停在原地）；关掉 = 继续走到它倒下的位置 |
| `baritonePrefix` | `#` | Baritone 命令前缀（一般不用改） |

## 宏（事件 → 动作）

**一条宏 = 一个 JSON 文件**，放在 `config/patrol-macro/` 文件夹里（文件名随意，建议和 `name` 一致）。`patrol-points.json` 里旧的 `macros` 数组也还认；同名时以文件夹里的文件为准。改文件后 1 秒内自动生效（文件夹里的改动也会被监听）。

不想手写 JSON 就 **全部在游戏里编辑**：`!patrol macro gui`（或配置界面底部的「宏」按钮）→ 列表里可以 **新建 / 编辑 / 开关 / 跑一次**；编辑器里能改 名字、事件、匹配界面、模式、冷却、启用、跑完恢复，以及**步骤的增删、上下移动和参数**（点保存自动写回 `config/patrol-macro/<名字>.json`）。

一个"死亡回副本"的完整例子（`config/patrol-macro/死亡回副本.json`）：

```json
{
  "name": "死亡回副本",
  "enabled": true,
  "trigger": "death",
  "when": "hunt",
  "cooldownSeconds": 30,
  "resumeAfter": true,
  "steps": [
    { "type": "wait", "seconds": 3 },
    { "type": "respawn" },
    { "type": "wait", "seconds": 5 },
    { "type": "goto", "point": "副本入口" },
    { "type": "cmd", "text": "/dungeon enter" },
    { "type": "wait", "seconds": 2 },
    { "type": "click", "slot": 13 }
  ]
}
```

| 字段 | 说明 |
|---|---|
| `trigger` | `death` 自己死了 / `respawn` 复活后 / `screen` 界面打开 |
| `screenMatch` | `trigger=screen` 时匹配界面标题的**子串**（如 `"副本选择"`）；填 `"container"` = 任意容器界面；留空 = 任意界面（开背包也会触发，建议写上） |
| `when` | 什么模式才触发：`always`(默认) / `hunt`(找怪中) / `patrol`(巡逻中) |
| `cooldownSeconds` | 两次触发的最小间隔，防连触发（默认 15） |
| `resumeAfter` | 宏跑完是否把开始时中断的巡逻/找怪接回来（默认 true） |

动作（`steps` 按顺序执行，每步做完才做下一步）：

| 动作 | 写法 | 说明 |
|---|---|---|
| 等 | `{"type":"wait","seconds":3}` | 等几秒 |
| 走 | `{"type":"goto","point":"入口"}` 或 `{"type":"goto","x":123,"y":64,"z":-45}` | 用 Baritone 走过去；`point` 是**当前世界**的点位名。走到自动下一步，超时（`timeoutSeconds`，默认 120 秒）会中止宏并提示 |
| 发命令 | `{"type":"cmd","text":"/dungeon enter"}` | 原样发到服务器（服务器命令/聊天）；`#` 开头的会被 Baritone 拦下（如 `#goto ...`） |
| 点界面 | `{"type":"click","slot":13}` | 点容器界面的第 13 个槽位（需要界面开着） |
| 点界面 | `{"type":"click","cx":260,"cy":150}` | 在界面坐标 (260,150) 模拟一次点击（点按钮/物品），`button` 默认 0 左键、1 右键 |
| 复活 | `{"type":"respawn"}` | 死亡画面上点"复活"（等价于客户端请求重生） |

开关与执行：`!patrol macro` 看列表，`!patrol macro on|off|toggle <宏名>` 开关（存进对应文件），`!patrol macro run <宏名>` 手动跑一次，`!patrol macro stop` 中止；`!patrol macro new [名字]` 建一条模板然后进编辑器。**宏运行时 HUD 会显示当前步骤，巡逻/找怪临时挂起，跑完自动接回**。

> 注意：`click cx/cy` 用的是 GUI 缩放后的坐标（就是界面上看到的像素位置）；不确定就先 `{"type":"wait","seconds":5}` 挂着看一眼，或者先用 `slot` 方式点容器格子。

## 从源码构建

需要 JDK 21：

```bash
./gradlew build          # 产物在 build/libs/patrol-mod.jar
```

仓库自带 GitHub Actions（`.github/workflows/build.yml`）：push 或手动 `workflow_dispatch` 都会构建，jar 在 run 的 artifact 里。

## 说明

- 本 mod 通过聊天通道向 Baritone 发送 `#goto` 等命令，Baritone 在网络层拦截，**不会真的发出去**；因此必须安装 Baritone 才能寻路
- 不要把 `AutoWalk`、`AntiAFK` 之类会抢占移动控制的模块和它同时开着
- 在多人服务器上使用自动寻路/巡逻可能违反服务器规则，后果自负

## License

MIT
