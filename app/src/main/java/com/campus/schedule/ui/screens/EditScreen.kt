package com.campus.schedule.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.campus.schedule.core.CoreDate
import com.campus.schedule.core.CoreValidators
import com.campus.schedule.data.ScheduleItem
import com.campus.schedule.ui.components.AppButton
import com.campus.schedule.ui.components.AppTextField
import com.campus.schedule.ui.components.ButtonStyle
import com.campus.schedule.ui.components.CardHeader
import com.campus.schedule.ui.components.ChoiceChip
import com.campus.schedule.ui.components.ColorSwatch
import com.campus.schedule.ui.components.FieldLabel
import com.campus.schedule.ui.components.PickerTrigger
import com.campus.schedule.ui.components.RaisedCard
import com.campus.schedule.ui.components.SwitchRow
import com.campus.schedule.ui.components.pressHighlight
import com.campus.schedule.ui.components.rememberEnterTracker
import com.campus.schedule.ui.components.pressScale
import com.campus.schedule.ui.components.rememberPressInteraction
import com.campus.schedule.ui.components.rippleClick
import com.campus.schedule.ui.components.staggerEnter
import com.campus.schedule.ui.icons.AppIcons
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens
import java.util.Calendar
import java.util.TimeZone

/**
 * 新建 / 编辑日程 —— 对应小程序的 `pages/project/edit`，
 * 砍掉类型、级别、必要性、主办方、联系人、材料、附件、可见范围等发布专属字段。
 *
 * 表单按小程序的分块结构组织：基本信息 → 时间 → 分类与提醒 → 备注，
 * 每块错位入场（90ms 步进），chip / 开关都有选中动效。
 */
