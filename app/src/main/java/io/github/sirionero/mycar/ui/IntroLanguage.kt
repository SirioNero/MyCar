package io.github.sirionero.mycar.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppLanguage
import io.github.sirionero.mycar.R

/**
 * The intro's language page (0.6): the phone's language, or one of myCar's nine, a tap away.
 * Android 13 and later apply it at once, and the page goes on in it; Android 12 starts myCar's
 * screen again, back on this page (see [AppLanguage.choose]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguagePicker(c: IntroColors, cq: (Float) -> Dp, cqSp: (Float) -> TextUnit, key: Any) {
    val context = LocalContext.current
    // Read again when the language changes: Android 13 applies it without starting myCar again.
    val config = LocalConfiguration.current
    var chosen by remember(config) { mutableStateOf(AppLanguage.chosen(context)) }
    val phone = AppLanguage.of(AppLanguage.phoneLocale())
    val phoneLabel = stringResource(R.string.language_phone, phone?.label ?: AppLanguage.ENGLISH.label)
    fun pick(language: AppLanguage?) {
        chosen = language
        (context as? Activity)?.let { AppLanguage.choose(it, language) }
    }
    FlowRow(
        Modifier.padding(top = cq(6f)).fillMaxWidth().up(entrance(key, 500, 700), cq(7f)),
        horizontalArrangement = Arrangement.spacedBy(cq(2.5f)),
        verticalArrangement = Arrangement.spacedBy(cq(2.5f)),
    ) {
        LanguageChip(c, cq, cqSp, phoneLabel, chosen == null) { pick(null) }
        AppLanguage.entries.forEach { language ->
            LanguageChip(c, cq, cqSp, language.label, chosen == language) { pick(language) }
        }
    }
}

@Composable
private fun LanguageChip(
    c: IntroColors, cq: (Float) -> Dp, cqSp: (Float) -> TextUnit,
    label: String, selected: Boolean, onClick: () -> Unit,
) {
    Text(
        if (selected) "✓ $label" else label,
        color = if (selected) c.onPrimary else c.onSurface,
        style = TextStyle(fontSize = cqSp(4.2f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(c.primary) else Modifier.border(BorderStroke(1.5.dp, c.outline), RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = cq(4.5f), vertical = cq(2.6f)),
    )
}
