# NPC 家庭系统设计与实现文档

> 本文记录家庭系统（婚姻、生育、成长、搬迁、住宅户划分、居住指数）的完整实现状态。

---

## 一、数据模型

### CitizenData 新增字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `familyId` | `UUID?` | 当前所在家庭（结婚/组建时更新） |
| `originFamilyId` | `UUID?` | 出生时所在家庭，永不改变，用于族谱回溯 |
| `pregnant` | `boolean` | 是否怀孕 |
| `pregnantSince` | `long` | 怀孕开始的游戏日 |
| `reservedBabyBedPoiId` | `UUID?` | 怀孕时预约给婴儿的住宅床位 POI ID |

死亡时 `pregnant=false, pregnantSince=0, reservedBabyBedPoiId=null`；`familyId/originFamilyId` 保留不清空。

### FamilyData（`citizen/family/FamilyData.java`）

```java
familyId, cityId
husbandId, wifeId        // 死亡后不清空
List<UUID> childIds       // 子女成年离家后移除
paternalFamilyId          // 丈夫来源家庭
maternalFamilyId          // 妻子来源家庭
generation                // 从根起算的世代深度
FamilyStatus status       // FORMING / ACTIVE / DISSOLVED
```

### FamilyStatus

| 状态 | 含义 |
|---|---|
| `FORMING` | 尚未完整（单身或缺一方） |
| `ACTIVE` | 至少一方在世 |
| `DISSOLVED` | 夫妻双亡，数据保留 |

---

## 二、SQLite 存储

### 新建表

```sql
families(family_id PK, city_id, husband_id, wife_id,
         paternal_family_id, maternal_family_id, generation, status)
family_members(family_id, citizen_id, role, PRIMARY KEY(family_id, citizen_id))
```

### citizens 表列迁移

```sql
family_id TEXT, origin_family_id TEXT,
pregnant INTEGER DEFAULT 0, pregnant_since INTEGER DEFAULT 0
```

### 索引

```sql
CREATE INDEX IF NOT EXISTS idx_families_city ON families(city_id)
CREATE INDEX IF NOT EXISTS idx_family_members_citizen ON family_members(citizen_id)
```

---

## 三、核心服务

### FamilyManager（`citizen/family/FamilyManager.java`）

有状态单例（同 CitizenManager 模式），主存储 SQLite，NBT SavedData 兜底。

关键 API：

| 方法 | 说明 |
|---|---|
| `createFamily(level, cityId, husbandId, wifeId)` | 结婚时创建 ACTIVE 家庭，自动计算世代 |
| `createSingle(level, cityId, adultId, gender)` | 成年时创建 FORMING 家庭 |
| `addChild(level, familyId, childId)` | 添加子女 |
| `handleMemberDeath(level, familyId, citizenId)` | 更新家庭状态 |
| `leaveFamily(level, familyId, childId)` | 子女离家 |
| `getAncestorTree(familyId, maxDepth)` | 最多回溯10代族谱 |

世代上限：`generation > 10` 时 `paternalFamilyId/maternalFamilyId = null`，现有数据不删除。

---

## 四、生命周期服务（每游戏日 tick）

执行顺序（CitizenManager.tickFamilySystemsIfNewDay）：

```
NpcGrowthService → NpcChildbirthService → NpcPregnancyService → NpcMarriageService
```

### NpcGrowthService

- 每游戏日对所有存活 NPC `age += 1`
- 孩子：age ≥ 18 时成年（切换成人皮肤、建立 FORMING 家庭、分配住所）
- 成年人：age ≥ lifespan 时自然死亡（优先走 CitizenDeathService，实体不在线时直接标记）

### NpcPregnancyService

- 仅对 ACTIVE 家庭的妻子触发
- 条件：alive、!child、!pregnant、家庭所在建筑有空余且未被他人预约的住宅床位、有医院覆盖
- 每日掷骰 < `familyPregnancyChancePerDay`（默认0.10）
- 怀孕成功后立即将该床位记入 `reservedBabyBedPoiId`，防止多胎并发抢占同一张床

### NpcChildbirthService

- 条件：pregnant、currentDay ≥ pregnantSince + duration
- 分娩位置：优先使用 `reservedBabyBedPoiId` 对应的医院床（若已入院）或住宅坐标，无预约时兜底搜索建筑内空床
- 孩子：age=1，homeId=预约床位 POI ID，familyId=originFamilyId=父母家庭，子皮肤暂用成人皮肤库
- 分娩完成后清空妻子孕期状态（含 `reservedBabyBedPoiId`），广播出生消息

