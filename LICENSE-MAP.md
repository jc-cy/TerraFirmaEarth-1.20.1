# TerraFirmaEarth License Map / 群峦地球协议分类表

Copyright (c) 2026 交错次元 and contributors.

## 中文

本文件只记录本项目随源码 / 构建产物分发的主要代码、数据与资源的协议分类。它的
目的很窄：区分哪些内容必须继续遵守 TFC 上游协议，哪些属于本项目作者原创实现并
按 ARR 处理。

### 分类原则

只有能识别出复制、改写或结构性派生自 TerraFirmaCraft (TFC) 表达的文件或片段，
才归入 `EUPL v1.2` 或 `CC BY-SA 4.0`。

仅调用 TFC API、面向 TFC 运行时行为、注入 TFC 方法，或独立实现兼容效果的代码，
按作者原创实现处理。

简要口径：

- TFC 派生代码：`EUPL v1.2`，或上游许可允许的更高版本。
- TFC 派生资源与数据：`CC BY-SA 4.0`。
- 本项目作者原创代码、原创兼容层、自有运行时逻辑、自有注册 / 配置 / 桥接实现：
  `All Rights Reserved`。

### A. 必须遵守上游协议的 TFC 派生部分

这些路径包含复制、改写或结构性派生自 TFC 源码、生成数据、资源或数据结构的内容，
继续适用对应的上游兼容协议。

| 路径 | 分类 | 依据 |
|---|---|---|
| `src/main/resources/data/tfc/**` | 非代码数据按 `CC BY-SA 4.0`；软件式生成 / 配置代码按 `EUPL v1.2` | TFC 命名空间数据、worldgen、fauna、tags、recipes、Patchouli 数据或同路径覆盖，来源于或派生自 TFC 1.20.x / 1.21.x 数据。 |
| `src/main/resources/assets/tfc/**` | `CC BY-SA 4.0` | TFC 命名空间资源、模型、blockstate、贴图、语言文件、手册资源，来源于或改写自 TFC 资源。 |
| `src/main/resources/assets/tfe/textures/colormap/**` | `CC BY-SA 4.0` | 从 TFC 1.21 复制的色图资源（1.21 版 `foliage.png`），供移植后的季节叶色算法读取；TFC 1.21 已替换该图并给旧图改名为 `foliage_evergreen.png`。 |
| `src/main/resources/tfe_server_data_overrides/data/tfc/**` | `CC BY-SA 4.0` | 内置服务端数据包中的 TFC worldgen 同路径覆盖，来源于 TFC 数据与 AFC 兼容需求。 |
| `src/main/resources/tfe_server_data_overrides/data/tfe/worldgen/configured_feature/tree/afc/**` 与同包内 `data/tfe/tags/worldgen/configured_feature/forest_121_trees.json` | `CC BY-SA 4.0` | 内置服务端数据包中的 AFC 树 entry 适配数据，结构性派生自 TFC / AFC worldgen entry 并用于 AFC 兼容。 |
| `src/main/resources/data/minecraft/**` | 若派生自 TFC tag 组合则按 `CC BY-SA 4.0`；纯原创 tag 追加按 `All Rights Reserved` | Minecraft 命名空间兼容 tag，匹配或扩展 TFC 数据包结构。 |
| `src/main/resources/data/forge/**` | 若派生自 TFC tag 组合则按 `CC BY-SA 4.0`；纯原创 tag 追加按 `All Rights Reserved` | Forge 命名空间兼容 tag，匹配或扩展 TFC 数据包结构。 |
| 明确复制或结构性翻译 TFC 源码表达的 Java 文件 / 片段 | 对应复制 / 派生片段按 `EUPL v1.2` | Java 目录默认不整体归入此类；以具体文件 / 片段证据为准。 |
| `src/main/java/com/newterraearth/tfe/api/climate/NTE121SolarCalculator.java` | `EUPL v1.2` | 定向移植 TFC 1.21 `SolarCalculator` 与 `SkyPos` 的太阳位置、太阳时和角度数据表达。 |
| `src/main/java/com/newterraearth/tfe/common/entity/ai/NTERammingPreyAi.java`、`NTEPrepareRamNearestTarget.java`、`NTERamTarget.java` | `EUPL v1.2` | 结构性移植 TFC 1.21 `RammingPrey` 冲撞脑、冲撞准备和命中行为到 1.20 Forge；保留上游 EUPL 许可声明。 |
| `src/main/java/com/newterraearth/tfe/client/model/entity/NTEArcticCharModel.java`、`NTEBurbotModel.java`、`NTEMuksunModel.java`、`NTENorthernPikeModel.java`、`NTEPacuModel.java`、`NTEPeacockBassModel.java`、`NTERedPiranhaModel.java`、`NTESpottedGudgeonModel.java`、`NTETilapiaModel.java`、`NTEBactrianCamelModel.java` | `EUPL v1.2` | 结构性移植 TFC 4.2.9 的专用鱼类 / 双峰骆驼模型表达到 1.20 Forge；仅做包名、实体类型和动画 API 兼容调整。 |
| `src/main/java/com/newterraearth/tfe/client/animation/NTEBactrianCamelAnimation.java`、`src/main/java/com/newterraearth/tfe/client/render/entity/NTEBactrianCamelRenderer.java` | `EUPL v1.2` | 定向移植 TFC 4.2.9 双峰骆驼的六组状态动画与专用纹理选择 renderer；按 1.20 渲染签名适配。 |
| `src/main/java/com/newterraearth/tfe/common/block/rope/**`、`src/main/java/com/newterraearth/tfe/common/item/NTERopeItem.java`、`src/main/java/com/newterraearth/tfe/common/entity/misc/NTERopeKnot.java` | 结构性派生部分按 `EUPL v1.2`；独立的 1.20 兼容 glue 按作者原创处理 | 定向移植 TFC 4.2.9 climbing rope 方块、物品、锚点和绳结行为到 1.20 Forge。 |
| `src/main/java/com/newterraearth/tfe/world/surface/NormalAlternateSurfaceBuilder.java` | `EUPL v1.2` | 结构性移植 TFC 4.2.9 `NormalAlternateSurfaceBuilder` 的河谷普通地表分支，并替换为当前 1.20 的本地表面状态。 |

