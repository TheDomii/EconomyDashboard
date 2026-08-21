function formatMoney(n) {
  return new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 }).format(n);
}

function formatNumber(n) {
  return new Intl.NumberFormat('de-DE').format(n);
}

function formatPrice(n) {
  return n === null || n === undefined ? '-' : new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);
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

function initSearch() {
  const input = document.getElementById('searchInput');
  const resultsEl = document.getElementById('searchResults');
  if (!input || !resultsEl) return;

  let searchDebounce = null;
  input.addEventListener('input', (e) => {
    clearTimeout(searchDebounce);
    const query = e.target.value.trim();
    if (query.length < 2) {
      resultsEl.style.display = 'none';
      return;
    }
    searchDebounce = setTimeout(() => runSearch(query, resultsEl), 250);
  });
}

async function runSearch(query, resultsEl) {
  try {
    const res = await fetch('/api/search?q=' + encodeURIComponent(query));
    const data = await res.json();

    let html = '';

    html += '<div class="search-section"><h4>Spieler</h4>';
    html += data.players.length ? data.players.map(p =>
      '<div class="bar-row"><div style="flex:1;">' + p.name + '</div><div>' + formatMoney(p.balance) + '</div></div>'
    ).join('') : '<div class="empty-hint">keine Treffer</div>';
    html += '</div>';

    html += '<div class="search-section"><h4>Shops</h4>';
    html += data.shops.length ? data.shops.map(s =>
      '<div class="bar-row"><div style="flex:1;">' + s.name + '</div><div>' + formatNumber(s.transactions) + ' Tx, Netto ' + formatMoney(s.net) + '</div></div>'
    ).join('') : '<div class="empty-hint">keine Treffer</div>';
    html += '</div>';

    html += '<div class="search-section"><h4>Items</h4>';
    html += data.items.length ? data.items.map(it =>
      '<div class="bar-row"><div style="flex:1;">' + it.name + '</div><div>Kauf: ' + formatPrice(it.buyPrice) +
      ' | Verkauf: ' + formatPrice(it.sellPrice) + ' | Historie: ' + formatNumber(it.boughtQty) + '/' + formatNumber(it.soldQty) + '</div></div>'
    ).join('') : '<div class="empty-hint">keine Treffer</div>';
    html += '</div>';

    html += '<div class="search-section"><h4>QuickShops</h4>';
    html += data.quickShops.length ? data.quickShops.map(s =>
      '<div class="bar-row"><div style="flex:1;">' + s.owner + ' - ' + s.item + '</div><div>' +
      (s.shopBuys ? 'kauft an' : 'verkauft') + ': ' + formatPrice(s.price) + '</div></div>'
    ).join('') : '<div class="empty-hint">keine Treffer</div>';
    html += '</div>';

    html += '<div class="search-section"><h4>Regionen</h4>';
    html += data.regions.length ? data.regions.map(r =>
      '<div class="bar-row"><div style="flex:1;">' + r.regionId + ' (' + r.world + ')</div><div>' +
      (r.sold ? ('verkauft an ' + r.owner) : 'verfuegbar') + ': ' + formatPrice(r.price) + '</div></div>'
    ).join('') : '<div class="empty-hint">keine Treffer</div>';
    html += '</div>';

    resultsEl.innerHTML = html;
    resultsEl.style.display = 'block';
  } catch (e) {
    resultsEl.innerHTML = '<div class="empty-hint">Fehler bei der Suche: ' + e + '</div>';
    resultsEl.style.display = 'block';
  }
}

document.addEventListener('DOMContentLoaded', initSearch);
