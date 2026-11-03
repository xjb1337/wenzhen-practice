# 问诊练习

一个面向中国四年级医学生的原生 Android 问诊训练应用。使用 Kotlin、Jetpack Compose 与 Material3 构建，包名为 `com.xjb.wenzhen`。应用通过标准化病人病例模拟问诊，在结束对话后生成规范大病历并提供结构化点评。

## 功能

- **病例库**：内置 10 个标准化病人病例，覆盖心内、消化、呼吸、神经、内分泌、泌尿、儿科和妇科。病例包含患者一般信息、开场白、隐藏的结构化病史与检查资料、10–15 条问诊要点、正确诊断及鉴别诊断。
- **科室筛选**：病例列表支持按科室筛选，并提供加载、空列表和错误状态。
- **AI 随机病例**：选择科室和难度后调用 DeepSeek 生成同结构的 JSON 病例；会校验必填字段与问诊要点数量，解析失败时显示可重试的中文错误提示。
- **患者对话**：AI 以标准化病人身份回答，只答被问到的内容，使用通俗口语，不主动透露诊断。发送消息时显示“患者正在输入…”。仅支持文字输入。
- **查体规则**：询问是否可以查体时，患者回答“这需要医生检查哦”；明确要求模拟查体结果时，才根据隐藏查体资料回答。
- **结束问诊**：用户先填写初步诊断和可选的诊断依据，随后生成大病历和评分。未问及的内容统一写为“未问及”，系统回顾强制位于体格检查之后。
- **双 Tab 结果页**：「病历」展示完整大病历；「点评」展示总分、四个分项、已覆盖要点、遗漏问题、优点、改进建议和正确诊断解析。支持复制当前内容与系统分享病历。
- **历史记录**：使用 Room 保存病例、对话、病历、评分和时间；历史列表可以进入详情回看。
- **设置**：保存 DeepSeek API Key、模型及 Base URL。默认模型为 `deepseek-v4-flash`，也可选择 `deepseek-v4-pro` 或填写自定义模型名。

## 截图

Roborazzi 截图测试会把图片写入：

```text
app/build/outputs/roborazzi/
```

包含以下页面：

- `case_library.png`：病例列表
- `history_empty.png`：空历史记录
- `chat.png`：问诊对话
- `settings.png`：设置页（API Key 默认遮罩）
- `finish_consultation.png`：结束问诊与填写诊断
- `result_record.png`：结果页「病历」Tab
- `result_review.png`：结果页「点评」Tab

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

- `PromptBuilderTest`：患者提示词包含隐藏资料并禁止泄露诊断；病历与评分提示词约束。
- `ScoreParserTest`：正常 JSON、Markdown 围栏 JSON、损坏 JSON 与分项上限。
- `MedicalRecordServiceTest`：使用 `FakeLlmClient` 验证病历生成及系统回顾顺序。
- `BuiltInCasesTest`：10 个病例及其字段完整性。
- `ScreenshotTest`：Robolectric 34 + Roborazzi 七张页面截图。

## 项目结构

```text
com.xjb.wenzhen
├── data
│   ├── api          LlmClient 接口与 OkHttp DeepSeekClient
│   ├── cases        内置病例和病例 JSON 编解码
│   ├── db           Room Entity、DAO、数据库与历史仓储
│   ├── model        病例、消息与 LLM 模型
│   └── settings     EncryptedSharedPreferences 设置仓储
├── domain           PromptBuilder、RecordParser、ScoreParser 与对话服务
└── ui               ViewModel、导航、主题、通用组件与七个页面
```

`LlmClient` 是接口，生产实现为 `DeepSeekClient`，测试可以使用 `FakeLlmClient` 注入固定回复。

## DeepSeek 配置

1. 打开应用底部「设置」。
2. 填入自己的 DeepSeek API Key。
3. 选择模型，或填写自定义模型名称。
4. Base URL 默认是 `https://api.deepseek.com`，客户端会调用 `/chat/completions`（OpenAI 兼容格式）。
5. 保存后即可进行 AI 问答、随机病例生成和结束问诊评分。

应用不内置任何 API Key。

## 隐私说明

- **API Key 仅保存在本机**：使用 Android Keystore 支持的 `EncryptedSharedPreferences` 加密存储，界面默认遮罩显示，应用不打印 Key 或请求头日志。
- **对话会发送到 DeepSeek**：患者对话、生成病例指令、病历与评分请求会发送到用户配置的 DeepSeek Base URL。请勿输入真实患者身份信息或敏感个人资料。
- **本地历史记录**：病例、对话、病历和评分保存在本机 Room 数据库中；应用不包含广告、分析或第三方追踪 SDK。
- **关闭云备份**：应用已禁用 Android 自动备份，避免加密设置被意外同步。
- **教学用途**：AI 生成的病例、病历和点评仅用于问诊练习，不构成医疗建议，也不能替代教师反馈与临床培训。

## 说明

- 应用仅支持文字问诊，不包含图片、语音或附件输入。
- 未配置 API Key 时，病例浏览仍可用，但 AI 对话、随机病例和结束评分会引导用户前往设置。
- 所有界面文案均为中文，采用青蓝色（`#00796B` / `#0097A7`）视觉体系。
