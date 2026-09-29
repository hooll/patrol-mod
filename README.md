# Patrol Mod

一个 Minecraft Fabric **客户端** mod：**多世界循环巡逻**，用 [Baritone](https://github.com/cabaletta/baritone) 自动寻路。走到点位自动切下一个，跑完一圈回到起点；打怪时自动暂停判定，卡住会重试/跳过；屏幕上有实时状态面板。

![build](https://github.com/hooll/patrol-mod/actions/workflows/build.yml/badge.svg)

- 目标版本：**Minecraft 1.21.11** + Fabric Loader ≥ 0.18.2
- 依赖：[Fabric API](https://modrinth.com/mod/fabric-api)、[Baritone（Meteor 版）](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.11-SNAPSHOT/)
- 命令全部在客户端处理，**不会发到服务器公聊**（交给 Baritone 的聊天拦截）
- HUD 面板只有自己可见

---

## 功能

- **多世界路线**：点位自动绑定记录时所在的世界（记录维度 id，兼容服务器自定义维度）；传送到别的世界会自动切换到该世界的路线，没有点位则停止并提示
- **循环巡逻**：走完最后一个点回到第一个，无限循环
- **打怪不误判**：附近有敌对怪时暂停"卡住/单点超时"计时——Baritone 在打怪时本来就会停下
- **卡住自愈**：连续若干秒没位移算卡住，先重试，仍卡住才跳过该点；单点另有总时长上限
- **实时 HUD**：屏幕顶部面板显示 `巡逻 3/6 → lb3`、距离、状态（行走中 / 附近有怪 / 卡住重试）和进度条；跳过、切换世界等自动事件以黄色小条提示，不刷聊天框
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

| 命令 | 说明 |
|---|---|
| `!patrol add <名字>` | 在当前位置记一个点（自动带当前世界） |
| `!patrol del <名字>` | 删除当前世界的点 |
| `!patrol list` | 列出所有点（按世界分组，标出当前世界） |
| `!patrol clear` | 清空当前世界的点 |
| `!patrol start [名字...]` | 开始循环巡逻（不带名字 = 当前世界全部点） |
| `!patrol stop` | 停止巡逻 |
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
| `combatFreezeSeconds` | `240` | 战斗中最多暂停计时多久 |
| `maxRetries` | `1` | 卡住后重试次数，用完才跳过 |
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
