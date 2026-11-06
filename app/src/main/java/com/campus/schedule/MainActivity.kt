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
                AppRoot(
                    focusId = focusIdState.value,
                    focusToken = focusTokenState.value
                )
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

@Composable
private fun AppRoot(focusId: String?, focusToken: Int) {
    val context = LocalContext.current
    val repo = remember {
        ScheduleRepository(AppDatabase.get(context).scheduleDao())
    }
    val vm: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory(repo, context))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val items by vm.allItems.collectAsStateWithLifecycle()

    var page by remember { mutableStateOf(Page.SCHEDULE) }
    var tab by remember { mutableStateOf(Tab.SCHEDULE) }
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var pendingDiscard by remember { mutableStateOf(false) }

    /** 页面转场方向：1 = 往右翻（新页从右进），-1 = 往左翻。 */
    var navDirection by remember { mutableIntStateOf(1) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 通知点进来时定位到对应日程。
    // 用 handledToken 保证同一次点击只消费一次 —— 否则 items 每次更新
    // （比如改完一条日程）都会把用户强行拽回那个条目。
    var handledToken by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusId, focusToken, items) {
        if (focusId == null || handledToken == focusToken || items.isEmpty()) return@LaunchedEffect
        val target = items.firstOrNull { it.id == focusId } ?: return@LaunchedEffect
        navDirection = -1
        editing = target
        page = Page.EDIT
        handledToken = focusToken
    }

    // 物理返回键：编辑页自己处理（可能要确认丢弃改动），非首页把 Tab 退回「日程」，
    // 其余情况不拦截，交给系统退出应用。
    BackHandler(enabled = tab != Tab.SCHEDULE) {
        navDirection = -1
        tab = Tab.SCHEDULE
        page = Page.SCHEDULE
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

    Box(modifier = Modifier.fillMaxSize().background(Tokens.Paper)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // 底部导航栏始终占位（进入编辑页也不塌陷）：否则 Scaffold 的
            // innerPadding 会在切页瞬间从「有底栏」跳到「无底栏」，
            // 所有带 padding(bottom) 的内容都会跟着上下弹一下。
            bottomBar = {
                AppBottomBar(
                    selected = tab,
                    enabled = page != Page.EDIT,
                    onSelect = { next ->
                        if (next != tab) {
                            // 按 Tab 的左右顺序决定滑动方向，而不是按点击先后
                            navDirection = if (next.ordinal > tab.ordinal) 1 else -1
                            tab = next
                            page = when (next) {
                                Tab.SCHEDULE -> Page.SCHEDULE
                                Tab.OVERVIEW -> Page.OVERVIEW
                                Tab.MINE -> Page.MINE
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = page == Page.SCHEDULE,
                    enter = scaleIn(
                        animationSpec = Motion.overlay(LocalReducedMotion.current),
                        initialScale = 0.8f
                    ) + fadeIn(tween(Motion.DurFast)),
                    exit = scaleOut(tween(Motion.DurExit)) + fadeOut(tween(Motion.DurExit))
                ) {
                    CreateFab {
                        navDirection = -1
                        editing = null
                        page = Page.EDIT
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
                targetState = page,
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
                                onModeChange = vm::setMode,
                                onFilterChange = vm::onSelectFilter,
                                onPrevWeek = vm::onPrevWeek,
                                onNextWeek = vm::onNextWeek,
                                onThisWeek = vm::onBackToThisWeek,
                                onToday = vm::onBackToToday,
                                onPrevDay = { vm.onSelectDate(CoreDate.addDays(state.selectedDate, -1)) },
                                onNextDay = { vm.onSelectDate(CoreDate.addDays(state.selectedDate, 1)) },
                                onSelectDate = vm::onSelectDate,
                                onItemClick = { item ->
                                    navDirection = -1
                                    editing = item
                                    page = Page.EDIT
                                },
                                onToggleStar = vm::onToggleStar,
                                onToggleConflict = vm::onToggleConflict,
                                onCreateNew = {
                                    navDirection = -1
                                    editing = null
                                    page = Page.EDIT
                                }
                            )

                            Page.OVERVIEW -> OverviewScreen(
                                items = items,
                                onItemClick = { item ->
                                    navDirection = -1
                                    editing = item
                                    page = Page.EDIT
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
                                    navDirection = -1
                                    tab = Tab.SCHEDULE
                                    page = Page.SCHEDULE
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
                                    if (ok) {
                                        navDirection = -1
                                        editing = null
                                        page = Page.SCHEDULE
                                    }
                                    ok
                                },
                                onDelete = { item ->
                                    vm.onDelete(item)
                                    navDirection = -1
                                    editing = null
                                    page = Page.SCHEDULE
                                },
                                onBack = {
                                    navDirection = -1
                                    editing = null
                                    page = if (tab == Tab.MINE) Page.MINE else Page.SCHEDULE
                                },
                                onRequestDiscard = { pendingDiscard = true }
                            )
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
            navDirection = -1
            editing = null
            page = if (tab == Tab.MINE) Page.MINE else Page.SCHEDULE
        },
        onDismiss = { pendingDiscard = false }
    )
}

/**
 * 底部导航（v2.2 重做）。
 *
 * ## 旧版错位从哪来
 *
 * 1. 图标容器 48rpx、图标 44rpx —— 上下左右各 2rpx 不对称余量；
 * 2. 文字用 caption，行高 36rpx，而图标 44rpx；两者堆叠后整组高 92rpx，
 *    却在一个 112rpx 的轨道里靠顶排列，底部空出 20rpx，看起来「贴天不贴地」；
 * 3. 选中 / 未选中字重不同（Bold vs Normal），文字宽度随状态变化，居中时会左右抖。
 *
 * ## 现在
 *
 * 图标 48rpx 与文字行高 28rpx 之间固定 4rpx 间隙，整组用 `Arrangement.Center`
 * 垂直居中；字重恒定 SemiBold，只靠颜色区分；指示器是图标正下方的一条
 * 28×4rpx 圆头短线，位置由同一套尺寸算出来，不再手写 padding。
 */
@Composable
private fun AppBottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    enabled: Boolean = true
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val tabs = Tab.values()
    val hairline = m.rpx(2)

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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cellWidth = maxWidth / tabs.size
            val barHeight = m.rpx(120)
            val iconBox = m.rpx(48)
            val labelHeight = m.rpx(28)
            val gap = m.rpx(4)
            val markW = m.rpx(28)
            val markH = m.rpx(4)
            // 指示器贴在整组内容的下沿，位置由同一套尺寸推导
            val groupH = iconBox + gap + labelHeight
            val markTop = (barHeight - groupH) / 2 + groupH - markH

            val index = tabs.indexOf(selected)
            val target = cellWidth * index.toFloat() + (cellWidth - markW) / 2
            val slideDp = animateFloatAsState(
                targetValue = target.value,
                animationSpec = Motion.tab(reduced),
                label = "tabUnderline"
            )
            val slidePx = with(density) { slideDp.value.dp.toPx() }

            Box(modifier = Modifier.fillMaxWidth().height(barHeight)) {
                Box(
                    modifier = Modifier
                        .padding(top = markTop)
                        .width(markW)
                        .height(markH)
                        .graphicsLayer { translationX = slidePx }
                        .clip(RoundedCornerShape(markH / 2))
                        .background(Tokens.Brand)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEach { item ->
                        BottomTabItem(
                            tab = item,
                            active = item == selected,
                            enabled = enabled,
                            onClick = { onSelect(item) },
                            modifier = Modifier.width(cellWidth).height(barHeight)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: Tab,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current
    val interaction = rememberPressInteraction()

    // 只做颜色过渡：字重恒定 → 文字宽度不变 → 居中不会抖
    val blend = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = Motion.fast(reduced),
        label = "tabBlend"
    )
    val tint = lerp(Tokens.InkMuted, Tokens.Brand, blend.value)

    Column(
        modifier = modifier.rippleClick(interaction, enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(m.rpx(48)),
            contentAlignment = Alignment.Center
        ) {
            // 让 Icon 撑满容器自己居中，比手算 padding 更不容易错位
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = tab.label,
            // 行高与 groupH 里的 labelHeight(28rpx) 完全一致
            style = m.micro.copy(
                fontSize = m.sp(24),
                lineHeight = m.sp(28),
                color = tint,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            modifier = Modifier.padding(top = m.rpx(4)),
            maxLines = 1
        )
    }
}


/** 新建日程的悬浮按钮。 */
@Composable
private fun CreateFab(onClick: () -> Unit) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    Box(
        modifier = Modifier
            .padding(bottom = m.rpx(24))
            .size(m.rpx(112))
            .pressHighlight(interaction, RoundedCornerShape(m.radiusLg))
            .pressScale(interaction, Motion.ScaleCard)
            .appShadow(2, CircleShape)
            .clip(CircleShape)
            .background(Tokens.BrandGradient)
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