### B. 作者原创 ARR 实现

这些路径是本项目作者原创实现，按 ARR 处理；其中若有明确复制 / 结构性
派生自 TFC 表达的片段，则仅该片段按 A 类处理。

| 路径 | 分类 | 依据 |
|---|---|---|
| `src/main/java/com/newterraearth/tfe/common/**` | `All Rights Reserved` | 本项目自有方块、物品、流体、实体注册与 block entity 实现。 |
| `src/main/java/com/newterraearth/tfe/api/**`（上一节单列的 TFC 派生文件除外） | `All Rights Reserved` | 本项目公开扩展接口、注册调度与兼容 API。 |
| `src/main/java/com/newterraearth/tfe/client/**` | `All Rights Reserved` | 本项目客户端缓存、气候显示、地图兼容与渲染辅助逻辑。 |
| `src/main/java/com/newterraearth/tfe/client/model/**` | `All Rights Reserved` | 本项目客户端模型适配与动态模型加载逻辑。 |
| `src/main/java/com/newterraearth/tfe/compat/**` | `All Rights Reserved` | 本项目对可选前置 / 兼容模组的反射与兼容逻辑。 |
| `src/main/java/com/newterraearth/tfe/config/**` | `All Rights Reserved` | 本项目配置项与开关。 |
| `src/main/java/com/newterraearth/tfe/debug/**` | `All Rights Reserved` | 本项目按系统属性启用的运行时诊断入口与本地复现辅助逻辑；默认关闭，不属于 TFC 上游表达复制。 |
| `src/main/java/com/newterraearth/tfe/event/**` | `All Rights Reserved` | 本项目事件入口与内置数据包注入逻辑。 |
| `src/main/java/com/newterraearth/tfe/network/**` | `All Rights Reserved` | 本项目网络同步与数据包逻辑。 |
| `src/main/java/com/newterraearth/tfe/world/**` | `All Rights Reserved` | 本项目地形、气候、季节、海洋、森林、河流、火山、植物、作物、刷怪与世界生成兼容实现；多数为 1.20 侧新增桥接 / 运行时逻辑。 |
| `src/main/java/com/newterraearth/tfe/mixin/**` | `All Rights Reserved` | 本项目 mixin 注入、访问器、重定向与兼容 glue。注入 TFC 方法本身不等于复制 TFC 表达。 |
| `src/main/java/com/newterraearth/tfe/NewTerraEarthMod.java` | `All Rights Reserved` | 本项目主类与初始化入口。 |
| `src/test/java/com/newterraearth/tfe/**` | `All Rights Reserved` | 本项目原创单元测试与世界生成不变量验证。 |
| `src/main/resources/tfe.mixins.json` | `All Rights Reserved` | 本项目 mixin 配置。 |
| `src/main/resources/META-INF/mods.toml` | 项目自写描述文本 `All Rights Reserved`；第三方名称与商标归各自权利人 | 本项目元数据与发布说明。 |
| `src/main/resources/pack.mcmeta` | `All Rights Reserved` | 本项目资源包元数据。 |
| `src/main/resources/tfe_server_data_overrides/pack.mcmeta` | `All Rights Reserved` | 本项目内置数据包元数据。 |
| `src/main/resources/data/tfe/**` | `All Rights Reserved`，但由 TFC 数据改名 / 改命名空间得到的文件按 A 类处理 | 本项目 `tfe` 命名空间数据、注册数据、配方、标签、worldgen 与兼容资源。 |
| `src/main/resources/assets/tfe/**` | `All Rights Reserved`，但由 TFC 资源改名 / 改命名空间得到的文件按 A 类处理 | 本项目 `tfe` 命名空间模型、blockstate、贴图与语言资源。 |
| `src/main/resources/data/c/**` | `All Rights Reserved` | 本项目新增的 common tag 兼容数据。 |

