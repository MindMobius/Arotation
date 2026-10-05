# 代码边界

## 三个 Android 入口

- `MainActivity`：单页设置、申请系统授权、添加快捷开关、用户前台启动。
- `RotationService`：唯一持有悬浮窗口和旋转会话的组件。前台通知、锁屏隐藏、权限撤销退出、停止时恢复均由这里管理。
- `RotationTileService`：仅处理系统面板的显示和点击。不能另建一份浮层，也不直接改系统旋转。

同一应用进程，无额外进程、事件框架或依赖注入。`RotationService.STATE` 是仅发往本包的状态广播；接收器不向其他应用导出。

## 快捷开关

系统正常绑定的 TileService，不声明 `ACTIVE_TILE`，只在面板需要时监听状态广播；停止监听时释放，图标固定。
开关显示服务真实的 `running` 状态和实时权限，不能用持久化的“希望启动”状态冒充已运行。
正常点击直接启停前台服务；授权缺失时用 `startActivityAndCollapse(PendingIntent)` 到既有设置页。
若系统拒绝后台启动前台服务，也通过这个用户点击进入前台设置页再启动。
带锁屏时用 `unlockAndRun`，认证完成后才执行。删除磁贴不等于停用应用，长按只打开设置。

添加入口用 `StatusBarManager.requestAddTileService`，系统确认后才添加。取消、已添加或系统拒绝均不伪报成功；可手动从快捷设置编辑页添加。

## 后台运行

已有前台服务与 `START_STICKY` 保留。新引导用公开 API 查询电池豁免、后台限制和全局省电状态，再打开系统设置，由用户选择。
不申请电池白名单专用权限，不静默修改全局省电，不注册开机广播、不持有唤醒锁、无后台轮询。
厂商的自启动、最近任务锁定等没有统一公开设置接口；引导应用详情页，不内置易失效的厂商私有 Activity 名。
“电池优化：已排除”只代表查询到该项状态，不代表所有厂商策略已放行。

## 旋转与恢复

`rotation/RotationController` 是唯一系统旋转写入者；`RotationPolicy` 只做无 Android 依赖的计算。
恢复日志在写入前同步保存，正常停用恢复启用前设置；用户后来从系统改变的方向优先。
进程回收可以由 sticky 服务重建；用户在系统中“强行停止”不会执行恢复代码或保证重启，下次启动再处理日志。
丢失写设置权限时保留恢复日志，授权恢复后重试。不能承诺所有固定方向应用响应旋转请求。

## 悬浮交互

`OverlayHost` 持有悬浮球窗口、动作键窗口、手势状态与两个可取消的动画；`FloatingDot` 只负责圆点绘制。
球窗口固定为 56/64/72dp 方形（视觉圆 40/48/56dp），从不缩小、从不隐藏。
窗口仍为 NOT_FOCUSABLE / NOT_TOUCH_MODAL，只在控件自身的触摸矩形内声明返回手势排除区，避免拖动被返回手势抢走；不接管其余屏幕边缘。
通过公开 `setCanPlayMoveAnimation(false)` 关闭系统额外位移动画。位移和透明度共用一个时钟，从当前状态中断，不先跳到端点。按压缩放仅作用于绘制，不缩小触摸或无障碍边界。
按下取消闲置计时并原位提亮；超过位移阈值后直接跟随手指。按住超过系统长按时长后松手不点击，但仍允许继续拖动。
没有长按设置回调、定时器或无障碍 long-click action。两指、取消、锁屏、配置变化均中断点击；屏幕隐藏时停止动画。
闲置只把透明度降到用户设定值，不改尺寸、不隐藏；展开扇形时圆点左右不动，只在贴近上下边缘时纵向微调，收起后回到保存的位置。
旋转、主题或偏好变化会先取消在途动画并移除动作键窗口，再按新的几何重新落位，旧坐标不会被写回。横竖位置记忆仍独立。闲置无轮询、无常驻动画。

## 快捷操作与系统导航

`quick/QuickAction` 是四个动作的唯一目录：偏好位、名称、图标与对应系统动作。
`data/Prefs` 只保存一个 4 位掩码；未知位被丢弃，空掩码回落到旋转，且不接受关掉最后一个动作。
轻点圆点始终展开扇形菜单，不做单动作直达的特例：键心落在以圆点为圆心、球半径加 10dp 加键半径的圆周上，
按 60° 步进在朝向屏幕内侧的半圆上对称排布。每个动作键是独立的 52dp `TYPE_APPLICATION_OVERLAY` 小窗口，
透明区域不会吞掉触摸。每个动作键使用 `quick/QuickAction` 里固定的 Tabler 字形，没有图标偏好。
动作执行后按键收回圆点并移除窗口，再次轻点圆点、拖动、锁屏、转屏或 5 秒无操作都会收起。
`RotationService` 处理导航请求：桌面是普通的 `ACTION_MAIN` + `CATEGORY_HOME` 启动，依靠已可见的悬浮窗保留后台启动豁免，不需要任何额外权限。
`accessibility/NavigationAccessibilityService` 只调用 `performGlobalAction` 执行返回和最近任务。
它的配置明确 `canRetrieveWindowContent=false`、`canPerformGestures=false`、`canTakeScreenshot=false`、`isAccessibilityTool=false`，
不订阅事件、不注入手势、不读取窗口内容；未连接时只提示一次，不排队动作。
`system/NavigationAccess` 只打开系统无障碍设置，不写入设置，也不替用户开启服务。

系统在设置、权限页等敏感界面会主动隐藏非系统浮层（`HIDE_NON_SYSTEM_OVERLAY_WINDOWS`）。
悬浮球在这些界面不可见，离开后自动恢复；这是平台策略，不是应用可控行为。

## 界面、数据与素材

`overlay/` 管窗口、手势、定位；`ui/` 管 Radix 色值与后台引导；`data/Prefs` 封装小型偏好文件。
`quick/` 管动作目录，`accessibility/` 管最小系统导航服务，`system/` 管系统设置跳转。
只按责任分目录，不拆多模块，不为一个小工具增加抽象接口层。
个人项目包名、源码命名空间和私有广播统一使用 `de.xianmu.arotation`；偏好文件为 `arotation`。
桌面图标固定为圆点：`MainActivity` 是唯一 launcher 入口，没有 alias 与组件切换。

## 平台参考

实现核对本机 Android 36 SDK 源码中的 `TileService`、`StatusBarManager`、`PowerManager`、`ActivityManager` 与 `Settings`。
对应官方文档：
- https://developer.android.com/develop/ui/views/quicksettings-tiles
- https://developer.android.com/reference/android/service/quicksettings/TileService
- https://developer.android.com/reference/android/app/StatusBarManager
- https://developer.android.com/training/monitoring-device-state/doze-standby
