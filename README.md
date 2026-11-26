# 问诊练习

一个面向中国医学生的原生 Android 问诊训练应用。使用 Kotlin、Jetpack Compose 与 Material3 构建，包名为 `com.xjb.wenzhen`。应用通过标准化病人病例模拟问诊，在结束对话后生成规范大病历并提供结构化点评。

当前版本为 v1.4.0，`versionCode` 为 16。

## 功能

- **内置病例库**：包含 54 个标准化病人病例，覆盖 30 个临床科室（心内、呼吸、消化、神经、内分泌、肾内、血液、风湿免疫、感染、普外、骨科、泌尿、妇产、儿科、精神心理、耳鼻喉、眼科、皮肤科、肛肠外科、急诊科、胸外科、神经外科、血管外科、乳腺外科、心脏大血管外科、老年医学科、肿瘤内科、口腔颌面外科、烧伤科、变态反应科）。病例包含患者一般信息、开场白、隐藏病史与检查资料、10–15 条问诊要点、正确诊断及鉴别诊断。
- **患者性格**：开始问诊或生成 AI 病例前，可选择配合、焦虑、表达不清、老年人（听力差、啰嗦）、不耐烦、隐瞒（需追问才说）六种沟通设定，并写入病人提示词。
- **流式患者回复**：DeepSeek 请求使用 `stream=true`，客户端逐行解析 SSE，患者回复在对话中逐步显示；同时保留非流式测试替身和错误处理。
- **问诊对话顺序与输入法滚动**：新问诊固定由医生先开口（「您好，我是接诊医生。请问您哪里不舒服？」），随后患者再描述主诉；旧草稿恢复时会自动补上医生开场白。聊天输入框唤起输入法后，消息列表按可见高度自动滚到最新一条，长对话与短对话均保持贴底展示；用户上翻历史时暂停自动贴底，右下角显示「回到最新消息」按钮。
- **问诊提示与计时**：对话页顶部显示 `mm:ss` 计时器，并可展开问诊覆盖提示。`CoverageAnalyzer` 按门诊/住院类型区分覆盖项：门诊统计主诉、现病史和既往/过敏/用药；住院额外统计个人史、婚育史、女性 12–55 岁的月经史、家族史和系统回顾。
- **查体与检查**：对话页提供「查体」「化验/检查」入口，可选择推荐项目或自行输入。AI 依据病例隐藏资料返回一致结果，病例未列出的项目会生成与诊断和病情相符的合理结果，并以系统卡片插入对话。
- **失败重试**：医生消息发送失败后可在原对话中点击「重试」，无需重新开始问诊。
- **病例库筛选与 AI 生成**：支持按全部科室筛选并展示加载、空列表和错误状态；可选择科室、难度和患者性格，让 DeepSeek 生成同结构的 JSON 病例。生成对话框按 8 个系统分组提供 54 个可选科室（内置 30 + 仅 AI 生成 24，如肝胆外科、重症医学科、核医学科、遗传咨询科等），每个科室附带症候提示注入提示词以提升生成质量；AI 专属科室不占用内置病例筛选行，只有真正生成过病例才补进筛选 chip。
- **结束问诊**：填写初步诊断、诊断依据和治疗方案后，可同时生成门诊/住院病历与评分；关闭“生成病历”只跳过病历生成，评分与参考诊疗方案仍会给出。学生治疗内容以本人填写为准，门诊写入“处理意见”、住院写入“诊疗计划”，未填写时写“未填写”。
- **结果页**：按内容提供「病历」「点评」「对话」。可点击问诊覆盖和诊断依据中的编号回到原对话。每条可与本次已取得的病史或检查结果及病例资料核对的诊断依据计 2 分，最多 4 分。结果也提供门诊或住院的参考诊疗方案，支持复制与系统分享。
- **病例收藏**：内置病例和 AI 生成病例均可在病例卡片上点星标收藏，在病例库的「收藏」筛选中查看。
- **成绩统计**：历史成绩趋势、各科室平均分、最常遗漏问诊要点 Top 10；统计由纯函数 `StatsCalculator` 计算，并可从底部导航进入。
- **设置页引导**：可折叠展示获取 DeepSeek API Key 的完整步骤，并提供打开 `platform.deepseek.com` 的按钮。
- **测试连接**：保存 Key 后可发送 `max_tokens=1` 的最小请求，区分成功、401/403 Key 无效、402 余额不足、429 限流、网络错误、超时和服务端异常等中文提示。
- **应用内更新**：从 GitHub Releases 的 `update.json` 检查版本，展示更新说明；下载 APK 后校验 SHA-256 与签名证书，再通过 FileProvider 和系统安装器安装。未授权未知来源时会跳转系统设置。
- **历史记录**：使用 Room 保存病例、性格、对话、病历、评分和时间。可进入历史详情回看，也可通过系统文件选择器保存或恢复 JSON 备份。
- **问诊草稿与离线训练**：问诊过程自动保存草稿，提交成功后清理草稿。可从病例库或设置开启离线练习，使用本地病例和规则评分。离线患者会按选定的性格回答，例如隐瞒型患者会在追问后提供相关病史。离线查体和检查仅展示病例中有记录的对应条目。
- **本地设置**：保存 DeepSeek API Key、模型、思考强度、默认病历生成开关和外观主题。设置页连接 DeepSeek 官方接口，可选择 `deepseek-flash` 或 `deepseek-v4-pro`。旧 `deepseek-v4-flash` 会自动迁移。
- **多配色主题**：设置页「外观主题」以迷你聊天页预览卡展示诊室青（默认）、临床蓝、暖杏、竹青、胭脂 5 套配色，每张卡同时预览浅色/深色两套色板、深浅跟随系统自动切换；选择后立即生效并持久化在本机。评分星标、科室标识、难度徽章等语义色固定，不随主题漂移。

