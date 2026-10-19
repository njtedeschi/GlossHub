// Shared set-up for the browser tests: a static server for public/ and a
// browser page that records errors. The site must be built first
// (`clj -M:release`).
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const root = path.join(__dirname, '..', '..', 'public');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.map': 'application/json' };

function serve() {
  return new Promise(resolve => {
    const server = http.createServer((req, res) => {
      const file = path.join(root, decodeURIComponent(req.url.split('?')[0]).replace(/\/$/, '/index.html'));
      if (!file.startsWith(root) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
      res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' });
      fs.createReadStream(file).pipe(res);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

/** Starts the server and a browser; `close` stops both. */
async function start() {
  if (!fs.existsSync(path.join(root, 'js', 'main.js'))) {
    throw new Error('public/js/main.js is missing: build the site first (clj -M:release).');
  }
  const server = await serve();
  const browser = await chromium.launch();
  const base = `http://127.0.0.1:${server.address().port}/`;
  return {
    base,
    browser,
    async close() { await browser.close(); server.close(); },
  };
}

/** The texts in test/fixtures, by name: latin, hebrew, japanese. */
const fixtures = Object.fromEntries(
  fs.readdirSync(path.join(__dirname, '..', 'fixtures'))
    .filter(f => f.endsWith('.gloss'))
    .map(f => [path.basename(f, '.gloss'), fs.readFileSync(path.join(__dirname, '..', 'fixtures', f), 'utf8')]));

/**
 * Saved data (as the site stores it) in which the fixtures are the reader's
 * own texts, with their names as ids: the Latin text is at #latin.
 */
function savedWithTexts() {
  const ids = Object.keys(fixtures);
  const docs = ids.map(id => `${JSON.stringify(id)} ${JSON.stringify(fixtures[id])}`).join(' ');
  return `{:format 1 :order [${ids.map(id => JSON.stringify(id)).join(' ')}] :docs {${docs}} :overrides {}}`;
}

/**
 * A new page in its own context (so its own storage), opened at `hash`.
 * Nothing is saved, unless `withTexts`: then the fixtures are the reader's
 * own texts. `page.errors` collects uncaught errors.
 */
async function freshPage(env, hash = '', { context, withTexts = false } = {}) {
  const ctx = context || await env.browser.newContext({ viewport: { width: 1100, height: 900 } });
  const page = await ctx.newPage();
  page.errors = [];
  page.on('pageerror', e => page.errors.push(e.message));
  if (withTexts) {
    await page.goto(env.base);
    await page.evaluate(saved => localStorage.setItem('glosshub', saved), savedWithTexts());
  }
  await page.goto(env.base + '#' + hash);
  if (withTexts) await page.reload();
  await page.waitForSelector('.app');
  return page;
}

/** Opens `hash` in a page, waiting for the app to render. */
async function open(page, hash) {
  await page.evaluate(h => { location.hash = h; }, hash);
  await page.waitForSelector('.app');
}

module.exports = { start, freshPage, open, fixtures };
