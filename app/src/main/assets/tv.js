(function () {
  if (window.tv) return;

  // ---------- CSS: interfaz limpia y sin efectos costosos ----------
  var css = [
    'html.tv-video [class*="DivSideNavContainer"]{display:none!important}',
    'html.tv-video [class*="DivBannerContainer"]{display:none!important}',
    '*{scroll-behavior:auto!important;transition:none!important}',
    // Ahorro de GPU/CPU para el chip Amlogic S905X2.
    // Si algo se ve roto o no carga, quita primero "animation:none".
    '*{animation:none!important;backdrop-filter:none!important;' +
      'box-shadow:none!important;text-shadow:none!important}'
  ].join('\n');
  var st = document.createElement('style');
  st.textContent = css;
  (document.head || document.documentElement).appendChild(st);
  document.documentElement.classList.add('tv-video');

  // ---------- Utilidades ----------
  function currentVideo() {
    var best = null, bestVis = 0;
    document.querySelectorAll('video').forEach(function (v) {
      var r = v.getBoundingClientRect();
      var vis = Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
      if (vis > bestVis) { bestVis = vis; best = v; }
    });
    return best;
  }

  function key(name, code) {
    ['keydown', 'keyup'].forEach(function (t) {
      document.dispatchEvent(new KeyboardEvent(t, {
        key: name, code: name, keyCode: code, which: code, bubbles: true
      }));
    });
  }

  var toastEl;
  function toast(msg) {
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.style.cssText =
        'position:fixed;top:5%;left:50%;transform:translateX(-50%);' +
        'background:rgba(0,0,0,.75);color:#fff;font:600 28px sans-serif;' +
        'padding:10px 24px;border-radius:12px;z-index:2147483647;' +
        'pointer-events:none;transition:opacity .3s';
      document.body.appendChild(toastEl);
    }
    toastEl.textContent = msg;
    toastEl.style.opacity = 1;
    clearTimeout(toastEl._t);
    toastEl._t = setTimeout(function () { toastEl.style.opacity = 0; }, 1200);
  }

  function step(dir) {
    var v = currentVideo();
    var art = v && v.closest('article');
    var sib = art && (dir > 0 ? art.nextElementSibling : art.previousElementSibling);
    if (sib) { sib.scrollIntoView({ block: 'center' }); return; }
    var btn = document.querySelector(
      dir > 0 ? 'button[data-e2e="arrow-right"]' : 'button[data-e2e="arrow-left"]');
    if (btn) { btn.click(); return; }
    dir > 0 ? key('ArrowDown', 40) : key('ArrowUp', 38);
  }

  // ---------- API pública (la llama MainActivity) ----------
  window.tv = {
    next: function () { step(1); },
    prev: function () { step(-1); },

    toggle: function () {
      var v = currentVideo();
      if (!v) return;
      if (v.paused) { v.play(); toast('▶'); } else { v.pause(); toast('⏸'); }
    },

    mute: function () {
      var m = !(currentVideo() || {}).muted;
      document.querySelectorAll('video').forEach(function (v) { v.muted = m; });
      toast(m ? '🔇 Silenciado' : '🔊 Sonido');
    },

    like: function () {
      var v = currentVideo();
      var scope = (v && v.closest('article')) || document;
      var icon = scope.querySelector('[data-e2e="like-icon"]');
      if (icon) { icon.click(); toast('❤'); } else { toast('No encontré el botón de like'); }
    },

    // true = interfaz completa (modo navegación)
    setUI: function (show) {
      document.documentElement.classList.toggle('tv-video', !show);
    }
  };
})();
