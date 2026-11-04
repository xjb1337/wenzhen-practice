package com.xjb.wenzhen.data.model

/**
 * AI 生成病例的分组。color 为固定语义色（沿用上一轮主题任务的「语义色固定」决策），
 * 不随皮肤 primary 漂移；深色主题取同文件 darkColor。
 */
data class CaseGenerationGroup(
    val label: String,
    val departments: List<String>,
    val color: Long,
    val darkColor: Long,
)

object CaseGenerationDepartments {
    // ---------- 24 个「仅 AI 生成」科室常量（不进入 Departments.all） ----------
    // 外科系统
    const val HEPATOBILIARY_SURGERY = "肝胆外科"
    const val PANCREATIC_SURGERY = "胰腺外科"
    const val SPINE_SURGERY = "脊柱外科"
    const val HAND_SURGERY = "手外科"
    const val PEDIATRIC_SURGERY = "小儿外科"
    const val PLASTIC_SURGERY = "整形外科"
    // 妇儿与生殖
    const val REPRODUCTIVE_MEDICINE = "生殖医学科"
    const val ANDROLOGY = "男科"
    // 五官、皮肤、口腔
    const val ORAL_MEDICINE = "口腔内科"
    const val DERMATOLOGIC_SURGERY = "皮肤外科"
    // 急危重症、麻醉、疼痛
    const val CRITICAL_CARE = "重症医学科"
    const val ANESTHESIOLOGY = "麻醉科"
    const val PAIN_MEDICINE = "疼痛科"
    // 精神、康复、缓和、营养
    const val REHABILITATION = "康复医学科"
    const val NUTRITION = "营养科"
    const val SLEEP_MEDICINE = "睡眠医学科"
    const val PALLIATIVE_CARE = "缓和医疗科"
    const val GENERAL_PRACTICE = "全科医学科"
    // 诊断与支撑
    const val NUCLEAR_MEDICINE = "核医学科"
    const val TRANSFUSION_MEDICINE = "输血科"
    const val VASCULAR_INTERVENTION = "血管介入科"
    const val CLINICAL_PHARMACY = "临床药学科"
    // 职业与遗传
    const val OCCUPATIONAL_MEDICINE = "职业病科"
    const val GENETIC_COUNSELING = "遗传咨询科"

