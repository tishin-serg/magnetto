(() => {
  "use strict";

  const API = "/api/v1";
  const TOKEN_KEY = "magnetto.apiToken";
  const TARGETS = new Set(["HOME_LIBRARY", "S3_LIBRARY", "PHONE_VPS_TEMP", "PHONE_S3_TEMP"]);
  const $ = (id) => document.getElementById(id);
  const state = { token: localStorage.getItem(TOKEN_KEY) || "", movie: null, automatic: true };

  function busy(on) { $("busy").classList.toggle("hidden", !on); }
  function toast(message, error = false) {
    const element = $("toast");
    element.textContent = message;
    element.classList.toggle("error", error);
    element.classList.remove("hidden");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => element.classList.add("hidden"), 4500);
  }
  function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"})[c]);
  }
  async function api(path, options = {}) {
    const response = await fetch(API + path, {
      ...options,
      headers: {"Authorization": `Bearer ${state.token}`, "Accept": "application/json", ...(options.body ? {"Content-Type":"application/json"} : {}), ...options.headers}
    });
    const text = await response.text();
    let body = null;
    if (text) { try { body = JSON.parse(text); } catch (_) { body = text; } }
    if (!response.ok) {
      if (response.status === 401) showAuth();
      throw new Error(body?.detail || body?.title || body?.error || `Ошибка API ${response.status}`);
    }
    return body;
  }
  async function run(action) {
    busy(true);
    try { await action(); } catch (error) { toast(error.message || "Ошибка", true); } finally { busy(false); }
  }
  function showAuth() {
    $("auth-view").classList.remove("hidden");
    $("app-view").classList.add("hidden");
    $("logout").classList.add("hidden");
  }
  function showApp() {
    $("auth-view").classList.add("hidden");
    $("app-view").classList.remove("hidden");
    $("logout").classList.remove("hidden");
  }
  function episodes() {
    return $("episodes").value.split(",").map(v => Number(v.trim())).filter(Number.isInteger);
  }
  function seriesFields(request) {
    if (state.movie?.type === "TV") {
      request.season = Number($("season").value);
      request.episodes = episodes();
    }
    return request;
  }
  function activateTab(name) {
    document.querySelectorAll(".tab").forEach(tab => tab.classList.add("hidden"));
    document.querySelectorAll("nav button").forEach(button => button.classList.toggle("active", button.dataset.tab === name));
    $(`tab-${name}`).classList.remove("hidden");
    if (name === "jobs") run(loadJobs);
    if (name === "preferences") run(loadPreferences);
  }

  async function searchCatalog() {
    const query = $("query").value.trim();
    if (!query) throw new Error("Введите название");
    const results = await api("/catalog/search", {method:"POST", body:JSON.stringify({query})});
    $("selection").classList.add("hidden");
    $("catalog").innerHTML = results.length ? results.map((item, index) => `
      <article class="card">
        <h3>${esc(item.title)}${item.year ? ` (${esc(item.year)})` : ""}</h3>
        <div class="meta">${item.type === "TV" ? "Сериал" : "Фильм"} • рейтинг ${esc(item.rating ?? "—")}</div>
        <div class="card-actions"><button data-movie="${index}" class="primary">Выбрать</button></div>
      </article>`).join("") : '<p class="muted">Ничего не найдено</p>';
    $("catalog").querySelectorAll("[data-movie]").forEach(button => button.onclick = () => selectMovie(results[Number(button.dataset.movie)]));
  }
  function selectMovie(movie) {
    state.movie = movie;
    $("selected-title").textContent = `${movie.title}${movie.year ? ` (${movie.year})` : ""}`;
    $("series-fields").classList.toggle("hidden", movie.type !== "TV");
    $("selection").classList.remove("hidden");
    $("torrents").innerHTML = "";
    $("selection").scrollIntoView({behavior:"smooth", block:"start"});
  }
  function setMode(automatic) {
    state.automatic = automatic;
    $("auto-mode").classList.toggle("active", automatic);
    $("manual-mode").classList.toggle("active", !automatic);
    $("create-auto").classList.toggle("hidden", !automatic);
    $("search-torrents").classList.toggle("hidden", automatic);
    $("torrents").innerHTML = "";
  }
  async function createDownload(torrentSelectionId = null) {
    if (!state.movie) throw new Error("Сначала выберите фильм или сериал");
    const request = seriesFields({
      movieSelectionId: state.movie.selectionId,
      deliveryTarget: $("target").value,
      automatic: !torrentSelectionId
    });
    if (torrentSelectionId) request.torrentSelectionId = torrentSelectionId;
    const accepted = await api("/downloads", {
      method:"POST",
      headers:{"Idempotency-Key": crypto.randomUUID()},
      body:JSON.stringify(request)
    });
    toast(`Загрузка принята: ${accepted.jobId}`);
    state.movie = null;
    $("catalog").innerHTML = "";
    $("selection").classList.add("hidden");
  }
  async function searchTorrents() {
    if (!state.movie) throw new Error("Сначала выберите фильм или сериал");
    const prefs = await api("/preferences");
    const request = seriesFields({selectionId:state.movie.selectionId, quality:prefs.quality, voice:prefs.voice});
    const results = await api("/torrents/search", {method:"POST", body:JSON.stringify(request)});
    $("torrents").innerHTML = results.length ? results.map((item, index) => `
      <article class="card">
        <h3>${esc(item.title)}</h3>
        <div class="meta">${formatBytes(item.sizeBytes)} • сидов ${esc(item.seeders)}</div>
        <div class="card-actions"><button data-torrent="${index}" class="primary">Скачать</button></div>
      </article>`).join("") : '<p class="muted">Подходящих раздач нет</p>';
    $("torrents").querySelectorAll("[data-torrent]").forEach(button => button.onclick = () => run(() => createDownload(results[Number(button.dataset.torrent)].selectionId)));
  }
  function formatBytes(bytes) {
    if (!Number.isFinite(Number(bytes))) return "—";
    const units = ["Б", "КБ", "МБ", "ГБ", "ТБ"];
    let value = Number(bytes), unit = 0;
    while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
    return `${value.toFixed(unit > 1 ? 1 : 0)} ${units[unit]}`;
  }

  async function loadJobs() {
    const jobs = await api("/downloads");
    $("jobs").innerHTML = jobs.length ? jobs.map(job => `
      <article class="card" data-job="${esc(job.jobId)}">
        <h3>${esc(job.status)}</h3>
        <div class="meta">${esc(job.progress)}%${job.error ? ` • ${esc(job.error)}` : ""}</div>
        <div class="progress"><i style="width:${Math.max(0, Math.min(100, Number(job.progress) || 0))}%"></i></div>
        ${job.links?.length ? `<div class="card-actions">${job.links.map(link => `<a href="${esc(link)}" target="_blank" rel="noopener">Открыть файл</a>`).join("")}</div>` : ""}
        <div class="card-actions"><button data-action="pause">Пауза</button><button data-action="resume">Продолжить</button></div>
        ${job.files?.length ? `<div class="files">${job.files.map(file => `<label class="check"><input type="checkbox" value="${esc(file.fileId)}"> ${esc(file.name)} (${formatBytes(file.sizeBytes)})</label>`).join("")}<button data-action="files" class="primary">Подтвердить файлы</button></div>` : ""}
      </article>`).join("") : '<p class="muted">Загрузок пока нет</p>';
    $("jobs").querySelectorAll("[data-job]").forEach(card => card.addEventListener("click", event => {
      const action = event.target.dataset.action;
      if (!action) return;
      run(async () => {
        if (action === "files") {
          const fileIds = [...card.querySelectorAll('input[type="checkbox"]:checked')].map(input => input.value);
          if (!fileIds.length) throw new Error("Выберите хотя бы один файл");
          await api(`/downloads/${card.dataset.job}/files`, {method:"PUT", body:JSON.stringify({fileIds})});
        } else {
          await api(`/downloads/${card.dataset.job}/${action}`, {method:"POST"});
        }
        toast("Готово");
        await loadJobs();
      });
    }));
  }

  async function loadPreferences() {
    const prefs = await api("/preferences");
    $("quality").value = prefs.quality || "any";
    $("voice").value = prefs.voice || "any";
    $("min-gib").value = Number(prefs.minBytes || 0) / 1073741824;
    $("max-gib").value = Number(prefs.maxBytes) > Number.MAX_SAFE_INTEGER ? "" : Number(prefs.maxBytes || 0) / 1073741824;
    $("min-seeders").value = prefs.minSeeders ?? 1;
    $("min-speed").value = Number(prefs.minSpeedBytesPerSecond || 0) / 1024;
    $("auto-replace").checked = Boolean(prefs.autoReplaceSlowDownload);
  }
  async function savePreferences() {
    const max = $("max-gib").value;
    const request = {
      minBytes: Math.round(Number($("min-gib").value || 0) * 1073741824),
      maxBytes: max === "" ? "9223372036854775807" : Math.round(Number(max) * 1073741824),
      minSeeders: Number($("min-seeders").value || 0),
      quality: $("quality").value,
      voice: $("voice").value,
      minSpeedBytesPerSecond: Math.round(Number($("min-speed").value || 0) * 1024),
      autoReplaceSlowDownload: $("auto-replace").checked
    };
    await api("/preferences", {method:"PUT", body:JSON.stringify(request)});
    toast("Настройки сохранены");
  }

  $("save-token").onclick = () => run(async () => {
    const token = $("token").value.trim();
    if (!token) throw new Error("Введите токен");
    state.token = token;
    await api("/preferences");
    localStorage.setItem(TOKEN_KEY, token);
    $("token").value = "";
    showApp();
    await startFromUrl();
  });
  $("logout").onclick = () => {
    localStorage.removeItem(TOKEN_KEY);
    state.token = "";
    showAuth();
  };
  document.querySelectorAll("nav button").forEach(button => button.onclick = () => activateTab(button.dataset.tab));
  $("search").onclick = () => run(searchCatalog);
  $("query").addEventListener("keydown", event => { if (event.key === "Enter") run(searchCatalog); });
  $("auto-mode").onclick = () => setMode(true);
  $("manual-mode").onclick = () => setMode(false);
  $("create-auto").onclick = () => run(() => createDownload());
  $("search-torrents").onclick = () => run(searchTorrents);
  $("refresh-jobs").onclick = () => run(loadJobs);
  $("save-preferences").onclick = () => run(savePreferences);

  async function startFromUrl() {
    const params = new URLSearchParams(location.search);
    const target = params.get("target");
    const query = params.get("q");
    if (TARGETS.has(target)) $("target").value = target;
    if (query) {
      $("query").value = query;
      activateTab("new");
      await searchCatalog();
    }
  }

  if (state.token) {
    run(async () => {
      await api("/preferences");
      showApp();
      await startFromUrl();
    });
  } else {
    showAuth();
  }
})();
