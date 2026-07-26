# SimuKraft 医疗建筑自定义教程

本文面向整合包开发者，说明如何通过 `.sk` 配置医疗建筑，以及通过医疗 JSON 配置服务覆盖范围。

## 床位自动识别

医疗床位由系统扫描 NBT 结构中的**白色床**（`minecraft:white_bed`）自动识别，无需在 `.sk` 中声明任何 `poi:` 字段。系统只统计床头方块（`BED_PART=head`），一张床计为一个床位。

## 文件位置

`.sk`、`.nbt` 和可选的医疗 `.json` 放在建筑包 ZIP 内部，路径格式为：

```text
buildings/public/<文件名>.sk
buildings/public/<文件名>.nbt
buildings/public/<文件名>.json
```

建筑包 ZIP 存放于游戏目录的 `simukraftbuilding/` 文件夹：

```text
<gameDir>/simukraftbuilding/my_medical_pack.zip
```

开发环境对应：

```text
run/simukraftbuilding/my_medical_pack.zip
```

ZIP 内部结构示例：

```text
buildings/
  public/
    city_hospital.sk
    city_hospital.nbt
    city_hospital.json
    clinic.sk
    clinic.nbt
```

不写医疗 JSON 时，系统会使用默认配置（`serviceRangeRings=3`），建筑仍能正常运作。

### .sk 文件格式

### 元数据字段

| 字段 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | 否 | 文件名（无扩展名） | 建筑显示名称 |
| `author` | 否 | `External` | 作者名称 |
| `size` | 否 | `-` | 建筑尺寸，格式 `宽x高x深` |
| `amount` | 否 | `-` | 建造所需城市资金；`price` 为别名 |
| `description` | 否 | 空 | 建筑描述；`desc` 为别名 |
| `structure` | 否 | `<sk文件名>.nbt` | 对应 NBT 结构文件；`file` 为别名 |

### medical: 显式指定 JSON（可选）

若医疗 JSON 文件名与 `.sk` 不同，可以在 `.sk` 中显式声明：

```
medical: my_hospital.json
```

不写时系统自动查找与 `.sk` 同名的 `.json`（如 `city_hospital.sk` → `city_hospital.json`）；两者均不存在时使用默认配置。

### 完整 .sk 示例

```
name: 市立医院
author: kafei
size: 15x12x14
amount: 3000
description: 城市中央医院，可容纳8名病患，覆盖全城区块
medical: city_hospital.json
```

---

## 医疗 JSON 格式

医疗 JSON 只需要定义三个字段：

```json
{
  "id": "city_hospital",
  "name": "市立医院",
  "serviceRangeRings": 4
}
```

### 字段说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | 字符串 | 否 | .sk 文件名或 `hospital` | 医疗建筑唯一标识符，用于日志和引用 |
| `name` | 字符串 | 否 | 与 `id` 相同 | 医疗控制箱 GUI 中显示的名称 |
| `serviceRangeRings` | 整数 | 否 | `3` | 服务覆盖圈数，范围 **[1, 6]** |

### serviceRangeRings 覆盖范围

`serviceRangeRings` 决定该医院能覆盖到多大范围的城市区块。覆盖到的区块内，居民才能获得医疗服务（怀孕前置条件、疾病治疗、医疗保障）。

| 值 | 区块矩阵 | 覆盖区块数 | 适用场景 |
| --- | --- | --- | --- |
| `1` | 1×1 | 1 | 极小型建筑，仅覆盖自身所在区块 |
| `2` | 3×3 | 9 | 小型诊所，覆盖周边一圈 |
| `3` | 5×5 | 25 | **默认值**，中型社区医院 |
| `4` | 7×7 | 49 | 较大医院，覆盖大部分中小城市 |
| `5` | 9×9 | 81 | 大型医院，适合扩张型城市 |
| `6` | 11×11 | 121 | **最大值**，超大型医院或城市核心医院 |

覆盖范围以医院控制箱所在区块为中心，向外以正方形扩展。超过 6 会被裁剪到 6，小于 1 会被裁剪到 1。

一个城市可以建多座医院，区块覆盖取并集；居民住宅区块只要被任意一座运营中的医院覆盖即可享受医疗服务。

---

## 加载优先级

1. `.sk` 里的 `medical:<file>.json` 显式声明
2. 与 `.sk` 同名的 `.json`
3. 以上均不存在时使用默认配置（`serviceRangeRings=3`，名称取 .sk 文件名）

---

## 完整示例

### 小型诊所（仅 .sk，无 JSON）

```
name: 社区诊所
author: kafei
size: 7x5x7
amount: 800
description: 小型诊所，覆盖周边区块，可入院2名病患
```

不写医疗 JSON 时默认 `serviceRangeRings=3`，适合大多数小型建筑。

### 大型医院（完整配置）

**city_hospital.sk：**

```
name: 市立医院
author: kafei
size: 15x12x14
amount: 5000
description: 城市核心医院，8个床位，覆盖半径较大
medical: city_hospital.json
```

**city_hospital.json：**

```json
{
  "id": "city_hospital",
  "name": "市立医院",
  "serviceRangeRings": 5
}
```

---

## 注意事项

- 医疗床位使用**白色床**（`minecraft:white_bed`）；住宅床位使用**红色床**（`minecraft:red_bed`），两者不能混用。
- 医院必须处于运营状态（已雇佣医生且控制箱正常）才算有效覆盖。
- 医院拆除时系统会自动释放所有在院病患的 `medicalBedPoiId`，无需手动处理。
- `serviceRangeRings` 超出 [1, 6] 范围会被自动修正并在日志中输出 WARN。
