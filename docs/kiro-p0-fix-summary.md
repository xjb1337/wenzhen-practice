# P0-1 / P0-2 修复说明（2026-11-06）

- 分支：`fix/p0-scoring-2026-11-06`（基于 `main` @ `5642e5e`），未提交。
- 对应审查报告：`docs/kiro-review-2026-11-06.md` 的 P0-1 / C-1、P0-2 / C-2。
- 范围：只改 P0-1、P0-2 相关代码和测试，没有处理报告中的其他条目。

## 修改的文件

| 文件 | 改动 |
| --- | --- |
| `domain/ScoreParser.kt` | 新增 `AiCommentStatus`（OK / FAILED）；`ScoreResult` 增加 `aiCommentStatus`、`aiCommentError`（后者不入库）；`encode`/`parse` 读写 `aiCommentStatus`，旧记录缺字段按 OK |
| `domain/ConsultationServices.kt` | `ScoreService.evaluate` 先算规则分再请求 LLM；LLM 异常或 JSON 损坏时返回 `ScoreService.ruleOnly(...)`；`ScoreReconciler` 把规则分说明（`RuleScore.notes`）追加到改进建议 |
| `domain/RuleScoreCalculator.kt` | 重写诊断、技巧、沟通、治疗四项规则；`RuleScore` 增加 `notes` |
| `domain/KeyQuestionCatalog.kt` | 要点关键词改为按连接词切分 + 去修饰词；同义词组改为“触发词 → 同义词”，去掉单字“烟”“酒”和“多久”“以前”等通用词；补充通用词表 |
| `domain/QuestionCoverageAnalyzer.kt` | 去掉任意 2 字切片；同一病例中被多个要点共用的词不作为证据；保留“逐字照抄要点原文”匹配 |
| `domain/OfflinePracticeService.kt` | 离线评分的改进建议带上 `rule.notes`（1 行） |
| `data/model/MedicalCase.kt` | 新增可选字段 `diagnosisAliases: List<String> = emptyList()` |
| `data/cases/CaseJsonCodec.kt` | 读写 `diagnosisAliases`；只在非空时写入，没有别名的病例 `caseJson` 与旧版逐字一致 |
| `data/db/ConsultationDao.kt`、`ConsultationRepository.kt` | 新增 `updateScore(id, scoreJson, totalScore)`（`@Query UPDATE`，不改表结构，不需要迁移） |
| `ui/AppViewModel.kt` | `finishConsultation` 删除全 0 分支，先入库再清草稿；新增 `regenerateAiComment(recordId)` |
| `ui/AppUiState.kt` | 新增 `regeneratingCommentId` |
| `ui/screens/ResultScreen.kt` | 点评页在 FAILED 时显示“AI 点评生成失败”卡片和“重新生成 AI 点评”按钮 |
| `ui/screens/HistoryDetailScreen.kt`、`ui/WenzhenApp.kt` | 结果页和历史详情接上重新生成按钮；历史详情显示 `notice` |

测试文件见下文“新增与修改的测试”。

## P0-1：评分失败不再丢数据

