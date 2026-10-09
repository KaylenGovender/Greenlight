// Greenlight Admin — shared UI: toasts, typed confirmations, Ctrl+K search, Functions status, tables.
window.gl = (() => {
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  function toast(message, kind = "success") {
    const el = document.createElement("div");
    el.className = `toast ${kind}`;
    el.role = "status";
    el.innerHTML = `<div class="d-flex align-items-start gap-2 p-3"><i class="bi ${kind === "danger" ? "bi-x-circle text-danger" : "bi-check-circle text-success"} mt-1"></i><div class="flex-grow-1">${esc(message)}</div><button type="button" class="btn-close btn-close-white ms-2" data-bs-dismiss="toast"></button></div>`;
    document.getElementById("toasts").appendChild(el);
    new bootstrap.Toast(el, { delay: kind === "danger" ? 9000 : 5000 }).show();
    el.addEventListener("hidden.bs.toast", () => el.remove());
  }

  const antiForgery = () => document.querySelector('input[name="__RequestVerificationToken"]')?.value ?? "";

  // Background calls to the console. If the sign-in has expired (401, or the cookie check
  // redirected to the sign-in page), go and sign in again, then come back to this page.
  async function call(url, options = {}) {
    const r = await fetch(url, { ...options, headers: { ...(options.headers ?? {}), "X-Requested-With": "fetch" } });
    if (r.status === 401 || (r.redirected && new URL(r.url).pathname.startsWith("/Account/Login"))) {
      location.href = "/Account/Login?expired=true&returnUrl=" + encodeURIComponent(location.pathname + location.search);
      throw new Error("Signed out");
    }
    return r;
  }

  async function post(url, data) {
    const body = new URLSearchParams({ ...data, __RequestVerificationToken: antiForgery() });
    const r = await call(url, { method: "POST", body, headers: { "RequestVerificationToken": antiForgery() } });
    return r.json();
  }

  // Forms with data-confirm="PHRASE" need the phrase typed into [data-confirm-input] before they submit.
  document.addEventListener("input", (e) => {
    const input = e.target.closest("[data-confirm-input]");
    if (!input) return;
    const form = input.closest("form");
    form.querySelector("[type=submit]").disabled = input.value.trim() !== form.dataset.confirm;
  });
  // Simple "Are you sure?" for smaller destructive buttons.
  document.addEventListener("submit", (e) => {
    const msg = e.target.dataset.ask;
    if (msg && !confirm(msg)) e.preventDefault();
    else e.target.querySelectorAll("[type=submit]").forEach((b) => { b.disabled = true; b.dataset.label = b.innerHTML; b.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Working…'; });
  });

  // Clickable table rows.
  document.addEventListener("click", (e) => {
    const row = e.target.closest("tr[data-href]");
    if (row && !e.target.closest("a,button,input,form")) location.href = row.dataset.href;
  });

  // ── Ctrl+K palette ──────────────────────────────────────────────────────
  const paletteEl = document.getElementById("palette");
  const palette = paletteEl ? new bootstrap.Modal(paletteEl) : null;
  const input = document.getElementById("paletteInput");
  const hitsEl = document.getElementById("paletteHits");
  let timer, active = 0, hits = [];
  function renderHits() {
    if (!hits.length) { hitsEl.innerHTML = `<div class="gl-empty py-4"><i class="bi bi-search"></i>${input.value.trim().length < 2 ? "Type at least 2 letters" : "Nothing found"}</div>`; return; }
    hitsEl.innerHTML = hits.map((h, i) => `<a class="hit ${i === active ? "active" : ""}" href="${h.url}">
      ${h.photoUrl ? `<img class="gl-avatar" src="${esc(h.photoUrl)}" referrerpolicy="no-referrer">` : `<span class="gl-avatar"><i class="bi ${h.type === "user" ? "bi-person" : "bi-people"}"></i></span>`}
      <span><div class="fw-semibold">${esc(h.title)}</div><div class="small text-muted">${esc(h.subtitle ?? "")}</div></span><span class="type">${h.type}</span></a>`).join("");
  }
  input?.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(async () => {
      const q = input.value.trim();
      if (q.length < 2) { hits = []; renderHits(); return; }
      hitsEl.innerHTML = '<div class="p-3"><div class="skeleton mb-2" style="height:44px"></div><div class="skeleton" style="height:44px"></div></div>';
      hits = await (await call(`/Dashboard/Search?q=${encodeURIComponent(q)}`)).json();
      active = 0; renderHits();
    }, 220);
  });
  input?.addEventListener("keydown", (e) => {
    if (e.key === "ArrowDown") { active = Math.min(active + 1, hits.length - 1); renderHits(); e.preventDefault(); }
    if (e.key === "ArrowUp") { active = Math.max(active - 1, 0); renderHits(); e.preventDefault(); }
    if (e.key === "Enter" && hits[active]) location.href = hits[active].url;
  });
  paletteEl?.addEventListener("shown.bs.modal", () => input.focus());
  document.getElementById("openPalette")?.addEventListener("click", () => palette.show());
  document.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") { e.preventDefault(); palette?.show(); }
  });

  // ── Functions status pill ───────────────────────────────────────────────
  async function status() {
    const pill = document.getElementById("fnStatus");
    if (!pill) return;
    try {
      const s = await (await call("/Dashboard/Status")).json();
      pill.classList.toggle("ok", s.ok); pill.classList.toggle("bad", !s.ok);
      pill.querySelector(".txt").textContent = s.ok ? `Functions · ${s.functionsMs} ms` : "Functions unreachable";
      pill.title = s.message ?? "Round trip to Greenlight's Cloud Functions";
    } catch { pill.classList.add("bad"); pill.querySelector(".txt").textContent = "API offline"; }
  }
  status(); setInterval(status, 60000);

  // ── Tables ──────────────────────────────────────────────────────────────
  function table(selector, options = {}) {
    return new DataTable(selector, {
      pageLength: 25, lengthMenu: [10, 25, 50, 100], order: [], language: { search: "", searchPlaceholder: "Filter…" },
      ...options,
    });
  }

  function exportCsv(filename, rows) {
    const csv = rows.map((r) => r.map((v) => `"${String(v ?? "").replace(/"/g, '""')}"`).join(",")).join("\n");
    const a = Object.assign(document.createElement("a"), { href: URL.createObjectURL(new Blob([csv], { type: "text/csv" })), download: filename });
    a.click(); URL.revokeObjectURL(a.href);
  }

  return { toast, toastJson: (t) => toast(t.message, t.kind), post, table, exportCsv, esc };
})();
