// Default display options (Settings), transcription delimiters, and the
// free translation's presentation.
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { start, freshPage, open } = require('./helpers.cjs');

describe('default display options', () => {
  let env, page;
  before(async () => { env = await start(); page = await freshPage(env, 'settings'); });
  after(() => env.close());

  const box = () => page.locator('.defaults-box');

  it('offers every kind of content except parallel rows and IPA', async () => {
    const text = await box().innerText();
    for (const label of ['Metrical marks', 'Ruby characters', 'Transliteration', 'Phonemic transcription',
                         'Phonetic transcription', 'Morphemic gloss', 'Parts of speech', 'Informal gloss',
                         'Stem marking', 'Root marking', 'Color coding', 'Free translation', 'Notes']) {
      assert.match(text, new RegExp(label), label);
    }
    assert.doesNotMatch(text, /IPA transcription/);
    assert.doesNotMatch(text, /Marked characters/);
    assert.ok(await box().getByRole('button', { name: 'Reset to the site defaults' }).isDisabled());
  });

  it('groups the settings, in order', async () => {
    const groups = await box().locator('.control-group').evaluateAll(
      els => els.map(e => [e.getAttribute('aria-label'),
                           [...e.querySelectorAll('.group-controls label')]
                             .map(l => l.textContent.trim())]));
    assert.deepEqual(groups.map(g => g[0]), ['Orthography', 'Glossing', 'Scansion', 'Commentary', 'Marking']);
    const orth = groups[0][1].join(' | ');
    assert.ok(/Transliteration.*Phonemic transcription.*Phonetic transcription.*Ruby characters/.test(orth), orth);
    assert.ok(/Stem marking.*Root marking.*Text formatting.*Color coding/.test(groups[4][1].join(' | ')), groups[4][1].join(' | '));
  });

  it('a group switch hides only its own group', async () => {
    await open(page, 'aeneid');
    const scan = await page.locator('.words.scanned').count();
    assert.ok(scan > 0);
    await page.locator('.control-group[aria-label="Glossing"] .group-switch input').uncheck();
    assert.equal(await page.locator('.row.glword').count(), 0);
    assert.equal(await page.locator('.words.scanned').count(), scan);
    await page.getByRole('button', { name: 'Reset display options' }).click();
    await open(page, 'settings');
  });

  it('a default applies to every text', async () => {
    await box().getByLabel('Informal gloss').uncheck();
    await open(page, 'aeneid');
    assert.equal(await page.locator('.row.glword').count(), 0);
    assert.ok(await page.getByRole('button', { name: 'Reset display options' }).isDisabled());
    await open(page, 'settings');
    await box().getByRole('button', { name: 'Reset to the site defaults' }).click();
    await open(page, 'aeneid');
    assert.ok(await page.locator('.row.glword').count() > 0);
  });

  it('IPA follows the phonemic and phonetic defaults', async () => {
    await open(page, 'settings');
    await box().getByLabel('Phonemic transcription').uncheck();
    await box().getByLabel('Phonetic transcription').uncheck();
    await open(page, 'guide-transcriptions');
    const example = page.locator('figure.example').first();
    await example.getByRole('button', { name: /Edit this example/ }).click();
    const input = example.locator('textarea');
    await input.fill('\\ex\n\\text a b\n\\text[ipa] ə bə');
    assert.equal(await example.locator('.row.transcription').count(), 2);  // Guide examples show everything
    await open(page, 'mine');
    await page.getByRole('button', { name: 'New' }).click();
    await page.getByRole('button', { name: 'Blank' }).click();
    await page.waitForFunction(() => /^#d/.test(location.hash));
    await page.getByRole('button', { name: 'Document source' }).click();
    await page.locator('textarea.source').fill('\\title T\n\\ex\n\\text a b\n\\text[ipa] ə bə\n\\ft one two');
    await page.getByRole('button', { name: 'Apply' }).click();
    assert.equal(await page.locator('.row.transcription').count(), 0);
    await open(page, 'settings');
    await box().getByLabel('Phonetic transcription').check();
    await page.goBack();
    await page.waitForSelector('.igt');
    assert.equal(await page.locator('.row.transcription').count(), 2);
    assert.deepEqual(page.errors, []);
  });
});

describe('presentation', () => {
  let env, page;
  before(async () => { env = await start(); page = await freshPage(env, 'japanese', { withTexts: true }); });
  after(() => env.close());

  it('encloses phonemic transcriptions in slashes and phonetic ones in brackets', async () => {
    assert.equal(await page.locator('.row.transcription.phonemic').first().innerText(), '/aozora/');
    assert.equal(await page.locator('.row.transcription.phonetic').first().innerText(), '[aozoɾa]');
  });

  it('shows the free translation without quotation marks', async () => {
    await open(page, 'aeneid');
    const free = await page.locator('.free').first().innerText();
    assert.match(free, /^Muse, recount/);
  });
});
