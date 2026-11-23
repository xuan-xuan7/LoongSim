# 手动检查更新

入口：**帮助 → 检查更新**。仅在用户点击后联网，启动软件时不检查、不下载。

1. 读取 `xuan-xuan7/LoongSim` 的 GitHub Releases，显示当前版本、新版本和发布说明。
2. 用户点击「下载并安装」。LoongSim 将安装包下载到临时目录并显示进度，下载完成后核对大小和 SHA-256。
3. 校验通过后，LoongSim 检查打开的电路，提示保存修改。取消保存或退出时可以稍后点击「安装更新」。
4. LoongSim 退出后自动启动安装器，按需显示 Windows 权限提示和安装进度。安装失败时可以从更新窗口或错误提示取得安装包路径。

当前应用内下载面向 Windows x64。其他系统可检查版本并打开发布页面；没有适用的可校验安装包时，不提供下载按钮。更新窗口复用 LoongSim 图标，中英文提示均已提供。

## 发布要求

LoongSim 从公开的 GitHub Releases 匿名检查和下载安装包，无需 GitHub 账号。发布接口不可访问时，应用会显示读取失败。

- 使用递增的正式标签，例如 `v1.1.1`、`v1.2.0`。同时修改 `Main.LOONGSIM_VERSION_NAME` 、`logisim/loongsim_version.xml` 和 `logisim/gradle.properties`；Gradle 版本保留构建后缀，例如 `1.2.0-java11`。
- 读取最近最多 100 个 Releases，按三段数字版本比较。忽略草稿、预发布、`-logisim-only` 等旧分发标签；不把 `1.10.0` 错排在 `1.9.0` 前面。
- Windows 安装包沿用 `LoongSim-<版本>-master-windows-x64-setup-unsigned.exe`。也识别 `-setup.exe`、`-setup-signed.exe`；优先选择不带 `-unsigned` 后缀的包，文件名不代替代码签名验证。
- 上传完成的安装包必须带 GitHub 资产的 `sha256:` digest，或者同名 `.sha256` 文件。现有打包脚本已生成后者，校验文件记录单个 SHA-256 和完整安装包文件名。
- 发布说明使用 Release 的正文，作为纯文本展示；详细文档仍可通过其中的地址在发布页面阅读。
- 正式发布需使用比已发布版本更高的版本号。替换同版本安装包不会触发新版本提示。

检查和下载在后台执行，设有连接与读取超时，窗口可取消。请求只使用 HTTPS，下载只跟随 GitHub 及其资产域名的 HTTPS 跳转；安装包和发布页须属于指定仓库。LoongSim 在用户发起更新且安装包校验通过后启动安装。Windows 安装器显示安装进度，并保留系统权限提示。

GitHub API： [Releases 官方文档](https://docs.github.com/en/rest/releases/releases)。JSON 解析使用 Gson 2.15.0，Apache-2.0 许可证和来源声明已随应用资源包含，见 `resources/logisim/licenses/`。

## 验证记录（2026-11-03）

- 29 项更新功能测试通过。通过本地 HTTP 服务检查真实响应流，覆盖版本排序、旧标签过滤、中英文、正确下载、API digest 与 `.sha256`、校验错误、文件名不符、截断、超长、取消、现有文件保护、HTTPS 跳转、私有仓库、限流及损坏元数据；另检查断网、超时以及应用与安装包版本号一致。
- 全部非原生测试回归：211 项中 184 项通过，0 失败，27 项原生 FPGA 工具测试按默认配置跳过。本轮未修改 FPGA 后端，前一轮真实 FPGA 全流程结果见[测试报告](../testing/2026-11-03.md)。
- 实际菜单与联网检查通过：中文“帮助 → 检查更新”可打开窗口，使用 LoongSim 图标；私有仓库的匿名请求显示不可访问提示，不误报最新版。打包后的独立预览启动检查通过，JAR 与源码构建产物一致。
- 未执行真实安装升级；当前私有仓库不能匿名读取发布信息，公开后还需用递增版本的正式 Release 完成实际下载和升级验收。

本机开发测试证据在 `local/update-2026-11-03/`，含 JUnit 与 HTML 报告；当时的预览程序在 `dist/LoongSim-1.1.0-update-preview-20261103/LoongSim/LoongSim.exe`。本功能已纳入 [2026-11-03 的1.1.0安装包更新](../changelog/1.1.0.md)。