    /** 8 个系统分组，共 54 个科室：内置 30 + 仅 AI 24。顺序即 UI 展示顺序。 */
    val groups: List<CaseGenerationGroup> = listOf(
        CaseGenerationGroup(
            label = "内科系统",
            color = 0xFF1565C0L, darkColor = 0xFF90CAF9L,
            departments = listOf(
                Departments.CARDIOLOGY, Departments.RESPIRATORY, Departments.GASTROENTEROLOGY,
                Departments.NEUROLOGY, Departments.ENDOCRINOLOGY, Departments.NEPHROLOGY,
                Departments.HEMATOLOGY, Departments.RHEUMATOLOGY, Departments.INFECTIOUS_DISEASE,
                Departments.GERIATRICS, Departments.ONCOLOGY, Departments.ALLERGY,
            ),
        ),
        CaseGenerationGroup(
            label = "外科系统",
            color = 0xFF00695CL, darkColor = 0xFF80CBC4L,
            departments = listOf(
                Departments.GENERAL_SURGERY, Departments.ORTHOPEDICS, Departments.UROLOGY,
                Departments.THORACIC_SURGERY, Departments.NEUROSURGERY, Departments.CARDIAC_SURGERY,
                Departments.VASCULAR_SURGERY, Departments.BREAST_SURGERY, Departments.ANORECTAL_SURGERY,
                Departments.BURN,
                HEPATOBILIARY_SURGERY, PANCREATIC_SURGERY, SPINE_SURGERY, HAND_SURGERY,
                PEDIATRIC_SURGERY, PLASTIC_SURGERY,
            ),
        ),
        CaseGenerationGroup(
            label = "妇儿与生殖",
            color = 0xFFAD1457L, darkColor = 0xFFF48FB1L,
            departments = listOf(
                Departments.OBSTETRICS_GYNECOLOGY, Departments.PEDIATRICS,
                REPRODUCTIVE_MEDICINE, ANDROLOGY,
            ),
        ),
        CaseGenerationGroup(
            label = "五官皮肤口腔",
            color = 0xFF00838FL, darkColor = 0xFF80DEEAL,
            departments = listOf(
                Departments.ENT, Departments.OPHTHALMOLOGY, Departments.DERMATOLOGY,
                Departments.ORAL_MAXILLOFACIAL, ORAL_MEDICINE, DERMATOLOGIC_SURGERY,
            ),
        ),
        CaseGenerationGroup(
            label = "急危重症与麻醉",
            color = 0xFFC62828L, darkColor = 0xFFEF9A9AL,
            departments = listOf(
                Departments.EMERGENCY, CRITICAL_CARE, ANESTHESIOLOGY, PAIN_MEDICINE,
            ),
        ),
        CaseGenerationGroup(
            label = "精神康复与缓和",
            color = 0xFF5E6BC7L, darkColor = 0xFF9FA8DAL,
            departments = listOf(
                Departments.PSYCHIATRY, REHABILITATION, NUTRITION,
                SLEEP_MEDICINE, PALLIATIVE_CARE, GENERAL_PRACTICE,
            ),
        ),
        CaseGenerationGroup(
            label = "诊断与介入支撑",
            color = 0xFF6A1B9AL, darkColor = 0xFFCE93D8L,
            departments = listOf(
                NUCLEAR_MEDICINE, TRANSFUSION_MEDICINE, VASCULAR_INTERVENTION, CLINICAL_PHARMACY,
            ),
        ),
        CaseGenerationGroup(
            label = "职业与遗传",
            color = 0xFF5D4037L, darkColor = 0xFFBCAAA4L,
            departments = listOf(OCCUPATIONAL_MEDICINE, GENETIC_COUNSELING),
        ),
    )

    /** 54 项，去重后顺序稳定；GenerateCaseDialog 与筛选行的数据源。 */
    val all: List<String> = groups.flatMap { it.departments }.distinct()

    /** 24 个仅 AI 生成科室，用于 DepartmentMark / 文档 / 测试断言。 */
    val aiOnly: List<String> = all.filter { it !in Departments.all }

    /** 内置 30 科必须全部出现在分组里；由 CaseGenerationDepartmentsTest 断言。 */
    val builtIn: List<String> = Departments.all.filter { it != Departments.ALL }

    fun groupOf(department: String): CaseGenerationGroup? =
        groups.firstOrNull { department in it.departments }

    fun colorOf(department: String): Long = groupOf(department)?.color ?: 0xFF00796BL
    fun darkColorOf(department: String): Long = groupOf(department)?.darkColor ?: 0xFF80CBC4L

