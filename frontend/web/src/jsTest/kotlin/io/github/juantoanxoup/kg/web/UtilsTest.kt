package io.github.juantoanxoup.kg.web

import io.github.juantoanxoup.kg.web.lib.cn
import io.github.juantoanxoup.kg.web.lib.csvField
import io.github.juantoanxoup.kg.web.lib.toCsv
import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {
    @Test
    fun classNamesSkipBlanks() {
        assertEquals("a b", cn("a", null, "", "b"))
    }

    @Test
    fun csvFieldsAreQuotedWhenNeeded() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
    }

    @Test
    fun csvHasHeaderAndRows() {
        assertEquals("Subject,Predicate\nA,\"b,c\"", toCsv(listOf("Subject", "Predicate"), listOf(listOf("A", "b,c"))))
    }
}