## 数据库迁移

Room 数据库当前版本为 v6。增量迁移会保留已有历史记录。

- `MIGRATION_1_2` 新增 `patientPersonality`（`TEXT NOT NULL DEFAULT 'COOPERATIVE'`）。
- `MIGRATION_2_3` 新增 `encounterType`、`treatmentAnswer`、`recordType`，三者均带默认值。
- `MIGRATION_3_4` 新增 `consultation_drafts` 表，用于问诊草稿恢复。
- `MIGRATION_4_5` 新增 `saved_cases` 表，用于保存生成病例和收藏状态。
- `MIGRATION_5_6` 为历史记录的创建时间添加索引，用于备份恢复时快速去重。

`MigrationRoomUpgradeTest` 使用 `Room.databaseBuilder` 验证 v1 和 v4 数据库升级到 v6，检查数据保留和迁移后的表结构。

## 应用内更新

更新清单固定读取：

```text
https://github.com/xjb1337/wenzhen-practice/releases/latest/download/update.json
```

清单字段为 `versionName`、`versionCode`、`apkUrl`、`sha256`、`signingCertSha256`、`notes`、`minSdk`。应用只提示安装 `versionCode` 高于本机的更新，`versionName` 用于展示。

发布工作流 `.github/workflows/release.yml` 会在构建签名 APK 后生成并上传 `dist/update.json`，其中 `apkUrl` 指向：

```text
https://github.com/xjb1337/wenzhen-practice/releases/download/vX/wenzhen-practice.apk
```

## 构建与测试

环境要求：JDK 17、Android SDK 35。项目使用 Gradle 8.9、AGP 8.7.3、Kotlin 1.9.24、Compose Compiler 1.5.14。

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

也可以一次完成测试与打包：