### 第三方材料

除 TerraFirmaCraft 派生材料、Gradle wrapper 组件与常规构建 / 运行期依赖外，本
分类表当前不单独列出其他第三方源码或资源。若以后加入单独许可的第三方文件，应
保留其原始许可声明。

本轮例外：`NTEArmadilloModel.java`、`assets/tfc/textures/entity/animal/armadillo.png`
及 `assets/tfc/textures/item/armadillo_scute.png`
来自本地 Minecraft 1.21.1 客户端模型 / 贴图的 1.20 兼容适配，不属于 TFC 派生
材料；其来源和分发边界按 Minecraft 官方条款处理。

---

## English

This file only records the license classification of the main code, data, and
resources distributed with this project. Its purpose is narrow: to separate the
parts that must remain under upstream TFC-compatible licenses from the
author-original implementation classified as ARR.

### Classification Rule

Only files or portions with identifiable copied, adapted, or structurally
derived TerraFirmaCraft (TFC) expression are classified as `EUPL v1.2` or
`CC BY-SA 4.0`.

Original compatibility code that uses TFC APIs, targets TFC runtime behavior,
injects into TFC methods, or independently implements compatibility behavior is
treated as author-original implementation.

In short:

- TFC-derived code: `EUPL v1.2`, or any later version where the upstream license
  permits.
- TFC-derived assets and data: `CC BY-SA 4.0`.
- Author-original code, compatibility layers, runtime logic, registration /
  configuration / bridge implementations: `All Rights Reserved`.

### A. TFC-Derived Portions That Must Follow Upstream Licenses

These paths contain files copied from, adapted from, or structurally derived
from TFC source code, generated data, resources, or data structure. They remain
under the corresponding upstream-compatible license.

