// The reader's data stays safe: failed saves are reported, other tabs'
// saves are adopted rather than overwritten, unreadable data is kept, and
// a page that fails to render is replaced by one that can't.
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { start, freshPage } = require('./helpers.cjs');

const saved = page => page.evaluate(() => localStorage.getItem('glosshub') || '');
const settle = page => page.waitForTimeout(600); // saves wait 300 ms

describe('data safety', () => {
  let env;
  before(async () => { env = await start(); });
  after(() => env.close());

  it('a failed save is reported, and Try again saves', async () => {
    const page = await freshPage(env, 'latin', { withTexts: true });
    await page.evaluate(() => {
      window.realSetItem = Storage.prototype.setItem;
      Storage.prototype.setItem = () => { throw new DOMException('full', 'QuotaExceededError'); };
    });
    await page.locator('.display-box select').nth(1).selectOption('full');
    await settle(page);
    const band = page.locator('.status-band.error');
    assert.match(await band.innerText(), /couldn’t be saved/);
    await page.evaluate(() => { Storage.prototype.setItem = window.realSetItem; });
    await band.getByRole('button', { name: 'Try again' }).click();
    await settle(page);
    assert.equal(await page.locator('.status-band').count(), 0);
    assert.match(await saved(page), /:pos :full/);
    await page.context().close();
  });

  it('a save in another tab is shown here, not overwritten', async () => {
    const a = await freshPage(env, 'latin', { withTexts: true });
    const b = await a.context().newPage();
    b.errors = [];
    b.on('pageerror', e => b.errors.push(e.message));
    await b.goto(env.base + '#latin');
    await b.waitForSelector('.igt');

    await a.getByRole('button', { name: 'Edit', exact: true }).click();
    await a.locator('.igt').nth(1).locator('.row.glword').first().click();
    await a.locator('input.cell-input').fill('FROM-A');
    await a.locator('input.cell-input').press('Enter');
    await settle(a);

    assert.equal(await b.locator('.igt').nth(1).locator('.row.glword').first().innerText(), 'FROM-A');
    assert.match(await b.locator('.status-band.info').innerText(), /another tab/);
    // B took A's data without saving it back, so A hears nothing.
    const after = await saved(a);
    await settle(a);
    assert.equal(await saved(a), after);
    assert.equal(await a.locator('.status-band').count(), 0);

    // And B's next edit doesn't undo A's.
    await b.locator('.display-box select').nth(1).selectOption('full');
    await settle(b);
    assert.match(await saved(b), /FROM-A/);
    assert.deepEqual([...a.errors, ...b.errors], []);
    await a.context().close();
  });

  it('saved data that can’t be read is kept aside, not overwritten', async () => {
    const page = await freshPage(env, 'published');
    await page.evaluate(() => localStorage.setItem('glosshub', '{:docs {"d1" "\\\\title Mine"'));
    await page.reload();
    await page.waitForSelector('.app');
    assert.match(await page.locator('.status-band.error').innerText(), /couldn’t be read/);
    const backups = await page.evaluate(() =>
      Object.keys(localStorage).filter(k => k.startsWith('glosshub-backup-')).map(k => localStorage.getItem(k)));
    assert.deepEqual(backups, ['{:docs {"d1" "\\\\title Mine"']);
    assert.deepEqual(page.errors, []);
    await page.context().close();
  });

  it('a page that fails to render is replaced, and the site recovers', async () => {
    const page = await freshPage(env, 'latin', { withTexts: true });
    // Make rendering a list fail, then open the Row order panel (a list).
    await page.evaluate(() => {
      const create = Document.prototype.createElement;
      window.restoreCreate = () => { Document.prototype.createElement = create; };
      Document.prototype.createElement = function (tag, ...rest) {
        if (tag === 'ol') throw new Error('test failure');
        return create.call(this, tag, ...rest);
      };
    });
    await page.getByRole('button', { name: 'Row order' }).click();
    assert.match(await page.locator('main.crash').innerText(), /Something went wrong[\s\S]*Your texts are safe/);
    await page.evaluate(() => window.restoreCreate());
    await page.getByRole('link', { name: 'Go to Texts' }).click();
    await page.waitForSelector('ul.library');
    assert.equal(await page.locator('main.crash').count(), 0);
    await page.context().close();
  });
});
