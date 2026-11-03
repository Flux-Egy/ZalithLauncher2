// Runs the worker against a fake upstream: node test.mjs
import worker, { isAllowed } from "./worker.js";
import assert from "node:assert/strict";

let seen = null;
globalThis.fetch = async (url, init) => {
  seen = { url, init };
  return new Response(JSON.stringify({ data: [] }), { status: 200, headers: { "Content-Type": "application/json" } });
};
const env = { CF_API_KEY: "test-key" };

assert.equal(isAllowed("GET", "/v1/mods/search"), true);
assert.equal(isAllowed("DELETE", "/v1/mods/search"), false);
assert.equal(isAllowed("GET", "/v1/../admin"), false);
assert.equal(isAllowed("GET", "/v1/mods/abc"), false);

let r = await worker.fetch(new Request("https://p.example/v1/mods/search?gameId=432&classId=6"), env);
assert.equal(r.status, 200);
assert.equal(seen.url, "https://api.curseforge.com/v1/mods/search?gameId=432&classId=6");
assert.equal(seen.init.headers.get("x-api-key"), "test-key");
assert.equal(r.headers.get("x-api-key"), null);

r = await worker.fetch(new Request("https://p.example/v1/mods", { method: "POST", body: '{"modIds":[1]}' }), env);
assert.equal(r.status, 200);
assert.equal(seen.init.body, '{"modIds":[1]}');

r = await worker.fetch(new Request("https://p.example/v1/games"), env);
assert.equal(r.status, 403);

r = await worker.fetch(new Request("https://p.example/v1/mods/search"), {});
assert.equal(r.status, 500);

console.log("proxy tests passed");
