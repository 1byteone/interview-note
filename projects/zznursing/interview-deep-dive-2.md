# 智颐养老系统 — 代码级深挖（第二弹）：入住·预约·设备数据·OSS·定时任务

> 交付日期：2026-08-20
> 项目路径：`D:\code\codeJava\heima-phase4\zznursing`
> 定位：第一弹覆盖简历 3 条（AI/IoT/登录），本弹深挖**面试官第二层追问**涉及的所有代码细节

---

## 📋 覆盖范围（本次新增 5 大模块）

| 模块 | 核心类 | 面试价值 |
|------|--------|----------|
| 入住办理 | `CheckInServiceImpl.apply()` | 多表事务 + 业务编排 |
| 预约管理 | `ReservationServiceImpl` + Mapper | 复杂 SQL + 业务规则 |
| 设备数据 | `DeviceDataServiceImpl.batchInsertDeviceData` | IoT 数据落地 |
| OSS 文件 | `AliyunOSSOperator` + `PDFUtil` | 文件存储链路 |
| 定时任务 | `ContractTask` + Quartz | 调度机制 |

---

## 🔴 第一组：入住办理（多表事务经典场景）

### 代码事实

```java
public void apply(CheckInApplyDto dto) {
    // 1. 校验老人是否已入住（身份证号 + status=1）
    Elder elder = elderMapper.selectOne(
        new LambdaQueryWrapper<Elder>()
            .eq(Elder::getIdCardNo, dto.getCheckInElderDto().getIdCardNo())
            .eq(Elder::getStatus, 1));
    if (elder != null) throw new BaseException("该老人已经入住");

    // 2. 更新床位状态为已入住
    Bed bed = bedMapper.selectById(dto.getCheckInConfigDto().getBedId());
    bed.setBedStatus(1);
    bedMapper.updateById(bed);

    // 3. 保存或更新老人
    elder = insertOrUpdate(bed, dto.getCheckInElderDto());

    // 4. 生成合同编号 HT + CodeGenerator
    String contractNo = "HT" + CodeGenerator.generateContractNumber();

    // 5. 新增合同
    insertContract(contractNo, elder, dto);

    // 6. 新增入住记录
    CheckIn checkIn = insertCheckInfo(elder, dto);

    // 7. 新增入住配置
    insertCheckInConfig(checkIn.getId(), dto);
}
```

### 面试官深剖

#### 题目 1「阿里级别」：apply() 操作了 4 张表（elder/bed/contract/check_in/config），怎么保证原子性？为什么面试官要重点问这个？

**答案**：
```java
// 事务入口 —— 关键！apply() 方法如果没加 @Transactional，就是大坑
// 你项目中实际是 Controller 或 Service 层调用的？
// 多表操作必须 @Transactional(rollbackFor = Exception.class)
```

**追问链**：
```
面试官：你 apply() 里操作了4张表，中途第3步失败了怎么办？
    ↓
你：必须在 apply() 上标注 @Transactional，让 4 张表的操作在同一事务中，任何一步失败全部回滚
    ↓
面试官：为什么有些开发者加了 @Transactional 还是回滚不了？
    ↓
你：① 同类内部调用 this.apply() 不会走代理，事务失效 ② 捕获了异常没抛出，事务认为成功 ③ rollbackFor 没配 Exception.class，只默认回滚 RuntimeException
    ↓
面试官：你这里床位状态更新先于合同生成，如果床位已被人占了怎么办？
    ↓
你：应该在事务内加锁（SELECT ... FOR UPDATE / 乐观锁版本号），确认床位可用再更新，避免并发入住同一床位
```

**加分回答**：承认这里可能没有做并发控制，并主动提出改进方案 → "如果并发两个请求同时入住同一床位，会有超住风险。改进方案：对 bed 行加悲观锁或乐观锁，或用 Redis 分布式锁。"

#### 题目 2「美团级别」：合同编号这个字符串怎么生成的？为什么？

**代码事实**：
```java
String contractNo = "HT" + CodeGenerator.generateContractNumber();
```

**答案**：
- 前缀 `HT`（合同 HeTong 拼音首字母）+ 时间戳/随机数
- 作用：业务唯一标识，方便人眼识别（区别于纯数字 ID）

