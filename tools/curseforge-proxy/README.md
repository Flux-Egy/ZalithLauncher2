# CurseForge API proxy for Flux Launcher

The CurseForge API rejects requests without an API key (HTTP 403). Builds made without
the `CURSEFORGE_API_KEY` secret have no key. Instead of putting a key into the app, you
can run this small proxy that adds your key on the server.

1. Get a free API key at <https://console.curseforge.com>.
2. Deploy (Cloudflare account, free tier is enough):
   ```bash
   cd tools/curseforge-proxy
   npx wrangler deploy
   npx wrangler secret put CF_API_KEY   # paste the key
   ```
3. In Flux Launcher: **Settings → Launcher → CurseForge proxy**, enter the Worker URL
   (for example `https://flux-curseforge-proxy.<you>.workers.dev`).

The proxy only forwards the read-only endpoints the launcher uses (`worker.js`,
`ALLOWED`), never returns your key, and rejects other paths and methods. File downloads
(`edge.forgecdn.net`) do not need a key and do not go through the proxy.

Test: `node test.mjs`
