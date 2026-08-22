function formatMoney(n) {
  return new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 }).format(n);
}

function formatNumber(n) {
  return new Intl.NumberFormat('de-DE').format(n);
}

function formatPrice(n) {
  return n === null || n === undefined ? '-' : new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);
}

/**
 * Escapes a value for safe insertion into an innerHTML string. Almost every table in this
 * dashboard renders player/item/shop/town names that ultimately come from Minecraft players
 * (via dtlTradersPlus logs, QuickShop item/shop names, Towny town/nation names, anvil-renamed
 * items, ...) - none of that is trustworthy input, so every such value needs this before going
 * into an innerHTML string. Numbers formatted via formatMoney()/formatNumber() and our own
 * fixed label strings (severity/category enums etc.) don't need it.
 */
function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function playerLink(name) {
  if (!name) return '-';
  return '<a href="/player.html?name=' + encodeURIComponent(name) + '">' + escapeHtml(name) + '</a>';
}

/**
 * Wires the shared #reloadBtn (present in every page's nav) to re-run this page's own refresh
 * functions on demand, instead of waiting for their setInterval. fns may be sync or async.
 */
function wireReloadButton(fns) {
  const btn = document.getElementById('reloadBtn');
  if (!btn) return;
  btn.addEventListener('click', async () => {
    btn.classList.add('spinning');
    btn.disabled = true;
    try {
      await Promise.all(fns.map((f) => f()));
    } finally {
      setTimeout(() => {
        btn.classList.remove('spinning');
        btn.disabled = false;
      }, 300);
    }
  });
}

/**
 * Wires click-to-sort on every <th data-sort="fieldName"> inside theadRowEl (Excel-style:
 * click sorts ascending, click the same column again reverses it). Returns a mutable state
 * object {key, dir} - read it in your render function via applySort(rows, state). onSortChange
 * fires after the state is already updated, so it only needs to trigger a re-render.
 */
function wireSortableTable(theadRowEl, onSortChange) {
  const state = { key: null, dir: 1 };
  if (!theadRowEl) return state;
  const ths = Array.from(theadRowEl.querySelectorAll('th[data-sort]'));
  ths.forEach((th) => {
    th.classList.add('sortable');
    th.addEventListener('click', () => {
      const key = th.dataset.sort;
      if (state.key === key) {
        state.dir = -state.dir;
      } else {
        state.key = key;
        state.dir = 1;
      }
      ths.forEach((t) => t.classList.remove('sort-asc', 'sort-desc'));
      th.classList.add(state.dir === 1 ? 'sort-asc' : 'sort-desc');
      onSortChange(state.key, state.dir);
    });
  });
  return state;
}

/** Returns a new sorted array (rows left untouched) according to a wireSortableTable() state. */
function applySort(rows, sortState) {
  if (!sortState || !sortState.key) return rows;
  const key = sortState.key;
  const dir = sortState.dir;
  return rows.slice().sort((a, b) => {
    let av = a[key];
    let bv = b[key];
    if (typeof av === 'boolean') av = av ? 1 : 0;
    if (typeof bv === 'boolean') bv = bv ? 1 : 0;
    if (av == null && bv == null) return 0;
    if (av == null) return 1;
    if (bv == null) return -1;
    if (typeof av === 'string' && typeof bv === 'string') return av.localeCompare(bv, 'de') * dir;
    return (av < bv ? -1 : av > bv ? 1 : 0) * dir;
  });
}

/**
 * Renders a statistical trend chart (line + filled area + dashed average line + highlighted
 * peak) as inline SVG into containerEl, plus a row of first/middle/last labels underneath.
 * points: [{label, value}] in chronological order. Self-contained, no chart library - matches
 * the dashboard's zero-dependency approach. Deliberately not a bar-per-row ("Gantt") layout:
 * the point is to show the shape of the trend, not enumerate every single day as a row.
 */
