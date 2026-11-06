# 校园日程 · Android 原生版

微信小程序「校园信息日程」的**个人版离线安卓 App**。原生 Kotlin + Jetpack Compose，
数据全部存本机 SQLite，不联网、无账号、无云服务。

> 源项目：`../wechat-miniprogram/miniprogram`（微信小程序 v1.4）
> 本项目：**4 个页面** / 0 云函数 / 1 张本地表
> 视觉与动效 **100% 对齐小程序**（同一套 token、同一套图标、同一套曲线）
> 目录：`android-app/` 是独立项目根，与 `wechat-miniprogram/` 平级；体积大的构建工具链留在上级工作区 `.workbuddy/tools`

---

## 一、这是什么

把小程序里「校园多人协作」的部分全部砍掉，只留下**个人登记日程**这一条主线：

| 保留 | 砍掉 |
|---|---|
| 日程时间轴（按天） | 群组 / 成员管理 |
| 课表周视图（7 列横滚） | 发布者 / 管理员权限体系 |
| 概况页（今日 / 本周 / 分类 / 提醒） | 申请与审核流程 |
| 新建 / 编辑 / 删除日程 | 评论、分享、通知中心 |
| 时间冲突高亮 | AI 推荐、反馈、文件上传 |
| 本地通知提醒 | 登录、身份、openid |
| JSON 备份导出 | 群组可见范围、公开/私密 |
| 预置示例 + 一键清空 | 云端订阅消息 |

**关键增强**：小程序靠云端订阅消息提醒（需服务器存活 + 用户授权），
本 App 改用 Android 本地通知（AlarmManager），**不依赖服务端，更可靠**。

---

## 二、v2.0 做了什么（整体重写）

v1.1 的界面是从零拼的，视觉与小程序有出入，动画也掉帧。v2.0 把**底层全部换掉**：

### 1. 设计系统与小程序同源

安卓端不再各写各的尺寸，而是**把小程序 `tokens.wxss` 当唯一真源**：

```kotlin
// 750rpx 恒等于屏幕宽 ⇒ 1rpx 对应的 dp 数随屏宽变化
val scale = (configuration.screenWidthDp / 750f).coerceIn(0.44f, 0.64f)
```

* 颜色、渐变、字号 / 行高、圆角、阴影、间距、图标尺寸 —— 逐项对齐 `tokens.wxss`
* 字号 / 行高同样成对定义（如 `--text-body: 28rpx` / `--leading-body: 44rpx`）
* 在 375dp 宽的机型上与小程序**像素级一致**，其它机型按比例缩放

### 2. 文本错位的根因修掉了

Android 字体默认带上下留白（font padding），同一行里字号不同的文字视觉基线就会错。
**所有 TextStyle 统一关闭 font padding**：

```kotlin
platformStyle = PlatformTextStyle(includeFontPadding = false)
```

配合行内统一 `Alignment.CenterVertically`、标题与状态胶囊用 `Alignment.Top`，
时间列用等宽数字（`fontFeatureSettings = "tnum"`），行内不再错位。

### 3. 动画不再掉帧

v1.1 卡顿的三个原因，全部改掉：

| 原来 | 现在 |
|---|---|
| `by animateFloatAsState(...)` —— 每帧重组整棵列表 | 只在 `graphicsLayer { }` 里读 `.value`，**动画帧只重绘不重组** |
| 动画改 `padding` / `height` 等布局属性 | 只改 transform / alpha，不触发布局 |
| 条目入场挂在 `LaunchedEffect + delay` 上，滚动时反复重启 | `EnterTracker` 记账，**只播一次**，滚出去再回来不重播 |

### 4. 图标与小程序同源

`scripts/gen_android_icons.js` 读项目内 `tools/icons-source.js`（小程序图标表快照），
把 55 个图标转成 Android VectorDrawable（`circle` / `rect` 自动转 pathData），
保持 24×24 画布、1.8 线宽、圆头圆角。描边统一为黑色，运行时用 `Icon(tint=)` 着色，
同一图形可复用品牌蓝 / 次级灰 / 金色 / 反白四种色调。