    /**
     * 科室症候提示，注入 PromptBuilder.buildCaseGenerationPrompt()，提升模型对新科室的生成质量。
     * 格式：典型症候 / 必问要点 / 红旗症状。缺项时回退到 groupHint。
     * 只写 24 个仅 AI 科室；内置 30 科沿用原有生成质量，不额外加 token。
     */
    private val hints: Map<String, String> = mapOf(
        HEPATOBILIARY_SURGERY to "典型症候：右上腹绞痛向右肩背放射、黄疸、发热寒战、陶土色便；必问：油腻饮食诱因、黄疸波动、既往胆石与胆道手术、饮酒、体重下降、凝血功能、抗生素用药史；红旗：Charcot 三联征、Reynolds 五联征、无痛性进行性黄疸、休克、意识改变。",
        PANCREATIC_SURGERY to "典型症候：上腹持续性剧痛向腰背呈束带样放射、恶心呕吐、腹胀、脂肪泻、消瘦、新发血糖升高；必问：暴饮暴食与饮酒、胆石病史、高甘油三酯、腹痛与体位关系、胰酶与影像、既往急性胰腺炎发作次数、止痛药使用；红旗：持续性剧烈腹痛伴休克、Grey-Turner/Cullen 征、黄疸、无痛性胰腺肿块伴体重下降。",
        SPINE_SURGERY to "典型症候：颈肩腰腿痛伴肢体麻木放射、间歇性跛行、行走无力、大小便功能改变；必问：疼痛节段与皮节分布、外伤与负重诱因、保守治疗经过、夜间痛、鞍区麻木、二便障碍、既往脊柱手术与骨质疏松；红旗：马尾综合征、进行性肌力下降、夜间静息痛伴体重下降、发热伴脊柱压痛、病理骨折。",
        HAND_SURGERY to "典型症候：手部外伤后肿痛畸形、活动受限、麻木、伤口渗液、屈伸障碍；必问：受伤机制与器械、时间、污染环境、破伤风免疫史、利手与职业、感觉与血运、主动活动度、既往手部手术；红旗：开放性骨折、肌腱断裂、指端缺血、气性坏疽征象、高压注射伤、间隙综合征。",
        PEDIATRIC_SURGERY to "典型症候：小儿反复腹痛呕吐、腹部包块、便秘或胎便排出延迟、腹股沟可复性肿物、新生儿胆汁性呕吐；必问：胎龄与出生史、喂养与体重曲线、呕吐是否含胆汁、排便排气、母亲孕期用药、家族先天畸形、按年龄换算的生命体征；红旗：胆汁性呕吐、果酱样血便、腹胀伴休克、嵌顿疝、脱水与电解质紊乱、意识改变。",
        PLASTIC_SURGERY to "典型症候：创面不愈合、瘢痕挛缩影响功能、皮肤软组织缺损、烧创伤后畸形、体表肿物；必问：创面成因与时间、感染与换药经过、既往手术与放疗、糖尿病与吸烟、瘢痕体质、功能受限程度、供区情况；红旗：创面快速扩大伴发热、坏死性筋膜炎征象、恶性肿瘤复发、植皮/皮瓣血运障碍。",
        REPRODUCTIVE_MEDICINE to "典型症候：规律同房未避孕一年未孕、月经稀发或闭经、排卵期出血、反复流产、男方精液异常；必问：双方年龄与备孕时长、月经周期与基础体温、既往妊娠与流产、盆腔炎/结核/子宫内膜异位症史、男方腮腺炎与隐睾史、促排卵用药、体重与甲状腺症状、职业毒物接触；红旗：闭经伴泌乳、卵巢过度刺激综合征表现、异位妊娠相关腹痛与阴道出血、染色体异常家族史。",
        ANDROLOGY to "典型症候：勃起功能障碍、早泄、血精、阴囊坠痛或包块、排尿异常、不育；必问：起病缓急与情境、晨勃、性欲与心理压力、糖尿病/高血压/心血管用药、吸烟饮酒、腮腺炎与隐睾史、阴囊外伤与手术、精索静脉曲张、睾酮相关症状；红旗：突发阴囊剧痛（睾丸扭转）、无痛性睾丸肿块、血精伴体重下降、垂体瘤相关视野缺损与头痛。",
        ORAL_MEDICINE to "典型症候：复发性口腔溃疡、口干眼干、牙龈出血肿胀、牙痛冷热刺激、口腔黏膜白色斑块或色素沉着、味觉改变；必问：溃疡周期与个数、义齿与修复体、刷牙与吸烟槟榔史、干燥综合征相关眼干与关节痛、免疫抑制用药、近期感染与抗生素、咬合创伤、糖尿病；红旗：溃疡超过 2 周不愈伴硬结、黏膜白斑/红斑伴糜烂、张口受限、颈部无痛性淋巴结肿大、快速肿胀伴气道受压。",
        DERMATOLOGIC_SURGERY to "典型症候：体表肿物渐大、色素痣形态改变、反复破溃出血、皮肤肿瘤术后缺损、腋窝或腹股沟肿物；必问：出现时间与生长速度、ABCDE 变化、日晒与职业暴露、既往皮肤癌与家族史、免疫抑制状态、外伤与感染、既往切除病理、淋巴结情况；红旗：快速增大伴破溃出血、色素不均与边界不规则、卫星灶、区域淋巴结肿大、免疫抑制患者的侵袭性皮损。",
        CRITICAL_CARE to "典型症候：呼吸困难需高流量吸氧、意识改变、血压下降少尿、乳酸升高、多器官功能不全、机械通气或血管活性药依赖；必问：入 ICU 主因与时序、感染灶与培养、既往基础病与功能状态、用药与过敏、插管与置管情况、镇静镇痛与谵妄、液体与出入量、家属治疗目标沟通；红旗：脓毒性休克、ARDS 进行性低氧、意识骤降伴瞳孔变化、恶性心律失常、消化道大出血、DIC。",
        ANESTHESIOLOGY to "典型症候：术前评估发现困难气道、未控制高血压、近期心梗或卒中、恶性高热家族史、麻醉后苏醒延迟；必问：既往麻醉与不良反应、气道解剖与张口度、禁食时间、当前用药（抗凝/降糖/降压）、过敏史与乳胶暴露、OSA 与打鼾、妊娠、义齿与反流、ASA 分级依据；红旗：困难气道无法通气、恶性高热征象、术中知晓、局麻药中毒、误吸、术后呼吸抑制。",
        PAIN_MEDICINE to "典型症候：慢性疼痛超过三个月、带状疱疹后神经痛、癌痛爆发、复杂性区域疼痛综合征、镇痛药剂量递增；必问：疼痛部位性质与 NRS 评分、时间节律与诱因、感觉异常与痛觉过敏、既往手术与放疗、心理与睡眠、阿片类用药剂量与副作用、介入治疗经过、功能与工作受限；红旗：新发剧烈头痛伴神经功能缺损、夜间痛伴体重下降、脊髓压迫征象、发热伴硬膜外脓肿风险、阿片过量呼吸抑制。",
        REHABILITATION to "典型症候：卒中后偏瘫与吞咽障碍、脊髓损伤后运动感觉平面、关节置换术后活动受限、失语与认知下降、长期卧床并发症；必问：原发病与发病时间、功能分级与 ADL、肌力肌张力与痉挛、吞咽与营养、压疮与深静脉血栓、疼痛、膀胱肠道管理、家庭照护与无障碍环境、既往康复方案；红旗：新发神经功能恶化、吞咽误吸伴发热、下肢肿痛伴呼吸困难、异位骨化、自主神经反射异常、深部压疮感染。",
        NUTRITION to "典型症候：非自愿体重下降、食欲减退、消瘦或肥胖伴代谢异常、术后或肿瘤相关营养不良、长期肠外营养；必问：体重变化幅度与时间、膳食结构与进食量、消化道症状与吸收不良、基础病与手术、白蛋白与前白蛋白、吞咽功能、药物与营养补充剂、活动量、社会经济与备餐条件；红旗：体重 6 个月内下降超过 10%、严重低蛋白伴水肿、再喂养综合征风险、短肠综合征、吞咽困难伴误吸。",
        SLEEP_MEDICINE to "典型症候：打鼾伴呼吸暂停、日间嗜睡、失眠入睡困难或早醒、不宁腿、夜间磨牙、晨起头痛；必问：睡眠日记与作息、Epworth 评分、 witnessed apnea、体位与饮酒镇静药、鼻塞与扁桃体、体重与颈围、高血压与房颤、情绪与压力、倒班与驾驶史；红旗：驾驶或操作机械时嗜睡、夜间心律失常与长间歇、晨起高血压危象、发作性猝倒、呼吸暂停伴低氧血症。",
        PALLIATIVE_CARE to "典型症候：晚期肿瘤或终末期器官病的难治性疼痛、呼吸困难、恶心呕吐、谵妄、厌食与恶病质、焦虑抑郁；必问：疾病分期与预后理解、症状负担与缓解因素、当前镇痛方案与突破痛、营养与吞咽、意识波动、心理与灵性困扰、家属照护能力、预立医疗照护计划与治疗目标、住院与转诊偏好；红旗：难治性呼吸困难、高钙血症、脊髓压迫、癫痫大发作、大出血、谵妄伴自伤风险。",
        GENERAL_PRACTICE to "典型症候：未分化主诉（乏力、头晕、低热、体重变化、多处不适）、多病共存多重用药、反复就诊、健康体检异常；必问：症状起病与演变、既往病史与用药清单、家族史与生活方式、职业与心理社会因素、预防接种与筛查、依从性、红旗症状逐系统筛查、求医期望与随访安排；红旗：隐匿性恶性肿瘤线索、心衰或心律失常、未控制高血压/糖尿病、抑郁伴自杀意念、跌倒与认知下降、药物相互作用与不良反应。",
        NUCLEAR_MEDICINE to "典型症候：甲亢拟行碘131治疗、甲状腺癌术后随访、骨痛待骨显像、肾功能分侧评估、PET-CT 发现的占位；必问：甲状腺功能与抗甲状腺药、碘摄入与近期造影剂、妊娠哺乳、手术与病理、放射性核素治疗史、肾功能与排尿、血糖与空腹要求、辐射防护与密切接触者（儿童/孕妇）；红旗：甲状腺风暴征象、未控制的甲亢伴房颤、疑似骨转移伴脊髓压迫、放射性碘治疗后颈部压迫、妊娠期误暴露。",
        TRANSFUSION_MEDICINE to "典型症候：输血中或输血后发热寒战、皮疹瘙痒、腰背痛与血红蛋白尿、呼吸困难、输血无效或迟发性溶血；必问：输血指征与血红蛋白、既往输血与妊娠史、血型与交叉配血、输血开始时间与速度、既往输血反应、冷凝集与自身抗体、用药（尤其降压与抗组胺）、标本采集与核对流程；红旗：急性溶血反应、TRALI、TACO、过敏性休克、细菌污染脓毒性反应、大量输血相关凝血病与低钙。",
        VASCULAR_INTERVENTION to "典型症候：下肢间歇性跛行或静息痛、肢体发凉苍白、透析通路失功、咯血或血尿待栓塞、动脉瘤随访、术后穿刺点血肿；必问：ABI 与步行距离、吸烟糖尿病血脂高血压、既往支架/搭桥与抗血小板药、肾功能与造影剂过敏、凝血功能、穿刺径路与制动、症状突然加重时间、足部溃疡与感染；红旗：急性肢体缺血 6P、腹主动脉瘤先兆破裂、造影剂外渗与筋膜室高压、穿刺点假性动脉瘤、对比剂肾病、远端栓塞。",
        CLINICAL_PHARMACY to "典型症候：多重用药、疑似药物不良反应、抗凝出血或血栓、抗菌药物治疗失败、肝肾功能不全剂量不当、依从性差；必问：完整用药清单（含 OTC 与中成药）、起用与加量时间、剂量间隔与疗程、肝肾功能与体重、药物相互作用与食物、过敏史与既往 ADR、血药浓度与 INR、漏服与自行停药、妊娠哺乳；红旗：华法林/DOAC 大出血、QT 延长尖端扭转型室速、5-羟色胺综合征、恶性综合征、抗生素相关艰难梭菌腹泻、胰岛素过量低血糖昏迷。",
        OCCUPATIONAL_MEDICINE to "典型症候：职业接触后咳嗽气短、皮疹、听力下降、肢体麻木、肝功能异常、反复头晕乏力；必问：岗位与工龄、接触物名称与 SDS、防护与通风、同事类似发病、班次与加班、脱离岗位后是否缓解、既往体检与职业病筛查、个人防护依从、吸烟与基础病、工伤与劳动关系资料；红旗：急性中毒（有机磷/一氧化碳/硫化氢）、尘肺进行性呼吸困难、职业性肿瘤线索、噪声性听力骤降、铅/汞/苯的神经系统与血液系统损害。",
        GENETIC_COUNSELING to "典型症候：反复流产或死胎、多发畸形或发育迟缓、家族中多人早发同类疾病、肿瘤家族聚集、婚前/产前筛查异常、染色体或基因检测报告解读；必问：三代家族史与发病年龄、血缘关系与民族、孕产史与 exposures、患儿表型与发育里程碑、既往基因检测与变异位点、父母携带者筛查、再发风险与心理承受、知情同意与检测局限、生育选择；红旗：已知致病变异携带、超声多发结构异常、新生儿代谢危象、早发心肌病或猝死家族史、遗传性肿瘤综合征（BRCA/Lynch）、染色体微缺失微重复。",
    )

