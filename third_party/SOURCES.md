# Arotation 素材来源

获取日期：2026-09-26。没有自行绘制图标，没有重新调配色值。

## Tabler Icons
- 作者/项目：Paweł Kuna / Tabler。
- 官方仓库：https://github.com/tabler/tabler-icons
- 固定版本：`0239805680a36bab4e1070529b6744924402d804`。
- 使用：dots 更多菜单图标；快捷操作为 rotate-clockwise、arrow-left、home、apps（同一固定提交）。
  其余字形已在 2026-10-06 随“取消换图标”一起移除。
- 原始 SVG 位于 `tabler/`。从该提交的 `icons/outline/` 下载。
- 转换：保留原始 pathData、24×24 viewport、2px 圆头圆角描边；转换为 Android VectorDrawable。
  桌面 adaptive icon 的前景是应用自有的圆环几何，不使用 Tabler 素材。
- 许可：MIT，完整文本见 `Tabler-Icons-MIT.txt`；也随 APK 打包。

## Radix Colors
- 作者/项目：Modulz / WorkOS，Radix Colors。
- 官方仓库：https://github.com/radix-ui/colors
- 官方设计文档：https://www.radix-ui.com/colors/docs/palette-composition/understanding-the-scale
- 固定版本：`dbdb85470547c7d34b9001f48fddb08ded335979`。
- 使用：Slate / Blue，错误状态使用 Red；取自 `src/light.ts` 和 `src/dark.ts`。
- `radix-colors.json` 保存直接提取的亮/暗主题色阶。Android 资源中的色值与原值逐一一致。
- 角色映射：Slate 1 背景，Slate 12 正文，Slate 11 次级文字，Slate 6 分隔线，Blue 11 操作，Blue 3 选中背景，浅色选中文字使用 Blue 12，Red 11 错误。
- 许可：MIT，完整文本见 `Radix-Colors-MIT.txt`；也随 APK 打包。

两套资源都以静态文件使用；没有加入运行库或联网加载。
