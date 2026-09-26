# Arotation

> 给 Android 平板用的手动横竖屏切换按钮。

很多平板默认开启自动旋转。平时它很方便，但在床上侧躺、半躺，或者只是拿平板的姿势变了一点，屏幕就可能突然转向。接下来往往只能反复打开、关闭自动旋转，打断正在做的事。

Arotation 只解决这一个问题：**把横竖屏的决定权交给你。**

关闭系统自动旋转后，在屏幕上保留一个小悬浮按钮。想换方向时轻点一下；不想让它挡住内容时，把它拖到边缘，或者让它自动贴边、收起。

![Arotation 悬浮按钮](docs/images/overlay.png)

## 它能做什么

- **轻点切换横竖屏**：不需要打开设置，也不需要先唤醒按钮。
- **按住拖动**：把按钮放到顺手的位置。横屏和竖屏的位置会分别记住。
- **自动贴边和贴边收起**：减少对页面内容的遮挡；收起后轻点仍然直接旋转。
- **入口不和操作打架**：悬浮按钮的长按不会跳到设置，设置从桌面图标、系统快捷开关或通知进入。

它不是另一个“更聪明的自动旋转器”，也不是方向锁定开关；它就是一个随时可按的手动控制。

## 开始使用

1. 从 [Releases](https://github.com/MindMobius/Arotation/releases/latest) 下载 APK。
2. 安装后允许“悬浮显示”和“修改系统设置”。
3. 打开 Arotation，开启“悬浮按钮”。
4. 轻点悬浮按钮切换方向，按住它移动位置。

Arotation 面向 Android 16 及以上的平板。
`0.4.0` 使用新的个人项目包名 `de.xianmu.arotation`。如果你安装过旧预览版，它和当前版本是两个独立应用，需要重新授权。

## 给贡献者

README 只保留产品用途和使用方式。实现细节、设计约束、测试与发布流程放在 `docs/`：

- [架构与代码边界](docs/ARCHITECTURE.md)
- [设计与交互约束](docs/DESIGN.md)
- [测试说明](docs/TESTING.md)
- [签名与发布](docs/RELEASING.md)
- [0.4.0 验证记录](docs/releases/v0.4.0.md)

源码使用 Java + Android SDK 原生 View，无第三方运行库。完整素材来源与许可见 [third_party/SOURCES.md](third_party/SOURCES.md)。

## 许可

代码采用 [MIT License](LICENSE)。
