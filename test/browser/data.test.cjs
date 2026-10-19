// The reader's data as a whole: exporting everything and importing it into
// another browser, and the footer every page carries.
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { start, freshPage, open } = require('./helpers.cjs');

describe('exporting everything', () => {
  let env, page;
  before(async () => { env = await start(); page = await freshPage(env, 'settings', { withTexts: true }); });
  after(() => env.close());

  it('every page has a feedback link and the version', async () => {
    const footer = page.locator('footer.site-footer');
    assert.equal(await footer.getByRole('link', { name: 'Send feedback' }).getAttribute('href'),
                 'https://github.com/njtedeschi/GlossHub/issues');
    // public/ is the plain release build; the deployed page carries its version.
    assert.match(await footer.innerText(), /Development version/);
  });

  it('exports every text in one file, and imports it into an empty browser', async () => {
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Export everything' }).click(),
    ]);
    assert.match(download.suggestedFilename(), /^glosshub-texts-\d{4}-\d{2}-\d{2}\.gloss\.txt$/);
    const file = await download.path();
    const text = fs.readFileSync(file, 'utf8');
    assert.match(text, /^% GlossHub export, .*: 3 texts\./);

    const other = await freshPage(env, 'settings');
    await other.locator('input[type=file]').first().setInputFiles(file);
    await other.waitForURL(/#mine$/);
    assert.match(await other.locator('.status-band').innerText(), /Imported 3 texts from the file\./);
    assert.equal(await other.locator('main li').count(), 3);
    assert.deepEqual(other.errors, []);
    assert.deepEqual(page.errors, []);
  });
});

describe('a published text that changed under an edit', () => {
  let env, page;
  before(async () => { env = await start(); page = await freshPage(env, 'published'); });
  after(() => env.close());

  it('says so, and the reader can keep their version', async () => {
    // An edited Aeneid whose recorded published version isn't today's.
    const source = fs.readFileSync(require('node:path').join(__dirname, '..', '..', 'texts', 'aeneid.gloss'), 'utf8');
    await page.evaluate(src => localStorage.setItem('glosshub',
      `{:format 1 :order [] :docs {} :overrides {"aeneid" ${JSON.stringify(src)}} :override-bases {"aeneid" 1}}`), source);
    await page.reload();
    await open(page, 'aeneid');
    const notice = page.locator('.notice-box');
    assert.match(await notice.innerText(), /The published version of this text has changed/);
    await notice.getByRole('button', { name: 'Keep my version' }).click();
    assert.match(await page.locator('.notice-box').innerText(), /You’re seeing your edited version/);
    assert.deepEqual(page.errors, []);
  });
});
