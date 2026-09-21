# 发布 Maven Index Doctor

本文说明如何将 Maven Index Doctor 签名并发布到 JetBrains Marketplace。

本文中的命令只会生成和读取本地凭据。不要把真实 Token、私钥或证书写入仓库，也不要发送到聊天工具或提交到 Git。

## 1. 发布前检查

在创建 Marketplace 发布凭据之前，先完成以下检查：

- `plugin.xml` 中的插件 ID 已固定为 `io.github.mavenindexdoctor`。
- 已确定维护者或组织名称、主页、源码地址和 Issue 地址。
- 已选择许可证，并准备好隐私说明和变更日志。
- 已在干净的 IDEA 沙箱中验证 Tool Window、扫描、清理、备份、重建和恢复。
- 已决定支持的 IntelliJ IDEA 版本，并对这些版本运行 Plugin Verifier。

当前项目的构建目标是 IntelliJ IDEA 2025.3.5，最低兼容 build 为 `253`。如需支持更早版本，必须先扩展验证矩阵，再修改 `build.gradle.kts`。

## 2. 四个凭据的来源

| 环境变量 | 来源 | 说明 |
| --- | --- | --- |
| `PUBLISH_TOKEN` | JetBrains Marketplace 个人访问令牌 | 用于上传插件 |
| `CERTIFICATE_CHAIN` | 本地生成的 `chain.crt` 文件内容 | 用于插件签名 |
| `PRIVATE_KEY` | 本地生成的 `private.pem` 文件内容 | 用于插件签名，必须保密 |
| `PRIVATE_KEY_PASSWORD` | 生成加密私钥时设置的密码 | 用于读取私钥 |

当前 `build.gradle.kts` 读取的是证书和私钥的文本内容，而不是文件路径：

```kotlin
signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
}

publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
}
```

## 3. 创建 JetBrains Marketplace Token

1. 打开 <https://plugins.jetbrains.com/> 并登录 JetBrains Account。
2. 打开个人资料页的 `My Tokens`。
3. 点击 `Generate Token` 创建发布 Token。
4. 立即复制 Token 并保存到密码管理器。

Token 通常只在生成时显示一次。它不是 GitHub Token，也不要把它写入 `gradle.properties`。

## 4. 生成插件签名材料

JetBrains 官方签名流程使用 RSA 私钥和证书链。当前 Windows 环境已安装 Git for Windows，可使用其中的 OpenSSL：

```powershell
$secretDir = "D:\maven-index-doctor-secrets"
$openssl = "D:\Git\usr\bin\openssl.exe"

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
```

生成加密私钥：

```powershell
& $openssl genpkey `
  -aes-256-cbc `
  -algorithm RSA `
  -out "$secretDir\private_encrypted.pem" `
  -pkeyopt rsa_keygen_bits:4096
```

命令提示输入的密码就是 `PRIVATE_KEY_PASSWORD`。随后转换为签名任务使用的 RSA 私钥：

```powershell
& $openssl rsa `
  -in "$secretDir\private_encrypted.pem" `
  -out "$secretDir\private.pem"
```

生成证书链：

```powershell
& $openssl req `
  -key "$secretDir\private.pem" `
  -new `
  -x509 `
  -days 365 `
  -out "$secretDir\chain.crt" `
  -subj "/CN=Maven Index Doctor/"
```

检查私钥：

```powershell
& $openssl rsa `
  -in "$secretDir\private.pem" `
  -check `
  -noout
```

应看到：

```text
RSA key ok
```

最终文件位于：

```text
D:\maven-index-doctor-secrets\private.pem
D:\maven-index-doctor-secrets\chain.crt
```

密钥目录应放在项目目录之外，并使用密码管理器保存私钥密码。证书到期前需要重新生成并更新 CI Secret。

## 5. 本机签名

在 PowerShell 中设置本次会话的环境变量。下面的命令不会把凭据写进项目文件：

```powershell
$env:JAVA_HOME = "D:\java21"
$env:GRADLE_USER_HOME = "D:\gradle-cache"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$env:CERTIFICATE_CHAIN = Get-Content -Raw "D:\maven-index-doctor-secrets\chain.crt"
$env:PRIVATE_KEY = Get-Content -Raw "D:\maven-index-doctor-secrets\private.pem"

$privateKeyPassword = Read-Host "PRIVATE_KEY_PASSWORD" -AsSecureString
$env:PRIVATE_KEY_PASSWORD = [System.Net.NetworkCredential]::new("", $privateKeyPassword).Password

$publishToken = Read-Host "PUBLISH_TOKEN" -AsSecureString
$env:PUBLISH_TOKEN = [System.Net.NetworkCredential]::new("", $publishToken).Password
```

先运行测试、打包和签名：

```powershell
.\gradlew.bat clean test buildPlugin
.\gradlew.bat signPlugin
```

签名后的 ZIP 位于：

```text
build\distributions\maven-index-doctor-0.1.0.zip
```

在正式上传前，建议将这个 ZIP 安装到一个干净的 IDEA 沙箱中再测试一次。

## 6. 第一次上传和后续自动发布

JetBrains 官方流程要求首次上传先在 Marketplace 页面创建插件条目：

1. 登录 JetBrains Marketplace。
2. 选择 `Add new plugin`。
3. 填写插件名称、描述、兼容产品、许可证、源码地址和截图。
4. 上传 `signPlugin` 生成的 ZIP。
5. 等待插件条目创建并完成审核流程。

首次上传完成后，后续版本可以使用：

```powershell
.\gradlew.bat publishPlugin
```

`publishPlugin` 会在签名配置存在时自动先执行签名。发布前请确认 `PUBLISH_TOKEN` 对应的 Marketplace 账号拥有该插件的发布权限。

## 7. GitHub Actions 发布

在 GitHub 仓库中打开：

```text
Settings
→ Secrets and variables
→ Actions
→ New repository secret
```

创建以下四个 Repository Secrets：

```text
PUBLISH_TOKEN
CERTIFICATE_CHAIN
PRIVATE_KEY
PRIVATE_KEY_PASSWORD
```

当前 `.github/workflows/release.yml` 已经读取这些 Secret，不需要把值写进 YAML 文件。发布流程还需要 GitHub Actions 具备创建 Release 和上传附件的权限。

## 8. 发布命令和验证结果

日常验证：

```powershell
.\gradlew.bat test --offline
.\gradlew.bat verifyPluginStructure --offline
```

需要网络或已缓存 Plugin Verifier 时执行：

```powershell
.\gradlew.bat verifyPlugin
```

只生成本地安装包：

```powershell
.\gradlew.bat buildPlugin
```

## 9. 安全规则

- 不要提交 `private.pem`、`private_encrypted.pem`、`chain.crt` 或任何包含凭据的文件。
- 不要把 Token、私钥、证书内容粘贴到 Issue、聊天或构建日志中。
- 不要把凭据放到 `gradle.properties`、`README.md` 或 GitHub Actions YAML 中。
- Token 泄露后，立即在 JetBrains Marketplace 撤销并重新生成。
- 私钥泄露后，不要继续使用原证书，重新生成密钥并更新发布凭据。

## 官方文档

- [Publishing Plugins](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
- [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)
- [Marketplace listing best practices](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html)