1. **规则分先算**：`ScoreService.evaluate` 第一步调用 `RuleScoreCalculator.compute`（纯本地计算）。随后请求 LLM 并解析 JSON；这一段抛出任何非取消异常（`LlmException` 超时/限流、`ScoreParseException` JSON 损坏等）时，返回 `ruleOnly` 结果。`ruleOnly` 的分项、总分、已覆盖要点、遗漏问题和证据链与正常结果一致，优点、改进建议和诊断解析留空，`aiCommentStatus = FAILED`，`aiCommentError` 是失败原因。`CancellationException` 照常抛出，不会被当成点评失败。
2. **先入库再清草稿**：`AppViewModel.finishConsultation` 删除了原来的全 0 `ScoreResult`、`resultId = 0L` 分支。`scoreResult` 失败时（理论上只剩规则计算之外的意外异常）在 ViewModel 里再算一次规则分兜底。流程固定为：构造实体 → `consultationRepository.save` → 更新结果页状态 → 关闭草稿持久化并清草稿。`save` 抛异常会进入原有的 `catch`，草稿、对话和结束页都保留，用户可以再次提交。
3. **文案**：删除“评分未生成，可返回后重试评分。”和“评分生成失败，暂不写入历史记录。”。FAILED 时结果页提示条为“AI 点评生成失败，分数已按本地规则计算并保存到历史记录，可在“点评”页重新生成：<原因>”。
4. **重新生成 AI 点评（已实现）**：点评页 FAILED 时显示卡片和按钮，结果页和历史详情都可用。`regenerateAiComment(recordId)` 从历史表读回病例、对话、诊断、依据和治疗作答，再调用一次 `ScoreService.evaluate`。成功时用 `updateScore` 覆盖同一条记录的 `scoreJson` 和 `totalScore`，并刷新界面；仍然失败时保留原记录，提示“AI 点评仍未生成……”。没有可用 Key 或邀请码时给出设置提示并跳转设置页。
5. **存储**：`aiCommentStatus` 写在 `scoreJson` 里，没有 Room schema 变更。

## P0-2：收紧规则分

### 诊断（`diagnosisNameScore`，名称满分 16，依据 4）
- 归一化：全角转半角、转小写、去括号内容、去标点空白，去掉“初步诊断/考虑/疑似/可能是”等前缀（可叠加）。
- 门槛：归一化后少于 2 个字（`MIN_DIAGNOSIS_LENGTH`），或只有“急性”“待查”“感染”等笼统词，名称记 0 分。这种情况诊断依据也不给分，并在改进建议里说明。
- 分档：完全一致或命中 `diagnosisAliases` → 16；包含完整标准诊断 → 14；去掉“急性/慢性/左侧/右侧”等修饰词后与标准诊断或其组成部分（按“，；、伴、合并”拆分）一致，或重叠部分同时占双方 60% 以上且至少 3 个字连续相同 → 12；连续 3 字以上相同或疾病名结尾 2 字相同（如“肺炎”）→ 8；其他已填写的诊断 → 6。“非 ST 段抬高”与“ST 段抬高”这类否定词不一致直接记 6。
- 别名机制：`MedicalCase.diagnosisAliases` 已支持并能随 `caseJson` 持久化，但**内置 54 个病例暂未填写别名**。别名属于医学内容，需要维护者逐条确认，本次没有改病例数据。

### 覆盖（`KeyQuestionCatalog` + `QuestionCoverageAnalyzer`）
- 关键词：把要点文本按“、 及 和 或 与 的 等 是否 有无 能否……”切分，去掉“近期/既往/是否/伴”前缀和“情况/史/时间/开始/症状”后缀，过滤通用词和单字。例如“胸痛开始的时间、诱因和持续时间”只剩“诱因”，加上起病时间同义词（“多久了”“多长时间”“几天了”“什么时候开始”……）。
- 同义词组改为“要点里出现触发词才启用”。去掉了单字“烟”“酒”，改成“吸烟/抽烟/烟酒/抽过烟/香烟”“饮酒/喝酒/烟酒/喝过酒”。去掉了“多久”“以前”“过去”“病史”“什么样的”“吃饭”等通用词。用药组不再用“服药”作触发词，因为它会误命中“含服药物”。
- 共用词剔除：同一病例里出现在 2 个及以上要点中的词（例如心梗病例里的“胸痛”）不能单独证明覆盖。剔除后某个要点没有词可用时，退回它自己的关键词。
- 逐字照抄要点原文仍算覆盖，原有的证据编号口径不变。

