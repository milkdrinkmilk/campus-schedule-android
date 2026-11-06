package com.campus.schedule.data

import com.campus.schedule.core.CoreDate

/**
 * JSON 备份 —— 对应小程序的 `exportLocalData` / `importLocalData`。
 *
 * 离线个人版不依赖云端，备份直接写文件到「下载」目录。
 */
object Backup {

    fun exportJson(items: List<ScheduleItem>): String {
        val entries = items.joinToString(",\n") { item ->
            """
            {
              "id": "${esc(item.id)}",
              "title": "${esc(item.title)}",
              "note": "${esc(item.note)}",
              "location": "${esc(item.location)}",
              "category": "${esc(item.category)}",
              "accent": "${esc(item.accent)}",
              "startTime": ${item.startTime},
              "endTime": ${item.endTime},
              "isAllDay": ${item.isAllDay},
              "remindBefore": ${item.remindBefore ?: "null"},
              "starred": ${item.starred},
              "createdAt": ${item.createdAt},
              "updatedAt": ${item.updatedAt}
            }
            """.trimIndent()
        }
        return """
        {
          "format": "campus-schedule-backup",
          "version": 1,
          "exportedAt": ${System.currentTimeMillis()},
          "count": ${items.size},
          "items": [
        $entries
          ]
        }
        """.trimIndent()
    }

    /** 解析导出的 JSON；解析失败返回空列表。 */
    fun importJson(json: String): List<ScheduleItem> {
        return try {
            val root = org.json.JSONObject(json)
            val arr = root.optJSONArray("items") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank {
                    "import_${System.currentTimeMillis()}_$i"
                }
                ScheduleItem(
                    id = id,
                    title = o.optString("title"),
                    note = o.optString("note"),
                    location = o.optString("location"),
                    category = o.optString("category").ifBlank { "其他" },
                    accent = o.optString("accent").ifBlank { "coral" },
                    startTime = o.optLong("startTime"),
                    endTime = o.optLong("endTime"),
                    isAllDay = o.optBoolean("isAllDay"),
                    remindBefore = if (o.isNull("remindBefore")) null else o.optInt("remindBefore"),
                    starred = o.optBoolean("starred"),
                    isSample = false,
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun esc(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    fun fileName(): String = "campus-schedule-${CoreDate.toDateKey(System.currentTimeMillis())}.json"
}
