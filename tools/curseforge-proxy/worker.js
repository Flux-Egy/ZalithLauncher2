// Flux Launcher - CurseForge API proxy (Cloudflare Worker)
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Forwards read-only CurseForge API requests and adds your API key on the server, so
// the key is never shipped inside the app. Deploy with your own key:
//   npx wrangler deploy              (see README.md)
//   npx wrangler secret put CF_API_KEY
// Then enter the Worker URL in Flux Launcher: Settings > Launcher > CurseForge proxy.

const UPSTREAM = "https://api.curseforge.com";

// Only the endpoints the launcher uses (search, project/file metadata, categories).
const ALLOWED = [
  /^\/v1\/mods\/search$/,
  /^\/v1\/mods$/,                       // POST: get mods by ids
  /^\/v1\/mods\/\d+$/,
  /^\/v1\/mods\/\d+\/files$/,
  /^\/v1\/mods\/\d+\/files\/\d+$/,
  /^\/v1\/mods\/\d+\/files\/\d+\/download-url$/,
  /^\/v1\/mods\/\d+\/description$/,
  /^\/v1\/mods\/files$/,                // POST: get files by ids
  /^\/v1\/fingerprints(\/\d+)?$/,       // POST: match by fingerprint
  /^\/v1\/categories$/,
  /^\/v1\/minecraft\/version$/,
  /^\/v1\/minecraft\/modloader$/,
];

export function isAllowed(method, path) {
  if (method !== "GET" && method !== "POST") return false;
  return ALLOWED.some((re) => re.test(path));
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!isAllowed(request.method, url.pathname)) {
      return new Response("Not allowed", { status: 403 });
    }
    if (!env.CF_API_KEY) {
      return new Response("Proxy is missing the CF_API_KEY secret", { status: 500 });
    }
    const headers = new Headers({
      "Accept": "application/json",
      "x-api-key": env.CF_API_KEY,
      "User-Agent": request.headers.get("User-Agent") || "FluxLauncher-proxy",
    });
    const init = { method: request.method, headers };
    if (request.method === "POST") {
      const body = await request.text();
      if (body.length > 64 * 1024) return new Response("Body too large", { status: 413 });
      headers.set("Content-Type", "application/json");
      init.body = body;
    }
    const upstream = await fetch(UPSTREAM + url.pathname + url.search, init);
    // pass the JSON through; never echo request headers (the key) back
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { "Content-Type": upstream.headers.get("Content-Type") || "application/json" },
    });
  },
};