    /** 8 个分组的默认提示，用于内置 30 科或 hints 缺项时兜底。 */
    private val groupHints: Map<String, String> = mapOf(
        "内科系统" to "典型症候以该内科系统的常见主诉为主；必问起病时间、诱因、伴随症状、既往史与用药史、过敏史、危险因素；红旗症状包括意识改变、呼吸困难、胸痛、大出血、休克、持续高热。",
        "外科系统" to "典型症候以该外科系统的急慢性局部症状为主；必问外伤/手术史、疼痛部位性质与放射、时间窗、进食与排便、既往手术与抗凝用药；红旗包括腹膜刺激征、进行性肿胀、肢体缺血、张力性气胸、意识改变。",
        "妇儿与生殖" to "按患者年龄与性别选择符合生理阶段的病史要素；必问月经/孕产/生长发育/喂养史、用药与疫苗接种、家族史；红旗包括阴道大出血、异位妊娠、胆汁性呕吐、脱水与意识改变。",
        "五官皮肤口腔" to "以局部症状与功能改变为主诉；必问起病缓急、单双侧、视力/听力/张口/吞咽变化、局部外伤与感染、过敏与用药；红旗包括视力骤降、气道受压、快速扩展的蜂窝织炎、颈部肿物。",
        "急危重症与麻醉" to "强调时间窗与生命体征；必问发病时刻、气道与呼吸循环、基础病与长期用药、过敏史、禁食与近期治疗；红旗包括休克、低氧、意识骤降、恶性高热、误吸。",
        "精神康复与缓和" to "兼顾躯体症状与功能/心理社会维度；必问病程、睡眠、情绪与认知、功能分级、照护与家庭支持、当前用药与副作用；红旗包括自伤意念、谵妄、误吸、脊髓压迫、深静脉血栓。",
        "诊断与介入支撑" to "以检查/治疗前后的评估与不良反应为主诉；必问检查或操作指征与时点、造影剂/放射/输血/药物暴露、肝肾功能与过敏史、妊娠、核对与防护流程；红旗包括过敏/溶血反应、对比剂肾病、急性肢体缺血、辐射误暴露。",
        "职业与遗传" to "强调暴露史与家族史；必问接触物与工龄、防护、同事发病、脱离后变化、三代家族史与发病年龄、既往筛查与基因检测；红旗包括急性中毒、早发猝死家族史、多发畸形、遗传性肿瘤综合征。",
    )

    /** PromptBuilder 用的症候提示：优先科室专属，其次分组默认，最后通用兜底。 */
    fun hintFor(department: String): String = hints[department]
        ?: groupOf(department)?.let { groupHints[it.label] }
        ?: "按该科室常见主诉与鉴别要点编写，必问起病时间、诱因、伴随症状、既往史、用药史与过敏史，并给出该科室的红旗症状。"
}
