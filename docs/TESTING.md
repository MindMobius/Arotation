# 测试

## 本机检查

```sh
python scripts/check-core.py
python scripts/check-assets.py
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

前两项只需要 JDK 和 Python 标准库。Windows 将 `./gradlew` 换成 `gradlew.bat`。
CI 执行这些检查，不伪称运行过模拟器。

## 原生设备断言

仅在独立的 Android 16 测试模拟器运行。测试会重置应用偏好，并修改旋转、授权与窗口状态。
不直接在日常使用的平板上执行这套自动化脚本。

```sh
adb -s emulator-5560 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell appops set de.xianmu.arotation SYSTEM_ALERT_WINDOW allow
adb -s emulator-5560 shell appops set de.xianmu.arotation WRITE_SETTINGS allow
adb -s emulator-5560 shell pm grant de.xianmu.arotation android.permission.POST_NOTIFICATIONS
adb -s emulator-5560 shell am instrument -w de.xianmu.arotation.test/de.xianmu.arotation.DeviceChecks
```

必须检查输出的 `TOTAL ... passed` 与 `FAIL`，不能只看 `am instrument` 的退出码。
测试使用 Android Framework Instrumentation，没有 JUnit 或 AndroidX。

## 发布包原样验证

`build-release.py --tests` 会使用同一发布签名生成 release 和 androidTest 两个 APK。
这样可以直接测试将要发布的 **非 debuggable** APK，不以调试包代替发布包验收。

```sh
python scripts/build-release.py --signing-config /private/arotation/signing.json --tests
adb -s emulator-5560 install -r app/build/outputs/apk/release/app-release.apk
adb -s emulator-5560 install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
```

后续授权和 Instrumentation 命令与上面相同。若模拟器安装过不同签名的调试包，先卸载这个测试应用再安装；不要改换正式签名来迁就测试。

## 补充端到端脚本

`check-device.py` 以及默认模式的 `check-startup.py` 面向 **debug** APK，因为进程恢复检查使用 `run-as`。正式包使用 `check-startup.py --release`：跳过需要 `run-as` 的进程结束/冷启动检查，其余快捷开关、授权、后台设置和锁屏流程仍实际执行。
每个脚本都先校验目标是模拟器。`ANDROID_HOME` 需指向本机 SDK。

```sh
python scripts/check-device.py --serial emulator-5560
python scripts/check-icons.py --serial emulator-5560
python scripts/check-startup.py --serial emulator-5560
# 测试正式签名、不可调试的 APK
python scripts/check-startup.py --serial emulator-5560 --release
```

`check-startup.py` 可通过 `--section tiles|permissions|background|lock` 单独运行某组检查。
该脚本按 1600×1000 / 200 dpi 平板、英文系统设置编写，应用界面为中文。
模拟器可覆盖横竖屏、三种大小、明暗主题、手势导航和无动画设置；真实厂商系统与触摸手感仍需要实机体验。
