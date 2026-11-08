// Serves the APKs and the update information of YT Morphe from an R2 bucket.
// Binding: the R2 bucket as BUCKET. Read-only: GET and HEAD under apk/ and updates/,
// at the root (YouTube) or below an app folder such as music/ (YT Music).
//   [<app>/]updates/<variant>.json  in-app update check (short cache, so a new build shows up quickly)
//   [<app>/]apk/<file>.apk          direct download (resumable)
//
// Optional binding: a D1 database as STATS. Each day it counts the APK downloads per file and the
// update checks per variant (about the number of installs in use). Only the path, the day and a
// count are stored, nothing about the visitor. The table is created on first use.
//
// Statistics page: /stats?key=<STATS_KEY>, with the secret STATS_KEY set on the Worker.
// Without the secret the page does not exist.

const STATS_TABLE = `CREATE TABLE IF NOT EXISTS hits (
  day TEXT NOT NULL,
  path TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, path)
)`;

let statsReady = false;

async function count(env, path) {
  if (!env.STATS) return;
  try {
    if (!statsReady) {
      await env.STATS.prepare(STATS_TABLE).run();
      statsReady = true;
    }
    const day = new Date().toISOString().slice(0, 10);
    await env.STATS.prepare(
      "INSERT INTO hits (day, path, count) VALUES (?1, ?2, 1) " +
      "ON CONFLICT (day, path) DO UPDATE SET count = count + 1"
    ).bind(day, path).run();
  } catch (error) {
    console.error("Could not count", path, error);
  }
}

/** A new download, not the continuation of one (a range that does not start at the beginning). */
function isNewDownload(request) {
  const range = request.headers.get("Range");
  return !range || /^bytes=0-/.test(range);
}

const escape = (text) => String(text).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

function table(title, columns, rows) {
  const head = columns.map((c) => `<th>${escape(c)}</th>`).join("");
  const body = rows.length
    ? rows.map((r) => `<tr>${columns.map((c) => `<td>${escape(r[c] ?? "")}</td>`).join("")}</tr>`).join("")
    : `<tr><td colspan="${columns.length}">—</td></tr>`;
  return `<h2>${escape(title)}</h2><div class="scroll"><table><tr>${head}</tr>${body}</table></div>`;
}

/** Shorter names: "YT Music · Original_Mod_BBK_Clone" instead of the full path. */
function label(path) {
  const app = path.startsWith("music/") ? "YT Music" : "YT";
  const file = path.split("/").pop();
  const variants = { "hiby.json": "Original", "apple.json": "Original", "morphe.json": "Amoled" };
  if (file.endsWith(".json")) return `${app} · ${variants[file] ?? file}`;
  return `${app} · ${file.replace(/^(YouTube|YT|YT_Music)_Morphe-/, "").replace(/\.apk$/, "")}`;
}

