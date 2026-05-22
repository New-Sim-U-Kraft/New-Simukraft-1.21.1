# LDLib2 1.21 使用总结

来源：https://low-drag-mc.github.io/LowDragMC-Doc/ldlib2/

适用目标：Minecraft 1.21.x / NeoForge 项目。
当前目录：`LDLib2-1.21` 是 LDLib2 源码目录，因此本文档单独保存为总结文件，避免覆盖源码。

## 1. LDLib2 是什么

LDLib2 是 LowDragMC 对旧版 LDLib 的重写版本，面向现代 Minecraft 版本设计。它不是简单的 GUI 工具库，而是一套更完整的模组开发基础设施，主要覆盖：

- UI 系统
- 数据同步
- 数据持久化
- RPC 事件通信
- Shader
- 模型渲染
- 游戏内可视化编辑器

相比旧版 LDLib，LDLib2 的重点是减少模板代码、降低同步和保存数据的维护成本，并提供更现代的 UI 架构。

## 2. 与旧版 LDLib 的主要区别

LDLib2 相比 LDLib 有这些核心变化：

1. 架构完全重写
   - 面向现代 Minecraft 内部结构重新设计。
   - 代码结构更清晰，旧系统被清理。

2. 文档更完整
   - 官方文档比旧 LDLib 更系统。
   - 注解、同步、UI 示例更明确。

3. 移除遗留系统
   - 删除旧版中很多过时或不再适合新版本的系统。
   - 更轻量，也更适合 1.21.x 版本项目。

4. 更好的兼容性
   - 官方文档提到对 JEI、KubeJS、EMI 等主流生态有更稳定的集成方向。

5. UI 系统重构
   - 基于 Taffy/Yoga 风格布局。
   - 支持 CSS-like 样式表 LSS。
   - 支持数据绑定和 RPC。
   - 支持 XML 和游戏内 UI 编辑器。

## 3. Gradle 集成方式

官方推荐 Maven 仓库：

```gradle
repositories {
    maven {
        url = "https://maven.firstdark.dev/snapshots"
    }
}
```

LDLib2 2.2.1 及之后：

```gradle
dependencies {
    implementation("com.lowdragmc.ldlib2:ldlib2-neoforge-${minecraft_version}:${ldlib2_version}:all")
}
```

LDLib2 2.2.1 之前：

```gradle
dependencies {
    implementation("com.lowdragmc.ldlib2:ldlib2-neoforge-${minecraft_version}:${ldlib2_version}:all") {
        transitive = false
    }
    compileOnly("org.appliedenergistics.yoga:yoga:1.0.0")
}
```

注意：当前主项目目前添加的是 CurseMaven 上的旧 LDLib：

```gradle
implementation "curse.maven:ldlib-626676:8125765"
```

这不等于官方文档中的 LDLib2 Maven 坐标。如果后续明确要使用 LDLib2 API，应改为官方 Maven 依赖，并确认版本号是否支持 Minecraft 1.21.1 / NeoForge 21.1.x。

## 4. 推荐 IDEA 插件

官方推荐安装 JetBrains 插件：

LDLib Dev Tool

插件地址：
https://plugins.jetbrains.com/plugin/28032-ldlib-dev-tool

插件能力：

- 代码高亮
- 语法检查
- 代码跳转
- 自动补全
- LDLib2 注解支持

如果项目大量使用 LDLib2 的 UI、LSS、注解同步系统，建议安装。

## 5. LDLibPlugin

LDLib2 支持插件入口。

示例：

```java
@LDLibPlugin
public class MyLDLibPlugin implements ILDLibPlugin {
    public void onLoad() {
        // 在这里注册或初始化 LDLib2 相关内容
    }
}
```

用途：

- 注册自定义 UI 组件
- 注册样式
- 注册编辑器扩展
- 初始化 LDLib2 相关系统

## 6. UI 系统概念

LDLib2 UI 是基于 Taffy 布局引擎的现代 UI 系统。

主要能力：

