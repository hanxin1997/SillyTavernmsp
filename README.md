# SillyTavern 墨水屏浏览器客户端

这是一个面向电纸书的 Android 浏览器壳。它只负责安全保存登录信息、处理会话、提供文件和外链能力，以及对 WebView 做墨水屏优化；角色、聊天、群聊、世界书、预设、扩展和模型设置全部来自服务器上的完整 SillyTavern 网页。

## 使用方法

1. 在 SillyTavern 所在服务器上启动网页服务。远程电纸书访问时，需要在 `config.yaml` 中启用 `listen: true`，并把电纸书的 IP 或局域网网段加入 `whitelist`。
2. 优先使用 HTTPS。仅在可信家庭或局域网内使用 HTTP；客户端第一次连接私网 HTTP 会显示风险确认，并检查 DNS 解析结果是否仍为私网地址。
3. 安装 APK，首次打开填写服务器根地址、SillyTavern 用户名和密码，然后点击“连接并打开”。未启用 SillyTavern 用户账号时，用户名和密码可以留空。
4. 密码会使用 Android Keystore 加密保存。下次打开会先恢复网页 Cookie，Cookie 失效后自动重新登录，不需要再次输入。
5. 网页底部工具栏提供后退、前进、刷新、主页和设置。设置中可以选择关闭、平衡或白底黑字高对比模式，调整网页文字大小、配置应用内网页代理、整页刷新或清除登录信息。
6. 电纸书的 Page Up/Page Down 按键按固定视口翻页。文件选择、附件上传、下载、Basic Auth 和外部链接按 Android 系统权限处理。

## 服务器安全要求

- 不要把 SillyTavern 的 8000 端口直接暴露到公网。
- HTTPS 证书必须被 Android 系统信任；客户端不会忽略证书错误。
- 如果服务器额外启用了 HTTP Basic Auth，客户端会在网页请求时单独询问 Basic Auth 凭据，可选择加密记住；它与 SillyTavern 用户密码分开保存。
- 客户端不把密码注入网页脚本，也不会在日志、Intent 或普通配置中写入明文密码。

## 墨水屏兼容

默认“平衡模式”会禁用网页动画、过渡、平滑滚动、透明模糊和高成本阴影，同时保留网页控件、图片、扩展、音视频和流式生成。“白底黑字高对比”会进一步固定纸白背景、黑色文字和边框，并隐藏装饰背景，但不会隐藏聊天中的图片、视频或画布。

客户端会在受信任的 SillyTavern 页面开始加载时尽早注入本地样式，不会修改或同步服务器上的账号主题。旧版系统 WebView 不支持文档启动脚本时，会在页面加载完成后回退注入，因此可能短暂显示原网页颜色。通用 Android WebView 无法控制 BOOX、汉王或 Bigme 的硬件波形，因此物理全刷和残影效果必须在目标设备上验证。

客户端依赖设备上的 Android System WebView。过旧的 WebView 可能不支持 SillyTavern 使用的 ES Modules、Fetch 流、IndexedDB、WASM 或部分扩展；请先将系统 WebView 更新到设备能提供的最新版本。

## 应用内网页代理

连接页和网页设置均可配置无认证的 HTTP、HTTPS 或 SOCKS5 代理。常见本地 SOCKS5 可填写 `127.0.0.1:1080`。当前 SillyTavern 主机固定直连，网页中的外部图库、索引和媒体请求经过代理；这与客户端的登录会话链保持一致。

代理由 Android System WebView 的进程级代理接口提供。启用后必须等待系统确认才会加载网页；不支持该接口或配置错误时会阻止网页加载，不会静默改为直连。此设置不是系统 VPN，只影响本应用 WebView；系统浏览器、Android `DownloadManager` 和其他应用不会继承。

SillyTavern 服务器进程自己执行的扩展 Git 安装或更新不属于 Android WebView 流量，因此不受此客户端代理影响。本项目不会远程改写服务器代理配置。

## 构建 APK

本工程不提交 Gradle Wrapper，也不要求在开发机安装 Android SDK、Java、Gradle、ADB 或 Docker。可以在已经配置好 Android 工具链的机器上运行：

```powershell
$ErrorActionPreference = 'Stop'
gradle --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

仓库中的 GitHub Actions 会安装 JDK 17、Gradle 8.9、Android API 35 和 Build Tools 35.0.0，完成测试、Lint 和 APK 构建。电纸书真机上的输入法、文件选择、网页扩展、翻页、残影和硬件刷新仍需要单独验收。

## 说明

新版客户端不需要 `eink-companion` 服务端插件，也不会修改 SillyTavern 的网页源码。这样服务器升级网页后，客户端可以继续使用同一套完整功能；客户端本身只维护浏览器能力、应用内网页代理和墨水屏适配。
