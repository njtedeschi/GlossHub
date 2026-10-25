// Notes: highlighting a note's words, and where notes are placed.
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { start, freshPage, open } = require('./helpers.cjs');

describe('notes', () => {
  let env, page;
  before(async () => { env = await start(); page = await freshPage(env, 'aeneid', { withTexts: true }); });
  after(() => env.close());

  // A highlighted word's text, without its scansion marks or note number.
  const highlighted = () => page.locator('.word.note-hl').evaluateAll(els =>
    els.map(e => [...e.querySelectorAll('.row.text .pc')].map(pc => pc.textContent).join('')));

  it('clicking a note number highlights its words, and clicking again clears them', async () => {
    await page.locator('.igt').first().locator('sup.note-mark').first().click();
    assert.deepEqual(await highlighted(), ['quō', 'nūmin-e', 'laes-ō']);
    await page.locator('.igt').first().locator('sup.note-mark').first().click();
    assert.deepEqual(await highlighted(), []);
  });

  it('clicking the note itself (when reading) does the same', async () => {
    await page.locator('.igt').first().locator('.notes li').first().click();
    assert.equal((await highlighted()).length, 3);
    await page.locator('.igt').first().locator('.notes li').first().click();
    assert.equal((await highlighted()).length, 0);
  });

  it('a note with several groups highlights all of them', async () => {
    await page.locator('.igt').nth(1).locator('sup.note-mark').first().click();
    assert.deepEqual(await highlighted(), ['tot', 'volv-ere', 'cās-ūs', 'tot', 'adīre', 'labōr-ēs']);
    await page.locator('.igt').nth(1).locator('sup.note-mark').first().click();
  });

  it('notes can be placed below the full text', async () => {
    await page.locator('.display-box select').filter({ has: page.locator('option[value=end]') }).selectOption('end');
    assert.equal(await page.locator('.igt .notes').count(), 0);
    assert.equal(await page.locator('.end-notes .notes li').count(), 4);
    await page.locator('.end-notes .notes li').first().click();
    assert.equal((await highlighted()).length, 3);
  });

  it('notes across IGTs highlight too, starred groups and spans included', async () => {
    await open(page, 'latin');
    const fourth = page.locator('.igt').nth(3);
    const fifth = page.locator('.igt').nth(4);
    // aquam portat in 4 and vīnum bibunt in 5.
    await fourth.locator('sup.note-mark').first().click();
    assert.equal(await page.locator('.word.note-hl').count(), 4);
    await fourth.locator('sup.note-mark').first().click();
    // The note written under 4 about nautae … vīnum in 5, with puella starred.
    assert.equal(await fifth.locator('.notes li').count(), 1);
    await fifth.locator('sup.note-mark').first().click();
    assert.equal(await page.locator('.word.note-hl').count(), 4);
    assert.equal(await fourth.locator('.word.note-hl').count(), 1);
    await fifth.locator('sup.note-mark').first().click();
    assert.deepEqual(page.errors, []);
  });

  it('the Guide’s examples highlight a note’s words too', async () => {
    await open(page, 'guide-notes');
    const example = page.locator('figure.example', { has: page.locator('sup.note-mark') }).first();
    await example.locator('sup.note-mark').first().click();
    assert.ok(await example.locator('.word.note-hl').count() > 0);
    await example.locator('sup.note-mark').first().click();
    assert.equal(await example.locator('.word.note-hl').count(), 0);
    assert.deepEqual(page.errors, []);
  });

  it('a note’s text is edited in its cell, and its quotes in the IGT’s source', async () => {
    await open(page, 'aeneid');
    await page.getByRole('button', { name: 'Reset display options' }).click();
    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    const first = page.locator('.igt').first();
    await first.locator('.notes .note-text').first().click();
    const input = page.locator('.cell-input');
    assert.equal(await input.inputValue(), 'Ablative absolute');
    await input.fill('Ablative absolute: *quō nūmine laesō*');
    await input.press('Enter');
    assert.match(await first.locator('.notes').innerText(), /Ablative absolute: quō nūmine laesō/);
    await first.getByRole('button', { name: 'Edit source' }).click();
    const source = page.locator('textarea.source');
    assert.match(await source.inputValue(), /\\note\[abl-abs\] \{quō nūmine laesō\} Ablative absolute: \*quō nūmine laesō\*/);
    await source.fill((await source.inputValue()).replace('{quō nūmine laesō}', '{quō nūmine laesus}'));
    assert.match(await page.locator('.source-editor .diags').innerText(), /“quō nūmine laesus” matches no words in this IGT/);
    await page.getByRole('button', { name: 'Cancel' }).click();
    assert.deepEqual(page.errors, []);
  });
});
