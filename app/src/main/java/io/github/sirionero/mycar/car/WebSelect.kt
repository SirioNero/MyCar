package io.github.sirionero.mycar.car

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsIconButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A line of a page's dropdown: a group's heading, or an option. */
internal sealed interface SelectRow {
    data class Group(val label: String) : SelectRow
    data class Option(val label: String, val index: Int, val selected: Boolean, val disabled: Boolean) : SelectRow
}

/** A page's dropdown, open on the car's screen; the answer goes back to the frame that asked. */
internal class SelectAsk(
    val web: WebView,
    val title: String,
    val multiple: Boolean,
    val rows: List<SelectRow>,
    private val id: Int,
    private val reply: JavaScriptReplyProxy,
) {
    fun answer(picked: List<Int>) {
        runCatching { reply.postMessage(JSONObject().put("id", id).put("pick", JSONArray(picked)).toString()) }
    }
}

/**
 * Dropdowns (<select>) of the pages in the car: the browser, Cinema's sites, Plancia's page.
 * Android's own list is a window of its own, which the car's screen can't show or touch (see
 * carWebContext), so a tap on a dropdown never opened anything. Here a script in every page and
 * frame takes that tap (Blink opens the list on the tap's mousedown), myCar shows the options as a
 * list on the car's screen, and the choice goes back to the page as if made in its own list:
 * selected, then "input" and "change". Where the web engine can't run it (WebView without these
 * features), dropdowns stay as before.
 */
internal object WebSelect {

    private val _asked = MutableStateFlow<SelectAsk?>(null)
    val asked: StateFlow<SelectAsk?> = _asked.asStateFlow()

    /** On a new web view, before its first page. */
    fun install(web: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            MyCarLog.i("web: no dropdowns in the car (this WebView can't run the script)")
            return
        }
        runCatching {
            WebViewCompat.addWebMessageListener(web, "MyCarSelect", setOf("*")) { view, message, _, _, reply ->
                // Any page could post here: only right after the driver touched the screen or the
                // knob, and as data, capped.
                if (!CarInput.touchedWithin(ASK_AFTER_TOUCH_MS)) return@addWebMessageListener
                parse(view, message.data, reply)?.let { _asked.value = it }
            }
            WebViewCompat.addDocumentStartJavaScript(web, SELECT_JS, setOf("*"))
        }.onFailure { MyCarLog.w("web: dropdowns not set up: ${it.message}") }
    }

    fun answer(picked: List<Int>) {
        _asked.value?.answer(picked)
        _asked.value = null
    }

    /** Closed without an answer: Back, a tap beside it, another screen, [web] gone (any if null). */
    fun cancel(web: WebView? = null) {
        if (web == null || _asked.value?.web === web) _asked.value = null
    }

    private fun parse(view: WebView, raw: String?, reply: JavaScriptReplyProxy): SelectAsk? = runCatching {
        if (raw == null || raw.length > MAX_MESSAGE) return null
        val o = JSONObject(raw)
        val a = o.getJSONArray("rows")
        val rows = (0 until minOf(a.length(), MAX_ROWS)).map { a.getJSONObject(it) }.map {
            if (it.has("g")) {
                SelectRow.Group(it.optString("g").take(120))
            } else {
                SelectRow.Option(it.optString("l").take(160), it.getInt("i"), it.optBoolean("s"), it.optBoolean("d"))
            }
        }
        if (rows.none { it is SelectRow.Option }) return null
        SelectAsk(view, o.optString("title").take(80), o.optBoolean("multiple"), rows, o.getInt("id"), reply)
    }.getOrNull()

    private const val ASK_AFTER_TOUCH_MS = 2_000L
    private const val MAX_MESSAGE = 300_000
    private const val MAX_ROWS = 1_500
}

/**
 * The open dropdown over the whole car screen - a quarter of a page has room for two lines -
 * as the sheet of a page part's choices: one option taps and closes, several are ticked and
 * then Done. Under a call's question, over everything else.
 */