- 现代布局系统
- 现代事件系统
- CSS-like 样式系统 LSS
- 数据绑定
- RPC 事件
- 大量可复用组件
- XML 支持
- 游戏内可视化 UI 编辑器
- XEI / KubeJS 支持方向

LDLib2 UI 的核心对象：

- `UIElement`
- `UI`
- `ModularUI`
- `ModularUIScreen`
- 组件类，例如 `Label`、`Button`、`TextField`、`ProgressBar`、`Toggle` 等

## 7. 最小 UI 示例

官方示例结构如下：

```java
private static ModularUI createModularUI() {
    var root = new UIElement();

    root.addChildren(
            new Label().setText("My First UI"),
            new Button().setText("Click Me!"),
            new UIElement()
                    .layout(layout -> layout.width(80).height(80))
                    .style(style -> style.background(
                            SpriteTexture.of("ldlib2:textures/gui/icon.png"))
                    )
    ).style(style -> style.background(Sprites.BORDER));

    var ui = UI.of(root);
    return ModularUI.of(ui);
}
```

嵌入普通 Screen：

```java
@Override
public void init() {
    super.init();
    var modularUI = createModularUI();
    modularUI.setScreenAndInit(this);
    this.addRenderableWidget(modularUI.getWidget());
}
```

使用官方封装 Screen：

```java
public static void openScreenUI() {
    var modularUI = createModularUI();
    minecraft.setScreen(new ModularUIScreen(modularUI, Component.empty()));
}
```

## 8. 布局系统

LDLib2 使用类似 Yoga/Flex 的布局方式。

常见布局写法：

```java
root.layout(layout -> layout.paddingAll(7).gapAll(5));
```

行布局：

```java
new UIElement().layout(layout -> layout.flexDirection(YogaFlexDirection.ROW))
```

占据剩余空间：

```java
new UIElement().layout(layout -> layout.flex(1))
```

设置固定尺寸：

```java
new UIElement().layout(layout -> layout.width(80).height(80))
```

文字居中：

```java
new Label()
        .setText("Title")
        .textStyle(textStyle -> textStyle.textAlignHorizontal(Horizontal.CENTER))
```

## 9. 事件系统

简单按钮事件：

```java
new Button()
        .setText("Click Me")
        .setOnClick(e -> {
            // 点击逻辑
        });
```

任意 UIElement 都可以监听事件：

```java
new UIElement()
        .addEventListener(UIEvents.MOUSE_DOWN, e -> {
            // 鼠标按下
        })
        .addEventListener(UIEvents.MOUSE_ENTER, e -> {
            // 鼠标进入
        }, true)
        .addEventListener(UIEvents.MOUSE_LEAVE, e -> {
            // 鼠标离开
        }, true);
```

这意味着不一定非要使用 Button 组件，可以用普通 UIElement + Label + 事件 + 样式做自定义按钮。

## 10. LSS 样式系统

LDLib2 提供 LSS，类似 CSS。

直接在元素上写：

```java
root.lss("background", "built-in(ui-gdp:BORDER)");
root.lss("padding-all", 7);
root.lss("gap-all", 5);

new UIElement()
        .lss("width", 80)
        .lss("height", 80)
        .lss("background", "sprite(ldlib2:textures/gui/icon.png)");
```

独立样式表：

```java
var lss = """
#root {
    background: built-in(ui-gdp:BORDER);
    padding-all: 7;
    gap-all: 5;
}

.image {
    width: 80;
    height: 80;
    background: sprite(ldlib2:textures/gui/icon.png);
}

#root label {
    horizontal-align: center;
}
""";

var stylesheet = Stylesheet.parse(lss);
var ui = UI.of(root, stylesheet);
```

内置样式表：

- `StylesheetManager.GDP`
- `StylesheetManager.MC`
- `StylesheetManager.MODERN`

示例：

```java
var ui = UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP));
```

## 11. 数据绑定

LDLib2 UI 支持数据绑定。

官方说明中提到基础概念：

- `IObserver<T>`
- `IDataProvider<T>`

用途：

- UI 显示值自动跟随数据变化
- 输入框更新数据源
- 进度条、标签等组件自动刷新
- 减少手动 tick 刷新 UI 的代码