| Path | Classification | Basis |
|---|---|---|
| `src/main/resources/data/tfc/**` | `CC BY-SA 4.0` for non-code data; `EUPL v1.2` where a file is software-like generated/configuration code | TFC namespace data, worldgen, fauna, tags, recipes, Patchouli data, or same-path overrides derived from TFC 1.20.x / 1.21.x data. |
| `src/main/resources/assets/tfc/**` | `CC BY-SA 4.0` | TFC namespace assets, models, blockstates, textures, language entries, and field guide resources copied or adapted from TFC resources. |
| `src/main/resources/assets/tfe/textures/colormap/**` | `CC BY-SA 4.0` | Colormap assets copied from TFC 1.21 (the 1.21 `foliage.png`), read by the ported seasonal foliage algorithm; TFC 1.21 replaced that image and renamed the old one to `foliage_evergreen.png`. |
| `src/main/resources/tfe_server_data_overrides/data/tfc/**` | `CC BY-SA 4.0` | Built-in server-data overrides of TFC worldgen paths, derived from TFC data and AFC compatibility needs. |
| `src/main/resources/tfe_server_data_overrides/data/tfe/worldgen/configured_feature/tree/afc/**` and the same pack's `data/tfe/tags/worldgen/configured_feature/forest_121_trees.json` | `CC BY-SA 4.0` | Built-in server-data AFC tree-entry adapter data, structurally derived from TFC / AFC worldgen entries for AFC compatibility. |
| `src/main/resources/data/minecraft/**` | `CC BY-SA 4.0` where derived from TFC tag composition; otherwise `All Rights Reserved` for purely original tag additions | Minecraft namespace compatibility tags matching or extending TFC data-pack structure. |
| `src/main/resources/data/forge/**` | `CC BY-SA 4.0` where derived from TFC tag composition; otherwise `All Rights Reserved` for purely original tag additions | Forge namespace compatibility tags matching or extending TFC data-pack structure. |
| Java files / portions clearly copied from or structurally translated from TFC source expression | `EUPL v1.2` for the copied/derived portions | No whole Java path is classified here by default; classification follows concrete file / portion evidence. |
| `src/main/java/com/newterraearth/tfe/api/climate/NTE121SolarCalculator.java` | `EUPL v1.2` | Targeted port of the solar-position, sun-based-time, and angle data expressions from TFC 1.21 `SolarCalculator` and `SkyPos`. |
| `src/main/java/com/newterraearth/tfe/common/entity/ai/NTERammingPreyAi.java`, `NTEPrepareRamNearestTarget.java`, `NTERamTarget.java` | `EUPL v1.2` | Structural port of the TFC 1.21 `RammingPrey` brain, ram preparation, and impact behaviors to the 1.20 Forge runtime; upstream EUPL notices are retained. |
| `src/main/java/com/newterraearth/tfe/client/model/entity/NTEArcticCharModel.java`, `NTEBurbotModel.java`, `NTEMuksunModel.java`, `NTENorthernPikeModel.java`, `NTEPacuModel.java`, `NTEPeacockBassModel.java`, `NTERedPiranhaModel.java`, `NTESpottedGudgeonModel.java`, `NTETilapiaModel.java`, `NTEBactrianCamelModel.java` | `EUPL v1.2` | Structural ports of the TFC 4.2.9 dedicated fish / Bactrian camel model expressions to 1.20 Forge; only package, entity-type, and animation API compatibility changes were made. |
| `src/main/java/com/newterraearth/tfe/client/animation/NTEBactrianCamelAnimation.java`, `src/main/java/com/newterraearth/tfe/client/render/entity/NTEBactrianCamelRenderer.java` | `EUPL v1.2` | Targeted ports of the TFC 4.2.9 Bactrian camel's six state animations and dedicated texture-selecting renderer, adapted to the 1.20 render signatures. |
| `src/main/java/com/newterraearth/tfe/common/block/rope/**`, `src/main/java/com/newterraearth/tfe/common/item/NTERopeItem.java`, `src/main/java/com/newterraearth/tfe/common/entity/misc/NTERopeKnot.java` | `EUPL v1.2` for structurally derived portions; independent 1.20 compatibility glue remains author-original | Targeted ports of TFC 4.2.9 climbing-rope block, item, anchor, and knot behavior to the 1.20 Forge runtime. |
| `src/main/java/com/newterraearth/tfe/world/surface/NormalAlternateSurfaceBuilder.java` | `EUPL v1.2` | Structural port of TFC 4.2.9 `NormalAlternateSurfaceBuilder` for the river-valley normal-surface branch, adapted to the local 1.20 surface states. |