@Composable
fun EditScreen(
    editing: ScheduleItem?,
    onSave: (CoreValidators.Form) -> Boolean,
    onDelete: (ScheduleItem) -> Unit,
    onBack: () -> Unit,
    onRequestDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val tracker = rememberEnterTracker()

    val initial = remember(editing) {
        if (editing != null) {
            CoreValidators.Form(
                title = editing.title,
                note = editing.note,
                location = editing.location,
                category = editing.category,
                accent = editing.accent,
                startTime = editing.startTime,
                endTime = editing.endTime,
                isAllDay = editing.isAllDay,
                remindBefore = editing.remindBefore,
                starred = editing.starred
            )
        } else {
            val start = CoreDate.addMinutes(System.currentTimeMillis(), 60)
            CoreValidators.Form(
                startTime = start,
                endTime = start + 60 * 60 * 1000L,
                remindBefore = 10
            )
        }
    }

    var title by remember(editing) { mutableStateOf(initial.title) }
    var note by remember(editing) { mutableStateOf(initial.note) }
    var location by remember(editing) { mutableStateOf(initial.location) }
    var category by remember(editing) { mutableStateOf(initial.category) }
    var accent by remember(editing) { mutableStateOf(initial.accent) }
    var startTime by remember(editing) { mutableStateOf(initial.startTime) }
    var endTime by remember(editing) { mutableStateOf(initial.endTime) }
    var isAllDay by remember(editing) { mutableStateOf(initial.isAllDay) }
    var remindBefore by remember(editing) { mutableStateOf(initial.remindBefore) }
    var starred by remember(editing) { mutableStateOf(initial.starred) }
    var showDelete by remember { mutableStateOf(false) }

    // 表单是否有未保存的改动 —— 只比较能编辑的字段，用于返回时的二次确认。
    val dirty = title != initial.title ||
        note != initial.note ||
        location != initial.location ||
        category != initial.category ||
        accent != initial.accent ||
        startTime != initial.startTime ||
        endTime != initial.endTime ||
        isAllDay != initial.isAllDay ||
        remindBefore != initial.remindBefore ||
        starred != initial.starred

    // 返回：有改动先确认，没改动直接走。
    val requestBack: () -> Unit = { if (dirty) onRequestDiscard() else onBack() }

    // 编辑页的物理返回键必须是「回上一页」，而不是退出整个 App。
    BackHandler(onBack = requestBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.TopGradient)
            .verticalScroll(rememberScrollState())
    ) {
        // ---------- 顶部栏 ----------
        TopBar(
            title = if (editing == null) "新建日程" else "编辑日程",
            onBack = requestBack,
            modifier = Modifier.staggerEnter(tracker, "topbar", 0)
        )

        Column(modifier = Modifier.padding(horizontal = m.pagePadding)) {
            // ---------- 基本信息 ----------
            RaisedCard(
                modifier = Modifier
                    .staggerEnter(tracker, "basic", 1)
                    .padding(top = m.rpx(24)),
                contentPadding = m.rpx(32)
            ) {
                CardHeader(index = "1", title = "基本信息")
                FieldLabel(text = "标题", required = true)
                Spacer(Modifier.height(m.rpx(12)))
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "例如：数据结构复习讲座"
                )

                Spacer(Modifier.height(m.rpx(24)))

                FieldLabel(text = "地点")
                Spacer(Modifier.height(m.rpx(12)))
                AppTextField(
                    value = location,
                    onValueChange = { location = it },
                    placeholder = "例如：计算机学院 A301"
                )
            }

            // ---------- 时间 ----------
            RaisedCard(
                modifier = Modifier
                    .staggerEnter(tracker, "time", 2)
                    .padding(top = m.rpx(24)),
                contentPadding = m.rpx(32)
            ) {
                CardHeader(index = "2", title = "时间")
                FieldLabel(text = "开始时间", required = true)
                Spacer(Modifier.height(m.rpx(12)))
                DateTimePicker(
                    timestamp = startTime,
                    isAllDay = isAllDay,
                    onChange = { startTime = it }
                )

                Spacer(Modifier.height(m.rpx(24)))

                FieldLabel(text = "结束时间", required = true)
                Spacer(Modifier.height(m.rpx(12)))
                DateTimePicker(
                    timestamp = endTime,
                    isAllDay = isAllDay,
                    onChange = { endTime = it }
                )

                Spacer(Modifier.height(m.rpx(8)))

                SwitchRow(
                    title = "全天日程",
                    subtitle = "按当天 00:00 到 23:59 计算",
                    checked = isAllDay,
                    onChange = { on ->
                        isAllDay = on
                        if (on) {
                            // 打开全天：立刻把起止时间快照到当天首尾。
                            // 之前开关只改一个布尔值、时间不动，用户存的时候
                            // 会撞上「全天却不在 00:00」的校验，怎么都存不进去。
                            startTime = CoreDate.startOfDay(startTime)
                            endTime = CoreDate.endOfDay(startTime)
                        } else if (endTime <= startTime) {
                            // 关掉全天：保证是一个可编辑的正区间。
                            endTime = startTime + 60 * 60 * 1000L
                        }
                    }
                )
            }

            // ---------- 分类与提醒 ----------
            RaisedCard(
                modifier = Modifier
                    .staggerEnter(tracker, "meta", 3)
                    .padding(top = m.rpx(24)),
                contentPadding = m.rpx(32)
            ) {
                FieldLabel(text = "分类")
                Spacer(Modifier.height(m.rpx(12)))
                ChipFlow(
                    options = CoreValidators.CATEGORIES,
                    selected = category,
                    onSelect = { category = it }
                )

                Spacer(Modifier.height(m.rpx(24)))

                FieldLabel(text = "提醒")
                Spacer(Modifier.height(m.rpx(12)))
                ChipFlow(
                    options = listOf("不提醒", "开始时", "提前 10 分钟", "提前 1 小时", "提前 1 天"),
                    selected = remindLabel(remindBefore),
                    onSelect = { remindBefore = parseRemind(it) }
                )

                Spacer(Modifier.height(m.rpx(24)))

                FieldLabel(text = "卡片色系")
                Spacer(Modifier.height(m.rpx(12)))
                Row(horizontalArrangement = Arrangement.spacedBy(m.rpx(16))) {
                    CoreValidators.ACCENTS.forEach { key ->
                        ColorSwatch(
                            color = Tokens.accentColor(key),
                            selected = accent == key,
                            onClick = { accent = key }
                        )
                    }
                }

                Spacer(Modifier.height(m.rpx(8)))

                SwitchRow(
                    title = "特别关注",
                    subtitle = "在「星标」筛选里优先看到",
                    checked = starred,
                    onChange = { starred = it }
                )
            }

            // ---------- 备注 ----------
            RaisedCard(
                modifier = Modifier
                    .staggerEnter(tracker, "note", 4)
                    .padding(top = m.rpx(24)),
                contentPadding = m.rpx(32)
            ) {
                FieldLabel(text = "备注")
                Spacer(Modifier.height(m.rpx(12)))
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "补充说明、材料清单等",
                    minLines = 4
                )
            }

            // ---------- 保存 ----------
            AppButton(
                text = if (editing == null) "创建日程" else "保存修改",
                onClick = {
                    onSave(
                        CoreValidators.Form(
                            title = title,
                            note = note,
                            location = location,
                            category = category,
                            accent = accent,
                            startTime = startTime,
                            endTime = endTime,
                            isAllDay = isAllDay,
                            remindBefore = remindBefore,
                            starred = starred
                        )
                    )
                },
                modifier = Modifier
                    .staggerEnter(tracker, "save", 5)
                    .padding(top = m.rpx(40))
            )

            if (editing != null) {
                Spacer(Modifier.height(m.rpx(16)))
                AppButton(
                    text = if (showDelete) "确认删除？再点一次" else "删除这个日程",
                    onClick = {
                        if (showDelete) onDelete(editing) else showDelete = true
                    },
                    style = if (showDelete) ButtonStyle.DANGER else ButtonStyle.GHOST,
                    modifier = Modifier.staggerEnter(tracker, "delete", 6)
                )
            }

            Spacer(Modifier.height(m.rpx(40)))
        }
    }
}

