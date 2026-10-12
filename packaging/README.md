# 安装包与代码签名

## 构建产物

- Windows x64：EXE 安装向导和 MSI 安装包，内置 Java 17 运行时。
- Apple Silicon Mac：arm64 DMG，内置对应架构的 Java 17 运行时。
- Intel Mac：x64 DMG，内置对应架构的 Java 17 运行时。
- 每个安装包附带 SHA256 校验文件，构建清单记录源码提交和 JAR 摘要。

## 本机构建

Windows 使用仓库根目录的 `package-windows.ps1`。本机需 Java 11（编译）、Java 17（打包）和 WiX 3；缺少便携工具时脚本会下载到忽略的 `local/tools/`。

```powershell
.\package-windows.ps1
```

使用已有 Windows 证书库中的代码签名证书：

```powershell
.\package-windows.ps1 -CertificateThumbprint '<证书指纹>' -RequireSignature
```

- 支持证书库中由硬件或密钥存储提供私钥的证书，不要求导出私钥。
- 先签名应用启动器和 MSI，再嵌入 EXE，随后签名 Burn 引擎及外层 EXE。
- 所有 Windows 签名使用 SHA256、RFC 3161 时间戳，并检查 Authenticode 信任链和时间戳。

Mac 必须在相应架构的 macOS 主机上构建：

```bash
export JAVA_HOME=/path/to/jdk17
bash scripts/package-macos.sh logisim/build/libs/LoongSim-1.0.0-java11-all.jar
```

缺少签名凭据的产物在文件名中标为 `unsigned`，可作为未签名版本发布，Release 必须注明签名状态。

## GitHub Actions

`.github/workflows/installers.yml` 在 `logisim-only` 的相关文件推送时构建三个平台，共用同一份 Java 11 字节码 JAR。首次构建不需要签名凭据，产物保存在 Actions 的 Artifacts 中。

正式签名需要在仓库 Actions Secrets 中配置：

| Secret | 用途 |
|---|---|
| `WINDOWS_CERTIFICATE_PFX_BASE64` | 可在 CI 使用的 Windows 代码签名证书及私钥；仅适用于签名提供方允许导出的证书 |
| `WINDOWS_CERTIFICATE_PASSWORD` | Windows 证书密码 |
| `MACOS_CERTIFICATE_P12_BASE64` | Apple Developer ID Application 证书及私钥 |
| `MACOS_CERTIFICATE_PASSWORD` | Apple 证书密码 |
| `MACOS_NOTARY_KEY_BASE64` | Apple 公证 API 的 `.p8` 密钥 |
| `MACOS_NOTARY_KEY_ID` | Apple API Key ID |
| `MACOS_NOTARY_ISSUER_ID` | Apple API Issuer ID |

Windows 的云签名服务或硬件证书可通过自托管 Windows runner 和 `-CertificateThumbprint` 接入；不要为了 CI 导出签名提供方禁止导出的私钥。

配置好 Secrets 后，将仓库 Actions Variable `LOONGSIM_SIGNED_BUILDS` 设置为 `true`，重新运行安装包流程。也可在工作流可手动运行时选择 `signed=true`。

- Mac 构建在临时钥匙串中导入身份，采用 Hardened Runtime 签名；应用与 DMG 分别提交 Apple 公证，并装订公证票据。
- 签名版本必须通过 Windows 签名验证、两个 Mac 的公证与装订，才允许发布。
- 暂不签名时，手动运行工作流选择 `signed=false` 和 `publish_unsigned=true`，发布带 `unsigned` 文件名的安装包。直接调用发布脚本需要设置 `ALLOW_UNSIGNED_RELEASE=true`；版本来源、三平台 JAR 一致性、校验文件和上传检查仍然必须通过。
- 发布流程先备份旧附件，再上传并核对新包；上传失败会恢复旧附件名称。
- 全部新包上传成功后，替换现有 `v1.0.0-logisim-only` Release 中的旧 Windows 附件；保留旧附件备份 90 天。
- 未明确选择发布未签名版本时，缺少可信签名或 Apple 公证的产物仅保存在 Actions Artifacts 中。

签名凭据仅通过证书库、环境变量或 GitHub Secrets 提供，证书、私钥和密码均不写入源码仓库。
