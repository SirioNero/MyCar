package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomePagesTest {
    private val plancia = "it.plancia.ha/it.plancia.ha.car.PlanciaCarAppService"

    @Test
    fun readyMadePagesFlankTheMap() {
        val pages = readyMade(plancia)
        assertEquals(listOf(PageLayout.HALVES, PageLayout.MAP, PageLayout.HALVES), pages.map { it.layout })
        assertEquals(listOf(PageContent.WEATHER, PageContent.AGENDA), pages[0].slots)
        assertEquals(listOf(plancia, PageContent.BROWSER), pages[2].slots)
    }

    @Test
    fun withoutPlanciaThereIsNoPageForIt() {
        assertEquals(listOf(PageLayout.HALVES, PageLayout.MAP), readyMade(null).map { it.layout })
    }

    @Test
    fun repairKeepsOneMapPageAndFitsTheSlots() {
        val pages = repaired(
            listOf(
                HomePage(3, PageLayout.GRID, listOf(PageContent.WEATHER)),
                HomePage(3, PageLayout.HALVES),
                HomePage(4, PageLayout.HALVES, listOf("a", "b", "c")),
            ),
        )
        assertEquals(listOf(5, 3, 4), pages.map { it.id })
        assertEquals(PageLayout.MAP, pages[0].layout)
        assertEquals(listOf(PageContent.WEATHER, null, null, null), pages[1].slots)
        assertEquals(listOf("a", "b"), pages[2].slots)

        val twoMaps = repaired(listOf(HomePage(0, PageLayout.MAP), HomePage(1, PageLayout.MAP)))
        assertEquals(listOf(0), twoMaps.map { it.id })
    }

    @Test
    fun aChoiceMovesWithinItsPage() {
        val pages = readyMade(plancia).withSlot(1, 1, PageContent.WEATHER)
        assertEquals(listOf(null, PageContent.WEATHER), pages[0].slots)
        assertEquals(listOf(plancia, PageContent.BROWSER), pages[2].slots)
    }

    @Test
    fun aNewLayoutKeepsWhatThePageShowed() {
        val grid = readyMade(plancia).withLayout(1, PageLayout.GRID)
        assertEquals(listOf(PageContent.WEATHER, PageContent.AGENDA, null, null), grid[0].slots)
        val halves = grid.withSlot(1, 0, null).withSlot(1, 3, PageContent.BROWSER).withLayout(1, PageLayout.HALVES)
        assertEquals(listOf(PageContent.AGENDA, PageContent.BROWSER), halves[0].slots)
    }

    @Test
    fun everyLayoutReadsBackFromItsId() {
        PageLayout.entries.forEach { assertSame(it, PageLayout.of(it.id)) }
        assertEquals(listOf(1, 3, 3, 3, 3), listOf(
            PageLayout.FULL, PageLayout.HALF_QUARTERS, PageLayout.QUARTERS_HALF,
            PageLayout.STRIP_QUARTERS, PageLayout.QUARTERS_STRIP,
        ).map { it.slots })
    }

    @Test
    fun theNewLayoutsKeepWhatFitsInOrder() {
        val grid = listOf(HomePage(1, PageLayout.GRID, listOf("a", null, "b", "c")))
        assertEquals(listOf("a"), grid.withLayout(1, PageLayout.FULL).single().slots)
        val mixed = grid.withLayout(1, PageLayout.HALF_QUARTERS)
        assertEquals(listOf("a", "b", "c"), mixed.single().slots)
        val strip = mixed.withLayout(1, PageLayout.QUARTERS_STRIP).single()
        assertEquals(PageLayout.QUARTERS_STRIP, strip.layout)
        assertEquals(listOf("a", "b", "c"), strip.slots)
        assertEquals(listOf("a", "b", "c", null), listOf(strip).withLayout(1, PageLayout.GRID).single().slots)
    }

    @Test
    fun repairFitsTheNewLayoutsToo() {
        val pages = repaired(listOf(HomePage(0, PageLayout.MAP), HomePage(1, PageLayout.STRIP_QUARTERS, listOf("a"))))
        assertEquals(listOf("a", null, null), pages[1].slots)
        assertEquals(listOf("x"), repaired(listOf(HomePage(0, PageLayout.MAP), HomePage(1, PageLayout.FULL, listOf("x", "y"))))[1].slots)
    }

    @Test
    fun theMapPageStaysPut() {
        val pages = readyMade(plancia)
        assertSame(pages[1], pages.withLayout(0, PageLayout.GRID)[1])
        assertEquals(pages, pages.without(0))
    }

    @Test
    fun newPagesGoRightAfterTheOneAskedFor() {
        val (pages, id) = readyMade(plancia).withNewPage(0)
        assertEquals(3, id)
        assertEquals(listOf(1, 0, 3, 2), pages.map { it.id })
        assertEquals(listOf(null, null), pages[2].slots)
    }

    @Test
    fun pagesMoveOnePlaceAndNoFurther() {
        val pages = readyMade(plancia)
        assertEquals(listOf(0, 1, 2), pages.moved(1, 1).map { it.id })
        assertEquals(listOf(1, 2, 0), pages.moved(2, -1).map { it.id })
        assertEquals(pages, pages.moved(1, -1))
        assertEquals(listOf(0, 2), pages.without(1).map { it.id })
    }
}
