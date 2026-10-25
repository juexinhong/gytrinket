# model-sources —— 模型源文件（不参与打包）

这里的 `geo/*.geo.json` 是 7 个构造体/方块模型**转换前的原始几何数据**
（Bedrock / GeckoLib 几何格式，Blockbench 可用 GeckoLib 插件打开编辑）。

- 它们**不是** mod 资源：`build.gradle` 的 resources 只包含 `src/main/resources`，
  所以这些文件不会进 jar；运行期也没有任何代码读取它们。
- 它们唯一的用途是喂给 `tools/model_convert/geo2vanilla.py`：
  改完这里 → 跑一遍脚本 → 重新生成 `src/main/java/.../*EntityModel.java`
  和重排后的贴图。
- 运行期**不需要 GeckoLib**：模型类已经是原生 `EntityModel` / `Model`，
  贴图也已经是原生盒式 UV 布局。

命名对应关系：

| geo 源文件 | 生成的模型类 | 渲染器 |
|---|---|---|
| `drone.geo.json` | `DroneEntityModel` | `DroneRenderer` |
| `assaultdrone.geo.json` | `AssaultDroneEntityModel` | 同上（按变体切换） |
| `defense_drone.geo.json` | `DefenseDroneEntityModel` | 同上（按变体切换） |
| `swarm.geo.json` | `SwarmEntityModel` | `SwarmRenderer` |
| `armor_fragment.geo.json` | `ArmorFragmentEntityModel` | `ArmorShardRenderer` |
| `drone_beam.geo.json` | `DroneBeamEntityModel` | `DroneBeamRenderer` |
| `light_point_core_block_gy.geo.json` | `LightPointCoreBlockModel` | `LightPointCoreBlockRenderer` / `LightPointCoreItemRenderer` |

如果真的不再需要回改模型，这一整个目录可以直接删（模型类本身已提交在源码里）。
