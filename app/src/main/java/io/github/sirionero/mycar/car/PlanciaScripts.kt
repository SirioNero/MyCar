package io.github.sirionero.mycar.car

/** JavaScript Plancia adds to Home Assistant's frontend. From Plancia, the Home Assistant app for the car. */
internal object PlanciaScripts {

    /**
     * Runs before the page's own scripts, or as soon as it has loaded where the WebView can't.
     * - Home Assistant's sidebar stays hidden: dashboards are picked from Plancia's menu.
     * - Home Assistant's ☰ button opens Plancia's menu instead of the sidebar.
     * - The page lays out at the width myCar gives it (`__planciaViewport`): wider than the part of
     *   the screen it has, then fitted into it, is the dashboard drawn smaller - Plancia's size.
     */
    const val SETUP = """
(function() {
  if (window.__plancia) return;
  window.__plancia = true;
  try { localStorage.setItem('dockedSidebar', JSON.stringify('always_hidden')); } catch (e) {}

  window.addEventListener('hass-toggle-menu', function(ev) {
    ev.stopImmediatePropagation();
    if (window.plancia) window.plancia.openMenu();
  }, true);

  function applyViewport() {
    var width = window.__planciaWidth;
    if (!width || !document.head) return;
    var content = 'width=' + Math.max(1, Math.round(width)) + ', user-scalable=no, viewport-fit=cover';
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'viewport';
      document.head.appendChild(meta);
    }
    if (meta.content !== content) meta.content = content;
  }

  window.__planciaViewport = function(width) {
    window.__planciaWidth = width;
    applyViewport();
  };

  // The page's own viewport line arrives as it's read: it's replaced once it's there.
  var watch = new MutationObserver(function() {
    if (document.querySelector('meta[name="viewport"]')) {
      watch.disconnect();
      applyViewport();
    }
  });
  watch.observe(document, { childList: true, subtree: true });
  document.addEventListener('DOMContentLoaded', applyViewport);
})();
"""

    /**
     * If the page started before [SETUP] could hide the sidebar, it closes it from inside, with the
     * same event Home Assistant uses.
     */
    const val HIDE_SIDEBAR = """
(function() {
  var tries = 0;
  var timer = setInterval(function() {
    if (++tries > 40) { clearInterval(timer); return; }
    var app = document.querySelector('home-assistant');
    if (!app || !app.hass) return;
    clearInterval(timer);
    if (app.hass.dockedSidebar === 'always_hidden') return;
    var ev = new Event('hass-dock-sidebar', { bubbles: true, composed: true });
    ev.detail = { dock: 'always_hidden' };
    app.dispatchEvent(ev);
  }, 500);
})();
"""
}