### B. Author-Original ARR Implementation

These paths are author-original implementation for this project and are
classified as ARR. If a specific portion is clearly copied from or structurally
derived from TFC expression, only that portion follows section A.

| Path | Classification | Basis |
|---|---|---|
| `src/main/java/com/newterraearth/tfe/common/**` | `All Rights Reserved` | Project-owned blocks, items, fluids, entity registration, and block entity implementation. |
| `src/main/java/com/newterraearth/tfe/api/**` (except the TFC-derived file listed above) | `All Rights Reserved` | Project-owned public extension interfaces, dispatch registration, and compatibility APIs. |
| `src/main/java/com/newterraearth/tfe/client/**` | `All Rights Reserved` | Project-owned client cache, climate display, map compatibility, and render helper logic. |
| `src/main/java/com/newterraearth/tfe/client/model/**` | `All Rights Reserved` | Project-owned client model adaptation and dynamic model loading logic. |
| `src/main/java/com/newterraearth/tfe/compat/**` | `All Rights Reserved` | Project-owned optional dependency and compatibility-mod integration logic. |
| `src/main/java/com/newterraearth/tfe/config/**` | `All Rights Reserved` | Project-owned configuration keys and switches. |
| `src/main/java/com/newterraearth/tfe/debug/**` | `All Rights Reserved` | Project-owned property-gated runtime diagnostics and local reproduction helpers; disabled by default and not copied TFC upstream expression. |
| `src/main/java/com/newterraearth/tfe/event/**` | `All Rights Reserved` | Project-owned event entry points and built-in data-pack injection logic. |
| `src/main/java/com/newterraearth/tfe/network/**` | `All Rights Reserved` | Project-owned network sync and packet logic. |
| `src/main/java/com/newterraearth/tfe/world/**` | `All Rights Reserved` | Project-owned terrain, climate, season, ocean, forest, river, volcano, plant, crop, spawn, and worldgen compatibility implementation; much of it is new 1.20-side bridge/runtime logic. |
| `src/main/java/com/newterraearth/tfe/mixin/**` | `All Rights Reserved` | Project-owned mixin injections, accessors, redirects, and compatibility glue. Injecting into TFC methods is not itself copied TFC expression. |
| `src/main/java/com/newterraearth/tfe/NewTerraEarthMod.java` | `All Rights Reserved` | Project main class and initialization entry point. |
| `src/test/java/com/newterraearth/tfe/**` | `All Rights Reserved` | Project-owned unit tests and world-generation invariant checks. |
| `src/main/resources/tfe.mixins.json` | `All Rights Reserved` | Project mixin configuration. |
| `src/main/resources/META-INF/mods.toml` | Project description text is `All Rights Reserved`; third-party names and marks remain with their owners | Project metadata and release description. |
| `src/main/resources/pack.mcmeta` | `All Rights Reserved` | Project resource-pack metadata. |
| `src/main/resources/tfe_server_data_overrides/pack.mcmeta` | `All Rights Reserved` | Project built-in pack metadata. |
| `src/main/resources/data/tfe/**` | `All Rights Reserved`, except files renamed / re-namespaced from TFC data, which follow section A | Project `tfe` namespace data, registration data, recipes, tags, worldgen, and compatibility resources. |
| `src/main/resources/assets/tfe/**` | `All Rights Reserved`, except files renamed / re-namespaced from TFC assets, which follow section A | Project `tfe` namespace models, blockstates, textures, and language resources. |
| `src/main/resources/data/c/**` | `All Rights Reserved` | Common tag compatibility data added for this project. |

### Third-Party Materials

This map does not separately list third-party files beyond TerraFirmaCraft-
derived materials, Gradle wrapper components, and normal build/runtime
dependencies. If separately licensed third-party files are added later, their
original license notices should be preserved.

Exception for this task: `NTEArmadilloModel.java`,
`assets/tfc/textures/entity/animal/armadillo.png`, and
`assets/tfc/textures/item/armadillo_scute.png` are 1.20 compatibility adaptations
of the local Minecraft 1.21.1 client model/texture, not TFC-derived material;
their source and distribution boundary follows Minecraft's official terms.
