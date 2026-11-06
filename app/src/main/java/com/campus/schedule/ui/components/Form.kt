package com.campus.schedule.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalPalette
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens

/**
 * 表单组件 —— 对齐小程序 `styles/base.wxss` 的 `.field` / `.input` / `.picker-trigger` / `.switch-row`。
 *
 * 全部自绘而非使用 Material 的 TextField / Switch：
 * Material 组件自带较多内建动画与重组点，在长表单里滚动时容易掉帧；
 * 自绘版本只保留必要的 focus / pressed 反馈，动效完全可控。
 */

/** 字段标题（`.field__label`）。 */
@Composable
fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    required: Boolean = false
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(8))
    ) {
        Text(
            text = text,
            style = m.meta.copy(
                color = Tokens.Ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        )
        if (required) {
            // 「必填」用一条 2rpx 描边的细标注代替变色小字，
            // 和卡片的线条语言一致；总高 = 行高 30 + 上下各 4 = 38rpx，
            // 与主标签（meta 行高 40）在同一行里 CenterVertically 才不会一个高一个低。
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(m.rpx(8)))
                    .border(m.rpx(2), p.brand.copy(alpha = 0.28f), RoundedCornerShape(m.rpx(8)))
                    .padding(horizontal = m.rpx(14), vertical = m.rpx(8))
            ) {
                Text(
                    text = "必填",
                    style = m.micro.copy(
                        fontSize = m.sp(24),
                        lineHeight = m.sp(30),
                        color = p.brandDeep,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 输入框（`.input` / `.textarea`）。
 *
 * 聚焦时边框变品牌蓝（`.input--focus`）。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(m.radiusMd)

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> Tokens.Danger
            focused -> p.brand
            else -> Tokens.LineStrong
        },
        animationSpec = Motion.color(LocalReducedMotion.current),
        label = "inputBorder"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (minLines > 1) m.rpx(240) else m.rpx(96))
            .clip(shape)
            .background(Tokens.PaperRaised)
            .border(m.rpx(2), borderColor, shape)
            .padding(horizontal = m.rpx(24), vertical = m.rpx(16)),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = m.bodyLg.copy(color = Tokens.InkMuted))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (minLines > 1) m.rpx(180) else m.rpx(46)),
            textStyle = m.bodyLg.copy(color = Tokens.Ink),
            cursorBrush = SolidColor(p.brand),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            maxLines = if (minLines > 1) minLines else 1
        )
    }
}

/** 选择器触发器（`.picker-trigger`）：左值右箭头。 */
@Composable
fun PickerTrigger(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusMd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = m.rpx(96))
            .clip(shape)
            .background(Tokens.PaperRaised)
            .border(m.rpx(2), Tokens.LineStrong, shape)
            .pressHighlight(interaction, shape)
            .rippleClick(interaction, onClick = onClick)
            .padding(horizontal = m.rpx(24), vertical = m.rpx(14)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(16))
    ) {
        Text(
            text = value,
            // 行高 46 + 上下各 14 = 74，撑到 minHeight 96 之前先留出居中余量，
            // 文字不会贴底
            style = m.bodyLg.copy(lineHeight = m.sp(44)),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailingIcon?.invoke()
    }
}

/**
 * 单选标签（chip）。
 *
 * 选中态用品牌蓝底 + 反白字，未选中是白底描边。
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusPill)

    val bg by animateColorAsState(
        targetValue = when {
            selected -> accent ?: p.brand
            else -> Tokens.PaperRaised
        },
        animationSpec = Motion.color(reduced),
        label = "chipBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Tokens.PaperRaised else Tokens.InkSecondary,
        animationSpec = Motion.color(reduced),
        label = "chipFg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) Color.Transparent else Tokens.LineStrong,
        animationSpec = Motion.color(reduced),
        label = "chipBorder"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(m.rpx(2), border, shape)
            .pressHighlight(interaction, shape)
            .rippleClick(interaction, onClick = onClick)
            // 总高 = 行高 40 + 上下各 12 = 64rpx，与 ColorSwatch 的 64rpx 同一档，
            // 一行里并排时不会一个高一个矮
            .padding(horizontal = m.rpx(24), vertical = m.rpx(12)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = m.meta.copy(
                color = fg,
                fontWeight = if (selected) {
                    androidx.compose.ui.text.font.FontWeight.SemiBold
                } else {
                    androidx.compose.ui.text.font.FontWeight.Normal
                }
            ),
            maxLines = 1
        )
    }
}

/** 色板选择（`.color-swatch`）。 */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current
    val interaction = rememberPressInteraction()
    val scale = animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = Motion.press(reduced),
        label = "swatchScale"
    )

    // 选中态不再改描边宽度（6rpx ↔ 2rpx 会让色块尺寸跳变、整排错位），
    // 改为：描边恒定 2rpx + 外圈多一层 6rpx 的纸色间隙 + 放大 1.08。
    // 尺寸与位移全程走 graphicsLayer，不触发布局。
    Box(
        modifier = modifier
            .size(m.rpx(64))
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(m.radiusSm))
            .background(if (selected) Tokens.PaperRaised else Color.Transparent)
            .padding(if (selected) m.rpx(6) else 0.dp)
            .clip(RoundedCornerShape(if (selected) m.rpx(10) else m.radiusSm))
            .background(color)
            .border(
                width = m.rpx(2),
                color = if (selected) Tokens.Ink else Tokens.Line,
                shape = RoundedCornerShape(m.radiusSm)
            )
            .pressHighlight(interaction, RoundedCornerShape(m.radiusSm))
            .rippleClick(interaction, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                painter = painterResource(com.campus.schedule.ui.icons.AppIcons.check),
                contentDescription = null,
                tint = Tokens.PaperRaised,
                modifier = Modifier.size(m.rpx(28))
            )
        }
    }
}

/**
 * 开关行（`.switch-row`）。
 *
 * 自绘开关：滑块位移与轨道颜色都在 draw / graphicsLayer 阶段完成，
 * 动画期间不会触发重组。
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val interaction = rememberPressInteraction()

    val trackColor by animateColorAsState(
        targetValue = if (checked) p.brand else Tokens.PaperSunken,
        animationSpec = Motion.color(reduced),
        label = "switchTrack"
    )
    val knobOffset = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = Motion.tab(reduced),
        label = "switchKnob"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = m.rpx(112))
            .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
            .rippleClick(interaction) { onChange(!checked) }
            .padding(horizontal = m.rpx(4), vertical = m.rpx(16)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(16))
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = m.bodyLg,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(text = subtitle, style = m.caption)
            }
        }
        Box(
            modifier = Modifier
                .width(m.rpx(88))
                .height(m.rpx(48))
                .clip(CircleShape)
                .background(trackColor)
                .drawBehind {
                    // 轨道内侧描边
                    drawCircle(
                        color = Tokens.Line,
                        radius = size.height / 2f,
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = m.rpx(4))
                    .align(Alignment.CenterStart)
                    .size(m.rpx(40))
                    .graphicsLayer {
                        // 轨道 88 - 左右各 4 内边距 - 滑块 40 = 40rpx 的可移动距离
                        translationX = knobOffset.value * (m.rpx(40).toPx())
                    }
                    .clip(CircleShape)
                    .background(Tokens.PaperRaised)
            )
        }
    }
}
