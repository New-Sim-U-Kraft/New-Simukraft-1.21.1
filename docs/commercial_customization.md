# SimuKraft 商业商店 JSON 自定义教程

本文面向整合包作者，说明如何通过外部 JSON 定义商业建筑的 NPC 交易内容。商业控制盒只负责雇佣、营业状态和管理；玩家右键商业员工 NPC 时打开的购买界面会读取这里的商业 JSON，并按村民交易风格显示。

## 文件位置

开发环境中的外部商业 JSON 放在：

```text
run/simukraftbuilding/commercial/
```

整合包实际发布时，对应游戏目录是：

```text
<gameDir>/simukraftbuilding/commercial/
```

推荐让商业 JSON 与建筑 `.sk` 元数据同名：

```text
breadShop.sk
breadShop.nbt
breadShop.json
```

也可以在 `.sk` 文件中显式指定商业 JSON：

```text
commercial:custom_bakery.json
```

加载优先级：

1. `.sk` 里的 `commercial:<file>.json`
2. 与 `.sk` 同名的外部 JSON
3. 模组内置的 `assets/simukraft/commercial/<building>.json`

JSON 请保存为 UTF-8。Windows PowerShell 校验中文 JSON 时建议显式加 `-Encoding UTF8`。

## 顶层结构

商业 JSON 只需要定义商店名称、职业和报价列表：

