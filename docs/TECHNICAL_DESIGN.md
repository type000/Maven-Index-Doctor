# Maven Index Doctor 技术设计

## 1. 产品边界

Maven Index Doctor 是 IntelliJ IDEA 插件，负责诊断和修复 IDEA Maven 索引层的问题。它不替代 Maven 的依赖解析，不修改 Maven POM，也不处理依赖冲突。

首个可发布版本优先覆盖本机文件系统诊断和可逆清理；网络抓取、配置漂移监听和自动重试放到后续迭代。

## 2. 用户流程

1. 用户打开 `View | Tool Windows | Maven Index Doctor`。
2. 点击“扫描”，插件扫描本地 Maven 仓库和 IDEA Maven 索引目录。
3. 结果按严重程度展示，并提供问题路径和建议操作。
4. 用户可以清理选中的 `.lastUpdated` 文件，或执行“安全重置”。
5. 安全重置先复制索引目录到临时备份目录，再清理并请求 Maven 索引刷新；备份信息保留在项目级服务中，支持恢复。

## 3. 分层结构

### Domain

- `DiagnosticSeverity`：INFO/WARNING/ERROR。
- `DiagnosticIssue`：一条诊断结果，包括标题、详情、路径和建议操作。
- `MavenDiagnosticReport`：一次扫描的时间、索引目录和问题集合。

### Application

- `MavenDiagnosticsService`：编排扫描器，负责生成诊断报告。
- `LastUpdatedScanner`：扫描 `~/.m2/repository` 下的 `.lastUpdated` 文件。
- `MavenIndexHealthScanner`：检查 IDEA system 目录下的 `maven/indices` 是否存在、可读、是否包含零字节文件。
- `MavenIndexResetService`（后续）：备份、清理和恢复索引目录。

### UI

- `MavenIndexDoctorToolWindowFactory`：创建 Tool Window。
- `MavenIndexDoctorPanel`（后续）：展示扫描状态、问题列表和操作按钮。

UI 不直接访问文件系统；所有耗时操作通过应用服务执行，结果再回到 EDT 更新组件。

## 4. 首个 MVP 的验收标准

- 插件可以通过 `runIde` 启动，并在 Tool Window 中完成一次扫描。
- 能够发现并列出本地仓库中的 `.lastUpdated` 文件。
- 能够报告 Maven 索引目录不存在、不可读和零字节文件。
- 扫描失败时在 UI 中显示明确错误，不吞掉异常。
- `buildPlugin`、`verifyPluginProjectConfiguration` 和单元测试通过。
- 插件元数据包含稳定的插件 ID、名称、描述、兼容的 since-build 和发布任务配置。

## 5. 上架相关约束

- 插件 ID 使用 `io.github.mavenindexdoctor`，发布后不要修改。
- `plugin.xml` 只声明实际使用的依赖：平台、Java 和 Maven 插件。
- 通过 Gradle 的 `signPlugin`、`publishPlugin` 读取环境变量，不把证书或 Marketplace token 写入仓库。
- 发布前需要替换 vendor URL、维护者信息、隐私说明和最终版本号，并在 JetBrains Plugin Verifier 中验证。

## 6. 后续迭代

1. 清理指定/全部 `.lastUpdated`。
2. 索引目录备份、清理、恢复和 Maven `updateIndex` 调用。
3. 本地仓库与索引内容一致性检查。
4. `settings.xml`、镜像、代理检查。
5. 文件变化监听和通知栏预警。