**追问链**：
```
面试官：为什么不用数据库自增 ID 当合同号？
    ↓
你：① 自增 ID 暴露业务量（别人能猜出你是第N个客户） ② 格式不可定制 ③ 分布式环境下全局唯一难保证
    ↓
面试官：如果两个请求同时生成合同号，会重复吗？
    ↓
你：CodeGenerator 如果基于时间戳+随机数，冲突概率极低；更严谨的方案是雪花算法（Snowflake）或 Redis INCR + 日期前缀
```

---

## 🔴 第二组：预约管理（复杂 SQL + 业务规则）

### 代码事实

```java
// 取消预约次数限制（同一天取消次数）
@Select("select count(1) from reservation where status = 2 and update_by = #{userId} " +
        "and update_time between #{startTime} and #{endTime}")
int getCancelledReservationCount(...);

// 每个时间段剩余预约次数（固定每时段6个名额）
@Select("select time, 6 - count(1) as count from reservation " +
        "where status != 2 and `time` between #{startTime} and #{endTime} group by `time`")
List<Map<String, Integer>> countByTime(...);
```

### 面试官深剖

#### 题目 3「美团/阿里级别」：预约系统的核心业务规则是什么？怎么在 SQL 里实现的？

**答案**：
- **规则 1**：每个时间段最多预约 6 人 → `6 - count(1)` 就是剩余名额
- **规则 2**：同一用户当天取消预约次数有限制 → `count(status=2)` 统计
- **规则 3**：已取消的预约（status=2）不占名额 → `status != 2`

**追问链**：
```
面试官：`6 - count(1)` 这个写法在高并发下会超卖吗？
    ↓
你：会！两个请求同时读到 count=5，都算出剩余1，都下单 → 超卖。需要：① 数据库层唯一约束 ② UPDATE ... WHERE 剩余名额>0 的原子写法 ③ Redis 预扣名额
    ↓
面试官：如果改成 Redis 实现名额预扣，怎么设计？
    ↓
你：① 每天初始化 Redis Hash（时间段→名额） ② 下单时 DECR 原子减 ③ 负数则拒绝 ④ 取消预约时 INCR 回补
    ↓
面试官：`update_by` 存的是用户ID吗？为什么不用 user_id？
    ↓
你：这是 MyBatis-Plus 自动填充（createBy/updateBy）的结果，框架约定。实际上业务上应该用独立字段存预约人ID更清晰
```

**加分点**：主动说出"当前实现有并发超卖隐患，我会用 Redis 预扣 + 数据库唯一约束兜底"——这是面试官最想听到的答案。

---

## 🔴 第三组：设备数据落地（IoT 数据血缘）

### 代码事实

```java
public void batchInsertDeviceData(IotMsgNotifyData iotMsgNotifyData) {
    String iotId = iotMsgNotifyData.getHeader().getDeviceId();
    // 1. 根据 iotId 查本地设备
    Device device = deviceMapper.selectOne(
        Wrappers.<Device>lambdaQuery().eq(Device::getIotId, iotId));
    if (device == null) { log.error("设备不存在"); return; }

    // 2. 遍历 services（设备可能有多个服务），每个服务有多个属性
    iotMsgNotifyData.getBody().getServices().forEach(s -> {
        Map<String,Object> properties = s.getProperties();
        if (properties.isEmpty()) return;

        // 3. UTC 时间转上海时间
        LocalDateTime eventTime = DateTimeZoneConverter.utcToShanghai(
            LocalDateTimeUtil.parse(s.getEventTime(), "yyyyMMdd'T'HHmmss'Z'"));

        // 4. 一个属性一条 device_data 记录
        List<DeviceData> list = new ArrayList<>();
        properties.forEach((k, v) -> {
            DeviceData deviceData = BeanUtil.toBean(device, DeviceData.class);
            deviceData.setId(null);
            deviceData.setAlarmTime(eventTime);
            deviceData.setFunctionId(k);
            deviceData.setDataValue(v.toString());
            list.add(deviceData);
        });

        // 5. 批量入库 + 写 Redis 最新数据
        saveBatch(list);
        redisTemplate.opsForHash().put(
            CacheConstants.IOT_DEVICE_LAST_DATA, device.getIotId(), JSONUtil.toJsonStr(list));
    });
}
```

### 面试官深剖