> 快照随项目分发，**单独拷走 `android-app/` 也能重新生成图标**。
> 小程序图标改版后用 `cp ../wechat-miniprogram/miniprogram/utils/icons.js tools/icons-source.js` 同步，
> 或设 `ICONS_SRC` 环境变量直接指向小程序真源。
> 生成器只清理自己命名的 `glyph_*.xml`，**不会误删应用图标**。

### 5. 内容补齐

新增**概况页**（今日概览 / 本周 7 天柱状分布 / 分类占比 / 即将提醒），
日程页增加**筛选条**（全部 / 今天 / 星标）与**冲突明细展开**，
底部导航改为 **日程 / 概况 / 我的** 三页。

---

## 三、目录结构

```
android-app/                  ← 本项目根（与 wechat-miniprogram/ 平级）
  app/src/main/java/com/campus/schedule/
    core/                 ← 从小程序 utils/ 翻译的纯逻辑（与 JS 行为对齐）
      CoreDate.kt           日期时间，固定 UTC+8
      CoreConflict.kt       时间冲突检测
      CoreFormat.kt         展示格式化
      CoreValidators.kt     表单校验
    data/
      ScheduleItem.kt       Room 实体（12 字段简化模型）
      ScheduleDao.kt        DAO
      AppDatabase.kt        Room 数据库
      ScheduleRepository.kt 本地仓库（替代 api.js 的 37 个接口）
      SeedData.kt           13 条预置示例
      Backup.kt             JSON 导出/导入
    notify/
      ReminderScheduler.kt  AlarmManager + 通知渠道
      ReminderReceiver.kt   闹钟广播
      BootReceiver.kt       重启后重建闹钟
    ui/
      ScheduleViewModel.kt  业务状态（筛选 / 冲突 / 模式）
      theme/
        Tokens.kt            设计 token（颜色/渐变/字号/圆角/阴影/间距）+ rpx 缩放
        Motion.kt             动效 token（时长/缓动/缩放）
        Theme.kt              主题装配 + 降低动效监听
      icons/
        AppIcons.kt          图标资源映射（由脚本生成）
      components/
        Motion.kt             动效 Modifier（按压/涟漪/错位入场/息屏亮屏）
        Primitives.kt         报头/分区/标签/按钮/胶囊/分段切换/空态/入口行/统计/身份卡
        Form.kt               表单（输入框/选择器/标签/色板/开关）
        Timeline.kt           时间轴（导航 + 条目 + 星标 + 冲突明细）
        WeekGrid.kt           周课表（6 时段 × 7 列网格 + 当前时间线）
      screens/
        ScheduleScreen.kt     日程页（课表 / 时间轴双视图）
        OverviewScreen.kt     概况页
        MineScreen.kt         我的页
        EditScreen.kt         新建 / 编辑页
    MainActivity.kt         入口 + 导航（页面转场 / 底部栏 / FAB / 导入导出）
    ScheduleApp.kt          Application（启动时重建闹钟）
  app/src/main/res/drawable/
    glyph_*.xml             55 个图标（脚本生成）
  app/src/test/            30 个单元测试（期望值取自小程序 JS 实跑输出）
  scripts/                 构建 / 图标生成 / 源码打包（全在项目内）
  tools/                   icons-source.js 图标快照 + debug.keystore 调试签名
  dist/                    安卓交付 zip（与小程序交付包分开）
```

---

## 四、核心逻辑的移植

小程序的 `miniprogram/utils/` 里有 **652 行零 `wx.*` 依赖**的纯逻辑，
它们被逐行翻译成 Kotlin，**行为与原 JS 完全一致**：

