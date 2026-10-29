package io.github.sirionero.mycar.car

import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The car's Back key goes to an app only while its screen shows a Back arrow (see AppBackKey). */
class TemplateBackTest {

    private fun list(header: Header?): ListTemplate =
        ListTemplate.Builder()
            .setSingleList(ItemList.Builder().build())
            .apply { if (header != null) setHeader(header) }
            .build()

    @Test
    fun `a list with a back arrow in its header has one`() {
        val template = list(Header.Builder().setStartHeaderAction(Action.BACK).setTitle("Results").build())
        assertEquals(Action.TYPE_BACK, template.backAction()?.type)
    }

    @Test
    fun `an app icon at the start of the header is not a back arrow`() {
        val template = list(Header.Builder().setStartHeaderAction(Action.APP_ICON).setTitle("Vela").build())
        assertNull(template.backAction())
    }

    @Test
    fun `no header, no arrow`() {
        assertNull(list(null).backAction())
        assertNull((null as Template?).backAction())
    }
}