#### 题目 4「华为/字节级别」：设备数据一条条插还是批量插？性能瓶颈在哪？为什么用 saveBatch？

**答案**：
- `saveBatch(list)` 是 MyBatis-Plus 批量插入，一条 SQL 插入多条
- 性能对比：单条 insert 1000 次（网络往返 1000 次）vs 批量 insert 一次（网络往返 1 次）

**追问链**：
```
面试官：设备上报数据频率很高（比如每秒10次），批量插入还有瓶颈吗？
    ↓
你：① 数据量大了建议先攒批（比如攒100条或1秒内的一起插） ② 或用 MQ 削峰，异步批量落库 ③ device_data 表数据量会暴涨，建议按月分表 + 定期归档
    ↓
面试官：Redis 存最新数据，和 MySQL 存历史数据，怎么保证查最新值不读旧值？
    ↓
你：① 上报时同时写 MySQL 和 Redis（当前实现） ② 查询最新值优先读 Redis ③ 用 Redis 的 Hash，同一设备的最新值覆盖旧值
    ↓
面试官：如果 saveBatch 失败（MySQL 挂了），Redis 还写吗？数据一致性怎么保证？
    ↓
你：当前实现是先 saveBatch 再写 Redis——如果 MySQL 失败会抛异常，Redis 不写（同线程）。但这不是强一致，理想方案是：先写 Redis 最新值（快速查询），再异步确保 MySQL 落库，MySQL 失败用死信重试
```

**加分回答**：主动指出 currentTimeMillis() 的 UTC 转上海时间细节（`DateTimeZoneConverter.utcToShanghai`）——显示你对时区问题的敏感度。

---

## 🔴 第四组：OSS 文件上传 + PDF 解析（文件链路）

### 代码事实

```java
// 上传体检报告
public AjaxResult uploadFile(@RequestPart("file") MultipartFile file,
                             @RequestPart("idCard") String idCardNo) {
    // 1. 上传 OSS
    String url = aliyunOSSOperator.upload(file.getBytes(), file.getOriginalFilename());
    // 2. PDF 内容提取成文本
    String content = PDFUtil.pdfToString(file.getInputStream());
    // 3. 存入 Redis Hash(healthReport, idCard, content) + 24h 过期
    redisTemplate.opsForHash().put("healthReport", idCardNo, content);
    redisTemplate.expire("healthReport", 24, TimeUnit.HOURS);
    return ajax;
}

// OSS 上传核心
// 目录：yyyy/MM/  +  UUID文件名（保留扩展名）
// 凭证：EnvironmentVariableCredentialsProvider（环境变量 OSS_ACCESS_KEY_ID/SECRET）
```

### 面试官深剖

#### 题目 5「阿里级别」：OSS 上传设计有什么可以优化的？文件重名怎么避免？凭证安全吗？

**答案**：
- **防重名**：`UUID.randomUUID() + 原始扩展名`，天然唯一
- **目录隔离**：按年月分目录（`2026/08/`），方便归档
- **凭证安全**：用环境变量注入 AK/SK，**不硬编码在代码里**（大加分！很多项目把 AK/SK 写在配置文件/代码里，面试官很看重这一点）

**追问链**：
```
面试官：上传文件到 OSS 的流程，和直接存本地磁盘比有什么优缺点？
    ↓
你：OSS：① 不限本地磁盘空间 ② 自带 CDN 加速 ③ 持久化容灾 ④ 成本略高。本地：快但容量有限、易丢
    ↓
面试官：体检报告是敏感数据，存 OSS 要考虑什么？
    ↓
你：① Bucket 私有访问 + 签名 URL（临时授权） ② 传输 HTTPS 加密 ③ 服务端加密 ④ 严格访问控制（只有护理人员角色能上传）
    ↓
面试官：PDF 解析可能失败（扫描件/图片型PDF），你的 PDFUtil 能处理吗？
    ↓
你：不能！PDFTextStripper 只能提取文本型 PDF。扫描件需要用 OCR（如 Tesseract、PaddleOCR）。这是一个**可以优化的点**
```

**加分回答**：主动说"扫描版体检报告需要接 OCR"——展示对真实业务场景的理解。

---

## 🔴 第五组：定时任务（Quartz + 业务补偿）

### 代码事实

