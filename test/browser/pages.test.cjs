// Every page renders without errors, and the navigation works.
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { start, freshPage, open } = require('./helpers.cjs');

describe('pages', () => {
  let env, page;
  before(async () => { env = await start(); page = await freshPage(env, '', { withTexts: true }); });
  after(() => env.close());

  const pages = [
    ['published', 'Texts · GlossHub'], ['mine', 'Texts · GlossHub'], ['guide', 'Guide · GlossHub'],
    ['about', 'About · GlossHub'], ['settings', 'Settings · GlossHub'],
    ['aeneid', 'Aeneid 1.8–11 · GlossHub'],
    ['latin', 'Latin sentences · GlossHub'], ['hebrew', 'Hebrew sentences · GlossHub'],
    ['japanese', 'Japanese sentence · GlossHub'],
  ];
  for (const [hash, title] of pages) {
    it(`#${hash} renders`, async () => {
      await open(page, hash);
      if (title) assert.equal(await page.title(), title);
      assert.equal(await page.locator('main.crash').count(), 0);
      assert.deepEqual(page.errors, []);
    });
  }

  it('the Guide and About pages are placeholders', async () => {
    for (const hash of ['guide', 'about']) {
      await open(page, hash);
      assert.match(await page.locator('main').innerText(), /This page is forthcoming\./, hash);
    }
    assert.match(await page.locator('main').innerText(), /saved only in this browser/);
    assert.equal(await page.locator('main a[href="#settings"]').count(), 1);
  });

  it('the Texts page lists the published texts', async () => {
    await open(page, 'published');
    assert.deepEqual(await page.locator('.library-title').allTextContents(), ['Aeneid 1.8–11']);
  });

  it('texts show their lines', async () => {
    for (const hash of ['aeneid', 'latin', 'hebrew', 'japanese']) {
      await open(page, hash);
      assert.ok(await page.locator('.line').count() > 0, hash);
    }
  });

  it('marks the current section in the navigation bar', async () => {
    await open(page, 'latin');
    assert.deepEqual(await page.locator('.nav-links [aria-current=page]').allTextContents(), ['Texts']);
    await open(page, 'guide');
    assert.deepEqual(await page.locator('.nav-links [aria-current=page]').allTextContents(), ['Guide']);
  });

  it('has no sideways scrolling on a phone', async () => {
    await page.setViewportSize({ width: 390, height: 800 });
    for (const hash of ['published', 'latin', 'hebrew', 'japanese', 'guide', 'settings']) {
      await open(page, hash);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, hash);
    }
    await page.setViewportSize({ width: 1100, height: 900 });
  });

  it('on a first visit from a phone, Display options starts closed', async () => {
    const ctx = await env.browser.newContext({ viewport: { width: 390, height: 800 } });
    const phone = await freshPage(env, 'aeneid', { context: ctx });
    assert.equal(await phone.getByRole('button', { name: /Display options/ }).getAttribute('aria-expanded'), 'false');
    await ctx.close();
  });
});