async function statsPage(env) {
  if (!env.STATS) return new Response("No STATS database bound", { status: 503 });
  try {
    await env.STATS.prepare(STATS_TABLE).run();
    const all = async (sql) => (await env.STATS.prepare(sql).all()).results;
    const labeled = (rows) => rows.map((r) => ({ ...r, File: label(r.path) }));

    const downloads30 = labeled(await all(
      "SELECT path, SUM(count) AS Downloads FROM hits WHERE path LIKE '%apk/%' " +
      "AND day >= date('now', '-30 days') GROUP BY path ORDER BY Downloads DESC"));
    const downloadsAll = labeled(await all(
      "SELECT path, SUM(count) AS Downloads FROM hits WHERE path LIKE '%apk/%' GROUP BY path ORDER BY Downloads DESC"));
    const daily = await all(
      "SELECT day AS Day, " +
      "SUM(CASE WHEN path LIKE 'apk/%' THEN count ELSE 0 END) AS \"YouTube downloads\", " +
      "SUM(CASE WHEN path LIKE 'music/apk/%' THEN count ELSE 0 END) AS \"YT Music downloads\", " +
      "SUM(CASE WHEN path LIKE 'updates/%' THEN count ELSE 0 END) AS \"YouTube checks\", " +
      "SUM(CASE WHEN path LIKE 'music/updates/%' THEN count ELSE 0 END) AS \"YT Music checks\" " +
      "FROM hits WHERE day >= date('now', '-14 days') GROUP BY day ORDER BY day DESC");
    const checks = labeled(await all(
      "SELECT path, " +
      "SUM(CASE WHEN day = date('now') THEN count ELSE 0 END) AS Today, " +
      "SUM(CASE WHEN day = date('now', '-1 day') THEN count ELSE 0 END) AS Yesterday, " +
      "SUM(CASE WHEN day >= date('now', '-7 days') THEN count ELSE 0 END) AS \"Last 7 days\" " +
      "FROM hits WHERE path LIKE '%updates/%' GROUP BY path ORDER BY Yesterday DESC"));

    const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex">
<title>Ramik Mods · Stats</title><style>
:root{color-scheme:light dark;--bg:#fff;--fg:#111;--muted:#666;--line:#ddd;--head:#f3f3f3}
@media (prefers-color-scheme:dark){:root{--bg:#0f0f0f;--fg:#f0f0f0;--muted:#999;--line:#333;--head:#1c1c1c}}
body{margin:0;padding:16px;background:var(--bg);color:var(--fg);font:15px/1.4 system-ui,sans-serif}
h1{font-size:20px;margin:0 0 4px}h2{font-size:16px;margin:24px 0 8px}p{color:var(--muted);margin:0}
.scroll{overflow-x:auto}table{border-collapse:collapse;min-width:100%}
th,td{border-bottom:1px solid var(--line);padding:6px 10px;text-align:left;white-space:nowrap}
th{background:var(--head)}td:not(:first-child),th:not(:first-child){text-align:right}
</style></head><body><h1>Ramik Mods · Stats</h1>
<p>Updated ${escape(new Date().toISOString().replace("T", " ").slice(0, 16))} UTC. Update checks are about the installs in use.</p>
${table("Downloads, last 30 days", ["File", "Downloads"], downloads30)}
${table("Per day, last 14 days", ["Day", "YouTube downloads", "YT Music downloads", "YouTube checks", "YT Music checks"], daily)}
${table("Update checks", ["File", "Today", "Yesterday", "Last 7 days"], checks)}
${table("Downloads, all time", ["File", "Downloads"], downloadsAll)}
</body></html>`;
    return new Response(html, {
      headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Robots-Tag": "noindex" },
    });
  } catch (error) {
    return new Response("Could not read the statistics: " + error, { status: 500 });
  }
}

/** Compares without leaking the length of the matching part through timing. */
function sameKey(given, expected) {
  if (!given || !expected || given.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < given.length; i++) diff |= given.charCodeAt(i) ^ expected.charCodeAt(i);
  return diff === 0;
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === "/stats" && request.method === "GET" && env.STATS_KEY) {
      if (!sameKey(url.searchParams.get("key"), env.STATS_KEY)) return new Response("Not found", { status: 404 });
      return statsPage(env);
    }
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
    }
    const key = decodeURIComponent(url.pathname.slice(1));
    if (!/^([a-z]+\/)?(apk\/[\w.\-]+\.apk|updates\/[\w\-]+\.json)$/.test(key)) {
      return new Response("Not found", { status: 404 });
    }

    const object = await env.BUCKET.get(key, { range: request.headers, onlyIf: request.headers });
    if (object === null) return new Response("Not found", { status: 404 });

    const apk = key.endsWith(".apk");
    // Test files are not counted
    const test = /(^|\/)apk\/test_|-test\.json$/.test(key);
    if (request.method === "GET" && !test && (!apk || isNewDownload(request))) {
      ctx.waitUntil(count(env, key));
    }

    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set("ETag", object.httpEtag);
    headers.set("Accept-Ranges", "bytes");
    if (apk) {
      headers.set("Content-Type", "application/vnd.android.package-archive");
      headers.set("Content-Disposition", `attachment; filename="${key.split("/").pop()}"`);
      headers.set("Cache-Control", "public, max-age=86400");
    } else {
      headers.set("Content-Type", "application/json; charset=utf-8");
      headers.set("Cache-Control", "no-cache");
    }

    // A conditional request whose condition failed: the object has no body
    if (!("body" in object)) return new Response(null, { status: 304, headers });

    let status = 200;
    if (object.range && request.headers.has("Range")) {
      const offset = object.range.offset ?? object.size - object.range.suffix;
      const length = object.range.length ?? object.size - offset;
      headers.set("Content-Range", `bytes ${offset}-${offset + length - 1}/${object.size}`);
      headers.set("Content-Length", String(length));
      status = 206;
    } else {
      headers.set("Content-Length", String(object.size));
    }
    return new Response(request.method === "HEAD" ? null : object.body, { status, headers });
  },
};
