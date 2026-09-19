import { chromium } from 'playwright';
import fs from 'fs';

const planJson = JSON.parse(fs.readFileSync('/tmp/plan.json', 'utf8'));
const planId = fs.readFileSync('/tmp/plan_id.txt', 'utf8').trim();
const dir = '/tmp/shots';

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } });

// ONLY the planning call is intercepted - no Anthropic key is configured here,
// so there is no model to produce one. Everything downstream (the approval
// POST, the plan lookup in Redis, the writes) hits the real backend.
await page.route('**/api/admin/agent/plan', route => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({
    planId,
    instruction: planJson.instruction,
    narrative: planJson.narrative,
    steps: planJson.steps.map(s => ({ tool: s.tool, summary: s.summary }))
  })
}));

await page.goto('http://localhost:5173/connexion', { waitUntil: 'networkidle' });
await page.fill('input[type=email]', 'admin@blainville.local');
await page.fill('input[type=password]', 'AdminDevPass123!');
await page.click('button[type=submit]');
await page.waitForURL('http://localhost:5173/', { timeout: 8000 });

await page.goto('http://localhost:5173/admin', { waitUntil: 'networkidle' });
await page.waitForTimeout(600);

await page.fill('.agent-form textarea', planJson.instruction);
await page.click('.agent-form button');
await page.waitForSelector('.agent-plan', { timeout: 10000 });
await page.waitForTimeout(300);
await page.screenshot({ path: `${dir}/13-agent-plan.png`, clip: { x: 0, y: 0, width: 1280, height: 820 } });
console.log('steps rendered:', await page.locator('.agent-steps li').count());

// Real approval against the real backend.
await page.click('.agent-actions button');
await page.waitForSelector('.agent-result', { timeout: 15000 });
await page.waitForTimeout(500);
await page.screenshot({ path: `${dir}/14-agent-applied.png`, clip: { x: 0, y: 0, width: 1280, height: 780 } });
console.log('result text:', (await page.textContent('.agent-result')).replace(/\s+/g, ' ').slice(0, 200));

await browser.close();
console.log('done');
