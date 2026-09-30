package uk.co.fireburn.lochssh.ui.terminal

import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import uk.co.fireburn.lochssh.R

class TerminalTypefaceAndroidTest {
    @Test
    fun bundledFontsKeepAsciiCharactersInEqualColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (resource in listOf(R.font.noto_sans_mono_regular, R.font.noto_sans_mono_bold,
            R.font.jetbrains_mono_regular, R.font.jetbrains_mono_bold)) {
            val face = ResourcesCompat.getFont(context, resource)
            assertNotNull(face)
            val paint = Paint().apply { typeface = face; textSize = 32f }
            val width = paint.measureText("M")
            for (code in 32..126) {
                assertEquals("Font $resource, character $code", width,
                    paint.measureText(code.toChar().toString()), 0.01f)
            }
            assertEquals(width * 4, paint.measureText("!=->"), 0.01f)
        }
    }
}
