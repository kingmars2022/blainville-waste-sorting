import { chromium } from 'playwright';

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
const dir = '/tmp/shots';

async function askOn(page, question) {
  await page.fill('.assistant-form input', question);
  await page.click('.assistant-form button');
  await page.waitForSelector('.assistant-answer', { timeout: 15000 });
  await page.waitForTimeout(400);
}

// 1. French, grounded
let page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
await page.goto('http://localhost:5173/tri', { waitUntil: 'networkidle' });
await page.waitForTimeout(400);
await askOn(page, 'Où va une boîte à pizza sale ?');
await page.screenshot({ path: `${dir}/10-assistant-fr.png`, clip: { x: 0, y: 0, width: 1280, height: 760 } });
console.log('FR answer:', (await page.textContent('.assistant-answer p')).slice(0, 120));

// 2. French, refusal (must look different)
await page.fill('.assistant-form input', '');
await askOn(page, 'Quel est le numéro de téléphone du maire ?');
await page.screenshot({ path: `${dir}/11-assistant-refusal.png`, clip: { x: 0, y: 0, width: 1280, height: 700 } });
const ungrounded = await page.locator('.assistant-answer.ungrounded').count();
console.log('refusal styled as ungrounded:', ungrounded === 1);

// 3. Chinese
await page.goto('http://localhost:5173/parametres', { waitUntil: 'networkidle' });
await page.locator('select').nth(0).selectOption('zh');
await page.waitForTimeout(300);
await page.goto('http://localhost:5173/tri', { waitUntil: 'networkidle' });
await page.waitForTimeout(400);
await askOn(page, '废电池怎么处理？');
await page.screenshot({ path: `${dir}/12-assistant-zh.png`, clip: { x: 0, y: 0, width: 1280, height: 760 } });
console.log('ZH answer:', (await page.textContent('.assistant-answer p')).slice(0, 80));

// back to French
await page.goto('http://localhost:5173/parametres', { waitUntil: 'networkidle' });
await page.locator('select').nth(0).selectOption('fr');
await page.waitForTimeout(300);

await browser.close();
console.log('done');
