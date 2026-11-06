# 动效规范（Android 端 v2.0）

与小程序 `../wechat-miniprogram/miniprogram/styles/motion.wxss` **同源**：时长、曲线、缩放幅度逐项对齐，
Compose 的 `CubicBezierEasing` 与 CSS `cubic-bezier()` 语义一致，两端运动观感相同。

---

## 一、Token

| Token | 值 | 用途 |
|---|---|---|
| `DurPress` | 220ms | 按压回弹 |
| `DurFast` | 260ms | 颜色、淡出 |
| `DurBase` | 300ms | 页面转场、列表入场 |
| `DurSheet` | 300ms | 底部弹层 |
| `DurExit` | 240ms | 退场 |
| `DurToastEnter` / `DurToastExit` | 280 / 240ms | 轻提示 |
| `DurTab` | 280ms | 视图切换、底部栏指示条 |
| `DurWake` | 220ms | 息屏亮屏的遮罩退场 |
| `StaggerStep` | 90ms | 错位入场步进（最多累加 8 项） |
| `EaseSpring` / `EaseOut` | `cubic-bezier(.34,1.56,.64,1)` | 回弹入场 |
| `EaseInOut` | `cubic-bezier(.77,0,.175,1)` | 匀速往复 |
| `EaseExit` | `cubic-bezier(.55,0,1,.45)` | 退场 |
| `EaseSoft` | `cubic-bezier(.22,1,.36,1)` | 柔和过渡 |
| `ScalePress` | 0.92 | 按钮 / 图标按压 |
| `ScaleCard` | 0.95 | 卡片按压 |
| `ScaleEnter` / `ScaleEnterSm` | 0.96 / 0.94 | 入场 |
| `ScaleWake` | 0.94 | 息屏 |

---

## 二、性能铁律

### 1. 动画值只在 `graphicsLayer { }` 里读

```kotlin
// ✅ 正确：延迟读取，动画帧只触发「重绘」
val factor = animateFloatAsState(targetValue = ..., animationSpec = ...)
return this.graphicsLayer {
    scaleX = factor.value
    scaleY = factor.value
}

// ❌ 错误：组合阶段读值，每一帧都重组整棵子树
val factor by animateFloatAsState(targetValue = ...)
```

### 2. 只动 transform / alpha

绝不对 `padding` / `height` / `size` / `offset` 做动画 —— 那会每帧触发
**重新测量与布局**，长列表里立刻掉帧。需要位移就用 `graphicsLayer { translationX/Y }`。

### 3. 颜色动画用 Color 专用规格

```kotlin
val color by animateColorAsState(
    targetValue = ...,
    animationSpec = Motion.color(reduced),   // AnimationSpec<Color>
    label = "..."
)
```

`Motion.fast()` 返回 `AnimationSpec<Float>`，喂给 `animateColorAsState` 会编译不过。

### 4. 入场动画只播一次

`EnterTracker` 记账已播过的条目 id。列表项滚出屏幕再回来时**不重播**，
否则滚动过程中会一直有元素在动，反而更晃。

```kotlin
val tracker = rememberEnterTracker()
TimelineRow(modifier = Modifier.staggerEnter(tracker, entry.id, index))
```

### 5. 不用 `LaunchedEffect { while(true) { delay(...) } }` 轮询

当前时间线这类「每 30 秒更新一次」的需求，用 Flow 实现，
只在真正到期时重组一次，不会在动画帧里反复触发重组。

```kotlin
@Composable
fun rememberNowMillis(intervalMs: Long = 30_000L): Long {
    val flow = remember(intervalMs) { flow { while (true) { emit(now()); delay(intervalMs) } } }
    val now by flow.collectAsState(initial = System.currentTimeMillis())
    return now
}
```

### 6. 不用 Material 的 TextField / Switch

这两个组件自带较多内建动画与重组点，在长表单里滚动容易掉帧。
本项目自绘 `AppTextField`（`BasicTextField`）与 `SwitchRow`，
只保留必要的 focus / pressed 反馈。

---

## 三、Modifier 链顺序

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

**为什么 shadow 必须在 clip 之前**：先裁切再投影，阴影会被裁切形状切掉，等于没有阴影。

**为什么 scale 必须在 background 之前**：先缩放再画背景，缩放的是整块内容；
反过来则只缩放文字，背景不动，看起来像「文字陷进去了」。

---

## 四、页面转场：息屏 → 亮屏

对应小程序的 `page-wake`：

| 阶段 | 页面 | 遮罩 |
|---|---|---|
| 0% | 缩到 0.94、透明 | 全暗（不透明度 1） |
| 38% | 回弹到 1.015 | 降到 0.55 |
| 100% | 落回 1.0 | 全透明 |

```kotlin
key(page) {                        // 用 key 强制重建 → 动画必定重播
    Box(Modifier.wakeIn()) { ... }
}
```

### 4.1 遮罩必须挂在**根层**，不能放在 Scaffold 的 content 里

小程序的遮罩写在 `custom-tab-bar` 内（`position:fixed; z-index:45`），
因为自定义 tabBar 由框架渲染在页面层之上，页面内的 z-index 压不住它。

安卓端同理：`Scaffold` 的 `bottomBar` 槽位会画在 content **之上**。
遮罩若留在 content 里就盖不住底栏 —— 结果是「底栏先亮、内容后亮」，
视觉上就是一次闪屏。现在遮罩挂在根 `Box` 的最后一层：

