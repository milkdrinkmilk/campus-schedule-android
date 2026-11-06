package com.campus.schedule.ui

import com.campus.schedule.ui.components.veilOpacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI 回归守卫。
 *
 * 这一批测试守的是 v2.0.1 修掉的三个**真机才暴露**的坑，
 * 它们都不会让编译失败，只会在用户手机上炸：
 *
 * 1. **单色 Brush 崩溃**：`Brush.linearGradient(listOf(单色))` 在绘制期抛
 *    `IllegalArgumentException: colors must have length of at least 2`。
 *    概况页的周分布柱状图就踩了这个坑 —— 一周里只要有 6 天的 count > 0
 *    就必崩。根因是它只在**绘制期**炸，编译期和单测都测不出来。
 * 2. **只设 width 的图标**：`Modifier.width()` 不改高度，图标宽高比失配被压扁，
 *    看上去就是「文本与图标错位」。
 * 3. **负 alpha**：入场曲线带回弹（cubic-bezier(0.34,1.56,0.64,1)），
 *    进度会冲到 1.0978；不钳制就会把负 alpha 交给 `Paint.setAlpha`，
 *    渲染未定义（表现为偶发闪屏 / 画面异常）。
 *
 * 1、2 靠**静态扫描源码**守住（因为没有 Robolectric，无法在 JVM 上渲染 Compose）；
 * 3 是纯函数，直接断言。
 */
class UiRegressionGuardTest {

    // -----------------------------------------------------------------------
    // 3. 遮罩透明度必须钳制在 [0, 1]
    // -----------------------------------------------------------------------

    @Test
    fun `veilOpacity 正常区间单调递减`() {
        assertEquals(1f, veilOpacity(0f), 0.0001f)
        assertEquals(0.55f, veilOpacity(0.45f), 0.0001f)
        assertEquals(0f, veilOpacity(1f), 0.0001f)
        // 单调：越亮越透明
        var prev = Float.MAX_VALUE
        var i = 0
        while (i <= 100) {
            val p = i / 100f
            val v = veilOpacity(p)
            assertTrue("veilOpacity 在 $p 处没有单调递减: $v > $prev", v <= prev + 0.0001f)
            prev = v
            i++
        }
    }

    @Test
    fun `veilOpacity 回弹过冲时不得返回负值`() {
        // 回弹曲线在 t≈0.5821 处输出 1.0978 —— 这正是上一版闪屏的元凶
        assertEquals(0f, veilOpacity(1.0978f), 0.0001f)
        assertEquals(0f, veilOpacity(2f), 0.0001f)
        assertEquals(1f, veilOpacity(-1f), 0.0001f)
        for (p in listOf(-0.5f, -0.01f, 1.0001f, 1.05f, 1.0978f, 1.2f, 5f)) {
            val v = veilOpacity(p)
            assertTrue("veilOpacity($p) = $v 越界", v >= 0f && v <= 1f)
        }
    }

    // -----------------------------------------------------------------------
    // 1 + 2. 静态扫描
    // -----------------------------------------------------------------------

    @Test
    fun `不允许出现单色渐变`() {
        val offenders = mutableListOf<String>()
        for (file in kotlinSources()) {
            val text = file.readText()
            var idx = 0
            while (true) {
                val hit = text.indexOf("Brush.linearGradient(", idx)
                if (hit < 0) break
                val args = extractArgs(text, hit + "Brush.linearGradient".length)
                // 形如 linearGradient(listOf(X)) —— 单元素列表就是崩溃点
                val listStart = args.indexOf("listOf(")
                if (listStart >= 0) {
                    val listBody = extractArgs(args, listStart + "listOf".length)
                    val colorCount = countTopLevelItems(listBody)
                    if (colorCount < 2) {
                        offenders += "${file.name}: Brush.linearGradient(listOf(<$colorCount 个颜色>))"
                    }
                }
                idx = hit + 1
            }
        }
        assertTrue(
            "单色 Brush.linearGradient 会在绘制期抛异常：\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `图标不得只设宽度`() {
        val offenders = mutableListOf<String>()
        for (file in kotlinSources()) {
            val text = file.readText()
            var idx = 0
            while (true) {
                val hit = text.indexOf("Icon(", idx)
                if (hit < 0) break
                val after = hit + "Icon(".length
                // 跳过 IconButton / IconToggle 等非图标组件
                if (after >= text.length || !isIconCall(text, after)) {
                    idx = hit + 1
                    continue
                }
                val args = extractArgs(text, hit + "Icon".length)
                if (args.contains(".width(")) {
                    offenders += "${file.name}: Icon(...) 使用了 .width(...) —— 会把图标压扁"
                }
                idx = hit + 1
            }
        }
        assertTrue(
            "图标只设宽度会导致宽高比失配：\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /** `Icon(` 后面必须紧跟 painter 或 painterResource，才是真正的图标调用。 */
    private fun isIconCall(text: String, after: Int): Boolean {
        val end = minOf(after + 80, text.length)
        val head = text.substring(after, end)
        return head.contains("painter")
    }

    /**
     * 取出一对括号里的内容。
     *
     * @param parenIndex 左括号的下标（不是右括号之后的位置）
     * @return 括号内的文本（不含这对括号本身）
     */
    private fun extractArgs(text: String, parenIndex: Int): String {
        var depth = 0
        var i = parenIndex
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(parenIndex + 1, i)
                }
            }
            i++
        }
        return text.substring(parenIndex + 1)
    }

    /** 统计顶层逗号分隔的条目数（忽略括号内的逗号与字符串里的逗号）。 */
    private fun countTopLevelItems(body: String): Int {
        var depth = 0
        var count = 1
        var i = 0
        while (i < body.length) {
            when (body[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) count++
                '"' -> {
                    // 跳过字符串
                    i++
                    while (i < body.length && body[i] != '"') {
                        if (body[i] == '\\') i++
                        i++
                    }
                }
            }
            i++
        }
        return if (body.isBlank()) 0 else count
    }

    /** 定位到源码根目录（兼容不同的测试工作目录）。 */
    private fun kotlinSources(): List<File> {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/main/java/com/campus/schedule")
            if (candidate.isDirectory) {
                return candidate.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".kt") }
                    .toList()
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("找不到源码目录 src/main/java/com/campus/schedule")
    }
}