### NpcMarriageService

- 按城市分组，同城异性未婚（familyId=null）NPC 随机配对
- 每日掷骰 < `familyMarriageChancePerDay`（默认0.05）
- 结婚后：happiness+10、广播消息、触发 FamilyRelocationService.tryRelocate

---

## 五、年龄与寿命参数

| 参数 | 值 |
|---|---|
| 系统生成 NPC 初始年龄 | 18~25 岁 |
| 寿命范围 | 70~100 岁 |
| 出生孩子年龄 | 1 岁 |
| 成年年龄 | 18 岁 |
| 每游戏日老化 | +1 岁 |

---

## 六、居住指数与搬家

### HabitationIndexCalculator

```
居住指数 = (体积分 + 空置分 + 宜居分) / 3
宜居分 = 100 - 废弃指数
```

体积分分档（方块数）：100→10、100-499→30、500-999→50、1000-2999→70、3000+→100

空置分：空床数 / 总床位数 × 100

### FamilyRelocationService

触发点：结婚后、孩子出生后、每5游戏日定期检查。

预期需求床位数：

| 家庭状态 | 预期床位 |
|---|---|
| 单身 | 1 |
| 已婚无子女 | 4 |
| 1个孩子 | 4 |
| 2+个孩子 | 实际人数+1 |

选房：倾向分 = 居住指数 × 需求匹配系数，取最优建筑整体搬入。

### BuildingAbandonmentService（废弃指数）

| 触发 | 变化 |
|---|---|
| 居民死亡 | +30 |
| 每日自然恢复 | -1 |
| 全城最小建筑（持续） | 每日 +5 |
| 新居民入住 | 重置为 0 |

---

## 七、住宅户划分

### .sk 文件格式扩展

```
# 范围模式
unit: 居所, 0,0,0~8,13,5

# 点列表模式
unit: 居所, 2,5,4|7,5,4|2,9,4|7,9,4
```

坐标为相对建筑 anchor 的相对坐标。

### 数据类

- `BuildingUnitDefinition(label, min, max, points)` — 定义，来自 .sk 解析
- `BuildingUnitInstance(unitId, label, poiIds)` — 运行时实例，完工时生成

`PlacedBuildingRecord` 新增 `unitDefinitions` 和 `unitInstances` 字段。
`CityPoiData` 新增 `unitId` 字段（null=散户）。

### 家庭分配规则

1. **有 unit: 定义的建筑**：每户只允许一个家庭，户内任意床被占则跳过
2. **无 unit: 定义的建筑**：整栋楼视为一户，有任意床被占则跳过（一楼一家）
3. **Phase 2 单身分配**：在家庭分配后，剩余空床按原逻辑分配给单身 NPC

---

## 八、NPC信息界面新增字段

| 字段 | 逻辑 |
|---|---|
| 家庭 | 已婚→丈夫姓名+"家庭"；未婚→回溯 originFamilyId 的父亲家庭 |
| 家族 | 提取丈夫姓氏（支持复姓）+"氏家族"；未婚同上 |

---

## 九、可配置项（ServerConfig [family] 块）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `pregnancyDurationDays` | 3 | 怀孕持续游戏日 |
| `childGrowthDurationDays` | 7 | （已保留，当前由 age 系统控制） |
| `marriageChancePerDay` | 0.05 | 每游戏日结婚概率 |
| `pregnancyChancePerDay` | 0.10 | 每游戏日怀孕概率 |

以上均可在游戏内配置界面（家庭标签页）修改。

---

## 十、怀孕 NPC 保护规则

- 怀孕中不出现在雇佣列表（`CitizenService.isHireable` 过滤）
- 已在岗的怀孕 NPC 每日自动解雇（`NpcPregnancyService.tickPregnancies`）
- 怀孕状态头顶标签翻译：`"pregnant"` → "孕期中"

---

## 十一、城市事件消息（城市成员 HUD toast）

| 事件 | 消息 |
|---|---|
| 结婚 | `%s 和 %s 喜结连理！` |
| 出生 | `%s 在 %s 家出生了！` |
| 搬家 | `%s 一家搬入了 %s。` |

---

## 十二、人口自然增长随机化

- 每游戏日检查一次（原固定间隔改为每日检查）
- 每日命中概率：`timesPerWeek / 7`
- `populationGrowthTimesPerWeek`（默认2，范围1~7）可在配置界面"通用"标签页修改
