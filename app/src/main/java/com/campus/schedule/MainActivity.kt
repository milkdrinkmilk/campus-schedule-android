package com.campus.schedule

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.campus.schedule.ui.components.clickableNoRipple
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campus.schedule.core.CoreDate
import com.campus.schedule.data.AppDatabase
import com.campus.schedule.data.Backup
import com.campus.schedule.data.ScheduleItem
import com.campus.schedule.data.ScheduleRepository
import com.campus.schedule.notify.ReminderScheduler
import com.campus.schedule.ui.ScheduleFilter
import com.campus.schedule.ui.ScheduleViewModel
import com.campus.schedule.ui.components.ConfirmOverlay
import com.campus.schedule.ui.components.LaunchOverlay
import com.campus.schedule.ui.components.SceneTransition
import com.campus.schedule.ui.components.pressHighlight
import com.campus.schedule.ui.components.pressScale
import com.campus.schedule.ui.components.rememberPressInteraction
import com.campus.schedule.ui.components.rippleClick
import com.campus.schedule.ui.components.appShadow
import com.campus.schedule.ui.components.enterPageTransition
import com.campus.schedule.ui.components.enterSheetTransition
import com.campus.schedule.ui.components.exitPageTransition
import com.campus.schedule.ui.components.exitSheetTransition
import com.campus.schedule.ui.icons.AppIcons
import com.campus.schedule.ui.screens.EditScreen
import com.campus.schedule.ui.screens.MineScreen
import com.campus.schedule.ui.screens.OverviewScreen
import com.campus.schedule.ui.screens.ScheduleScreen
import com.campus.schedule.ui.theme.CampusScheduleTheme
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalPalette
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * 通知点进来时要定位的日程 id。
     *
     * 必须是**快照状态**而不是 onCreate 里读一次的局部变量：
     * 应用已在前台时点通知走的是 [onNewIntent]（Activity 不会重建），
     * 之前用于接收的局部变量永远停在冷启动时的值，所以「点通知不跳转」。
     */
    private val focusIdState = mutableStateOf<String?>(null)

    /** 每次 [onNewIntent] 自增，作为「同一条通知再点一次」的触发键。 */
    private val focusTokenState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ 需要运行时申请通知权限，否则本地提醒不会弹出。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        focusIdState.value = intent?.getStringExtra(EXTRA_FOCUS_ID)

        setContent {
            CampusScheduleTheme {
                // 启动动画盖在整个界面之上：覆盖层自己会在入场结束后淡出并移除。
                // 主界面在覆盖层之下**同时**完成首帧组合，所以淡出时底下已经是
                // 画好的页面，不是白纸 —— 这是「无闪屏」的关键。
                // 启动时序（v2.4.1）：
                // booted   —— 覆盖层第一帧就置真，让 SceneTransition 立刻开始收拢、
                //              主界面趁覆盖层还在时完成全部重组（预热）。
                // dismissed —— 约 1300ms 后移除覆盖层节点。之前 onFinished 一触发
                //              就同时「撤遮罩 + 首次组合整页」，两件事挤在同几帧里，
                //              这就是用户看到的「动画卡顿」。现在二者错开。
                var booted by remember { mutableStateOf(false) }
                var dismissed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!dismissed) {
                        kotlinx.coroutines.delay(1300)
                        dismissed = true
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        focusId = focusIdState.value,
                        focusToken = focusTokenState.value,
                        sceneEnabled = booted,
                    )
                    if (!dismissed) {
                        LaunchOverlay(onFinished = { booted = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 热启动路径必须在这里把 id 写回状态，组合层才会重新读取。
        focusIdState.value = intent.getStringExtra(EXTRA_FOCUS_ID)
        focusTokenState.value += 1
    }

    companion object {
        const val EXTRA_FOCUS_ID = "extra_focus_id"
    }
}

/** 顶层页面。 */
private enum class Page { SCHEDULE, OVERVIEW, MINE, EDIT }

/** 底部导航的三个 Tab。 */
private enum class Tab(val label: String, val iconRes: Int) {
    SCHEDULE("日程", AppIcons.schedule),
    OVERVIEW("概况", AppIcons.overview),
    MINE("我的", AppIcons.mine)
}

/** Tab → 它对应的首页。 */
private fun Tab.toPage(): Page = when (this) {
    Tab.SCHEDULE -> Page.SCHEDULE
    Tab.OVERVIEW -> Page.OVERVIEW
    Tab.MINE -> Page.MINE
}

/** 页面 → 它应该高亮的 Tab（编辑页不属于任何 Tab，回落到日程）。 */
private fun Page.tabOrDefault(): Tab = when (this) {
    Page.OVERVIEW -> Tab.OVERVIEW
    Page.MINE -> Tab.MINE
    else -> Tab.SCHEDULE
}

@Composable
private fun AppRoot(focusId: String?, focusToken: Int, sceneEnabled: Boolean = true) {
    val context = LocalContext.current
    val repo = remember {
        ScheduleRepository(AppDatabase.get(context).scheduleDao())
    }
    val vm: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory(repo, context))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val items by vm.allItems.collectAsStateWithLifecycle()

    // 返回栈：栈底的 Tab 页是「首页」，往上压二级页（目前只有编辑页）。
    // 每次返回只弹一层，弹出来的目标页决定 tab 高亮 —— 见 BackHandler。
    val pageStack = remember { mutableStateListOf(Page.SCHEDULE) }
    // 当前页 = 栈顶。写成 lambda 每次读都取最新栈顶，避免多存一份状态
    val currentPage: () -> Page = { pageStack.last() }
    var tab by remember { mutableStateOf(Tab.SCHEDULE) }
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
    // 页面转场方向：1 = 往右翻（新页从右进），-1 = 往左翻。
    var navDirection by remember { mutableIntStateOf(1) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var pendingDiscard by remember { mutableStateOf(false) }

    fun push(next: Page) {
        if (currentPage() == next) return
        if (next == Page.EDIT) navDirection = -1 else {
            navDirection = if ((next.tabOrDefault().ordinal) >= tab.ordinal) 1 else -1
        }
        pageStack.add(next)
    }

    /** 回到某个 Tab 页：把栈裁剪到只保留它（Tab 之间切换 = 换首页）。 */
    fun switchTab(next: Tab) {
        if (next == tab && currentPage() == next.toPage()) return
        navDirection = if (next.ordinal > tab.ordinal) 1 else -1
        tab = next
        editing = null
        pageStack.clear()
        pageStack.add(next.toPage())
    }

    /** 从二级页返回一级：弹栈，目标页决定 tab。 */
    fun popPage() {
        if (pageStack.size <= 1) return
        navDirection = -1
        val leaving = pageStack.last()
        pageStack.removeAt(pageStack.lastIndex)
        val target = pageStack.last()
        if (leaving == Page.EDIT) editing = null
        if (target != Page.EDIT) tab = target.tabOrDefault()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 通知点进来时定位到对应日程。
    // 用 handledToken 保证同一次点击只消费一次 —— 否则 items 每次更新
    // （比如改完一条日程）都会把用户强行拽回那个条目。
    var handledToken by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusId, focusToken, items) {
        if (focusId == null || handledToken == focusToken || items.isEmpty()) return@LaunchedEffect
        val target = items.firstOrNull { it.id == focusId } ?: return@LaunchedEffect
        editing = target
        push(Page.EDIT)
        handledToken = focusToken
    }

    // 物理返回键：只返回**上一级**（v2.3）。
    //
    // 旧版写的是 `tab = SCHEDULE; page = SCHEDULE`，于是从「我的」按返回会
    // 一步跨到日程首页 —— 相当于把 Tab 历史全部丢掉，连点两次返回的人会
    // 直接怀疑人生。现在维护一个真正的返回栈：进二级页压栈，按返回弹一层。
    // 编辑页自己还有一层 BackHandler（脏数据确认），它比这里先响应。
    BackHandler(enabled = pageStack.size > 1) {
        navDirection = -1
        val leaving = pageStack.last()
        pageStack.removeAt(pageStack.lastIndex)
        val target = pageStack.last()
        if (leaving == Page.EDIT) editing = null
        if (target != Page.EDIT) tab = target.tabOrDefault()
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeToast()
        }
    }

    // ---------- 导入备份 ----------
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (text.isNullOrBlank()) {
                    snackbarHostState.showSnackbar("备份文件读不到内容")
                    return@launch
                }
                val parsed = runCatching { Backup.importJson(text) }.getOrNull()
                if (parsed == null) {
                    snackbarHostState.showSnackbar("备份格式不对，导入失败")
                } else {
                    repo.importAll(parsed)
                    // 导入的条目也要把提醒闹钟排上，否则「导入了但不会提醒」。
                    parsed.forEach { ReminderScheduler.schedule(context, it) }
                    snackbarHostState.showSnackbar("已导入 ${parsed.size} 条日程")
                }
            }
        }
    }

    // ---------- 导出备份 ----------
    // 走 SAF（用户自己选保存位置），不再直写公共下载目录 ——
    // targetSdk 34 上 Scoped Storage 会直接抛异常，且应用没有存储权限。
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val count = items.size
                val ok = runCatching {
                    val bytes = Backup.exportJson(items).toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("无法写入所选位置")
                }.isSuccess
                snackbarHostState.showSnackbar(if (ok) "已导出 $count 条日程" else "导出失败，请换个位置重试")
            }
        }
    }

    // 经典「Scene 转场」：启动画面里的元素不会凭空消失，而是交出控制权。
    // sceneEnabled 由启动覆盖层控制，见 MainActivity.onCreate 的 setContent。
    SceneTransition(enabled = sceneEnabled) {
    Box(modifier = Modifier.fillMaxSize().background(Tokens.Paper)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // 底部导航栏始终占位（进入编辑页也不塌陷）：否则 Scaffold 的
            // innerPadding 会在切页瞬间从「有底栏」跳到「无底栏」，
            // 所有带 padding(bottom) 的内容都会跟着上下弹一下。
            bottomBar = {
                // v2.4：编辑页不显示底栏。
                //
                // 之前是「始终渲染、只把 enabled 关掉」，为的是让 Scaffold 的
                // innerPadding 不跳变。但编辑页是整屏表单，底下压一条导航栏
                // 既挤占输入空间，视觉上又像「表单上盖了层东西」。
                //
                // 跳变的代价用 AnimatedVisibility 抹平：底栏用同一个时长
                // 收起/展开，而编辑页本身是 slideInVertically 从底部进来的，
                // 两者方向一致，看起来就是「底栏被新页面顶掉了」。
                AnimatedVisibility(
                    visible = currentPage() != Page.EDIT,
                    enter = slideInVertically(
                        animationSpec = tween(Motion.DurPage, easing = Motion.EaseSoft)
                    ) { it } + fadeIn(tween(Motion.DurFast)),
                    exit = slideOutVertically(
                        animationSpec = tween(Motion.DurPage, easing = Motion.EaseInOut)
                    ) { it } + fadeOut(tween(Motion.DurFast))
                ) {
                    AppBottomBar(
                        selected = tab,
                        enabled = true,
                        onSelect = { next -> switchTab(next) }
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = currentPage() == Page.SCHEDULE,
                    enter = scaleIn(
                        animationSpec = Motion.overlay(LocalReducedMotion.current),
                        initialScale = 0.8f
                    ) + fadeIn(tween(Motion.DurFast)),
                    exit = scaleOut(tween(Motion.DurExit)) + fadeOut(tween(Motion.DurExit))
                ) {
                    CreateFab {
                        editing = null
                        push(Page.EDIT)
                    }
                }
            }
        ) { padding ->
            // ---------- 页面内容 ----------
            // v2.1：不再「key(page) + 各自入场」。旧做法在切页的瞬间把旧页整棵拆掉，
            // 新页从透明淡入，中间大概率漏出一帧空白或深色遮罩 —— 就是用户说的闪屏。
            // 现在交给 AnimatedContent：旧页与新页**同时存在**，一个滑出、一个滑入，
            // 任何一帧都有完整内容，全屏平均亮度恒定。
            // 转场必须在这里预先算好：transitionSpec 的 lambda 不是 @Composable 上下文，
            // 里面不能直接调用带 LocalConfiguration 的组合函数。
            val forward = navDirection > 0
            val tabEnter = enterPageTransition(fromRight = forward)
            val tabExit = exitPageTransition(fromRight = forward)
            val backEnter = enterPageTransition(fromRight = false)
            val enterSheet = enterSheetTransition()
            val exitSheet = exitSheetTransition()
            val cancelExit = ExitTransition.None

            AnimatedContent(
                targetState = currentPage(),
                transitionSpec = {
                    when {
                        // 二级页：从底部推入，盖在 Tab 之上
                        targetState == Page.EDIT -> enterSheet togetherWith cancelExit
                        // 从二级页返回：新页从左侧退回，编辑页往下沉
                        initialState == Page.EDIT -> backEnter togetherWith exitSheet
                        // Tab 之间：同层横移
                        else -> tabEnter togetherWith tabExit
                    }
                },
                label = "page-transition",
                modifier = Modifier.fillMaxSize()
            ) { current ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (current == Page.EDIT) 0.dp else padding.calculateBottomPadding())
                ) {
                    when (current) {
                            Page.SCHEDULE -> ScheduleScreen(
                                state = state,
                                onFilterChange = vm::onSelectFilter,
                                onPrevWeek = vm::onPrevWeek,
                                onNextWeek = vm::onNextWeek,
                                onThisWeek = vm::onBackToThisWeek,
                                onToday = vm::onBackToToday,
                                onPrevDay = vm::onPrevDay,
                                onNextDay = vm::onNextDay,
                                onSelectDate = vm::onSelectDate,
                                onItemClick = { item ->
                                    editing = item
                                    push(Page.EDIT)
                                },
                                onToggleStar = vm::onToggleStar,
                                onToggleConflict = vm::onToggleConflict,
                                onCreateNew = {
                                    editing = null
                                    push(Page.EDIT)
                                }
                            )

                            Page.OVERVIEW -> OverviewScreen(
                                items = items,
                                onItemClick = { item ->
                                    editing = item
                                    push(Page.EDIT)
                                }
                            )

                            Page.MINE -> MineScreen(
                                items = items,
                                onFilterJump = { key ->
                                    vm.onSelectFilter(
                                        when (key) {
                                            "today" -> ScheduleFilter.TODAY
                                            "starred" -> ScheduleFilter.STARRED
                                            else -> ScheduleFilter.ALL
                                        }
                                    )
                                    switchTab(Tab.SCHEDULE)
                                },
                                onExport = { exportLauncher.launch(Backup.fileName()) },
                                onImport = {
                                    importLauncher.launch(
                                        arrayOf("application/json", "text/plain", "*/*")
                                    )
                                },
                                onClearSamples = vm::clearSamples,
                                onClearAll = { confirmClearAll = true }
                            )

                            Page.EDIT -> EditScreen(
                                editing = editing,
                                onSave = { form ->
                                    val ok = if (editing == null) {
                                        vm.onCreate(form)
                                    } else {
                                        vm.onUpdate(editing!!, form)
                                    }
                                    if (ok) popPage()
                                    ok
                                },
                                onDelete = { item ->
                                    vm.onDelete(item)
                                    popPage()
                                },
                                onBack = { popPage() },
                                onRequestDiscard = { pendingDiscard = true }
                            )
                        }
                }
            }
        }
    }

    }

    // 清空全部数据的二次确认
    ConfirmOverlay(
        visible = confirmClearAll,
        title = "清空全部日程？",
        body = "所有日程（含你自己创建的）都会被删除，且无法恢复。建议先导出备份。",
        confirmText = "确认清空",
        onConfirm = {
            vm.clearAll()
            confirmClearAll = false
        },
        onDismiss = { confirmClearAll = false }
    )

    // 编辑页带着未保存改动返回时的确认
    ConfirmOverlay(
        visible = pendingDiscard,
        title = "放弃这次编辑？",
        body = "改动还没有保存，离开后不会保留。",
        confirmText = "放弃修改",
        onConfirm = {
            pendingDiscard = false
            popPage()
        },
        onDismiss = { pendingDiscard = false }
    )
}

