package com.campus.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campus.schedule.core.CoreDate
import com.campus.schedule.data.ScheduleItem
import com.campus.schedule.ui.components.EntryDivider
import com.campus.schedule.ui.components.EntryList
import com.campus.schedule.ui.components.EntryRow
import com.campus.schedule.ui.components.IdentityCard
import com.campus.schedule.ui.components.Masthead
import com.campus.schedule.ui.components.SectionHeader
import com.campus.schedule.ui.components.StatsRow
import com.campus.schedule.ui.components.TagTone
import com.campus.schedule.ui.components.rememberEnterTracker
import com.campus.schedule.ui.components.staggerEnter
import com.campus.schedule.ui.icons.AppIcons
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.Tokens

/**
 * 「我的」页。
 *
 * 结构与小程序 `pages/mine/index` 一致：报头 → 身份卡 → 统计条 → 分区入口列表。
 * 云端专属的「群组 / 权限 / 发布 / 通知中心」全部去掉，替换为离线版的
 * 「我的日程」与「数据管理」。
 */
@Composable
fun MineScreen(
    items: List<ScheduleItem>,
    onFilterJump: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearSamples: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val tracker = rememberEnterTracker()
    val now = rememberNowMillisMine()

    val total = items.size
    val todayCount = items.count { CoreDate.isToday(it.startTime, now) }
    val starredCount = items.count { it.starred }
    val sampleCount = items.count { it.isSample }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.TopGradient),
        contentPadding = PaddingValues(
            start = m.pagePadding,
            end = m.pagePadding,
            // 底栏高度已由 Scaffold 的 contentPadding 让出，这里只补一段呼吸空间。
            bottom = m.rpx(48)
        )
    ) {
        item(key = "masthead") {
            Masthead(
                kicker = "CAMPUS CALENDAR / 第 ${CoreDate.isoWeekNumber(now)} 周",
                title = "我的",
                meta = "${CoreDate.formatDate(now)} · 本地离线版"
            )
        }

        item(key = "identity") {
            IdentityCard(
                name = "我的日程",
                initial = "我",
                subline = "全部数据只存在这台设备上",
                hint = "共 $total 条日程，其中 $sampleCount 条是示例",
                tagText = "离线",
                tagTone = TagTone.NEUTRAL,
                modifier = Modifier.staggerEnter(tracker, "identity", 0)
            )
        }

        item(key = "stats") {
            StatsRow(
                stats = listOf(
                    "$total" to "全部日程",
                    "$todayCount" to "今天",
                    "$starredCount" to "特别关注"
                ),
                modifier = Modifier
                    .staggerEnter(tracker, "stats", 1)
                    .padding(top = m.rpx(16))
            )
        }

        // ---------- 我的日程 ----------
        item(key = "sec1") {
            SectionHeader(
                eyebrow = "01 / 我的日程",
                title = "我的日程",
                modifier = Modifier
                    .staggerEnter(tracker, "sec1", 2)
                    .padding(top = m.rpx(48))
            )
        }

        item(key = "list1") {
            EntryList(modifier = Modifier.staggerEnter(tracker, "list1", 3)) {
                EntryRow(
                    label = "全部日程",
                    iconRes = AppIcons.calendar,
                    count = "$total",
                    onClick = { onFilterJump("all") }
                )
                EntryDivider()
                EntryRow(
                    label = "今天的日程",
                    iconRes = AppIcons.schedule,
                    count = "$todayCount",
                    onClick = { onFilterJump("today") }
                )
                EntryDivider()
                EntryRow(
                    label = "特别关注",
                    iconRes = AppIcons.star,
                    iconTint = Tokens.GoldText,
                    iconBackground = Tokens.GoldTint,
                    count = "$starredCount",
                    onClick = { onFilterJump("starred") }
                )
            }
        }

        // ---------- 数据管理 ----------
        item(key = "sec2") {
            SectionHeader(
                eyebrow = "02 / 数据管理",
                title = "数据管理",
                modifier = Modifier
                    .staggerEnter(tracker, "sec2", 4)
                    .padding(top = m.rpx(48))
            )
        }

        item(key = "list2") {
            EntryList(modifier = Modifier.staggerEnter(tracker, "list2", 5)) {
                EntryRow(
                    label = "导出备份",
                    iconRes = AppIcons.export,
                    onClick = onExport
                )
                EntryDivider()
                EntryRow(
                    label = "导入备份",
                    iconRes = AppIcons.upload,
                    onClick = onImport
                )
                EntryDivider()
                EntryRow(
                    label = "清空示例数据",
                    iconRes = AppIcons.refresh,
                    onClick = onClearSamples
                )
                EntryDivider()
                EntryRow(
                    label = "清空全部数据",
                    iconRes = AppIcons.trashBrand,
                    iconTint = Tokens.Danger,
                    iconBackground = Tokens.DangerTint,
                    onClick = onClearAll
                )
            }
        }

        // ---------- 关于 ----------
        item(key = "sec3") {
            SectionHeader(
                eyebrow = "03 / 关于",
                title = "关于",
                modifier = Modifier
                    .staggerEnter(tracker, "sec3", 6)
                    .padding(top = m.rpx(48))
            )
        }

        item(key = "about") {
            Column(
                modifier = Modifier
                    .staggerEnter(tracker, "about", 7)
                    .padding(top = m.rpx(4))
            ) {
                // 页脚标记：两道 2rpx 短线 + 居中细字，代替原来的纯文本堆叠
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = m.rpx(12))
                        .width(m.rpx(72))
                        .height(m.rpx(2))
                        .background(Tokens.LineStrong)
                )
                Text(
                    text = "校园日程 · 个人离线版",
                    style = m.caption.copy(color = Tokens.InkMuted),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(m.rpx(8)))
                Text(
                    text = "所有日程保存在本机数据库，不联网、不上传，也不需要登录。" +
                        "卸载应用会一并删除数据，建议定期导出备份。",
                    style = com.campus.schedule.ui.theme.LocalMetrics.current.meta
                )
            }
        }
    }
}

/** 30 秒刷新一次的「现在」。 */
@Composable
private fun rememberNowMillisMine(): Long =
    com.campus.schedule.ui.components.rememberNowMillis()