function renderTrendChart(containerEl, points, opts) {
  opts = opts || {};
  const width = opts.width || 600;
  const height = opts.height || 150;
  containerEl.innerHTML = '';
  if (!points || points.length === 0) {
    containerEl.innerHTML = '<div class="empty-hint">Noch keine Daten - die Aufzeichnung läuft erst seit Kurzem.</div>';
    return;
  }

  const padding = { top: 14, right: 10, bottom: 8, left: 10 };
  const innerW = width - padding.left - padding.right;
  const innerH = height - padding.top - padding.bottom;
  const values = points.map((p) => p.value);
  const maxV = Math.max(...values, 1);
  const minV = Math.min(0, ...values);
  const range = Math.max(1e-9, maxV - minV);
  const avg = values.reduce((a, b) => a + b, 0) / values.length;
  const stepX = points.length > 1 ? innerW / (points.length - 1) : 0;
  const xAt = (i) => padding.left + i * stepX;
  const yAt = (v) => padding.top + innerH - ((v - minV) / range) * innerH;

  let peakIdx = 0;
  values.forEach((v, i) => { if (v > values[peakIdx]) peakIdx = i; });

  const linePts = points.map((p, i) => xAt(i).toFixed(1) + ',' + yAt(p.value).toFixed(1)).join(' ');
  const areaPts = linePts + ' ' + xAt(points.length - 1).toFixed(1) + ',' + (padding.top + innerH) +
    ' ' + xAt(0).toFixed(1) + ',' + (padding.top + innerH);
  const gradId = 'trendGrad' + Math.random().toString(36).slice(2, 9);

  containerEl.innerHTML =
    '<svg viewBox="0 0 ' + width + ' ' + height + '" preserveAspectRatio="none" style="width:100%;height:' + height + 'px;display:block;">' +
    '<defs><linearGradient id="' + gradId + '" x1="0" y1="0" x2="0" y2="1">' +
    '<stop offset="0%" stop-color="var(--accent)" stop-opacity="0.5"></stop>' +
    '<stop offset="100%" stop-color="var(--accent)" stop-opacity="0"></stop>' +
    '</linearGradient></defs>' +
    '<polygon points="' + areaPts + '" fill="url(#' + gradId + ')"></polygon>' +
    '<line x1="' + padding.left + '" y1="' + yAt(avg).toFixed(1) + '" x2="' + (width - padding.right) + '" y2="' + yAt(avg).toFixed(1) +
    '" stroke="var(--muted)" stroke-width="1" stroke-dasharray="4 4"></line>' +
    '<polyline points="' + linePts + '" fill="none" stroke="var(--accent2)" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"></polyline>' +
    '<circle cx="' + xAt(peakIdx).toFixed(1) + '" cy="' + yAt(values[peakIdx]).toFixed(1) + '" r="4" fill="var(--accent)"></circle>' +
    '</svg>' +
    '<div class="trend-chart-labels"><span>' + escapeHtml(points[0].label || '') + '</span>' +
    (points.length > 2 ? '<span>' + escapeHtml(points[Math.floor((points.length - 1) / 2)].label || '') + '</span>' : '') +
    '<span>' + escapeHtml(points[points.length - 1].label || '') + '</span></div>';
}

function relativeTime(millis) {
  const diffSec = Math.max(0, Math.round((Date.now() - millis) / 1000));
  if (diffSec < 5) return 'gerade eben';
  if (diffSec < 60) return 'vor ' + diffSec + 's';
  const diffMin = Math.round(diffSec / 60);
  if (diffMin < 60) return 'vor ' + diffMin + 'min';
  const diffH = Math.round(diffMin / 60);
  if (diffH < 24) return 'vor ' + diffH + 'h';
  return 'vor ' + Math.round(diffH / 24) + 'd';
}

function exportCsv(panelId, endpoint) {
  const panel = document.getElementById(panelId);
  const inputs = panel.querySelectorAll('[data-param]');
  const params = new URLSearchParams();
  inputs.forEach((inp) => {
    if (inp.value) {
      params.set(inp.dataset.param, inp.value);
    }
  });
  const qs = params.toString();
  window.location.href = endpoint + (qs ? '?' + qs : '');
}

/** 0 (or the literal string "all") means "show everything already fetched". */
function pageSizeValue(selectEl) {
  const raw = selectEl.value;
  return raw === 'all' ? 0 : parseInt(raw, 10) || 50;
}

function sliceForDisplay(array, size) {
  return size > 0 ? array.slice(0, size) : array;
}

function readFilterValues(formId) {
  const panel = document.getElementById(formId);
  const values = {};
  panel.querySelectorAll('[data-param]').forEach((inp) => {
    if (inp.value) {
      values[inp.dataset.param] = inp.value;
    }
  });
  return values;
}

/**
 * Wires a filter form's inputs (any element with [data-param] inside #formId) to live-filter
 * an already-fetched, in-memory array (used for registry-style tables: players, shops, prices,
 * towns, quickshop/region listings - small enough to hold entirely in the browser). matchFn
 * receives (item, values) and returns true/false. onChange receives the filtered array and is
 * responsible for rendering (it should also apply the page-size slice).
 */
function initClientFilterTable(formId, matchFn, onChange) {
  const panel = document.getElementById(formId);
  if (!panel) return () => {};
  const apply = () => onChange(matchFn(readFilterValues(formId)));
  panel.querySelectorAll('[data-param]').forEach((inp) => {
    inp.addEventListener('input', apply);
    inp.addEventListener('change', apply);
  });
  apply();
  return apply;
}

/**
 * Wires a filter form to a server endpoint returning JSON (used for transaction-history
 * tables - too large to hold entirely in the browser). Debounced so typing doesn't spam
 * requests. onResults receives the parsed JSON array.
 */
function initServerFilterTable(formId, endpoint, onResults, debounceMs) {
  const panel = document.getElementById(formId);
  if (!panel) return () => {};
  let timer = null;
  const fetchNow = async () => {
    const values = readFilterValues(formId);
    const params = new URLSearchParams(values);
    try {
      const res = await fetch(endpoint + '?' + params.toString());
      const data = await res.json();
      onResults(data);
    } catch (e) {
      console.error('Fehler beim Laden der gefilterten Daten: ' + e);
      onResults([]);
    }
  };
  panel.querySelectorAll('[data-param]').forEach((inp) => {
    const trigger = () => {
      clearTimeout(timer);
      timer = setTimeout(fetchNow, debounceMs || 300);
    };
    inp.addEventListener('input', trigger);
    inp.addEventListener('change', trigger);
  });
  fetchNow();
  return fetchNow;
}
