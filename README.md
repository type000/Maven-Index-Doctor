# Maven Index Doctor

IntelliJ IDEA 插件，用于诊断和修复 Maven 索引问题。

它把“手动翻目录、删除缓存、重启 IDE”的排障流程收敛到一个可逆的 Tool Window 中，目标是帮助用户定位 `.lastUpdated` 残留、Maven 索引目录异常以及本地仓库与索引不一致等问题。

## 当前状态

当前版本是可运行的 MVP 骨架，已包含：

- Maven Index Doctor Tool Window
- 本地仓库 `.lastUpdated` 扫描
- IDEA Maven 索引目录健康检查
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

发布前需要在 CI 或本机环境变量中提供：

- `PUBLISH_TOKEN`
- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`

然后执行：

```bash
./gradlew signPlugin publishPlugin
```

插件 ID 是 `io.github.mavenindexdoctor`，发布后不要修改。正式上架前还需要补充维护者/厂商 URL、插件图标和最终隐私说明，并通过 JetBrains Plugin Verifier。

## 目录结构

```text
docs/                              技术设计
src/main/kotlin/.../domain         诊断领域模型
src/main/kotlin/.../application    扫描与修复用例
src/main/kotlin/.../ui             Tool Window 和 Swing UI
src/main/resources/META-INF        plugin.xml、图标
```