| 小程序 | 安卓 | 说明 |
|---|---|---|
| `date.js` (225 行) | `CoreDate.kt` | 固定 UTC+8，不依赖设备时区 |
| `conflict.js` (82 行) | `CoreConflict.kt` | A.start < B.end && B.start < A.end |
| `format.js` (103 行) | `CoreFormat.kt` | 只保留个人版需要的子集 |
| `validators.js` (242 行) | `CoreValidators.kt` | 裁掉群组/材料/附件/申请规则 |

### 翻译中的两个坑（已在测试中锁定）

1. **星期索引差 1** —— JS `getUTCDay()` 是 0=周日，Java `Calendar.DAY_OF_WEEK` 是 1=周日。
   直接照搬公式会让所有日期偏移一天。`CoreDate.jsDayOfWeek()` 做了统一换算。
2. **ISO 周号跨年** —— `isoWeekNumber` 里 `thursday` / `yearStart` 是**纯 UTC**（不带 +8），
   而 `shiftedDate` 是 +8 视图。混用会在年末年初差一周。

### 数据模型简化（30 字段 → 12 字段）

```
ScheduleItem { id, title, note, location, category, accent,
               startTime, endTime, isAllDay, remindBefore, starred,
               isSample, createdAt, updatedAt }
```

砍掉：`visibleTo` / `allowConfirm` / `allowReshare` / `allowComment` /
`allowDeadlineRemind` / `creatorOpenid` / `creatorName` / `contactPhone` /
`contactName` / `organizer` / `requiredMaterials` / `attachments` /
`type` / `level` / `necessity` / `audience` / `summary` / `content` / `status`

---

## 五、构建

### 一键构建

在 `android-app/` 目录下执行（脚本自动探测项目内 `tools/` 或上级工作区 `.workbuddy/tools/` 的 JDK/Gradle）：

```bat
scripts\build-android.bat
```

产出：`app/build/outputs/apk/debug/app-debug.apk`

### 手动构建

```bat
set JAVA_HOME=D:\Coder-Projects\XCX\.workbuddy\tools\jdk17
set GRADLE_USER_HOME=D:\Coder-Projects\XCX\.workbuddy\tools\gradle-home
set ANDROID_HOME=D:\sdk
cd android-app
..\.workbuddy\tools\gradle8\bin\gradle.bat assembleDebug testDebugUnitTest
```

### 重新生成图标

```bash
# 用项目内快照（默认，不依赖小程序项目）
node scripts/gen_android_icons.js

# 小程序图标改版后同步快照
cp ../wechat-miniprogram/miniprogram/utils/icons.js tools/icons-source.js
node scripts/gen_android_icons.js
```

### 环境要求

| 组件 | 位置 | 版本 |
|---|---|---|
| JDK | 上级工作区 `../.workbuddy/tools/jdk17` | 17 |
| Gradle | 上级工作区 `../.workbuddy/tools/gradle8` | 8.7 |
| Android SDK | `D:\sdk` | platform-34 + build-tools 34.0.0 |

> 工具链体积大，**不随项目分发**：留在当前工作区时脚本会自动找到 `../.workbuddy/tools/`；
> 单独拷走 `android-app/` 时需自备 JDK 17 / Gradle 8.7 / SDK 34，或把它们放进项目内
> `tools/jdk17`、`tools/gradle8`，也可用 `JAVA_HOME` / `GRADLE_BIN` / `ANDROID_HOME` 覆盖。

> ⚠️ **SDK 路径必须是相对路径**（`local.properties` 里写 `sdk.dir=../../../sdk`）。
> AGP 用 `File(rootDir, path)` 拼接 SDK 路径，写绝对路径会在 Windows 上抛
> `IOException: 文件名、目录名或卷标语法不正确`。

### 装到手机

在 `android-app/` 目录下执行：

