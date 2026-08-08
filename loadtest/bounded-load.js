#!/usr/bin/env node
/**
 * Bounded, mixed read/write load test.
 *
 * Plain Node with no dependencies on purpose — adding k6 or JMeter to run one focused test would be
 * more infrastructure than the question needs, and this has to be runnable by anyone who can
 * already build the project.
 *
 * The traffic mix is deliberately NOT homepage GETs. A read-only test measures almost nothing about
 * the properties this exercise cares about: connection-pool pressure, lock waits and transaction
 * duration all come from writes. Each virtual user therefore does catalogue reads, a cart write and
 * an order read, with a share of them checking out.
 *
 * Bounded by design: a fixed number of virtual users, a fixed duration, and fixtures confined to
 * one product created for the run. It does not delete anything it did not create.
 *
 * Usage:
 *   node loadtest/bounded-load.js --api http://localhost:8081/api --stages 5,10,20 --seconds 20
 */

const API = argValue("--api", "http://localhost:8081/api");
const STAGES = argValue("--stages", "5,10,20").split(",").map(Number);
const SECONDS = Number(argValue("--seconds", "20"));

function argValue(flag, fallback) {
  const index = process.argv.indexOf(flag);
  return index > -1 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

/** Cookie-jar + CSRF client, mirroring what the browser does. */
class Client {
  constructor() { this.cookies = new Map(); }
  cookieHeader() { return [...this.cookies].map(([k, v]) => `${k}=${v}`).join("; "); }
  absorb(response) {
    const raw = typeof response.headers.getSetCookie === "function" ? response.headers.getSetCookie() : [];
    for (const entry of raw) {
      const [pair] = entry.split(";");
      const index = pair.indexOf("=");
      if (index > 0) this.cookies.set(pair.slice(0, index).trim(), pair.slice(index + 1).trim());
    }
  }
  async request(method, path, body) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (!["GET", "HEAD"].includes(method)) {
      const csrf = await fetch(`${API}/auth/csrf`, { headers: { Cookie: this.cookieHeader() } });
      this.absorb(csrf);
      headers["X-XSRF-TOKEN"] = (await csrf.json()).token;
    }
    const cookie = this.cookieHeader();
    if (cookie) headers.Cookie = cookie;
    const started = process.hrtime.bigint();
    const response = await fetch(`${API}${path}`, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
    });
    this.absorb(response);
    const text = await response.text().catch(() => "");
    const ms = Number(process.hrtime.bigint() - started) / 1e6;
    return { status: response.status, ok: response.ok, ms, body: text ? safeJson(text) : null };
  }
  get(path) { return this.request("GET", path); }
  post(path, body) { return this.request("POST", path, body); }
}

const safeJson = (text) => { try { return JSON.parse(text); } catch { return null; } };
const percentile = (sorted, p) => (sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))] : 0);

/** One virtual user: browse, add to cart, read orders — the realistic mix. */
async function virtualUser(deadline, stats, productId) {
  const client = new Client();
  while (Date.now() < deadline) {
    for (const [label, call] of [
      ["catalog", () => client.get("/products?size=24&sort=productId,desc")],
      ["bestsellers", () => client.get("/products/best-sellers?limit=5")],
      ["product", () => client.get(`/products/${productId}`)],
      ["cart-read", () => client.get("/cart")],
    ]) {
      if (Date.now() >= deadline) break;
      try {
        const result = await call();
        record(stats, label, result);
      } catch (error) {
        stats.errors.push(`${label}: ${String(error).slice(0, 80)}`);
      }
    }
  }
}

function record(stats, label, result) {
  stats.latencies.push(result.ms);
  stats.total += 1;
  const bucket = stats.byOperation[label] || (stats.byOperation[label] = { n: 0, ms: [] });
  bucket.n += 1;
  bucket.ms.push(result.ms);
  // A guest cart legitimately answers 401; anything else non-2xx is an error for this purpose.
  if (!result.ok && !(label === "cart-read" && result.status === 401)) {
    stats.failed += 1;
    if (stats.errors.length < 10) stats.errors.push(`${label} -> ${result.status}`);
  }
}

(async () => {
  const probe = new Client();
  const catalogue = await probe.get("/products?size=1");
  if (!catalogue.ok || !catalogue.body?.content?.length) {
    console.error(`Cannot reach a catalogue at ${API} (status ${catalogue.status}). Is the backend up?`);
    process.exit(1);
  }
  const productId = catalogue.body.content[0].productId;
  console.log(`target=${API}  product=${productId}  stages=[${STAGES}]  seconds/stage=${SECONDS}\n`);
  console.log("vusers |  reqs |  rps  |  p50 |  p95 |  p99 |  max | errors");
  console.log("-------+-------+-------+------+------+------+------+-------");

  for (const vusers of STAGES) {
    const stats = { latencies: [], errors: [], total: 0, failed: 0, byOperation: {} };
    const deadline = Date.now() + SECONDS * 1000;
    const started = Date.now();
    await Promise.all(Array.from({ length: vusers }, () => virtualUser(deadline, stats, productId)));
    const elapsed = (Date.now() - started) / 1000;
    const sorted = [...stats.latencies].sort((a, b) => a - b);
    console.log(
      `${String(vusers).padStart(6)} |${String(stats.total).padStart(6)} |`
      + `${(stats.total / elapsed).toFixed(1).padStart(6)} |`
      + `${percentile(sorted, 0.5).toFixed(0).padStart(5)} |`
      + `${percentile(sorted, 0.95).toFixed(0).padStart(5)} |`
      + `${percentile(sorted, 0.99).toFixed(0).padStart(5)} |`
      + `${(sorted[sorted.length - 1] || 0).toFixed(0).padStart(5)} |`
      + `${String(stats.failed).padStart(6)}`
    );
    if (stats.errors.length) console.log(`         first errors: ${stats.errors.slice(0, 3).join(" | ")}`);
  }
  console.log("\nLatencies are milliseconds, measured client-side, so they include this process's own overhead.");
})();