适合本项目未来这些功能：

- 城市资金显示
- 城市人口显示
- 市民饥饿度 / 幸福度显示
- 建筑工作进度显示
- NPC 工作状态显示

## 12. 菜单与 Screen 通信

官方教程包含：

- ModularUI for Menu
- Screen and Menu communication

这说明 LDLib2 不只是纯客户端 Screen，还可以用于服务端 Menu 场景。

项目中如果城市核心需要：

- 展示城市信息
- 修改城市名
- 邀请玩家
- 修改用户组权限
- 管理市民职业

应优先考虑服务端 Menu + LDLib2 UI，避免客户端伪造数据。

## 13. 同步与持久化系统

LDLib2 的同步和持久化是核心功能之一。

传统 Minecraft / Forge / NeoForge 写法通常需要：

- 手动 NBT 读写
- 手动网络包
- 手动 dirty 标记
- 手动客户端更新
- 手动防止不同步

LDLib2 提供注解式数据管理框架。

核心目标：

> 不要手写同步和序列化代码，只声明字段是什么，LDLib2 处理如何同步和保存。

官方示例：

```java
public class ExampleBE extends BlockEntity implements ISyncPersistRPCBlockEntity {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Persisted
    @DescSynced
    public int energy = 0;

    @Persisted
    @DescSynced
    public String owner = "";
}
```

含义：

- `@Persisted`：字段自动持久化
- `@DescSynced`：字段自动同步到客户端
- `FieldManagedStorage`：字段管理存储
- `ISyncPersistRPCBlockEntity`：支持同步、持久化和 RPC 的 BlockEntity 接口

LDLib2 会自动处理：

- 变化检测
- server → client 同步
- 数据保存
- 按字段同步
- 异步序列化

## 14. Codec 与 NBT 简化

LDLib2 可以通过 `@Persisted` 自动生成 Codec。

官方示例：

```java
public class MyObject implements IPersistedSerializable {
    public final static Codec<MyObject> CODEC = PersistedParser.createCodec(MyObject::new);

    @Persisted(key = "rl")
    private ResourceLocation resourceLocation = LDLib2.id("test");

    @Persisted(key = "enum")
    private Direction enumValue = Direction.NORTH;

    @Persisted(key = "item")
    private ItemStack itemstack = ItemStack.EMPTY;
}
```

实现 `IPersistedSerializable` 后，可以获得：

- 自动 NBT 序列化
- 自动 NBT 反序列化
- 与 `INBTSerializable` 兼容
- 自动 Codec 生成

这比手动写 `CompoundTag`、`Codec`、`RecordCodecBuilder` 更适合复杂数据结构。

## 15. 对当前项目的使用建议

当前项目已经有这些系统雏形：

- `CitizenData`
- `CitizenManager`
- `CitizenService`
- `CityData`
- `CityManager`
- 城市权限用户组

如果后续切换到 LDLib2，可以考虑以下方向。

### 15.1 城市核心 UI

城市核心右键后打开 LDLib2 UI：

- 显示城市名
- 显示市长
- 显示资金
- 显示市民数量
- 显示成员列表
- 显示权限等级
- 市长可添加 / 移除玩家
- 市长可设置官员
- 官员可邀请普通市民
- 市民只读，无管理按钮

### 15.2 市民数据同步

`CitizenData` 当前是 `SavedData + CompoundTag` 手写方式。

未来可迁移为：

- `@Persisted` 保存字段
- `@DescSynced` 同步需要展示的字段
- 非展示字段只持久化不同步

建议同步字段：

- name
- jobId
- status
- skinPath
- cityId

不建议频繁同步字段：

- hunger
- happiness
- health
- skills 全量 Map

这些可以按需同步到 UI，而不是每 tick 同步。

### 15.3 城市用户组

城市成员权限适合服务端保存，客户端只显示副本。

可使用 LDLib2 的同步能力把当前玩家所在城市信息同步到城市核心 UI。

权限判断必须仍然在服务端完成，不能只依赖客户端按钮是否可见。

