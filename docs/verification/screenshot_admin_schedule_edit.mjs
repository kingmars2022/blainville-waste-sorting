// The schedule panel: move a collection to a different date and check the
// trilingual note travels with it rather than being blanked by the edit.
import { chromium } from 'playwright';

const base = 'http://localhost:5173';
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 1400 } });

await page.goto(`${base}/connexion`, { waitUntil: 'networkidle' });
await page.fill('input[type=email]', process.env.ADMIN_EMAIL);
await page.fill('input[type=password]', process.env.ADMIN_PASSWORD);
await page.click('button[type=submit]');
await page.waitForURL(`${base}/`, { timeout: 10000 });
const token = await page.evaluate(() =>
  JSON.parse(localStorage.getItem('bienvenue-blainville.auth') ?? '{}').token);

await page.goto(`${base}/admin`, { waitUntil: 'networkidle' });

const panel = page.locator('.admin-panel').filter({ hasText: 'Calendrier des collectes' });
const firstRow = panel.locator('tbody tr').first();
const before = await firstRow.locator('td').allInnerTexts();
console.log('ROW BEFORE', JSON.stringify(before.slice(0, 4)));

await firstRow.getByRole('button', { name: 'Modifier' }).click();
await page.waitForTimeout(300);
await page.screenshot({ path: '/tmp/shots/admin-schedule-edit.png', fullPage: true });

const loaded = await panel.locator('form input, form select').evaluateAll(els => els.map(e => e.value));
console.log('FORM LOADED WITH', JSON.stringify(loaded));

// Move it one day later.
const originalDate = loaded[0];
const moved = new Date(`${originalDate}T00:00:00Z`);
moved.setUTCDate(moved.getUTCDate() + Number(process.env.SHIFT_DAYS ?? 1));
const movedDate = moved.toISOString().slice(0, 10);

await panel.getByLabel('Date').fill(movedDate);
await panel.getByRole('button', { name: 'Enregistrer' }).click();
await page.waitForTimeout(1200);

const after = await panel.locator('tbody tr').first().locator('td').allInnerTexts();
const stillThere = await panel.locator('tbody tr').filter({ hasText: movedDate }).count();
console.log('MOVED TO', movedDate, 'rows matching:', stillThere);

// And the note survived the move: read it back from the API.
const all = await (await fetch('http://localhost:8080/api/admin/collections', {
  headers: { Authorization: `Bearer ${token}` }
})).json();
const row = all.find(e => e.collectionDate === movedDate);
console.log('NOTE AFTER MOVE', JSON.stringify({ fr: row?.noteFr, zh: row?.noteZh, src: row?.sourceUrl }));

// Put it back.
await panel.locator('tbody tr').filter({ hasText: movedDate }).first()
  .getByRole('button', { name: 'Modifier' }).click();
await page.waitForTimeout(300);
await panel.getByLabel('Date').fill(originalDate);
await panel.getByRole('button', { name: 'Enregistrer' }).click();
await page.waitForTimeout(1000);
console.log('RESTORED TO', originalDate);

await browser.close();
