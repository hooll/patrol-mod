# Patrol Mod

一个 Minecraft Fabric **客户端** mod：**多世界循环巡逻 + 自动找怪**，用 [Baritone](https://github.com/cabaletta/baritone) 自动寻路。走到点位自动切下一个，跑完一圈回到起点；打怪时自动暂停判定，卡住会重试/跳过；还能锁定视角前方那只怪（或自动搜索附近的敌对怪）走过去，击杀交给 KillAura；屏幕上有实时状态面板。

![build](https://github.com/hooll/patrol-mod/actions/workflows/build.yml/badge.svg)

- 目标版本：**Minecraft 1.21.11** + Fabric Loader ≥ 0.18.2
- 依赖：[Fabric API](https://modrinth.com/mod/fabric-api)、[Baritone（Meteor 版）](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.11-SNAPSHOT/)
- 命令全部在客户端处理，**不会发到服务器公聊**（交给 Baritone 的聊天拦截）
- HUD 面板只有自己可见

---

## 功能

- **多世界路线**：点位自动绑定记录时所在的世界（记录维度 id，兼容服务器自定义维度）；传送到别的世界会自动切换到该世界的路线，没有点位则停止并提示
- **自动找怪**：`!patrol hunt` 用视角射线锁定你准星前的那只怪并走过去（不限类型，任何活物都行）；`!patrol hunt auto` 持续搜索附近的怪，一只接一只走过去（移动到哪就重新寻路，怪物打死自动换下一只）
- **目标类型可配**：自动找怪默认只认原版敌对生物，插件服那种"拿豹猫/狼/村民套自定义模型"的怪不算，用 `!patrol hunt targets mob`（或 `all`）一行切换；`!patrol scan` 能列出附近的实体类型，帮你确认该配哪个
- **记住你要打的那种怪 + 白/黑名单三线并行**：①用准星 `!patrol hunt` 锁定一次，mod 就把这个类型记成"以后只打这种"（存进配置的 `huntTypes`）；②`!patrol hunt type` 随时手动改"只打某类"；③`!patrol hunt ignore` 把不想打的拉黑（存进 `huntIgnoreTypes`）。三者叠加生效，优先级：**黑名单 > 白名单 > `huntTargets` 粗过滤**（手动 `!patrol hunt` 视角锁定不受名单限制，指谁打谁）
- **不追技能特效**：`all` 会排除盔甲架这类装饰实体（技能模型/挂件基本都是隐形盔甲架），另外任何目标都要在客户端存活 ≥ `huntMinAliveSeconds`(默认 1 秒) 才可选——放技能时一闪而过的模型不会被当成怪，Baritone 也就不会被指到会消失的坐标上
- **循环巡逻**：走完最后一个点回到第一个，无限循环
- **打怪不误判**：附近有敌对怪时暂停"卡住/单点超时"计时——Baritone 在打怪时本来就会停下
- **卡住自愈**：连续若干秒没位移算卡住，先重试，仍卡住才跳过该点；单点另有总时长上限
- **实时 HUD**：屏幕顶部面板显示 `巡逻 3/6 → lb3`、距离、状态（行走中 / 附近有怪 / 卡住重试）和进度条；跳过、切换世界等自动事件以黄色小条提示，不刷聊天框
- **走不到不会卡死**：Baritone 算不出路径时（怪在墙后、地洞里、或目标方块站不住）会一直挂着 goal 反复重算还刷屏；本 mod 检测到"若干秒完全没位移"就重发 goto，试满次数仍过不去会**主动取消 goal**（Baritone 立刻安静）并把这只记进 30 秒冷却名单，自动模式换下一只
- **聊天栏 Tab 补全**：输入 `!pat` 按 Tab 补全命令，`del` / `start` 后面能补全点位名
- **配置热重载**：改完配置文件保存后 1 秒内自动生效，也可以 `!patrol reload` 手动重载

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
!patrol hunt            准星对着怪执行 → 记住它并走过去，同时记住「以后只打这种」
!patrol hunt auto       开关自动找怪：找最近的目标 → 走过去 → 打死后再找下一只
!patrol hunt type       改「只打某类」白名单：不带参数=用当前/上次锁定的那种，也可写 ocelot
!patrol hunt type clear 取消「只打某类」
!patrol hunt ignore     把某类拉黑（黑名单）：不带参数=把当前/上次锁定的那种拉黑，也可写 zombie
!patrol hunt ignore clear 清空黑名单
!patrol hunt targets mob  没设「只打某类」时，自动找怪认哪些实体：hostile(默认,原版敌对) / mob(所有生物) / all(除玩家和盔甲架外所有活物)
!patrol scan            列出附近活物的实体类型 + 会不会被锁定，用来排查自定义怪
!patrol hunt stop       停止找怪
```

> **想只打一种怪**：准星对着它 → `!patrol hunt`（mod 会自动把 `minecraft:ocelot` 这类类型记进配置的 `huntTypes`）→ `!patrol hunt auto`。
> 中途想换目标就再锁一次另一种（白名单为空时才会自动写入；要强制改随时用 `!patrol hunt type`）。

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
| `!patrol hunt` | 锁定视角前方的活物并走过去（不限类型），并记住「以后只打这种」 |
| `!patrol hunt auto` | 开关自动找怪（在目标范围内循环） |
| `!patrol hunt type [类型\|clear]` | 查看/修改「只打某类」白名单（会存进配置） |
| `!patrol hunt ignore [类型\|clear]` | 查看/修改黑名单，拉黑的类型自动找怪永不选（会存进配置） |
| `!patrol hunt targets <hostile\|mob\|all>` | 没设白名单时，自动找怪认哪些实体（会存进配置） |
| `!patrol scan` | 列出附近活物的实体类型，排查自定义怪 |
| `!patrol hunt stop` | 停止找怪 |
| `!patrol stop` | 停止巡逻 / 找怪 |
| `!patrol status` | 当前状态（含 mod 版本） |
| `!patrol reload` | 重新读取配置文件 |

聊天栏输入 `!pat` 按 **Tab** 可以补全这些命令；`!patrol del ` 或 `!patrol start ` 后按 Tab 会列出当前世界的点位名。

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
| `huntTypes` | `[]` | 只打这些实体类型（白名单，优先级最高）。`!patrol hunt` 锁定时会自动写入，也可手写 `["ocelot"]` |
| `huntIgnoreTypes` | `[]` | 任何模式下都排除的实体类型，如 `["minecraft:villager", "ocelot"]` |
| `combatFreezeSeconds` | `240` | 战斗中最多暂停计时多久 |
| `maxRetries` | `1` | 卡住后重试次数，用完才跳过 |
| `huntRange` | `48.0` | 找怪的射线长度 / 自动找怪半径 |
| `huntRetargetDistance` | `4.0` | 目标移动超过这个距离才重新下发 goto（调小会更贴怪，但 Baritone 消息更吵） |
| `huntMinRetargetIntervalSeconds` | `3.0` | 两次重发 goto 的最小间隔，防刷屏 |
| `huntArriveRadius` | `3.0` | 距目标多少格算"到了"，交给 KillAura |
| `huntStuckSeconds` | `10` | 多少秒完全没位移算"过不去" |
| `huntMaxAttempts` | `3` | 每个目标最多重试几次，用完就取消并跳过（自动模式换下一只） |
| `huntMinAliveSeconds` | `1.0` | 目标至少存活这么久才会被选（挡掉技能特效那种一闪而过的模型实体） |
| `hud` | `true` | 状态面板开关 |
| `hudPosition` | `top-center` | 也可 `top-left` |
| `hudWhenIdle` | `false` | 没巡逻时也显示面板 |
| `chatEvents` | `false` | 跳过/重试等事件是否也发聊天框 |
| `baritonePrefix` | `#` | Baritone 命令前缀（一般不用改） |

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
