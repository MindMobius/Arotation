# 签名与发布

## 签名材料

正式包使用独立的 release 密钥，不使用 Android debug key。
私钥和密码应一起离线备份，放在源码仓库之外；后续更新必须沿用同一把密钥。
GitHub Actions 只做调试构建和检查，不持有发布密钥。

本地配置格式如下，值仅为占位示例：

```json
{
  "storeFile": "arotation-release.p12",
  "storePassword": "<local-only>",
  "keyAlias": "arotation",
  "keyPassword": "<local-only>"
}
```

密钥路径可相对于这个配置文件。构建脚本通过子进程环境传入 Gradle，不将密码写入源码或日志。
脚本拒绝使用放在源码目录内部的签名配置。

## 编译与验签

```sh
python scripts/build-release.py --signing-config /private/arotation/signing.json --tests
```

需要缓存已齐全时，可增加 `--offline`。设置 `ANDROID_HOME`，并安装 Build Tools 36.0.0。
脚本执行 `assembleRelease`、`lintRelease` 和 `apksigner verify`；`--tests` 额外构建同签名设备测试包。
产物位于 `app/build/outputs/apk/release/app-release.apk`。

直接运行 Gradle 而不提供签名环境时会生成 unsigned release，不得把它当成可安装发布包上传。

## 发布顺序

1. 更新 `app/build.gradle` 中的 `versionCode` / `versionName` 和 `CHANGELOG.md`。
2. 运行算法、素材、Lint、构建和独立模拟器验收。发布包的包名应为 `de.xianmu.arotation`，`debuggable` 应为 false。
3. 对 APK 执行验签，记录证书指纹、文件 SHA-256 和验证结果。
4. 提交源码，确认工作区干净，推送 `main` 并等待 CI 通过。
5. 为该提交创建版本标签，例如 `v0.4.0`；发布 Release 时指定这个已有标签。
6. 上传签名 APK、SHA256SUMS、验证记录和对应提交的源码 ZIP；不上传密钥、密码、本机配置或调试测试 APK。
7. 从 GitHub 下载 APK，再比对 SHA-256 和签名，确认上传的文件就是验收过的文件。

GitHub CLI 可执行 `gh release create` 和 `gh release upload`。发布凭据使用开发者现有认证，不保存到项目。

公开的源码、标签和 Release 相互对应；APK 不放进 Git 历史。MIT 许可证及第三方素材许可随源码保留。