### 技巧和沟通
- 技巧分（15）= 提问比例（≤4）+ 命中问诊要点的提问比例（≤6）+ 开放式提问（≤3）+ 有效提问数量（≤2）− 重复提问（每条 1 分，最多 3 分）。“有效提问”指命中某个要点，或者去重后至少 5 个字的提问。
- 沟通分（15）= 底分 3（原来是 7）+ 共情短语（≤4，只认“别担心/请放心/理解您/慢慢说/辛苦了……”，去掉“需要/建议/注意/配合”）+ 平均长度适中（≤3）+ 有效提问数量（≤5）。
- Python 镜像在 54 个病例上的结果：开场白加 4 条带问号的空话，技巧分 + 沟通分最高 10 分；只发空话时覆盖分都是 0。同一组示范问诊（心梗病例 12 条具体提问）得覆盖 35、技巧 15、沟通 11。

### 治疗方案（15）
- 病例没有 `treatmentPlan`：0 分，并在改进建议里写“该病例没有参考治疗方案，治疗方案分暂记 0 分。”。原来的规则是写满 12 个字给 11 分。
- 有方案时，从“药物：”一节按“药名 + 剂量”抽取药名，用药名末尾 3 个字匹配（例如“阿司匹林300mg嚼服”取“司匹林”），命中比例给药物分（≤10）；再从“治疗原则/处理”两节抽取措施关键词（PCI、溶栓、吸氧、心电监护……），按命中个数给措施分（≤5）。方案里没有带剂量的药物时，只按措施计分（≤15）。

## 新增与修改的测试

新增：
- `domain/RuleScoreCalculatorTest.kt`（11 个用例）：单字/笼统诊断不超过 8 分（实际 0 分）；标准诊断全称、括号、前缀和别名得 16 分，未登记别名的缩写不得分；部分重叠、否定词不一致只拿部分分；遍历 54 个病例，“4 条带问号的空话”（含开场白、不含开场白、重复 4 次）技巧分 + 沟通分不超过 18 且覆盖分为 0；“需要/建议”不再加沟通分；完整问诊仍拿到覆盖 ≥30、技巧 ≥12、沟通 ≥10；合理治疗方案 ≥12、空泛方案 ≤4；无参考方案时治疗分为 0 并说明；药名抽取；总分等于分项和。
- `domain/QuestionCoverageAnalyzerTest.kt`（7 个用例）：遍历 54 个病例，“这个多长时间了”最多覆盖 1 个要点；寒暄和空话不覆盖任何要点；每个要点至少有 1 个非通用、不少于 2 字的关键词（报告建议放在 `BuiltInCasesTest`，这里放在覆盖分析器的测试里，没有改 `BuiltInCasesTest`）；关键词里没有“时间/开始/持续”和单字“烟/酒”；“烟呢？”不算问到吸烟；共用症状词不能单独证明覆盖；口语化问法仍能覆盖。
- `domain/ScoreServiceTest.kt`（4 个用例）：LLM 抛 `LlmException` 时返回规则分并标记 FAILED；JSON 损坏时同样处理；LLM 正常时分数取规则分、点评取模型；`CancellationException` 继续向上抛。

修改：
- `domain/ScoreParserTest.kt`：新增 `aiCommentStatus` 往返和旧记录默认 OK 的用例。
- `ui/AppViewModelTest.kt`：原用例“结束问诊后即使评分生成失败也必须清除草稿”补充断言：记录已入库（`recordId > 0`、数据库能读到）、状态为 FAILED、总分一致。新增“评分请求抛出 LlmException 时按规则分写入历史并清除草稿”（历史多 1 条，`totalScore` 等于规则分，草稿已清，界面没有“可返回后重试”）和“重新生成 AI 点评成功后更新同一条历史记录”（同一 id，`scoreJson` 带上模型给的 strengths/improvements，记录数仍为 1）。合法评分 JSON 抽成 `VALID_SCORE_JSON` 常量，与 `ValidScoreClient` 共用。

## 验证情况

