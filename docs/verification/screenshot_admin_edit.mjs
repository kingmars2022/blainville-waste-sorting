// Drives the admin console the way an administrator would: pick a sorting item,
// click Modifier, change the French name, save, and check the resident-facing
// endpoint agrees - including the examples and seasonal wording that only exist
// in the database since V10/V11 and that a form without those fields would wipe.
import { chromium } from 'playwright';

const dir = '/tmp/shots';
const base = 'http://localhost:5173';

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 1400 } });

await page.goto(`${base}/connexion`, { waitUntil: 'networkidle' });
await page.fill('input[type=email]', process.env.ADMIN_EMAIL);
await page.fill('input[type=password]', process.env.ADMIN_PASSWORD);
await page.click('button[type=submit]');
await page.waitForURL(`${base}/`, { timeout: 10000 });

await page.goto(`${base}/admin`, { waitUntil: 'networkidle' });

// The first sorting item, before anything is touched.
const before = await (await fetch('http://localhost:8080/api/sorting-items')).json();
const target = before[0];
console.log('BEFORE', JSON.stringify({
  id: target.id, name: target.fr.name,
  examples: target.fr.examples, availability: target.fr.availability,
  keywords: target.keywordsFr
}));

// Second panel is the sorting items; its first row's Modifier button.
const sortingPanel = page.locator('.admin-panel').filter({ hasText: 'Articles de tri' });
await sortingPanel.locator('tbody tr').first().getByRole('button', { name: 'Modifier' }).click();

await page.waitForTimeout(300);
await page.screenshot({ path: `${dir}/admin-edit-loaded.png`, fullPage: true });

const nameInput = sortingPanel.locator('input').nth(1); // sourceUrl is 0, nameFr is 1
const loaded = await sortingPanel.locator('form input, form textarea').evaluateAll(
  els => els.map(e => e.value));
console.log('FORM LOADED WITH', JSON.stringify(loaded));

const edited = `${target.fr.name} (verifie)`;
await sortingPanel.getByLabel('Nom francais').fill(edited);
await sortingPanel.getByRole('button', { name: 'Enregistrer' }).click();
await page.waitForTimeout(1200);
await page.screenshot({ path: `${dir}/admin-edit-saved.png`, fullPage: true });

const after = await (await fetch('http://localhost:8080/api/sorting-items')).json();
const saved = after.find(i => i.id === target.id);
console.log('AFTER', JSON.stringify({
  id: saved.id, name: saved.fr.name,
  examples: saved.fr.examples, availability: saved.fr.availability,
  keywords: saved.keywordsFr
}));

const ok =
  saved.fr.name === edited &&
  JSON.stringify(saved.fr.examples) === JSON.stringify(target.fr.examples) &&
  saved.fr.availability === target.fr.availability &&
  JSON.stringify(saved.keywordsFr) === JSON.stringify(target.keywordsFr) &&
  saved.zh.name === target.zh.name;
console.log(ok ? 'RESULT: edit applied, nothing else lost' : 'RESULT: SOMETHING WAS LOST');

// Put it back so the database is left as it was found.
await sortingPanel.locator('tbody tr').first().getByRole('button', { name: 'Modifier' }).click();
await page.waitForTimeout(300);
await sortingPanel.getByLabel('Nom francais').fill(target.fr.name);
await sortingPanel.getByRole('button', { name: 'Enregistrer' }).click();
await page.waitForTimeout(1000);

const restored = await (await fetch('http://localhost:8080/api/sorting-items')).json();
console.log('RESTORED', restored.find(i => i.id === target.id).fr.name);

await browser.close();
