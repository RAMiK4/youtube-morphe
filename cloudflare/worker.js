// Serves the APKs and the update information of YouTube Morphe from an R2 bucket.
// Binding: the R2 bucket as BUCKET. Read-only: GET and HEAD under apk/ and updates/.
//   /updates/<variant>.json  in-app update check (short cache, so a new build shows up quickly)
//   /apk/<file>.apk          direct download (resumable)

export default {
  async fetch(request, env) {
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
    }
    const key = decodeURIComponent(new URL(request.url).pathname.slice(1));
    if (!/^(apk\/[\w.\-]+\.apk|updates\/[\w\-]+\.json)$/.test(key)) {
      return new Response("Not found", { status: 404 });
    }

    const object = await env.BUCKET.get(key, { range: request.headers, onlyIf: request.headers });
    if (object === null) return new Response("Not found", { status: 404 });

    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set("ETag", object.httpEtag);
    headers.set("Accept-Ranges", "bytes");
    if (key.endsWith(".apk")) {
      headers.set("Content-Type", "application/vnd.android.package-archive");
      headers.set("Content-Disposition", `attachment; filename="${key.slice(4)}"`);
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