```bat
D:\sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## 六、界面与动效（v2.0）

### 页面结构（对齐小程序）

| 页面 | 结构 |
|---|---|
| **日程** | 报头（眉标 / 标题 / 副信息 / 今天胶囊）→ 视图切换（课表 / 时间轴）→ 视图主体 |
| **概况** | 报头 → 今日概览卡（渐变）→ 本周 7 天柱状分布 → 分类占比 → 即将提醒 |
| **我的** | 报头 → 身份卡 → 统计条 → 我的日程 → 数据管理 → 关于 |
| **新建 / 编辑** | 返回栏 → 基本信息 → 时间 → 分类与提醒 → 备注 → 保存 |

### 组件对照

| 小程序类 | 安卓组件 |
|---|---|
| `.masthead` | `Masthead` |
| `.section-header` | `SectionHeader` / `SectionAction` |
| `.tag` / `.timeline-row__status` | `Tag` / `StatusPill` |
| `.meta-line` | `MetaLine` |
| `.paper-card` | `PaperCard` / `RaisedCard` |
| `.btn` | `AppButton` |
| `.locate-btn` | `LocateButton` |
| `.view-switch` | `SegmentedSwitch`（滑动指示器） |
| `.empty-state` | `EmptyState` / `EmptySeal` |
| `.conflict-bar` | `HintBar` + `ConflictDetail` |
| `.entry-row` / `.stats` / `.identity` | `EntryRow` / `StatsRow` / `IdentityCard` |
| `.input` / `.picker-trigger` / `.switch-row` | `AppTextField` / `PickerTrigger` / `SwitchRow` |
| `.timeline-row` | `TimelineRow` |
| `.week-view` | `WeekGrid` |

### 动效基建

```
ui/theme/Motion.kt       时长 / 缓动 / 缩放 token
ui/components/Motion.kt  pressScale / rippleClick / staggerEnter / wakeIn / rememberNowMillis
```

| Token | 值 | 用途 |
|---|---|---|
| `DurPress` | 220ms | 按压回弹 |
| `DurFast` | 260ms | 颜色、淡出等快速过渡 |
| `DurBase` | 300ms | 页面转场、列表入场 |
| `DurTab` | 280ms | 视图切换、底部栏指示条 |
| `DurWake` | 220ms | 息屏亮屏的遮罩退场 |
| `StaggerStep` | 90ms | 列表错位入场的单项间隔（最多累加 8 项） |
| `ScalePress / ScaleCard / ScaleEnter / ScaleWake` | 0.92 / 0.95 / 0.96 / 0.94 | 各类缩放幅度 |

曲线直接沿用小程序的 cubic-bezier 数值（Compose 的 `CubicBezierEasing` 与 CSS 语义一致），
因此两端运动观感完全相同。详见 [`MOTION.md`](MOTION.md)。

### 降低动效

安卓对应系统「开发者选项 → 动画时长 = 0」或「无障碍 → 移除动画」。
`Theme.rememberReducedMotion()` 用 `ContentObserver` 监听 `ANIMATOR_DURATION_SCALE`，
关闭时全部动效时长归零（**立即到位，不改变布局**），用户改完系统设置立即生效。

### Modifier 顺序约定（重要）

```kotlin
Modifier
    .pressScale(interaction)   // ① 最外层：整块内容一起缩放
    .appShadow(level, shape)   // ② 投影：必须在 clip 之前，否则被裁掉
    .clip(shape)               // ③ 裁切形状，同时裁掉涟漪
    .background(color)         // ④ 背景
    .border(...)               // ⑤ 描边
    .rippleClick(interaction)  // ⑥ 最内层：点击 + 涟漪
    .padding(...)