/**
 * 底部导航（v2.4：加了滑块）。
 *
 * ## 演化过程（教训都在这里）
 *
 * * v2.1：浮块指示器 + 弹性曲线 → 反馈「动画很奇怪」。68rpx 的实心块
 *   比页面里绝大多数元素都重，且弹跳曲线过冲 9.78%，停下来的那一下像在抽搐。
 * * v2.2：滑动下划线 + 细线分隔 → 反馈「错位」。下划线用 `padding(top)`
 *   手算位置，改一次字号就偏一次。
 * * v2.3：干脆全部删掉，只靠颜色区分 → 干净了，但少了「按下去有个东西
 *   在走」的反馈。用户这次明确要「滑块动画」。
 * * v2.4（现在）：把滑块做成**一枚浅色药丸**，铺在三个格子中间滑，而不是
 *   压在格子外面。
 *
 * ## 为什么这样不会重蹈 v2.1/v2.2 的覆辙
 *
 * 1. **位置只有一个来源**：药丸的 x 由 `BoxWithConstraints` 拿到的 `cellWidth`
 *    乘索引得出，不参与任何 padding 手算；格子自己也是 `weight(1f)`，
 *    两者共用同一个宽度基准，永远对齐。
 * 2. **不抢主体**：药丸是 `brandTint` 浅色 + 无描边，视觉重量远低于页面里的
 *    卡片；图标和文字仍然承担主要的「选中」编码。
 * 3. **曲线不过冲**：用 `Motion.tab()`（线性缓出，260→220ms），到位置就停住，
 *    没有回弹。回弹在底栏这种「高频、低信息量」的控件上只会显得毛躁。
 * 4. **和内容同一节奏**：药丸滑动时长与页转场（`Motion.DurPage`）分开配置，
 *    但都落在 200–300ms 区间，联动起来不会一快一慢。
 *
 * 编辑页不显示底栏（`Scaffold` 里判断），见 AppRoot。
 */
