package com.ounben.amaradio.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StationPlaceholderUtilsTest {

    @Test
    fun latinNamesKeepUpstreamBehaviour() {
        assertEquals("102", StationPlaceholderUtils.extractPlaceholderText("102.7 KIIS FM"))
        assertEquals("CN", StationPlaceholderUtils.extractPlaceholderText("Classic News"))
        assertEquals("RA", StationPlaceholderUtils.extractPlaceholderText("radio"))
        assertEquals("?", StationPlaceholderUtils.extractPlaceholderText("  "))
    }

    @Test
    fun cjkNamesUseTheDistinctivePart() {
        val cases = mapOf(
            "北京新闻广播 FM94.5" to "新闻",
            "北京交通广播 FM103.9" to "交通",
            "京津冀之声 FM100.6" to "京津冀",
            "北京城市广播 FM102.5（原107.3）" to "城市",
            "北京音乐广播（蜻蜓源）" to "音乐",
            "中国之声" to "中国",
            "经济之声" to "经济",
            "中国交通广播" to "交通",
            "中国乡村之声" to "乡村",
            "CRI 环球资讯广播" to "环球",
            "CNR-1 中国之声" to "中国",
            "经典音乐广播" to "经典"
        )
        cases.forEach { (name, expected) ->
            assertEquals(expected, StationPlaceholderUtils.extractPlaceholderText(name), name)
        }
    }
}
