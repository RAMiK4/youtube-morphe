// Serves the APKs and the update information of YouTube Morphe from an R2 bucket.
// Binding: the R2 bucket as BUCKET. Read-only: GET and HEAD under apk/ and updates/,
// at the root (YouTube) or below an app folder such as music/ (YT Music).
//   [<app>/]updates/<variant>.json  in-app update check (short cache, so a new build shows up quickly)
//   [<app>/]apk/<file>.apk          direct download (resumable)
//
// Optional binding: a D1 database as STATS. Each day it counts the APK downloads per file and the
// update checks per variant (about the number of installs in use). Only the path, the day and a
// count are stored, nothing about the visitor. The table is created on first use.

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

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
    }
    const key = decodeURIComponent(new URL(request.url).pathname.slice(1));
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