```bash
./gradlew testDebugUnitTest assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

单元测试覆盖：

- `PromptBuilderTest`：患者提示词包含隐藏资料并禁止泄露诊断；门诊/住院病历与评分提示词区分；学生治疗方案写入病历提示；AI 病例必须输出结构化治疗方案，评分解析不得补写病例外资料。
- `ReferenceCarePlanBuilderTest`：内置病例的门诊/住院参考方案、诊断依据、检查、药物与随访分段，以及缺治疗方案时不编造。
- `ScoreParserTest`：正常 JSON、Markdown 围栏 JSON、损坏 JSON 与分项上限。
- `MedicalRecordServiceTest`：`FakeLlmClient` 实现同步流式回调，验证患者流式回复和病历系统回顾顺序。
- `SseParserTest`：空行、注释、delta、忽略 `reasoning_content`、`[DONE]` 和损坏 JSON。
- `DeepSeekClientTest`：验证 OFF/LOW/MAX 思考等级对应的请求 payload。
- `SettingsRepositoryTest`：验证旧模型名迁移和默认模型回退。
- `UpdateInfoTest`：更新清单字段解析、非法哈希/地址拒绝和版本比较。
- `CoverageAnalyzerTest`：门诊/住院覆盖差异、月经史适用年龄和患者回答不计入医生提问。
- `StatsCalculatorTest`：趋势、科室均分、Top 10 遗漏和空数据。
- `PatientPersonalityTest`：六种性格均写入病人提示词，并验证年龄可用性约束。
- `BuiltInCasesTest`：至少 54 个病例、全部 30 科室覆盖、字段完整性和治疗方案非空。
- `CaseGenerationDepartmentsTest`：AI 生成科室 54 项清单、8 个系统分组、内置 30 科全覆盖、症候提示三段式与颜色映射一致性。
- `RecordParserTest`：门诊/住院病历小节及学生治疗内容校验。
- `MigrationRoomUpgradeTest`：Room v1→v6、v4→v6 真实升级路径与表结构校验。

## 项目结构

```text
com.xjb.wenzhen
├── data
│   ├── api          LlmClient、DeepSeekClient 与 SseParser
│   ├── cases        内置病例、扩展病例和病例 JSON 编解码
│   ├── db           Room Entity、DAO、数据库、迁移与历史仓储
│   ├── model        病例、临床请求、消息与 LLM 模型
│   ├── settings     EncryptedSharedPreferences 设置仓储
│   └── update       更新清单、检查器、签名校验与安装器
├── domain           PromptBuilder、解析器、问诊覆盖、统计与对话服务
└── ui               ViewModel、导航、主题、通用组件与页面
```

`LlmClient` 是接口，生产实现为 `DeepSeekClient`，测试可注入实现同步 `complete` 与 `stream` 的 `FakeLlmClient`。

## DeepSeek 配置

1. 打开应用底部「设置」。
2. 展开「如何获取 DeepSeek API Key」，按引导打开 `platform.deepseek.com`，注册登录并充值。
3. 在左侧菜单进入 API keys，创建并复制 Key；完整 Key 通常只显示一次。
4. 回到设置页粘贴 Key，选择模型和思考强度后保存。
5. 点击「测试连接」确认 Key、余额、限流和网络状态。
6. 默认连接 DeepSeek 官方接口（`https://api.deepseek.com`）。

应用不内置任何 API Key。

## 隐私说明

- **首次启动告知**：首次打开应用会显示隐私与 AI 使用说明，确认后才能进入主界面。
- **API Key 仅保存在本机**：使用 Android Keystore 支持的加密存储（`EncryptedSharedPreferences`，security-crypto 1.1.0），界面默认遮罩显示，应用不打印 Key 或请求头日志。
- **对话会发送到 AI 服务**：患者对话、生成病例指令、查体/检查请求、病历与评分请求会发送到用户配置的 DeepSeek Base URL。请勿输入真实患者身份信息或敏感个人资料。
- **本地历史记录**：病例、对话、病历和评分保存在本机 Room 数据库中；应用不包含广告、分析或第三方追踪 SDK。
- **关闭云备份**：应用已禁用 Android 自动备份，避免加密设置被意外同步。
- **更新校验**：仅接受 GitHub Releases 地址的 APK，并在安装前校验 SHA-256 和签名证书。
- **AI 内容标识**：结果页中的 AI 评分与参考诊疗方案标注为 AI 生成；AI 生成的病例、查体/检查结果和病历请在练习中结合上下文判断，不要当作真实临床资料。
- **教学用途**：AI 生成的病例、病历、检查和点评仅用于问诊练习，不构成医疗建议，也不能替代教师反馈与临床培训；参考方案中的药物与剂量仅用于教学复盘，请勿直接用于真实患者。

## 说明

- 应用仅支持文字问诊，不包含图片、语音或附件输入。
- 未配置 API Key 时，病例浏览仍可用，但 AI 对话、查体/检查、随机病例和结束评分会引导用户前往设置。
- 所有界面文案均为中文，采用青蓝色（`#00796B` / `#0097A7`）视觉体系。
