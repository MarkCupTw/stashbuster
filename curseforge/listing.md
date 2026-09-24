# CurseForge 上传资料 —— Stash Buster（储物克星）

| 文件 | 用途 |
|---|---|
| `logo-400x400.png` | 项目图标（CurseForge 必填，1:1 原图 PNG） |
| `LogoGen.java` | 图标生成脚本，改设计后重跑：`java -Djava.awt.headless=true LogoGen.java` |
| `listing.md` | 本文件：项目名、简介、正文、分类、上传步骤 |

要上传的文件本体：`../build/libs/stashbuster-1.0.0.jar`

> **上传前先看 §7 的同类模组说明** —— 这个赛道已经有两个同类模组了。

---

## 1. 基本字段

| 字段 | 值 |
|---|---|
| **Game** | Minecraft |
| **Project name** | `Stash Buster`（中文名《储物克星》可写在正文标题与 mcmod 条目里） |
| **Logo** | `logo-400x400.png` |
| **Class** | Mods |
| **Main category** | 建议 `Mobs` |
| **Additional categories** | 可加 `Storage`、`Server Utility` |
| **License** | MIT（或 Custom 后粘贴 `../LICENSE`） |
| **Experimental** | 不勾 |
| **Allow Comments** | 建议勾上 |

**Summary（英文）**
```
Creepers stop chasing you and start hunting your stash - each one picks the player-placed container holding the most items and blows it open.
```

**Summary（中文）**
```
苦力怕不再只追你，而是开始搜刮你的储物：挑出玩家放置的、物品最多的容器，走过去炸开。
```

---

## 2. 项目正文（英文，推荐粘贴）

```markdown
# Stash Buster

Creepers stop chasing you and start **hunting your storage**. A creeper scans the containers
*you* placed around it, picks the one holding the most items, walks over, and blows it open.

## What it does

- **Targets storage, not people.** Chests, trapped chests, barrels, shulker boxes, hoppers,
  dispensers, droppers, crafters, the whole furnace family, brewing stands, decorated pots,
  chiseled bookshelves - and **any container from any other mod** that implements the standard
  `Container` interface. Ender chests are excluded.
- **Picks the fullest container.** Score is `itemCount - 1.5 * distance`, so by default a full
  chest outweighs roughly 10 blocks of distance. It will ignore the empty chest beside it and
  cross the room for the loaded one.
- **Player-placed only.** Natural chests in villages, dungeons and ruins are untouched, loot or
  not. Placement is recorded per dimension and persisted in the save.
- **Players still come first.** The moment a creeper locks onto you it abandons the chest.
  Creative and spectator players are never targeted (vanilla behaviour) - but a creative player
  within 24 blocks *does* activate the behaviour.
- **Only within 24 blocks of a player.** No non-spectator player nearby, no raid. Walk out of
  range mid-approach and it gives up.
- **It is afraid of cats.** A cat or ocelot nearby makes it break off and flee - so a cat by
  your storage room actually protects it.
- **Contents still scatter.** Blowing up a container drops its contents; that is vanilla
  behaviour and this mod deliberately does not reimplement it.

## Configuration

Generated at `config/stashbuster-common.toml`:

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Master switch |
| `searchRadius` | `24` | Search radius, also used as the "player within N blocks" activation radius |
| `requireNearbyPlayer` | `true` | Require a nearby player (only spectators are excluded) |
| `minimumItemCount` | `1` | Containers below this item count are ignored; `0` lets it blow up empty ones |
| `scoreUpdateIntervalTicks` | `20` | Minimum interval between target re-evaluations |
| `excludedBlocks` | `["minecraft:ender_chest"]` | Block IDs that never get raided |
| `distancePenalty` | `1.5` | Score penalty per block of distance |
| `countMode` | `TOTAL_ITEMS` | Count stacked items, or occupied slots |

## Compatibility and performance

- **Server-side only.** No rendering, UI, keybinds or packets. Install on the server; for
  singleplayer nothing extra is needed.
- **No world scanning.** It only iterates the set of positions where a player placed a
  container, filters by distance arithmetically first, and only then reads container contents.
- **No custom explosions.** It just lights the vanilla fuse - blast radius, damage, sound and
  the creeper's own death all stay vanilla.
- **No duplicated logic.** Container loot dropping is left to vanilla.

## Requirements

- Minecraft **1.21.1**
- **NeoForge 21.1.x**
- Singleplayer or dedicated server
```

---

## 3. 项目正文（中文，可另开一段或贴在末尾）

