// Display options (per text), Settings, how text features are drawn, and
// the Guide's live examples. Text features are checked on the fixtures,
// which the reader has as their own texts, not on the Guide's pages.
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { start, freshPage, open } = require('./helpers.cjs');

describe('display options and settings', () => {
  let env, page;
  before(async () => { env = await start(); page = await freshPage(env, 'latin', { withTexts: true }); });
  after(() => env.close());

  const pos = () => page.locator('.display-box select').nth(1);
  const reset = () => page.getByRole('button', { name: 'Reset display options' });

  it('a display change applies to its text only', async () => {
    assert.ok(await reset().isDisabled());
    await pos().selectOption('full');
    assert.ok(await page.locator('.row.pos').count() > 0);
    assert.ok(!(await reset().isDisabled()));
    await open(page, 'hebrew');
    assert.ok(await reset().isDisabled());
    await open(page, 'latin');
    assert.equal(await pos().inputValue(), 'full');
  });

  it('Reset returns the text to the defaults', async () => {
    await reset().click();
    assert.equal(await pos().inputValue(), 'none');
    assert.ok(await reset().isDisabled());
  });

  it('theme, text size and opening mode persist', async () => {
    await open(page, 'settings');
    await page.getByRole('button', { name: 'Dark', exact: true }).click();
    await page.getByRole('button', { name: 'Large', exact: true }).click();
    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    await page.reload();
    await page.waitForSelector('.app');
    assert.deepEqual(await page.evaluate(() => [document.documentElement.dataset.theme, document.documentElement.dataset.textSize]),
                     ['dark', 'large']);
    await open(page, 'latin');
    assert.equal(await page.locator('.mode-switch [aria-pressed=true]').innerText(), 'Edit');
  });

  it('the dark theme sets the same colors whether chosen or the system’s', async () => {
    // The site's colors with the system's `scheme` and the reader's `theme`.
    const colors = async (scheme, theme) => {
      await page.emulateMedia({ colorScheme: scheme });
      return page.evaluate(theme => {
        const root = document.documentElement;
        const before = root.dataset.theme;
        if (theme) root.dataset.theme = theme; else delete root.dataset.theme;
        const style = getComputedStyle(root);
        const own = [...document.styleSheets].filter(sheet => /css\/style\.css$/.test(sheet.href || ''));
        const colors = Object.fromEntries(own.flatMap(sheet => [...sheet.cssRules])
          .filter(r => r.selectorText === ':root').flatMap(r => [...r.style])
          .filter(p => p.startsWith('--')).map(p => [p, style.getPropertyValue(p).trim()]));
        if (before) root.dataset.theme = before; else delete root.dataset.theme;
        return colors;
      }, theme);
    };
    const chosen = await colors('light', 'dark');
    const system = await colors('dark', null);
    await page.emulateMedia({ colorScheme: null });
    assert.ok(Object.keys(chosen).length > 10);
    assert.notDeepEqual(chosen, await colors('light', null));
    assert.deepEqual(chosen, system);
  });

  it('labels the gloss rows by their kind', async () => {
    await open(page, 'aeneid');
    const labels = await page.locator('.display-box').innerText();
    assert.match(labels, /Morphemic gloss/);
    assert.doesNotMatch(labels, /Formal gloss/);
  });

  it('shows transcription rows, each with its own switch', async () => {
    await open(page, 'japanese');
    assert.equal(await page.locator('.row.transcription.phonemic').count(), 6);
    assert.equal(await page.locator('.row.transcription.phonetic').count(), 6);
    assert.match(await page.locator('.igt').first().innerText(), /kɯmo/);
    const switches = await page.locator('.display-box').innerText();
    assert.match(switches, /Phonemic transcription/);
    assert.match(switches, /Phonetic transcription/);
  });

  it('draws morpheme breaks, stems and roots from the \\morph tiers', async () => {
    await open(page, 'hebrew');
    const igt = page.locator('.igt').nth(2);
    const katav = igt.locator('.word').nth(1);
    assert.equal(await katav.locator('.row.translit').innerText(), 'katav');
    assert.ok(await katav.locator('.row.translit .root-double').count() > 0);
    assert.equal(await katav.locator('.row.glmorph .root-double').innerText(), 'write');
    assert.equal(await igt.locator('.word').first().locator('.row.translit').innerText(), 'ha=yeled');
  });

  it('draws an infix in angle brackets, its gloss paired with it', async () => {
    await open(page, 'latin');
    const word = page.locator('.igt').nth(4).locator('.word').last();
    assert.equal(await word.locator('.row.text').innerText(), 'vi<n>c-unt.');
    assert.deepEqual(await word.locator('.row.text .sep').allTextContents(), ['<', '>', '-']);
    assert.equal((await word.locator('.row.glmorph').innerText()).toLowerCase(), 'conquer<prs>-3pl');
  });

  it('shows labels behind a backslash or in Leipzig brackets as labels', async () => {
    await open(page, 'hebrew');
    const wrote = page.locator('.igt').nth(2).locator('.word').nth(1).locator('.row.glmorph');
    assert.equal(await wrote.innerText(), 'write\\pst.3sg.m');
    assert.equal(await wrote.locator('abbr.gram').first().getAttribute('title'), 'past');
    assert.equal(await wrote.locator('abbr.gram').count(), 3);
    await open(page, 'latin');
    const boy = page.locator('.igt').nth(3).locator('.word').first().locator('.row.glmorph');
    assert.equal(await boy.innerText(), 'boy[nom.sg]');
    assert.equal(await boy.locator('abbr.gram').first().getAttribute('title'), 'nominative');
    assert.equal(await boy.locator('abbr.gram').count(), 2);
  });

  it('formats text, carries it into the glosses, and links', async () => {
    await open(page, 'latin');
    // Links are followed when reading (the opening mode is Edit since the test above).
    await page.getByRole('button', { name: 'Read', exact: true }).click();
    const fifth = page.locator('.igt').nth(4);
    assert.equal(await fifth.locator('.row.text .fmt-bold').innerText(), 'ās');
    assert.equal(await fifth.locator('.row.glmorph .fmt-bold').innerText(), 'acc.pl');
    const first = page.locator('.igt').first();
    assert.equal(await first.locator('.word').nth(2).locator('.row.glword .fmt-bold').innerText(), 'the boys');
    assert.equal(await fifth.locator('a.link').first().getAttribute('href'),
                 'https://en.wiktionary.org/wiki/vinco#Latin');
  });

  it('Text formatting turns formatting off in every row', async () => {
    await open(page, 'aeneid');
    assert.ok(await page.locator('.row.text [class*=fmt-]').count() > 0);
    await page.locator('.display-box label.toggle', { hasText: 'Text formatting' }).locator('input').uncheck();
    assert.equal(await page.locator('.words [class*=fmt-]').count(), 0);
    await reset().click();
  });

  it('shows section headings', async () => {
    await open(page, 'hebrew');
    assert.deepEqual(await page.locator('.section-heading').allTextContents(), ['At home', 'At school']);
    assert.deepEqual(page.errors, []);
  });

  it('Guide examples can be edited live and reset', async () => {
    await open(page, 'guide-morphemes-and-glosses');
    const example = page.locator('figure.example').first();
    await example.getByRole('button', { name: /Edit this example/ }).click();
    const input = example.locator('textarea');
    await input.fill((await input.inputValue()).replace('\\glmorph ', '\\glmorph XYZ.'));
    assert.match(await example.locator('.example-result').innerText(), /Unknown abbreviation XYZ/);
    await example.getByRole('button', { name: /Reset/ }).click();
    assert.equal(await example.locator('textarea').count(), 0);
    assert.deepEqual(page.errors, []);
  });

  it('a Guide example gives its source under it', async () => {
    await open(page, 'guide-infixes');
    assert.match(await page.locator('figure.example figcaption').first().innerText(), /Leipzig Glossing Rules/);
  });

  // Last: it erases the reader's texts, which the tests above open.
  it('erasing everything takes two clicks', async () => {
    await open(page, 'settings');
    await page.getByRole('button', { name: /Erase everything/ }).click();
    await page.getByRole('button', { name: /Click again/ }).click();
    assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), undefined);
    assert.match(await page.locator('main').innerText(), /has been erased/);
  });
});
