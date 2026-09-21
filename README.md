# Maven Index Doctor

IntelliJ IDEA 插件，用于诊断和修复 Maven 索引问题。

它把“手动翻目录、删除缓存、重启 IDE”的排障流程收敛到一个可逆的 Tool Window 中，目标是帮助用户定位 `.lastUpdated` 残留、Maven 索引目录异常以及本地仓库与索引不一致等问题。

## 当前功能

当前版本已包含：

- 按严重程度展示 Maven 诊断结果
- 本地仓库 `.lastUpdated` 扫描、定向重试和全部清理
- IDEA Maven 索引目录读写、空目录、零字节文件和大小下降检查
- 本地仓库 GAV 与 IDEA 本地索引一致性比对
- `settings.xml` 镜像、代理以及 IDEA Maven 离线模式检查
- 本地索引刷新、全部索引重建
- Maven 索引安全备份、清理、重建和一键恢复
- `.lastUpdated`、索引异常和 `settings.xml` 变化通知
- Marketplace 所需的插件 ID、版本、兼容范围、签名和发布配置

详细设计见 [`docs/TECHNICAL_DESIGN.md`](docs/TECHNICAL_DESIGN.md)。

## 本地开发

```bash
./gradlew runIde
```

常用校验：

```bash
./gradlew test
./gradlew verifyPluginProjectConfiguration
./gradlew buildPlugin
```

## 发布到 JetBrains Marketplace

完整的首次上传、签名、Token、OpenSSL 和 GitHub Actions 配置见 [`docs/PUBLISHING.md`](docs/PUBLISHING.md)。

配置好凭据后可以执行：

```bash
./gradlew signPlugin publishPlugin
```

插件 ID 是 `io.github.mavenindexdoctor`，发布后不要修改。正式上架前还需要补充维护者/厂商 URL、插件图标和最终隐私说明，并通过 JetBrains Plugin Verifier。

## 目录结构

```text
docs/                              技术设计
src/main/java/.../domain           诊断领域模型
src/main/java/.../application      扫描与修复用例
src/main/java/.../ui               Tool Window 和 Swing UI
src/main/resources/META-INF        plugin.xml、图标
```