```java
// ContractTask —— 定时更新合同状态
public void updateContractStatusTask() {
    contractService.updateContractStatus();
    log.info("定时更新合同状态成功！");
}

// ContractServiceImpl.updateContractStatus()
// 状态0（待生效）→ 状态1（生效中）
// 条件：开始时间 <= now <= 结束时间
List<Contract> list = list(Wrappers.<Contract>lambdaQuery()
    .eq(Contract::getStatus, 0)
    .le(Contract::getStartDate, LocalDateTime.now())
    .ge(Contract::getEndDate, LocalDateTime.now()));
list.forEach(item -> item.setStatus(1));
updateBatchById(list);
```

### 面试官深剖

#### 题目 6「美团级别」：合同状态为什么用定时任务扫，而不是用户访问时实时算？定时任务有什么坑？

**答案**：
- **为什么定时扫**：状态从"待生效"变"生效"是时间驱动的，用户在某个时刻触达时才更新会有体验差异（不同人看到的状态不同）
- **为何选 Quartz**：RuoYi 内置，支持 cron 表达式、任务日志、集群部署

**追问链**：
```
面试官：定时任务如果上一个还没执行完，下一个又开始了，怎么办？
    ↓
你：Quartz 有 @DisallowConcurrentExecution 注解，防止同一任务并发执行
    ↓
面试官：如果你的服务多实例部署，每个实例都会执行这个定时任务，会重复处理吗？
    ↓
你：会！单机 Quartz 只支持单实例。多实例需要：① 集成 Quartz 集群（数据库锁） ② 或用分布式锁（Redis SETNX）保证只有一个实例执行 ③ 或升级用 xxl-job / ElasticJob
    ↓
面试官：合同过期了（endDate < now）状态怎么处理？当前实现关注了吗？
    ↓
你：当前只处理"待生效→生效"，应该补充"生效→已到期"的状态流转，或者用计划任务在到期时自动变更
```

**加分回答**：主动提"当前实现只处理了单向流转，可以扩展状态机"。

---

## 📋 深入盘点：代码中的隐患（面试官深挖点）

> 面试官问"你项目有什么痛点/坑"时，主动讲这些 = 加分

| # | 隐患 | 位置 | 改进建议 |
|---|------|------|----------|
| 1 | apply() 多表操作事务/并发控制待加强 | `CheckInServiceImpl.apply()` | @Transactional + 床位乐观锁 |
| 2 | 预约为 `6 - count(1)` 存在超卖风险 | `ReservationMapper.countByTime` | Redis 预扣 + 唯一约束 |
| 3 | 定时任务多实例会重复执行 | `ContractTask` | 分布式锁 / xxl-job |
| 4 | 扫描件 PDF 无法解析 | `PDFUtil` | 接入 OCR |
| 5 | device_data 表会无限膨胀 | `DeviceDataServiceImpl` | 按月分表 + 归档 |
| 6 | 合同状态流转不完整（无"已到期"） | `ContractServiceImpl` | 状态机扩展 |
| 7 | CodeGenerator 合同号并发唯一性 | `CheckInServiceImpl` | 雪花算法 |

---

## 📋 一页速记卡（面试前 5 分钟）

```
智颐养老核心流程三句话：
① 健康评估 = PDF上OSS → PDFBox提取文本 → Redis缓存 → 千帆AI → 结构化JSON → 落库
② 设备数据 = 华为云AMQP → 线程池异步消费 → 批量入库 + Redis最新值
③ 入住办理 = 校验老人 → 更新床位 → 存老人 → 生成合同 → 存入住+配置（多表事务）

五个必答追问：
① ThreadLocal 为什么用 afterCompletion remove → 防内存泄漏
② 缓存为什么用 Hash 不是 String → 按身份证独立管理
③ 批量插入为什么用 saveBatch → 减少网络往返
④ AMQP 断连怎么办 → failover 重连
⑤ 预约占满会不会超卖 → Redis 预扣（改进点）
```

---

> 📎 配套文档：`zznursing/interview-qa.md`（第一弹）+ `interview-resume-matching.md`（简历匹配）
> 
> 💡 面试黄金法则：当面试官深挖到代码细节时，先展示"我知道这里有什么问题"，再给"我的改进方案"——这比完美代码更能打动面试官。