```kotlin
Box(Modifier.fillMaxSize()) {
    Scaffold(bottomBar = { AppBottomBar(...) }) { padding -> ... }   // 底栏
    WakeVeil(wake)                                                 // 遮罩在最后 → 盖住全屏
}
```

### 4.2 只有**真正的 Tab 切换**才播遮罩

对齐小程序 `utils/tab-motion.js` 的 `markTabSwitch` / `consumeTabSwitch`：
从二级页（编辑）进入或返回、应用回前台，**都不播**。
否则每次打开编辑页都会「屏幕先暗一下再亮」，看起来像故障。

用 [WakeController] 承担这个状态机：`token` 自增作为动画触发键，
`token == 0` 表示还没切换过 → 不播。

### 4.3 压暗必须**同步**发生，不能丢进协程

遮罩压暗必须与「新页面入场（alpha 从 0 开始）」落在**同一帧**。
若把压暗放进 `LaunchedEffect` / 协程，中间的某一帧会是
「旧页已走、新页还没亮、遮罩还没压暗」= **一帧白底** —— 这正是用户报的闪屏。

所以 `arm()` 在点击回调里同步写状态：

```kotlin
onSelect = { next ->
    if (next != tab) {
        tab = next; page = ...
        wake.arm()      // 同步压暗 → 与页面入场同帧
    }
}
```

### 4.4 alpha 必须钳制在 [0, 1]

回弹曲线 `cubic-bezier(.34,1.56,.64,1)` 的输出最大到 **1.0978**（t≈0.5821）。
Compose 的 `TweenSpec` 只钳制**输入** fraction，回弹发生在 `easing.transform()` **之后**，
所以进度会真的超过 1。CSS 那边由浏览器自动把 opacity 钳到 [0,1]，小程序看不出来；
安卓端不钳制就会把负 alpha 交给 `Paint.setAlpha(-25)` —— 渲染未定义。

```kotlin
fun veilOpacity(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.45f -> 1f - 0.45f * (p / 0.45f)
        else -> 0.55f * (1f - (p - 0.45f) / 0.55f)
    }
}
```

`staggerEnter` 的 `alpha = p` 同样要 `coerceIn(0f, 1f)`。

### 4.5 二级页用 `pageEnter()`，不要用 `wakeIn()`

Tab 页 = 息屏亮屏（`wakeIn`）；二级页 = 上浮淡入（`pageEnter`，
对应小程序 `.page-enter`）。二级页不该再叠一层「息屏」。

---

## 五、底部导航

对齐小程序 `custom-tab-bar`：

| 元素 | 小程序 | 安卓端 |
|---|---|---|
| 图标 | 52rpx | **52rpx** |
| 指示框 | 96rpx 圆角 30rpx | 68rpx 圆角 24rpx（要给文字让位） |
| 图标交叉淡入 | 两层 `scale(.86)→scale(1)` | 底层常态色 + 上层品牌色，同效果 |
| 文字 | 无（森空岛风格） | 26rpx / 行高 30rpx |

指示框用 `graphicsLayer { translationX }` 平移，动画期间只重绘不重排。
图标两层交叉淡入时，上层用 `Motion.tab()`（带回弹），下层用 `Motion.fast()`（柔和）。

底栏图标尺寸**必须**用 `Modifier.size()`，不能用 `Modifier.width()` ——
只设宽度会让宽高比失配，图标被压扁，看上去就是「文本与图标错位」。

---

## 六、降低动效

系统「开发者选项 → 动画时长 = 0」或「无障碍 → 移除动画」时：

* 所有时长归零（`tween(0)`），**立即到位，不改变布局**
* `ContentObserver` 监听 `ANIMATOR_DURATION_SCALE`，用户改完立即生效
* 息屏遮罩不播（否则会闪一下黑屏）

---

## 七、检查清单

新增动效前对照这几条：

- [ ] 动画值是 `State<Float>` 而不是 `by` 取值
- [ ] `.value` 只在 `graphicsLayer { }` / `drawBehind { }` 里读
- [ ] 没有动画布局属性
- [ ] 颜色动画用 `Motion.color()`
- [ ] 列表入场接了 `EnterTracker`，滚动不重播
- [ ] `shadow` 在 `clip` 之前
- [ ] `scale` 在 `background` 之前
- [ ] 降低动效下时长归零且不改变布局
- [ ] 遮罩挂在根层（在 Scaffold 之外），能盖住底栏
- [ ] 只有真正的 Tab 切换才 `arm()`，压暗是**同步**调用
- [ ] 所有 alpha 都 `coerceIn(0f, 1f)`（回弹曲线会过冲到 1.0978）
- [ ] 图标用 `Modifier.size()`，不用 `Modifier.width()`
- [ ] 单色背景用 `SolidColor`，**不要** `Brush.linearGradient(listOf(单色))`
      —— 单色渐变会在绘制期抛 `IllegalArgumentException` 直接闪退

---

## 八、回归守卫

`app/src/test/java/com/campus/schedule/ui/UiRegressionGuardTest.kt` 守着上面最后三条：

* `不允许出现单色渐变` —— 静态扫描源码，防止 `Brush.linearGradient(listOf(单色))` 回潮
* `图标不得只设宽度` —— 静态扫描源码，防止 `.width(` 压扁图标
* `veilOpacity 回弹过冲时不得返回负值` —— 纯函数断言

前两条只能靠**扫源码**来守：单色渐变是在**绘制期**才炸的，
编译期和普通单测都测不出来，而本项目没有 Robolectric，跑不了 Compose 渲染测试。