```

---

## 七、本地通知提醒

| 环节 | 实现 |
|---|---|
| 调度 | `AlarmManager.setExactAndAllowWhileIdle`（无精确闹钟权限时自动降级） |
| 通道 | `NotificationChannel` IMPORTANCE_HIGH |
| 接收 | `ReminderReceiver`（BroadcastReceiver） |
| 重启恢复 | `BootReceiver` 监听 `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` |
| 应用启动 | `ScheduleApp.onCreate` 重建全部闹钟 |
| 权限 | Android 13+ 运行时申请 `POST_NOTIFICATIONS`；Android 12+ `SCHEDULE_EXACT_ALARM` |

---

## 八、测试

在 `android-app/` 目录下执行（或用 `scripts\build-android.bat` 一次跑完构建 + 测试）：

```bat
..\.workbuddy\tools\gradle8\bin\gradle.bat testDebugUnitTest
```

**30 个单元测试，全部通过。** 期望值不是手写的，而是**用小程序 JS 实跑输出**作为基准：

```bash
# 在小程序目录跑原始 JS，取真实输出
node -e "const d=require('./miniprogram/utils/date.js'); console.log(d.isoWeekNumber(...))"
```

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `CoreDateTest` | 13 | 日/周边界、ISO 周号跨年、时区固定、格式化 |
| `CoreConflictTest` | 7 | 重叠判定、全天排除、取消/结束排除 |
| `CoreValidatorsTest` | 11 | 标题长度、时间先后、分类色系、全天归一化 |
| `UiRegressionGuardTest` | 4 | 动效/尺寸常量的回归守卫 |

---

## 九、已知限制

1. **未做真机验证** —— APK 已编译通过、单元测试全绿，但**未在真实设备上跑过**，
   通知权限流程、精确闹钟、周视图横滚手感、动效节奏需你在真机确认。
2. **无重复日程规则** —— 每周/每月的循环日程未实现（小程序也没有）。
3. **周课表仅 6 个固定时段** —— 08:00–21:55，与小程序一致；超出的条目收在网格下方。
4. **仅浅色主题** —— 未做深色模式。
5. **示例数据锚定当前日期** —— 预置 13 条示例按「今天」重新计算，
   而不是沿用小程序的固定历史时间戳（否则一打开全是过期的）。
6. **`local.properties` 不进版本库** —— 换机器需重建。
7. **UI 层没有自动化测试** —— 当前只有纯逻辑单测，界面靠人工验证。

---

## 十、v2.0.2 修复与打磨记录

### 功能缺陷（都已修复并有单测/编译保证）

| # | 问题 | 修复 |
|---|---|---|
| 1 | **全天日程永远存不进去** —— 校验器把「全天必须落在 00:00」当错误，而编辑页的全天开关又不同步时间戳 | 校验改为静默归一化到 `startOfDay` / `endOfDay`；开关联动 |
| 2 | **日期/时间选择器每选一次 +8 小时** —— `utcCalendar` 补偿了 `TZ_OFFSET`，回调却没有减回去 | 两处回调统一 `- CoreDate.TZ_OFFSET` |
| 3 | **取消提醒后旧闹钟照响** —— `schedule()` 遇到 `remindBefore == null` 直接 return，不 cancel | 先 `cancel()` 再判断；清空示例/全部数据时 `cancelAll()` |
| 4 | **编辑页返回键直接退出 App** —— 全项目没有 `BackHandler` | 编辑页返回走脏数据确认；非首个 Tab 返回回到「日程」 |
| 5 | **通知点击不跳转** —— `focusId` 只在 `onCreate` 读一次 | `onNewIntent` 写入 state + token，组合层响应 |
| 6 | **导出备份在 Android 10+ 崩溃** —— 直写公共 Downloads 目录 | 改走 SAF `CreateDocument`，全版本可用 |
| 7 | **时间轴「进行中」标签不刷新** —— `remember` 没把 `now` 当 key | 按分钟桶纳入 key |
| 8 | **周视图当前时间线位置偏** —— 用 08:00–21:55 线性映射，忽略了课间空档和行间距 | 按时段分段映射（时段内线性、空档按 gap 比例） |
| 9 | **闹钟 requestCode 只有 4096 个槽** | 掩码放大到 `0x3FFFFFFF` |

### 动效与线条

- 当前时间线改 `graphicsLayer { translationY }`（不触发布局）+ 叶子节点呼吸圆点，遵循「动画值只在 graphicsLayer 里读」的铁律。
- 切周改为 `AnimatedContent`：新周轻推淡入、旧周反向淡出，位移仅 1/16 宽度。
- 空态补了「线条生长」插画：Path + PathMeasure 分段描边，进度只在 draw 阶段读取，零重组；系统「移除动画」时直接画完整线稿。
- 21 处硬编码 `1.dp` 细线统一为 `m.rpx(2)`，不再随屏宽漂移；表头选中态的阴影被 `clip` 裁掉的问题一并修正。

### 性能

- 课表底格从 **42 个 composable** 改为单次 `drawBehind` 画 6 个圆角格。
- `ScheduleUiState.visibleItems` 是计算属性（每次访问都全量 filter），页面里原来访问 6 次 → 只取一次。
- 课表按天分组改 `groupBy`，去掉 7 次循环内的 `filter`。
- `LazyColumn` 的 `itemsIndexed` 补 `contentType`。

> 冲突检测仍是 O(n²)：个人日程量级（<100 条）约一万次比较，实测无感，故保留原实现不冒险改动。

---

## 十一、v2.1 视觉重做记录

这一版只改「手感」，不碰数据层，配色（`Tokens`）一个值都没动。

### 用户反馈 → 根因 → 处理

| # | 反馈 | 根因 | 处理 |
|---|---|---|---|
| 1 | 切换界面闪屏 | `WakeVeil` 整屏先盖一层深墨色再淡出，同时新页 `wakeIn()` 从 alpha=0 淡入，两者叠加必然出现「既不是旧页也不是新页」的空帧 | 删掉 `WakeController` / `WakeVeil` / `wakeIn`；换页改用 `AnimatedContent`，新页从 1/8 屏宽处滑入并淡入，旧页反向滑出，全程没有纯色遮罩 |
| 2 | 课表 / 时间轴切换没有动效 | 两个模式只是同一个 `LazyColumn` 里换 item，Compose 只能瞬间重排 | 外层分成「报头 + 分段切换」固定区，下方用 `AnimatedContent(targetState = isWeek)`：新视图从下方 1/40 高度滑入淡入，同时高度随内容平滑过渡 |
| 3 | 字体偏小 / 有些字过小 | 750rpx 等比字号在 392dp 屏上只剩 14.6sp（小程序有浏览器最小字号兜底，原生没有） | `Metrics.sp()` 统一乘 `TYPE_SCALE = 1.15`；课表时段/课块、时间轴时间列、标签、状态胶囊、图表刻度等零散小字全部抬到 24rpx 以上 |
| 4 | 提示框点开有割裂感 | 卡片用 `scaleIn(0.96)` 从屏幕正中长出，与界面毫无空间关系，遮罩又骤然变暗 | `ConfirmOverlay` 重做成**底部面板**：遮罩淡入 + 面板从底部整块滑入（`navigationBarsPadding` 避开手势条），抓手条、标题、正文、按钮按 60/100/140ms 错峰浮现；返回键可关闭 |
| 5 | 点击阴影很脏 | `appShadow(1/2)` 的淡蓝阴影落在浅蓝纸面上只会糊出一圈灰边 | 所有静态阴影去掉（唯一保留：悬浮的「新建」按钮）；层次改由 `2rpx` 线条 + 纸色分层表达 |
| 6 | 点击反馈不自然 | 按下 `scale(0.92)` 会让整块卡片「跳舞」，连续点击尤其明显 | 新增 `Modifier.pressHighlight()`：按下时叠一层 6% 墨色、几何完全不动；所有卡片/按钮/列表项统一改用它 |
| 7 | 底部任务栏动画很奇怪 | 指示器是 68rpx 浮块，又用 9.78% 过冲的弹性曲线 + 图标双层交叉淡入 + 0.86→1 放大，三套节奏互相打架 | 浮块改为一条 32×4rpx 的品牌色下划线，用不过冲的 `EaseInOut` 横移；文字颜色单独 260ms 渐变；图标尺寸固定 |

### 美术重做（配色不变）

- **卡片**：`PaperCard` 统一为「白纸底 + 2rpx `Line` 细线 + 可选左侧竖条」，新增 `CardHeader`（2rpx 描边方框编号 + 标题），编辑页的「基本信息 / 时间」两块用上了 `1` `2` 编号。
- **按钮**：次要按钮从「白底 + 几乎看不见的 PaperTint 描边」改为「白底 + 2rpx `LineStrong` 描边」；危险按钮加同色系淡描边；按压反馈全部走 `pressHighlight`。
- **课表块**：底色改成该色系 6% 的着色纸，左侧 6rpx 圆头实色竖条 + 2rpx 同色系淡描边；进行中的块换品牌色调并补一个 8rpx 品牌点。
- **时间轴卡片**：同样改成 5% 着色纸 + 圆头短竖条（原来直角竖条会溢出圆角），描边跟随色系。
- **概览页今日卡**：右上角加三道斜线装饰，交代「日程表」的意象。
- **表单**：「必填」从变色小字改成 2rpx 描边的细标注。
- **我的页页脚**：加一条 72×2rpx 的居中短线做分隔。

### 兼容与回归

- `veilOpacity` 保留并标记 `@Deprecated` —— `UiRegressionGuardTest` 仍在测它。
- 单测全部通过：`CoreDateTest` 13 / `CoreConflictTest` 7 / `CoreValidatorsTest` 11 / `UiRegressionGuardTest` 4。

---

## 十二、v2.2 错位修复与对齐重做

这一版**只动布局与视觉尺寸**，数据层、动效曲线、配色（`Tokens` 的颜色值）都没有改动。
核心原则：**把「间距」从手写 padding 改成由行高推导**，让每一个文本块的高度都是可预测的常量。

### 为什么以前会错位

| 现象 | 根因 |
|---|---|
| 底栏图标和文字「不齐」 | 图标容器 48rpx 里塞 44rpx 图标（上下各 2rpx 不对称）；文字用 `caption`（行高 36）而图标 44，整组 92rpx 在 112rpx 轨道里**靠顶**摆放，底部空 20rpx；选中态字重 Bold / 未选中 Normal，文字宽度变化导致居中抖动 |
| 底栏按下时出现一个大圆 | `BottomTabItem` 的 `Column` 是 112rpx 全高，却用了 `pressHighlight(..., CircleShape)`，圆的直径等于全高，按下时是一块巨大的圆 |
| 切页时内容上下弹一下 | `bottomBar = { if (page != Page.EDIT) AppBottomBar(...) }`：进入编辑页时底栏整个消失，Scaffold 的 `innerPadding` 跳变 |
| 状态胶囊里的字贴边甚至被裁 | `StatusPill` 固定 `height(40rpx)`，而行高是 32，上下只剩 4rpx；字号放大到 24 之后就溢出了 |
| 时间轴条目左右「起笔不齐」 | 时间列用 `padding(top = 16rpx)` 硬对齐，卡片标题第一行的行高中心与之没有关系；标题与胶囊用 `Alignment.Top`，胶囊高度一变就浮起来 |
| 课表表头双行往上飘 | 星期 24/行高 34 + 日期 26/行高 36 + 间距 4 = 74rpx，塞在 88rpx 的表头里上下只剩 7rpx |
| 课表时间槽与格线对不上 | 时间槽用 `padding(top = 8rpx)` + 两行 32 行高（共 72rpx），而格子只有 132rpx，最后一行末端与下一格开始时间挤在一起 |
| 空状态文字歪 | `EmptyState` 只做了横向居中，多行正文本身仍是左对齐 |
| 色板选中时整排跳 | `ColorSwatch` 选中把描边从 2rpx 改成 6rpx，尺寸变化推动整行重排 |
| 「必填」标注比标题低一截 | 标注总高 34rpx、主标签行高 40rpx，同一行 `CenterVertically` 时重心差 3rpx |

### 处理（逐项）

1. **底栏**：图标容器 48rpx + 间隙 4rpx + 文字行高 28rpx，整组 `Arrangement.Center`；`Icon(fillMaxSize())` 自己居中；字重恒定 `SemiBold`，只靠颜色区分；指示器是图标正下方一条 28×4rpx 圆头短线，位置由同一套尺寸推导（`markTop = (barHeight - groupH)/2 + groupH - markH`）。
2. **底栏常驻**：`bottomBar` 始终渲染，编辑页只把 `enabled` 置 false，`innerPadding` 不再跳变。
3. **状态胶囊**：高度由 `行高 32 + 上下各 9 = 50rpx` 推导，圆点 8rpx，内边距 14rpx。
4. **时间轴条目**：时间列 `padding(top = 10rpx)` 让开始时间（行高 40）的**行高中心**与卡片标题第一行（行高 48 + 内边距 18）的中心对齐；结束时间行高收紧到 30；标题与胶囊改 `Alignment.Bottom`；卡片内边距统一 18/20rpx。
5. **课表表头**：星期 22/行高 32 + 日期 28/行高 34 + 间距 2 = 68rpx，在 88rpx 表头里上下各留 10rpx；今天的小圆点移到日期行右侧。
6. **课表时间槽**：开始时间行高 30/结束时间行高 30 + 间距 2 = 62rpx，`padding(top = 2rpx)` 顶齐格子上边线。
7. **课块**：时间行高 30（22rpx 字）、标题行高 32（24rpx 字）+ 2rpx 间距，内边距 6/4，进行中圆点右上内缩 10rpx。
8. **报头与分区标题**：标题行改 `Alignment.Bottom`（大标题与右侧胶囊按钮按底边对齐更接近视觉齐平），三段文字间距统一为 10rpx。
9. **分段切换**：轨道 104→112rpx，指示器实际高 96rpx，文字行高 44 在内部居中后上下各留 26rpx。
10. **表单**：`FieldLabel` 的「必填」标注总高 38rpx；`PickerTrigger` 文字行高 44；`ChoiceChip` 总高 64rpx（与 `ColorSwatch` 同档）；`SwitchRow` 两侧各加 4rpx 内边距。
11. **色板**：选中态不再改描边宽度，改为「描边恒定 2rpx + 6rpx 纸色间隙 + 1.08 倍缩放」，全程走 `graphicsLayer`。
12. **入口行 / 统计条 / 身份卡**：右侧数值与箭头之间补 8rpx 间距；统计条数值行高 48 + 标签行高 32；身份卡名字行高收紧到 58、首字行高 46（在 104rpx 方块里才是几何居中）。
13. **空状态**：标题与正文都加 `textAlign = Center`，正文改用次级墨色。
14. **按压形状**：`pressHighlight` 的高亮形状一律与本体一致（导航箭头、课表表头、越界小卡原来都用了比本体更大的圆角，按下时四角会露出方角）。

### 回归

- 单测：`CoreDateTest` 13 / `CoreConflictTest` 7 / `CoreValidatorsTest` 11 / `UiRegressionGuardTest` 4，全部通过。
- `veilOpacity` 与 `wakeIn` / `pageEnter` 仍然保留（前者被单测引用），只是不再被界面调用。

---

## 十三、下一步（可选增强）

- 重复日程规则（每周 / 每月 / 工作日）
- 首页小组件（今日日程）
- 日历月视图
- ics 导入导出（对接系统日历）
- 深色主题
- 桌面快捷方式快速记录
- Compose 界面自动化测试
