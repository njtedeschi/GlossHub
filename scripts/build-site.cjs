// Assembles the deployable site in site/ from the release build in public/
// (run `clj -M:release` first):
//   - the script and the stylesheet are named after their content
//     (js/main.3f2a91c0.js), and index.html points at those names, so a
//     browser never keeps running an old script after a deploy;
//   - the version (the commit it was built from) is written into the page,
//     where the footer shows it.
// Usage: node scripts/build-site.cjs [version]
// The version defaults to $GITHUB_SHA, else `git rev-parse HEAD`, shortened.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execSync } = require('node:child_process');

const root = path.join(__dirname, '..');
const from = path.join(root, 'public');
const to = path.join(root, 'site');

function version() {
  const given = process.argv[2] || process.env.GITHUB_SHA
    || execSync('git rev-parse HEAD', { cwd: root }).toString().trim();
  const date = new Date().toISOString().slice(0, 10);
  return `${given.slice(0, 7)} (${date})`;
}

/** Copies `file` (relative to public/) into site/ under a content-named name. */
function hashed(file) {
  const source = path.join(from, file);
  if (!fs.existsSync(source)) {
    throw new Error(`${path.relative(root, source)} is missing: build the site first (clj -M:release).`);
  }
  const content = fs.readFileSync(source);
  const hash = crypto.createHash('sha256').update(content).digest('hex').slice(0, 8);
  const { dir, name, ext } = path.parse(file);
  const named = path.posix.join(dir, `${name}.${hash}${ext}`);
  fs.mkdirSync(path.join(to, dir), { recursive: true });
  fs.writeFileSync(path.join(to, named), content);
  return named;
}

function replaceOnce(html, from, to) {
  if (!html.includes(from)) throw new Error(`index.html no longer refers to ${from}`);
  return html.replace(from, to);
}

fs.rmSync(to, { recursive: true, force: true });
fs.mkdirSync(to);

let html = fs.readFileSync(path.join(from, 'index.html'), 'utf8');
html = replaceOnce(html, '"js/main.js"', `"${hashed('js/main.js')}"`);
html = replaceOnce(html, '"css/style.css"', `"${hashed('css/style.css')}"`);
const v = version();
html = replaceOnce(html, '<title>', `<meta name="glosshub-version" content="${v}">\n  <title>`);
fs.writeFileSync(path.join(to, 'index.html'), html);

console.log(`site/ built, version ${v}`);
