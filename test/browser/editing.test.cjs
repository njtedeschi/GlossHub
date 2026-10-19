// Editing a text: cells, undo/redo, source editors, Read mode, new texts.
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { start, freshPage, open } = require('./helpers.cjs');

describe('editing', () => {
  let env, page;
  before(async () => {
    env = await start();
    page = await freshPage(env, 'latin', { withTexts: true });
    await page.getByRole('button', { name: 'Edit', exact: true }).click();
  });
  after(() => env.close());

  const line = i => page.locator('.line').nth(i);

  it('edits a gloss cell; Tab moves on, Escape stops', async () => {
    await line(0).locator('.row.glmorph').nth(2).click();
    const input = page.locator('input.cell-input');
    assert.equal(await input.inputValue(), 'boy-NOM.PL');
    assert.ok(await input.evaluate(e => e === document.activeElement));
    await input.fill('boy-NOM-PL');
    await input.press('Tab');
    assert.equal(await page.locator('input.cell-input').inputValue(), 'laugh-3PL.PRS');
    await page.keyboard.press('Escape');
    assert.match((await line(0).locator('.diags').innerText()), /has 2 morpheme\(s\) but its gloss .* has 3/);
    assert.match((await line(0).locator('.diags li').first().innerText()), /^(Error|Warning)\b/);
  });

  it('undoes and redoes', async () => {
    await page.getByRole('button', { name: 'Undo' }).click();
    assert.equal(await line(0).locator('.diags li').count(), 0);
    await page.getByRole('button', { name: 'Redo' }).click();
    assert.equal(await line(0).locator('.diags li').count(), 1);
    await page.getByRole('button', { name: 'Undo' }).click();
  });

  it('edits a line as source, with a live preview', async () => {
    await line(0).hover();
    await line(0).getByRole('button', { name: 'Edit source' }).click();
    const source = page.locator('textarea.source');
    await source.fill((await source.inputValue()).replace('The sailor sings', 'THE SAILOR sings'));
    assert.match(await page.locator('.preview').innerText(), /THE SAILOR/);
    await page.getByRole('button', { name: 'Apply' }).click();
    assert.match(await line(0).locator('.free').innerText(), /THE SAILOR sings/);
  });

  it('opens the whole document as source', async () => {
    await page.getByRole('button', { name: 'Document source' }).click();
    assert.match(await page.locator('textarea.source').inputValue(), /^\\glosshubformat 1\n\\title Latin sentences[\s\S]*\\text Nauta canit,[^\n]*\n\\morph Naut-a/);
    await page.getByRole('button', { name: 'Cancel' }).click();
  });

  it('points each problem in a source editor at its source line', async () => {
    await page.getByRole('button', { name: 'Document source' }).click();
    const source = page.locator('textarea.source');
    await source.fill('\\title T\n\\ex\n\\text a\n\\glmorph x y');
    const link = page.getByRole('button', { name: 'Line 4' });
    await link.click();
    const selected = await source.evaluate(t => t.value.slice(t.selectionStart, t.selectionEnd));
    assert.equal(selected, '\\glmorph x y');
    await page.getByRole('button', { name: 'Cancel' }).click();
  });

  it('fills a \\morph row from the words', async () => {
    await page.getByRole('button', { name: '+ Add line' }).click();
    const source = page.locator('textarea.source');
    await source.fill('\\ex\n\\text Arma virumque, canō');
    await page.getByRole('button', { name: '\\morph', exact: true }).click();
    assert.equal(await source.inputValue(), '\\ex\n\\text Arma virumque, canō\n\\morph Arma virumque canō');
    assert.ok(await page.getByRole('button', { name: '\\morph', exact: true }).isDisabled());
    await page.getByRole('button', { name: 'Cancel' }).click();
  });

  it('adds a line after a given line, with its own editor there', async () => {
    const before = await page.locator('.line').count();
    await line(0).getByRole('button', { name: '+ Line' }).click();
    const editor = page.locator('.line.editing');
    assert.equal(await editor.locator('.label').innerText(), 'New line');
    // A new line starts with only \ex and \text.
    assert.equal(await editor.locator('textarea').inputValue(), '\\ex\n\\text ');
    // The editor stands right after the first line.
    assert.ok(await page.locator('.line').nth(1).evaluate(e => e.classList.contains('editing')));
    await editor.locator('textarea').fill('\\ex\n\\text Inserted words');
    await page.getByRole('button', { name: 'Apply' }).click();
    assert.equal(await page.locator('.line').count(), before + 1);
    assert.match(await line(1).innerText(), /Inserted/);
    await page.getByRole('button', { name: 'Undo', exact: true }).click();
    assert.equal(await page.locator('.line').count(), before);
  });

  it('keeps edits after a reload', async () => {
    await line(1).locator('.row.glword').first().click();
    await page.locator('input.cell-input').fill('EDITED');
    await page.locator('input.cell-input').press('Enter');
    await page.reload();
    await page.waitForSelector('.line');
    assert.equal(await line(1).locator('.row.glword').first().innerText(), 'EDITED');
  });

  it('keeps edits to a published text as the reader’s version', async () => {
    await open(page, 'aeneid');
    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    await page.locator('.line').nth(1).locator('.row.glword').first().click();
    await page.locator('input.cell-input').fill('EDITED');
    await page.locator('input.cell-input').press('Enter');
    assert.match(await page.locator('.notice-box').innerText(), /edited version/);
    await page.reload();
    await page.waitForSelector('.line');
    assert.equal(await page.locator('.line').nth(1).locator('.row.glword').first().innerText(), 'EDITED');
    await page.getByRole('button', { name: 'Discard my changes' }).click();
    await page.getByRole('button', { name: 'Click again to discard' }).click();
    assert.notEqual(await page.locator('.line').nth(1).locator('.row.glword').first().innerText(), 'EDITED');
    await open(page, 'latin');
  });

  it('a document change reaches every line', async () => {
    await page.getByRole('button', { name: 'Edit', exact: true }).click();
    const color = () => page.locator('.line').last().locator('[style*=color]').first().getAttribute('style');
    const before = await color();
    await page.getByRole('button', { name: 'Document source' }).click();
    const source = page.locator('textarea.source');
    await source.fill((await source.inputValue()).replace(/\\color (\S+) #\w+/, '\\color $1 #00AA00'));
    await page.getByRole('button', { name: 'Apply' }).click();
    assert.match(await color(), /rgb\(0, 170, 0\)/);
    await page.getByRole('button', { name: 'Undo' }).click();
    assert.equal(await color(), before);
  });

  it('Read mode shows no editing controls', async () => {
    await page.getByRole('button', { name: 'Read', exact: true }).click();
    assert.equal(await page.locator('.editable').count(), 0);
  });

  it('a new blank text opens in Edit', async () => {
    await open(page, 'mine');
    await page.getByRole('button', { name: 'New' }).click();
    await page.getByRole('button', { name: 'Blank' }).click();
    await page.waitForFunction(() => /^#d/.test(location.hash));
    assert.equal(await page.locator('.mode-switch [aria-pressed=true]').innerText(), 'Edit');
    // It has no lines yet, only the button to add one.
    assert.equal(await page.locator('.line').count(), 0);
    assert.ok(await page.getByRole('button', { name: '+ Add line' }).isVisible());
    assert.deepEqual(page.errors, []);
  });
});