### 15.4 建筑控制盒

住宅 / 工业 / 商业 / 其他控制盒未来也适合做 LDLib2 UI。

例如：

- 住宅控制盒：显示居民、入住状态、回家点设置
- 工业控制盒：显示配方、工人、产出、输入输出
- 商业控制盒：显示店员、货物、收入
- 其他控制盒：显示功能扩展信息

## 16. 在本项目中使用 LDLib2 的注意事项

1. 当前依赖是 LDLib，不是 LDLib2

当前 build.gradle 中已有：

```gradle
implementation "curse.maven:ldlib-626676:8125765"
```

如果要按本文档使用 LDLib2 API，需要替换或额外添加官方 LDLib2 依赖。

2. 注意包名不同

旧 LDLib 常见包名：

```java
com.lowdragmc.lowdraglib...
```

LDLib2 API 可能使用新包结构，需要以实际依赖中的类为准。

3. 不要在服务端直接引用客户端 UI 类

城市核心方块、网络包、数据管理器必须在 common 侧保持服务端安全。

客户端 Screen / UI 应放在 client 包。

4. 权限判断必须在服务端

即使 LDLib2 UI 隐藏了按钮，也不能只靠客户端隐藏按钮防作弊。

正确流程：

```text
客户端点击按钮
  ↓
发送请求到服务端
  ↓
服务端 CityManager.hasPermission(...)
  ↓
通过后修改数据
  ↓
同步结果到客户端
```

5. 大量市民数据不要全量同步

NPC 数量可能很多，必须避免每 tick 全量同步所有市民数据。

推荐：

- 平时只同步实体名、职业、状态
- 打开城市核心 UI 时才请求城市相关列表
- 打开单个市民详情时才请求该市民完整数据

## 17. 城市核心用 LDLib2 的推荐架构

```text
CityCoreBlock
  右键
  ↓
服务端检查玩家和城市状态
  ↓
打开 / 请求 CityCoreScreen
  ↓
CityCoreScreen 使用 LDLib2 构建 UI
  ↓
按钮操作通过 RPC / 网络包回服务端
  ↓
CityManager 修改数据
  ↓
同步 UI 数据
```

建议拆分：

```text
common/cn/kafei/simukraft/block/CityCoreBlock.java
common/cn/kafei/simukraft/city/CityManager.java
common/cn/kafei/simukraft/city/CityData.java
client/cn/kafei/simukraft/client/screen/CityCoreScreen.java
client/cn/kafei/simukraft/client/screen/CityCoreUiFactory.java
common/cn/kafei/simukraft/network/city/*.java
```

## 18. 后续开发建议

短期建议：

1. 确认是否切换到 LDLib2 官方 Maven 依赖。
2. 如果继续使用 CurseMaven 的旧 LDLib，就不能直接照 LDLib2 文档写 API。
3. 城市核心先恢复方块逻辑。
4. UI 层等依赖确认后再接 LDLib2。

中期建议：

1. 城市核心 UI 使用 LDLib2。
2. 城市用户组管理使用服务端校验。
3. 玩家邀请、权限修改、城市信息同步独立成 city network 包。

长期建议：

1. 把复杂 BlockEntity 数据逐步迁移到 LDLib2 注解持久化。
2. 把控制盒 UI 统一迁移到 LDLib2。
3. 用 LSS 抽取统一 UI 风格，避免每个界面重复写颜色和布局。

## 19. 关键链接

LDLib2 首页：
https://low-drag-mc.github.io/LowDragMC-Doc/ldlib2/

Java Integration：
https://low-drag-mc.github.io/LowDragMC-Doc/ldlib2/java_integration/

UI Introduction：
https://low-drag-mc.github.io/LowDragMC-Doc/ldlib2/ui/

UI Getting Started：
https://low-drag-mc.github.io/LowDragMC-Doc/ldlib2/ui/getting_start/

Sync and Persistence：
https://low-drag-mc.github.io/LowDragMC-Doc/ldlib2/sync/

LDLib2 GitHub：
https://github.com/Low-Drag-MC/LDLib2
