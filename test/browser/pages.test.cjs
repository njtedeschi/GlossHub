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
    ['guide-scansion', 'Scansion · Guide · GlossHub'],
  ];
  for (const [hash, title] of pages) {
    it(`#${hash} renders`, async () => {
      await open(page, hash);
      if (title) assert.equal(await page.title(), title);
      assert.equal(await page.locator('main.crash').count(), 0);
      assert.deepEqual(page.errors, []);
    });
  }

  it('the Texts page lists the published texts', async () => {
    await open(page, 'published');
    assert.deepEqual(await page.locator('.library-title').allTextContents(), ['Aeneid 1.8–11']);
  });

  it('texts show their IGTs', async () => {
    for (const hash of ['aeneid', 'latin', 'hebrew', 'japanese']) {
      await open(page, hash);
      assert.ok(await page.locator('.igt').count() > 0, hash);
    }
  });

  it('the Guide lists its pages by group, and each page links to the rest of its group', async () => {
    await open(page, 'guide');
    assert.equal(await page.locator('.guide-group h2').first().innerText(), 'Getting started');
    await page.locator('.guide-pages a', { hasText: 'Infixes' }).click();
    await page.waitForFunction(() => location.hash === '#guide-infixes');
    assert.match(await page.locator('.guide-crumb').innerText(), /Interlinear glossing/);
    const more = await page.locator('.guide-more a').allTextContents();
    assert.ok(more.includes('Stems and roots'));
    assert.ok(!more.includes('Infixes'));
    await open(page, 'guide-scansion');  // the only page in its group
    assert.equal(await page.locator('.guide-more').count(), 0);
    assert.deepEqual(page.errors, []);
  });

  it('the input format reference under a text links to the Guide’s Commands page', async () => {
    await open(page, 'latin');
    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    await page.locator('details.help summary').click();
    await page.locator('details.help').getByRole('link', { name: 'Commands' }).click();
    // The address changes first; the page is drawn on the hashchange event
    // that follows, so wait for its heading.
    await page.locator('h1.title', { hasText: 'Commands' }).waitFor();
    assert.equal(await page.evaluate(() => location.hash), '#guide-commands');
    assert.equal(await page.title(), 'Commands · Guide · GlossHub');
    await open(page, 'latin');
    await page.getByRole('button', { name: 'Read', exact: true }).click();
  });

  it('Tiers and rows lays the source out on a grid, a row for each tier', async () => {
    await open(page, 'guide-tiers-and-rows');
    const grid = page.locator('figure.tier-grid').nth(1);
    assert.deepEqual(await grid.locator('th').allTextContents(), ['\\text', '\\morph', '\\syl', '\\glmorph']);
    assert.deepEqual(await grid.locator('tr').nth(1).locator('td').allTextContents(), ['Arma', 'vir-um=que', 'can-ō']);
    // Grids aren't live examples.
    assert.equal(await page.locator('figure.example').count(), 3);
    assert.deepEqual(page.errors, []);
  });

  it('About opens its link out of the site in a new tab', async () => {
    await open(page, 'about');
    const leipzig = page.locator('main a', { hasText: 'Leipzig Glossing Rules' });
    assert.equal(await leipzig.getAttribute('target'), '_blank');
    assert.equal(await leipzig.getAttribute('rel'), 'noopener');
  });

  it('marks the current section in the navigation bar', async () => {
    await open(page, 'latin');
    assert.deepEqual(await page.locator('.nav-links [aria-current=page]').allTextContents(), ['Texts']);
    await open(page, 'guide-sublines');
    assert.deepEqual(await page.locator('.nav-links [aria-current=page]').allTextContents(), ['Guide']);
  });

  it('has no sideways scrolling on a phone', async () => {
    await page.setViewportSize({ width: 390, height: 800 });
    for (const hash of ['published', 'latin', 'hebrew', 'japanese', 'guide-morphemes-and-glosses', 'settings']) {
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