- 本机补充安装了 Android SDK（`E:\dsh project\.android-sdk`，platform 35 + build-tools 35）后执行了真实验证：
  - `./gradlew testDebugUnitTest`：**34 个测试类、85 个测试用例，0 failures、0 errors、0 skipped，BUILD SUCCESSFUL**（含新增的 `RuleScoreCalculatorTest` 11 个、`QuestionCoverageAnalyzerTest` 7 个、`ScoreServiceTest` 4 个，以及扩充后的 `AppViewModelTest` 13 个）。
  - `./gradlew assembleDebug`：**BUILD SUCCESSFUL**，产物 `app/build/outputs/apk/debug/app-debug.apk`。
  - LDPlayer9 模拟器（1920×1080）卸载旧签名版本后安装 debug APK：启动进入病例库；开启「离线基础训练」，完成一次“胸痛三小时”问诊并提交评分，结果页正常显示（总分 19，分项正常），证明新规则在真机 UI 链路可用。
  - P0-1 真机失败路径：在 LDPlayer9 上配置无效 API Key（`sk-invalid`）后完成一次问诊并提交评分，结果页显示“AI 点评生成失败，分数已按本地规则计算并保存到历史记录……”，总分 18，确认“LLM 失败仍入库、不再丢数据”在真机 UI 链路生效（有效 Key 的正常 AI 点评未测）。
- 静态检查：
  - 用 Python 实现了关键词抽取、共用词剔除、覆盖匹配、技巧/沟通分、诊断分档、药名抽取的镜像逻辑，从 `data/cases/*.kt` 解析出全部 54 个内置病例，校验了测试里依赖真实病例数据的断言：每个要点都有可用关键词；“这个多长时间了”最多覆盖 1 个要点；空话不覆盖任何要点；要点原文能覆盖自己；“烟呢？”“酒呢？”不命中；空话的技巧分 + 沟通分最高 10。另外逐条核对了诊断分档、心梗病例药名抽取和治疗分的期望值。镜像脚本放在临时目录，没有加入仓库。
  - 对所有改动过的 `.kt` 文件检查了括号配对（忽略字符串和注释）以及新增 import 是否被使用。
- 剩余风险：
  1. **测试脆弱点**：`RuleScoreCalculatorTest` 中具体分数（例如 8、12）依赖规则细节，以后调整规则时需要同步改断言。
  2. **分数口径变化**：新规则整体比旧规则严格，同样的作答分数会下降（沟通底分从 7 降到 3；没有参考方案的病例治疗分记 0；通用词不再算覆盖）。历史记录保存的是当时的分数，不会重算，所以统计页的趋势会在升级后出现一次明显的下降。只有 AI 点评失败、用户点了“重新生成”的记录会按新规则重算，这类记录只会在本版本之后产生。
  3. **可能低估**：共用词剔除比较保守。如果一个要点的关键词都和其他要点重复，就只能靠它自己的关键词或原文命中。另外，诊断写成类别名（如“急性心肌梗死”对应 STEMI）只得 8 分，需要靠病例别名补齐。
  4. **未覆盖的路径**：没有为“`consultationRepository.save` 抛异常时草稿保留”写测试，因为真实 Room 下难以稳定地构造写入失败。逻辑上保存语句在清草稿之前，异常会进入 `catch`。如果保存成功但清草稿失败，结果页状态已更新，同时结束页也会显示错误，再次提交会产生重复记录。这种情况概率很低，本次没有处理。

## 暂未实现 / 建议后续处理

- 内置病例的 `diagnosisAliases` 数据（如 STEMI、急性前壁心肌梗死），需要维护者确认后补充。
- 报告 C-2 建议把每个要点的 `keywords/synonyms` 显式写进病例数据。本次是在运行时从要点文本自动抽取，没有改病例数据。
- 复制/分享点评文本（`reviewAsText`）里没有标注“AI 点评生成失败”。
- 点评页底部“评分来自 AI 模拟反馈”与“分数由本地规则计算”的事实不一致，本次没有改。
