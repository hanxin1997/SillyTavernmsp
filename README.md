# SillyTavern 墨水屏浏览器客户端

这是一个面向电纸书的 Android 浏览器壳。它只负责安全保存登录信息、处理会话、提供文件和外链能力，以及对 WebView 做墨水屏优化；角色、聊天、群聊、世界书、预设、扩展和模型设置全部来自服务器上的完整 SillyTavern 网页。

## 使用方法

1. 在 SillyTavern 所在服务器上启动网页服务。远程电纸书访问时，需要在 `config.yaml` 中启用 `listen: true`，并把电纸书的 IP 或局域网网段加入 `whitelist`。
2. 优先使用 HTTPS。仅在可信家庭或局域网内使用 HTTP；客户端第一次连接私网 HTTP 会显示风险确认，并检查 DNS 解析结果是否仍为私网地址。
3. 安装 APK，首次打开填写服务器根地址、SillyTavern 用户名和密码，然后点击“连接并打开”。未启用 SillyTavern 用户账号时，用户名和密码可以留空。
4. 密码会使用 Android Keystore 加密保存。下次打开会先恢复网页 Cookie，Cookie 失效后自动重新登录，不需要再次输入。
5. 网页底部工具栏提供后退、前进、刷新、主页和设置。设置中可以关闭墨水模式、调整网页文字大小、整页刷新或清除登录信息。
6. 电纸书的 Page Up/Page Down 按键按固定视口翻页。文件选择、附件上传、下载、Basic Auth 和外部链接按 Android 系统权限处理。

## 服务器安全要求

- 不要把 SillyTavern 的 8000 端口直接暴露到公网。
- HTTPS 证书必须被 Android 系统信任；客户端不会忽略证书错误。
- 如果服务器额外启用了 HTTP Basic Auth，客户端会在网页请求时单独询问 Basic Auth 凭据，可选择加密记住；它与 SillyTavern 用户密码分开保存。
- 客户端不把密码注入网页脚本，也不会在日志、Intent 或普通配置中写入明文密码。

## 墨水屏兼容

默认“平衡模式”会禁用网页动画、过渡、平滑滚动、透明模糊和高成本阴影，同时保留网页控件、图片、扩展、音视频和流式生成。通用 Android WebView 无法控制 BOOX、汉王或 Bigme 的硬件波形，因此物理全刷和残影效果必须在目标设备上验证。

客户端依赖设备上的 Android System WebView。过旧的 WebView 可能不支持 SillyTavern 使用的 ES Modules、Fetch 流、IndexedDB、WASM 或部分扩展；请先将系统 WebView 更新到设备能提供的最新版本。

## 构建 APK

本工程不提交 Gradle Wrapper，也不要求在开发机安装 Android SDK、Java、Gradle、ADB 或 Docker。可以在已经配置好 Android 工具链的机器上运行：

```powershell
$ErrorActionPreference = 'Stop'
gradle --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

仓库中的 GitHub Actions 会安装 JDK 17、Gradle 8.9、Android API 35 和 Build Tools 35.0.0，完成测试、Lint 和 APK 构建。电纸书真机上的输入法、文件选择、网页扩展、翻页、残影和硬件刷新仍需要单独验收。

## 说明

新版客户端不需要 `eink-companion` 服务端插件，也不会修改 SillyTavern 的网页源码。这样服务器升级网页后，客户端可以继续使用同一套完整功能；客户端本身只维护浏览器能力和墨水屏适配。