@Composable
private fun AppBottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    enabled: Boolean = true
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val tabs = Tab.values()
    val hairline = m.rpx(2)
    val barHeight = m.rpx(112)
    // 药丸高度比格子略矮，上下留出「浮在格子里」的余量
    val pillHeight = m.rpx(88)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.PaperRaised)
            .drawBehind {
                // 2rpx 顶部分隔线：和卡片描边同一套线条语言
                drawRect(
                    color = Tokens.Line,
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(size.width, hairline.toPx())
                )
            }
            .navigationBarsPadding()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val cellWidth = maxWidth / tabs.size
            // 药丸比格子窄一点，左右各留 12rpx，滑到位时和格子居中
            val pillInset = m.rpx(12)
            val pillWidth = cellWidth - pillInset * 2
            val pillTarget = with(density) {
                (pillInset + cellWidth * tabs.indexOf(selected)).toPx()
            }
            val pillTop = (barHeight - pillHeight) / 2

            val pillX = animateFloatAsState(
                targetValue = pillTarget,
                animationSpec = if (reduced) tween(0) else Motion.tab(reduced),
                label = "tabPill"
            )

            Box(
                modifier = Modifier
                    .padding(start = pillInset, top = pillTop)
                    .graphicsLayer { translationX = pillX.value }
                    .width(pillWidth)
                    .height(pillHeight)
                    .clip(RoundedCornerShape(m.radiusPill))
                    .background(p.brandTint)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEach { item ->
                    BottomTabItem(
                        tab = item,
                        active = item == selected,
                        enabled = enabled,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 单个 Tab。
 *
 * 高度锚定在父级给的 112rpx 上（`fillMaxHeight` 而不是自算），
 * 图标盒与文字行高固定，两者之间 6rpx —— 这是整套里唯一的几何常数，
 * 改字号也不会推动图标。
 */
@Composable
private fun BottomTabItem(
    tab: Tab,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val interaction = rememberPressInteraction()

    // 只做颜色过渡：尺寸/字宽恒定 → 居中不会抖
    val blend = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = Motion.fast(reduced),
        label = "tabBlend"
    )
    val tint = lerp(Tokens.InkSecondary, p.brand, blend.value)
    val shape = RoundedCornerShape(m.radiusMd)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = m.rpx(8), vertical = m.rpx(6))
            .clip(shape)
            .pressHighlight(interaction, shape, enabled = enabled)
            .clickableNoRipple(interaction, enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(m.rpx(44)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = tab.label,
            style = m.micro.copy(
                fontSize = m.sp(24),
                lineHeight = m.sp(30),
                color = tint,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(top = m.rpx(6)),
            maxLines = 1
        )
    }
}


/** 新建日程的悬浮按钮。 */
@Composable
private fun CreateFab(onClick: () -> Unit) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()
    Box(
        modifier = Modifier
            .padding(bottom = m.rpx(24))
            .size(m.rpx(112))
            .pressHighlight(interaction, RoundedCornerShape(m.radiusLg))
            .pressScale(interaction, Motion.ScaleCard)
            .appShadow(2, CircleShape)
            .clip(CircleShape)
            .background(p.brandGradient)
            .rippleClick(interaction, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(AppIcons.plus),
            contentDescription = "新建日程",
            tint = Tokens.PaperRaised,
            modifier = Modifier.size(m.rpx(48))
        )
    }
}

/*
 * 导出备份的实际写入在 AppRoot 的 exportLauncher（SAF）里完成。
 *
 * 这里原本有一段 `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`
 * + `File.writeText` 的实现，在 targetSdk 34 下会被 Scoped Storage 拒绝并崩溃，
 * 且应用并未申请任何存储权限，所以整段移除。
 */