```markdown
# 储物克星（Stash Buster）

苦力怕不再只是追着你跑 —— 它们会**主动去搜刮你的仓库**，挑物品最多的那个容器，走过去炸开。

## 它做什么

- **瞄准储物，不是瞄准人**：箱子、陷阱箱、木桶、潜影盒、漏斗、发射器、投掷器、合成器、
  熔炉系、酿造台、饰纹陶罐、雕纹书架，以及**其它 mod 的任何标准容器**。末影箱排除。
- **挑物品最多的**：打分公式 `物品数 - 1.5 × 距离`，默认满箱可以压过约 10 格距离差，
  它会绕过近处的空箱子，去炸远处装满的那个。
- **只认玩家放置的**：村庄、地牢、遗迹里原有的箱子不参与，哪怕有战利品。记录按维度持久化进存档。
- **玩家仍然优先**：一旦锁定你就立刻放弃箱子。创造与旁观玩家不会被锁定（原版行为），
  但创造模式玩家在 24 格内**会**激活这套行为。
- **玩家 24 格内才动手**（旁观不计）；中途你走出范围，它会放弃。
- **怕猫**：附近有猫或豹猫时会中断搜刮去逃跑 —— 在储物间拴只猫就能防住它。
- **内容物照常散落**：这是原版行为，本模组刻意不重复实现。

## 配置

生成于 `config/stashbuster-common.toml`，全部可调：`enabled`、`searchRadius`、
`requireNearbyPlayer`、`minimumItemCount`、`scoreUpdateIntervalTicks`、`excludedBlocks`、
`distancePenalty`、`countMode`。

## 兼容性与性能

- **纯服务端**：无渲染 / UI / 按键 / 网络包，装服务端即可，单机无需额外设置。
- **不扫描世界**：只遍历"玩家放置过容器的坐标集合"，先做纯算术距离淘汰，再读容器内容。
- **不自造爆炸**：只点燃原版引信，爆炸强度、范围、音效、自爆即死全部沿用原版。

## 需求

- Minecraft **1.21.1**
- **NeoForge 21.1.x**
- 单人存档或专用服务端均可
```

---

## 4. 首个版本的 changelog

```
1.0.0 - first release

- Creepers actively seek out the player-placed storage container holding the most items and blow it open
- Supports chests, trapped chests, barrels, shulker boxes, hoppers, dispensers, droppers, crafters, the furnace family, brewing stands, decorated pots, chiseled bookshelves, and any standard container from other mods
- Ender chests are excluded
- Only containers placed after installing the mod are tracked; records persist in the save
- Requires a non-spectator player within 24 blocks; survival players take priority over containers
- Creepers break off and flee when a cat or ocelot is nearby
- Fully configurable
```

---

## 5. 上传步骤

**流程顺序：CurseForge 要求项目先创建并通过审核，才能上传文件。**

1. 登录 CurseForge → 右上头像 → **Author Dashboard** → **Create Project**
2. 按 §1、§2 填字段，Logo 用本目录 `logo-400x400.png`
3. 提交后**进入审核**（自动 + 人工）。通过前传不了文件。
   结果与整改要求看 <https://www.curseforge.com/my-notifications>
4. 过审后 → 项目页 **Files** → **Upload File**：
   - 文件选 `../build/libs/stashbuster-1.0.0.jar`
   - **Release type**：`Release`
   - **Game version**：`1.21.1`
   - **Mod loader**：`NeoForge`
   - Changelog 用 §4
5. 文件同样要过一遍审核才公开。

---

## 6. 后续版本自动上传（可选）

需要两样东西：**Project ID**（项目页 URL 里的数字）和 **API Token**
（<https://legacy.curseforge.com/account/api-tokens> 创建）。之后可以在 `build.gradle`
里接 `net.darkhax.curseforgegradle`，把 token 放环境变量，`gradlew publishCurseforge` 一条命令上传。
**建议等你项目建好、拿到 ID 再接**，否则只能填占位符。

---

## 7. 同类模组（上传前务必了解）

同名或功能相近的已有项目：

| 模组 | 情况 |
|---|---|
| [[CS]智能爆破专家 (Creeper Super)](https://www.mcmod.cn/class/19893.html) | CurseForge；Forge 1.20.1 + NeoForge **1.21.1**；服务端需装；**半弃坑**；开源 |
| [智慧苦力怕 (Smart Creeper)](https://www.mcmod.cn/class/19954.html) | CurseForge / Modrinth / GitHub；Forge 1.12.2 + NeoForge **1.21.1**；**客户端与服务端都需装**；活跃但热度低；开源 |
| `chestseek` | Modrinth 上的 **Fabric 客户端**小工具，做的是"开箱子时搜索物品"的界面，**功能与本模组无关**，只是名字像。这也是本模组从 `ChestSeeker` 改名为 `Stash Buster` 的原因 |

**本模组的差异点**（两个同类模组都未提及）：

- **只认玩家放置的容器**，放置时记录并持久化 —— 村庄/地牢原有箱子不受影响
- **按物品数量排序**，挑最多的炸，而不是按方块类型锁定
- **怕猫** —— 保留原版逃跑行为（Smart Creeper 是反着做：遇猫进化成闪电苦力怕）
- **纯服务端**，客户端不需要装（Smart Creeper 客户端也要装）
- 仍在维护（Creeper Super 已半弃坑）

> ⚠️ 上表关于另外两个模组的描述**来自 MC百科的简介与 Modrinth API 的文字，我没有实测过它们**。
> 如果要在项目页写对比说明，请先自己实际安装确认，不要把这里的转述当依据。
