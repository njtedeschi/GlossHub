// Display options (per text), Settings, and how text features are drawn.
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
    assert.match(await page.locator('.line').first().innerText(), /kɯmo/);
    const switches = await page.locator('.display-box').innerText();
    assert.match(switches, /Phonemic transcription/);
    assert.match(switches, /Phonetic transcription/);
  });

  it('draws morpheme breaks, stems and roots from the \\morph rows', async () => {
    await open(page, 'hebrew');
    const line = page.locator('.line').nth(2);
    const katav = line.locator('.word').nth(1);
    assert.equal(await katav.locator('.row.translit').innerText(), 'katav');
    assert.ok(await katav.locator('.row.translit .root-double').count() > 0);
    assert.equal(await katav.locator('.row.glmorph .root-double').innerText(), 'write');
    assert.equal(await line.locator('.word').first().locator('.row.translit').innerText(), 'ha=yeled');
  });

  it('draws an infix in angle brackets, its gloss paired with it', async () => {
    await open(page, 'latin');
    const word = page.locator('.line').nth(4).locator('.word').last();
    assert.equal(await word.locator('.row.text').innerText(), 'vi<n>c-unt.');
    assert.deepEqual(await word.locator('.row.text .sep').allTextContents(), ['<', '>', '-']);
    assert.equal((await word.locator('.row.glmorph').innerText()).toLowerCase(), 'conquer<prs>-3pl');
  });

  it('formats text, carries it into the glosses, and links', async () => {
    await open(page, 'latin');
    // Links are followed when reading (the opening mode is Edit since the test above).
    await page.getByRole('button', { name: 'Read', exact: true }).click();
    const fifth = page.locator('.line').nth(4);
    assert.equal(await fifth.locator('.row.text .fmt-bold').innerText(), 'ās');
    assert.equal(await fifth.locator('.row.glmorph .fmt-bold').innerText(), 'acc.pl');
    const first = page.locator('.line').first();
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

  // Last: it erases the reader's texts, which the tests above open.
  it('erasing everything takes two clicks', async () => {
    await open(page, 'settings');
    await page.getByRole('button', { name: /Erase everything/ }).click();
    await page.getByRole('button', { name: /Click again/ }).click();
    assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), undefined);
    assert.match(await page.locator('main').innerText(), /has been erased/);
  });
});
