# TerraFirmaEarth / 群峦地形移植

面向 `Minecraft 1.20.1` 的独立 TerraFirmaCraft（TFC）附属模组：把高版本 TFC 的
地形、气候、植物、作物与生物等内容移植 / 适配回 1.20，并新增与重做本模组自有
内容。本模组不修改 TFC、ImmersiveEngineering、Firmalife 等前置源码，全部通过
注册、Mixin、事件、数据包与资源包实现。

## 许可

`Licensed under the EUPL, Version 1.2.` 协议全文随仓库提供，亦可在
https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12 取得。

- 仓库整体按 **EUPL v1.2**（European Union Public Licence v1.2）发布：你可以自由
  使用、修改与再分发；再分发衍生作品时继续适用 EUPL v1.2，并保留署名与修改声明。
- 完整条款见 [LICENSE](LICENSE)；上游署名、移植清单与第三方例外见
  [NOTICE](NOTICE)；使用风险与责任范围见 [DISCLAIMER.md](DISCLAIMER.md)。

## 运行环境

- Minecraft `1.20.1`、Forge `47.1.3+`、Java `17`
- 必需前置：TerraFirmaCraft `1.20.x`
- 可选兼容：Another Forest Compat（`afc`）等，仅在运行期按反射 / 数据包交互

## 构建

- 离线构建：`gradlew.bat build --offline --no-daemon`（不要加 `clean`）
- 产物：`build/libs/Wildfire_TerraFirmaEarth-1.20.1-forge-<版本>.jar`，同时生成
  `-LICENSE.txt`、`-NOTICE.txt` 与 `-DISCLAIMER.md`
- 单元测试基线：21 个测试套件

## 文档

- 游戏内帕秋莉手册是玩法内容的唯一准确来源。
- [更新日志.md](更新日志.md)：按版本号记录的变更历史（先中文、后英文）。
- [数据包兼容写法教程.md](数据包兼容写法教程.md)：第三方数据包追加生物刷新、
  追加 fauna、协调 AFC 兼容的写法说明。

## English

An independent TerraFirmaCraft (TFC) addon for `Minecraft 1.20.1`: it ports and
adapts newer-version TFC terrain, climate, plant, crop and mob content back to
1.20, and adds and reworks a great deal of its own content. This mod does not
modify the source of TFC, ImmersiveEngineering or Firmalife; everything is
implemented through registration, Mixin, events, datapacks and resource packs.

### Licence

`Licensed under the EUPL, Version 1.2.` The full licence text is bundled with
the repository and is also available at
https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12 .

- The repository as a whole is distributed under the **EUPL v1.2** (European
  Union Public Licence v1.2): you may freely use, modify and redistribute it;
  redistributed derivative works remain under the EUPL v1.2 with attribution and
  modification notices preserved.
- See [LICENSE](LICENSE) for the full terms, [NOTICE](NOTICE) for upstream
  attribution, the port list and third-party exceptions, and
  [DISCLAIMER.md](DISCLAIMER.md) for usage risk and liability.

### Environment

- Minecraft `1.20.1`, Forge `47.1.3+`, Java `17`
- Required dependency: TerraFirmaCraft `1.20.x`
- Optional compatibility: Another Forest Compat (`afc`) and others, interacting
  only at runtime via reflection / datapacks

### Building

- Offline build: `gradlew.bat build --offline --no-daemon` (do not add `clean`)
- Artifact: `build/libs/Wildfire_TerraFirmaEarth-1.20.1-forge-<version>.jar`,
  together with `-LICENSE.txt`, `-NOTICE.txt` and `-DISCLAIMER.md`
- Unit test baseline: 21 test suites

### Documents

- The in-game Patchouli field guide is the only accurate source for gameplay
  content.
- [更新日志.md](更新日志.md): the changelog recorded by version number (Chinese
  first, then English).
- [数据包兼容写法教程.md](数据包兼容写法教程.md): how third-party datapacks add
  mob spawning, extend fauna and coordinate AFC compatibility.