/** 顶部返回栏。 */
@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = m.pagePadding, vertical = m.rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(12))
    ) {
        Box(
            modifier = Modifier
                .size(m.rpx(72))
                .clip(RoundedCornerShape(m.radiusPill))
                .background(Tokens.PaperRaised)
                .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
                .rippleClick(interaction, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(AppIcons.chevronLeftBrand),
                contentDescription = "返回",
                tint = Tokens.Brand,
                modifier = Modifier.size(m.iconSm)
            )
        }
        Text(
            text = title,
            style = m.h2,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 自动换行的标签组。 */
@Composable
private fun ChipFlow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val m = LocalMetrics.current
    // 用固定列数实现换行，避免引入 FlowRow 的实验性 API
    val columns = 3
    Column(verticalArrangement = Arrangement.spacedBy(m.rpx(12))) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(m.rpx(12))) {
                row.forEach { option ->
                    ChoiceChip(
                        text = option,
                        selected = option == selected,
                        onClick = { onSelect(option) }
                    )
                }
            }
        }
    }
}

/**
 * 日期时间选择器。
 *
 * 用原生 DatePickerDialog / TimePickerDialog，避免自绘带来的行为差异。
 * 日期与时间拆成两个触发器，与小程序的 `.picker-trigger` 观感一致。
 */
@Composable
private fun DateTimePicker(
    timestamp: Long,
    isAllDay: Boolean,
    onChange: (Long) -> Unit
) {
    val m = LocalMetrics.current
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(m.rpx(12))
    ) {
        PickerTrigger(
            value = CoreDate.formatMonthDay(timestamp),
            onClick = {
                val cal = utcCalendar(timestamp)
                DatePickerDialog(
                    context,
                    { _, y, month, d ->
                        val updated = cal.clone() as Calendar
                        updated.set(y, month, d)
                        // utcCalendar 把时间平移了 +TZ_OFFSET 以便用 UTC 视图读字段，
                        // 写回时必须减掉，否则每选一次日期就往后漂 +8 小时。
                        onChange(updated.timeInMillis - CoreDate.TZ_OFFSET)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.weight(1f),
            trailingIcon = {
                Icon(
                    painter = painterResource(AppIcons.calendar),
                    contentDescription = null,
                    tint = Tokens.Brand,
                    modifier = Modifier.size(m.iconSm)
                )
            }
        )

        if (!isAllDay) {
            PickerTrigger(
                value = CoreDate.formatTime(timestamp),
                onClick = {
                    val cal = utcCalendar(timestamp)
                    TimePickerDialog(
                        context,
                        { _, h, min ->
                            val updated = cal.clone() as Calendar
                            updated.set(Calendar.HOUR_OF_DAY, h)
                            updated.set(Calendar.MINUTE, min)
                            onChange(updated.timeInMillis - CoreDate.TZ_OFFSET)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    Icon(
                        painter = painterResource(AppIcons.chevronDownBrand),
                        contentDescription = null,
                        tint = Tokens.Brand,
                        modifier = Modifier.size(m.iconSm)
                    )
                }
            )
        }
    }
}

/** 与 CoreDate 一致：按 UTC+8 视图构造 Calendar。 */
private fun utcCalendar(ts: Long): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = ts + CoreDate.TZ_OFFSET
    }

private fun remindLabel(minutes: Int?): String = when (minutes) {
    null -> "不提醒"
    0 -> "开始时"
    10 -> "提前 10 分钟"
    60 -> "提前 1 小时"
    1440 -> "提前 1 天"
    else -> "提前 $minutes 分钟"
}

private fun parseRemind(label: String): Int? = when (label) {
    "不提醒" -> null
    "开始时" -> 0
    "提前 10 分钟" -> 10
    "提前 1 小时" -> 60
    "提前 1 天" -> 1440
    else -> null
}
