package io.github.juantoanxoup.kg.web.components.chat

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatStateTest {
    @Test
    fun the_page_first_shows_the_card_on_a_desktop_and_the_button_on_a_phone() {
        assertEquals(PanelView.CARD, PanelViews.initial(isMobile = false))
        assertEquals(PanelView.BUTTON, PanelViews.initial(isMobile = true))
    }

    @Test
    fun minimize_goes_to_the_card_before_the_first_message_and_to_the_button_after() {
        assertEquals(PanelView.CARD, PanelViews.minimized(PanelView.SIDEBAR, hasMessages = false, isMobile = false))
        assertEquals(PanelView.CARD, PanelViews.minimized(PanelView.FULLSCREEN, hasMessages = false, isMobile = false))
        assertEquals(PanelView.BUTTON, PanelViews.minimized(PanelView.SIDEBAR, hasMessages = true, isMobile = false))
        assertEquals(PanelView.BUTTON, PanelViews.minimized(PanelView.CARD, hasMessages = false, isMobile = false))
        assertEquals(PanelView.BUTTON, PanelViews.minimized(PanelView.BUTTON, hasMessages = false, isMobile = false))
        // A phone has no card.
        assertEquals(PanelView.BUTTON, PanelViews.minimized(PanelView.FULLSCREEN, hasMessages = false, isMobile = true))
    }

    @Test
    fun opening_lands_on_the_sidebar_or_the_remembered_fullscreen_and_always_fullscreen_on_a_phone() {
        assertEquals(PanelView.SIDEBAR, PanelViews.opened(null, isMobile = false))
        assertEquals(PanelView.SIDEBAR, PanelViews.opened(PanelView.CARD, isMobile = false))
        assertEquals(PanelView.FULLSCREEN, PanelViews.opened(PanelView.FULLSCREEN, isMobile = false))
        assertEquals(PanelView.FULLSCREEN, PanelViews.opened(null, isMobile = true))
        assertEquals(PanelView.SIDEBAR, PanelViews.toggledExpand(PanelView.FULLSCREEN))
        assertEquals(PanelView.FULLSCREEN, PanelViews.toggledExpand(PanelView.SIDEBAR))
        assertTrue(PanelViews.isOpen(PanelView.SIDEBAR) && PanelViews.isOpen(PanelView.FULLSCREEN))
        assertTrue(!PanelViews.isOpen(PanelView.CARD) && !PanelViews.isOpen(PanelView.BUTTON))
    }

    @Test
    fun the_remembered_view_is_parsed_and_unknown_values_are_ignored() {
        assertEquals(PanelView.FULLSCREEN, PanelViews.parse("FULLSCREEN"))
        assertNull(PanelViews.parse("sidebar"))
        assertNull(PanelViews.parse(null))
    }

    @Test
    fun the_graph_comes_from_the_workspace_route_only() {
        assertEquals("20260924_230853_17f5e9cd", graphIdFromPath("/workspace/20260924_230853_17f5e9cd"))
        assertEquals("abc", graphIdFromPath("/workspace/abc?tab=chat"))
        assertNull(graphIdFromPath("/workspace"))
        assertNull(graphIdFromPath("/"))
    }

    @Test
    fun a_graph_choice_carries_its_day_when_the_date_is_valid() {
        assertEquals("sample.txt · Sep 24", graphLabel("sample.txt", "2026-09-24T23:08:53"))
        assertEquals("sample.txt", graphLabel("sample.txt", "not a date"))
    }

    @Test
    fun the_transcript_lists_every_entry_with_its_facts() {
        val text =
            transcript(
                "Graph Assistant",
                "sample.txt",
                listOf(
                    ChatEntry.Status("1", "Graph Assistant joined", "7:39 PM"),
                    ChatEntry.User("2", "Who discovered radium?", "7:40 PM"),
                    ChatEntry.Agent("3", "Marie and Pierre Curie.", "7:40 PM", listOf("Marie Curie discovered radium")),
                ),
            )
        assertTrue(text.startsWith("Graph Assistant chat transcript\nGraph: sample.txt"))
        assertTrue(text.contains("— Graph Assistant joined (7:39 PM)"))
        assertTrue(text.contains("You (7:40 PM):\nWho discovered radium?"))
        assertTrue(
            text.contains("Graph Assistant (7:40 PM):\nMarie and Pierre Curie.\n    · Marie Curie discovered radium"),
        )
        assertEquals("chat-transcript-g1-2026-09-25.txt", transcriptFileName("g1", Date("2026-09-25T12:00:00Z")))
        assertEquals("chat-transcript-no-graph-2026-09-25.txt", transcriptFileName(null, Date("2026-09-25T12:00:00Z")))
    }
}