@Composable
internal fun WebSelectSheet() {
    val ask by WebSelect.asked.collectAsState()
    val a = ask ?: return
    val scheme = MaterialTheme.colorScheme
    var picked by remember(a) {
        mutableStateOf(a.rows.filterIsInstance<SelectRow.Option>().filter { it.selected }.map { it.index }.toSet())
    }
    val first = a.rows.indexOfFirst { it is SelectRow.Option && it.selected }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = first)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { WebSelect.cancel() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(MyCarSpace.L)
                .clip(RoundedCornerShape(MyCarRadius.Sheet))
                .background(scheme.surfaceContainerHigh)
                // Taps on the sheet itself stay on it.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(MyCarSpace.L),
            verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.title.ifBlank { "Choose" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (a.multiple) {
                    GsButton(onClick = { WebSelect.answer(picked.sorted()) }, modifier = Modifier.heightIn(min = MyCarSize.TouchTarget)) {
                        Text("Done", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(MyCarSpace.S))
                }
                GsIconButton(Icons.Filled.Close, "Close", { WebSelect.cancel() }, size = MyCarSize.TouchTarget)
            }
            LazyColumn(Modifier.weight(1f), state = list) {
                itemsIndexed(a.rows) { _, row ->
                    when (row) {
                        is SelectRow.Group -> Text(
                            row.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = MyCarSpace.L, top = MyCarSpace.M, bottom = MyCarSpace.XS),
                        )
                        is SelectRow.Option -> OptionRow(row, checked = row.index in picked) {
                            if (a.multiple) {
                                picked = if (row.index in picked) picked - row.index else picked + row.index
                            } else {
                                WebSelect.answer(listOf(row.index))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(option: SelectRow.Option, checked: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MyCarSize.TouchTarget)
            .clip(RoundedCornerShape(MyCarRadius.Button))
            .background(if (checked) scheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = !option.disabled, onClick = onClick)
            .padding(horizontal = MyCarSpace.L),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            option.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (option.disabled) scheme.onSurface.copy(alpha = 0.38f) else scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (checked) Icon(Icons.Filled.Check, contentDescription = "Chosen", Modifier.size(28.dp), tint = scheme.primary)
    }
}

/**
 * In every page and frame, from its start: a tap (or the knob's OK) on a dropdown opens myCar's
 * list instead of Android's, which can't show in the car. Only dropdowns that open a list: one
 * drawn in the page (several lines shown, the page's own "base-select" style) is left to the page.
 */
private const val SELECT_JS = """
(function () {
  if (window.__mycarSelect || !window.MyCarSelect) return;
  window.__mycarSelect = true;
  var car = window.MyCarSelect, open = new Map(), next = 1, lastAsk = 0;
  function selectOf(e) {
    var t = e.composedPath ? e.composedPath()[0] : e.target;
    return t && t.tagName === 'SELECT' ? t : null;
  }
  function ours(s) {
    if (s.matches(':disabled')) return false;
    try { if (getComputedStyle(s).appearance === 'base-select') return false; } catch (x) {}
    return true;
  }
  function clip(v, n) { return String(v || '').replace(/\s+/g, ' ').trim().slice(0, n); }
  function rows(s) {
    var out = [], group = null;
    for (var i = 0; i < s.options.length && out.length < 1500; i++) {
      var o = s.options[i], g = o.closest('optgroup');
      if (g !== group) { group = g; if (g) out.push({ g: clip(g.label, 120) }); }
      if (o.hidden) continue;
      out.push({ l: clip(o.label, 160), i: i, s: o.selected, d: o.disabled || !!(g && g.disabled) });
    }
    return out;
  }
  function ask(s) {
    var now = Date.now();
    if (now - lastAsk < 500) return;
    lastAsk = now;
    var id = next++, l = s.labels && s.labels[0];
    open.clear(); open.set(id, s);
    try { s.focus({ preventScroll: true }); } catch (x) {}
    car.postMessage(JSON.stringify({ id: id, multiple: s.multiple, rows: rows(s),
      title: clip(s.getAttribute('aria-label') || (l && l.innerText) || s.title, 80) }));
  }
  car.onmessage = function (ev) {
    var m; try { m = JSON.parse(ev.data); } catch (x) { return; }
    var s = open.get(m.id); open.delete(m.id);
    if (!s || !s.isConnected || !Array.isArray(m.pick)) return;
    var state = function () { return Array.prototype.map.call(s.options, function (o) { return o.selected; }).join(); };
    var was = state();
    if (s.multiple) {
      for (var i = 0; i < s.options.length; i++) if (!s.options[i].disabled) s.options[i].selected = m.pick.indexOf(i) >= 0;
    } else if (s.options[m.pick[0]] && !s.options[m.pick[0]].disabled) {
      s.selectedIndex = m.pick[0];
    }
    if (state() === was) return;
    s.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
    s.dispatchEvent(new Event('change', { bubbles: true }));
  };
  function take(e) { var s = selectOf(e); if (s && e.isTrusted && ours(s)) { e.preventDefault(); ask(s); } }
  window.addEventListener('mousedown', function (e) { if (e.button === 0) take(e); }, { capture: true, passive: false });
  window.addEventListener('click', take, true);
  window.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' || e.key === ' ' || e.key === 'F4' || (e.altKey && /^Arrow(Up|Down)$/.test(e.key))) take(e);
  }, true);
  var show = window.HTMLSelectElement && HTMLSelectElement.prototype.showPicker;
  if (show) HTMLSelectElement.prototype.showPicker = function () {
    if (ours(this) && navigator.userActivation && navigator.userActivation.isActive) ask(this);
    else return show.call(this);
  };
})();
"""