```json
{
  "id": "breadShop",
  "name": "面包店",
  "job": {
    "id": "bread_shop_owner",
    "name": "面包店老板",
    "heldItem": "minecraft:bread"
  },
  "offers": []
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 建议 | 商业定义 ID，建议与建筑文件名一致 |
| `name` | 是 | 界面显示的商店名 |
| `job.id` | 是 | 商业员工写入市民数据的职业键 |
| `job.name` | 是 | 玩家看到的职业名 |
| `job.heldItem` | 否 | 商业员工默认手持物品 ID |
| `offers` | 是 | 交易报价列表，不能为空 |

旧版字段 `buildingId`、`shopMode`、`trades`、`buyTrades`、`sellPrice`、`buyPrice`、`materials`、`messages` 不会被新版商业加载器读取，不要再使用。

## 报价结构

每条报价都是 `cost -> result`：

```json
{
  "id": "shop_sells_bread",
  "visibleTo": "mixed",
  "cost": [{ "money": 0.25 }],
  "result": [{ "item": "minecraft:bread", "count": 1 }],
  "stock": {
    "item": "minecraft:bread",
    "max": 64,
    "initial": 32,
    "restockAmount": 32,
    "restockInterval": 12000
  }
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 报价 ID，同一个 JSON 内必须唯一 |
| `visibleTo` | 否 | `player`、`npc`、`mixed`，默认 `player` |
| `cost` | 是 | 交易成本，最多 4 项 |
| `result` | 是 | 交易产出，最多 4 项 |
| `stock` | 建议 | 该报价关联的库存物品规则 |

资源写法：

```json
{ "money": 20.0 }
```

```json
{ "item": "minecraft:oak_log", "count": 64 }
```

规则：

- `money` 必须大于 0。
- `item` 必须是已注册物品 ID。不要写只有模型但没有注册的物品。
- `count` 最小为 1；省略时按 1 处理。
- 一条报价的 `cost` 和 `result` 都不能为空。

## 三个交易 Tab

界面左侧的 Tab 不需要在 JSON 里写字段，系统会按 `cost` 和 `result` 自动归类：

| Tab | JSON 形态 | 含义 |
| --- | --- | --- |
| 出售 | `cost` 有资金，`result` 有物品 | 商店向玩家出售物品 |
| 收购 | `cost` 有物品，`result` 有资金 | 商店收购玩家物品 |
| 交换 | `cost` 和 `result` 都是物品，且没有资金 | 以物换物 |

示例：

```json
{
  "id": "shop_buys_wheat",
  "visibleTo": "player",
  "cost": [{ "item": "minecraft:wheat", "count": 16 }],
  "result": [{ "money": 3.20 }],
  "stock": { "item": "minecraft:wheat", "max": 256 }
}
```

```json
{
  "id": "exchange_wheat_for_bread",
  "visibleTo": "player",
  "cost": [{ "item": "minecraft:wheat", "count": 3 }],
  "result": [{ "item": "minecraft:bread", "count": 1 }],
  "stock": { "item": "minecraft:bread", "max": 64 }
}
```

## visibleTo

`visibleTo` 决定报价给谁使用：

| 值 | 玩家界面可见 | NPC 自动经营可用 |
| --- | --- | --- |
| `player` | 是 | 否 |
| `npc` | 否 | 是 |
| `mixed` | 是 | 是 |

通常建议：

- 商店出售给玩家的商品用 `mixed`，这样 NPC 自动经营也能产生营业收入。
- 商店收购玩家物品用 `player`，避免 NPC 自动经营持续消耗城市资金。
- 以物换物一般用 `player`。

## 库存规则

`stock` 控制某个物品的库存：

```json
{
  "item": "minecraft:bread",
  "max": 64,
  "initial": 32,
  "restockAmount": 16,
  "restockInterval": 12000
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `item` | 库存物品 ID |
| `max` | 最大库存 |
| `initial` | 首次创建商业箱库存时的初始库存 |
| `restockAmount` | 每次补货增加数量 |
| `restockInterval` | 补货间隔，单位为服务器运行 tick |

库存原理：

- `cost` 里的物品会进入商店库存。
- `result` 里的物品会离开商店库存。
- 资金写在 `cost` 时会扣玩家所属城市资金；资金写在 `result` 时会存入玩家所属城市资金。
- 补货按 `level.getGameTime()` 计算服务器运行 tick 间隔，不受 `/time set` 影响。
- 同一个物品如果出现在多条报价里，库存初始化使用第一次遇到的 `stock` 规则。建议把带 `initial/restockAmount/restockInterval` 的出售报价放在前面。
- 如果某个交易物品完全没有任何 `stock` 规则，它不会被库存限制，也不会写入商业库存。商店出售和以物换物产出的物品必须配置库存。

修改 `max` 会同步到已有商业箱库存记录；修改 `initial` 不会重置已有库存。如果需要重置存档里的库存，需要删除对应商业控制盒或清理对应 SQLite 记录。

## 完整示例

```json
{
  "id": "custom_bakery",
  "name": "自定义面包店",
  "job": {
    "id": "custom_baker",
    "name": "面包师",
    "heldItem": "minecraft:bread"
  },
  "offers": [
    {
      "id": "shop_sells_bread",
      "visibleTo": "mixed",
      "cost": [{ "money": 0.25 }],
      "result": [{ "item": "minecraft:bread", "count": 1 }],
      "stock": {
        "item": "minecraft:bread",
        "max": 64,
        "initial": 32,
        "restockAmount": 16,
        "restockInterval": 12000
      }
    },
    {
      "id": "shop_buys_wheat",
      "visibleTo": "player",
      "cost": [{ "item": "minecraft:wheat", "count": 16 }],
      "result": [{ "money": 3.20 }],
      "stock": {
        "item": "minecraft:wheat",
        "max": 256
      }
    },
    {
      "id": "exchange_wheat_for_bread",
      "visibleTo": "player",
      "cost": [{ "item": "minecraft:wheat", "count": 3 }],
      "result": [{ "item": "minecraft:bread", "count": 1 }],
      "stock": {
        "item": "minecraft:bread",
        "max": 64
      }
    }
  ]
}
```

## 校验建议

只检查 JSON 语法：

```powershell
Get-ChildItem .\simukraftbuilding\commercial -Filter *.json |
  ForEach-Object { Get-Content -Raw -Encoding UTF8 $_.FullName | ConvertFrom-Json | Out-Null }
```

开发环境校验内置与运行目录：

```powershell
Get-ChildItem .\run\simukraftbuilding\commercial,.\src\main\resources\assets\simukraft\commercial -Filter *.json |
  ForEach-Object { Get-Content -Raw -Encoding UTF8 $_.FullName | ConvertFrom-Json | Out-Null }
```

常见问题：

| 现象 | 排查 |
| --- | --- |
| 控制盒显示定义无效 | `offers` 是否为空、物品 ID 是否真实注册、资金是否大于 0 |
| 某个 Tab 没有商品 | 报价形态是否符合出售/收购/交换归类规则 |
| 玩家能无限拿某物品 | 该 `result` 物品是否至少有一条 `stock` 规则 |
| 玩家卖出物品后库存不增加 | 该 `cost` 物品是否有 `stock` 规则 |
| NPC 自动经营没有处理报价 | `visibleTo` 是否为 `npc` 或 `mixed` |
| 中文 JSON 在 PowerShell 中校验失败 | 命令是否使用 `-Encoding UTF8` |
