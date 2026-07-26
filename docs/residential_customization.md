# SimuKraft 住宅建筑 .sk 自定义教程

本文面向整合包开发者，说明如何编写住宅建筑的 `.sk` 元数据文件，包括床位 POI 声明和多户型划分。

## 文件位置

`.sk` 及配套 `.nbt` 放在建筑包 ZIP 内部，路径格式为：

```text
buildings/residential/<文件名>.sk
buildings/residential/<文件名>.nbt
```

建筑包 ZIP 存放于游戏目录的 `simukraftbuilding/` 文件夹：

```text
<gameDir>/simukraftbuilding/my_housing_pack.zip
```

开发环境对应：

```text
run/simukraftbuilding/my_housing_pack.zip
```

ZIP 内部结构示例：

```text
buildings/
  residential/
    cottage.sk
    cottage.nbt
    apartment.sk
    apartment.nbt
```

## .sk 文件格式

`.sk` 是纯文本文件，每行一个 `key: value`，`#` 开头为注释，空行忽略。

### 元数据字段

| 字段 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | 否 | 文件名（无扩展名） | 建筑显示名称 |
| `author` | 否 | `External` | 作者名称 |
| `size` | 否 | `-` | 建筑尺寸，格式 `宽x高x深`，例如 `9x8x7` |
| `amount` | 否 | `-` | 建造所需城市资金；`price` 为别名 |
| `description` | 否 | 空 | 建筑描述；`desc` 为别名 |
| `structure` | 否 | `<sk文件名>.nbt` | 对应 NBT 结构文件名；`file` 为别名 |

`structure` 虽然有默认值，但如果 ZIP 包内找不到对应 NBT 文件，该建筑会被直接丢弃。请确保 `.sk` 与 `.nbt` 文件名一致，或显式声明。

### 床位自动识别

住宅床位由系统扫描 NBT 结构中的**红色床**（`minecraft:red_bed`）自动识别，无需在 `.sk` 中声明任何 `poi:` 字段。系统只统计床头方块（`BED_PART=head`），一张床计为一个床位。

### unit: 户型划分（可选）

`unit:` 用于将一栋建筑拆分成多个独立居住单元。每个单元允许一个家庭入住；不写 `unit:` 时整栋建筑视为一户。

**范围模式：**

```
unit: <标签>, <minX>,<minY>,<minZ>~<maxX>,<maxY>,<maxZ>
```

**点列表模式：**

```
unit: <标签>, <x1>,<y1>,<z1>|<x2>,<y2>,<z2>|...
```

坐标均为相对建筑 anchor 的结构坐标，不是世界坐标。

| 参数 | 说明 |
| --- | --- |
| 标签 | 该单元的名称，例如 `一楼` `居所A` `Unit 1` |
| 坐标 | 范围模式填最小角到最大角；点列表模式填床头方块的逐个坐标 |

一个 `.sk` 可以写多行 `unit:`，每行一个独立单元。

家庭分配规则：

- 有 `unit:` 定义的建筑：每个单元只允许一个家庭，单元内有任意床位已被占则跳过该单元。
- 无 `unit:` 定义的建筑：整栋作为一户，有任意床位被占则整栋跳过（一楼一家）。
- 家庭分配完成后，剩余空床按原规则分配给单身市民。

## 完整示例

### 普通独栋住宅

```
# 单户住宅，一家人住整栋
name: 小木屋
author: kafei
size: 9x8x7
amount: 500
description: 精致木制小屋，可入住一户家庭
```

不写 `unit:` 时整栋为一户，适合独栋别墅、农舍等。

### 双层公寓（多户）

```
name: 双层公寓
author: kafei
size: 10x10x9
amount: 1200
description: 上下两层，每层独立居住，共四个床位

# 一楼户型（结构 Y=0~4 范围内的床位归属一楼家庭）
unit: 一楼, 0,0,0~9,4,8

# 二楼户型（结构 Y=5~9 范围内的床位归属二楼家庭）
unit: 二楼, 0,5,0~9,9,8
```

### 四单元公寓（点列表模式）

```
name: 四单元楼
author: kafei
size: 12x8x12
amount: 2000
description: 四个独立单元，每单元一张床

# 四个单元各指定自己床头方块的结构坐标
unit: 单元A, 1,1,1
unit: 单元B, 6,1,1
unit: 单元C, 1,1,6
unit: 单元D, 6,1,6
```

## 注意事项

- 住宅床位使用**红色床**（`minecraft:red_bed`），系统自动扫描 NBT 结构中的红床床头方块统计容量，无需 `poi:` 声明。
- `unit:` 的坐标必须覆盖对应单元内所有的住宅床位，才能正确归属家庭。
- 多户建筑建议每个单元内的床位数 ≥ 2，以便夫妻共住。
- 建筑完工登记后，系统会根据 `unit:` 定义生成运行时单元实例；修改 `.sk` 后需要重建该建筑才能生效。
- 字段名大小写不敏感，坐标中的空格会被忽略。
