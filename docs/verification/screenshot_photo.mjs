import { chromium } from 'playwright';
import fs from 'fs';

const dir = '/tmp/shots';
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } });

// This environment has no AWS account and no Anthropic key, so the two calls
// that need them are answered here. Everything else on the page - the file
// input, the validation, the request sequence, the rendering, the source
// links - is the real application. The pipeline itself is covered by
// PhotoPipelineIntegrationTest against a real S3 API.
await page.route('**/api/photos/upload-url', route => route.fulfill({
  status: 200, contentType: 'application/json',
  body: JSON.stringify({
    photoId: '11111111-2222-3333-4444-555555555555',
    uploadUrl: 'https://s3.example/put', expiresIn: 300 })
}));
await page.route('https://s3.example/put', route => route.fulfill({ status: 200, body: '' }));
await page.route('**/api/photos/*/identify*', route => route.fulfill({
  status: 200, contentType: 'application/json',
  body: JSON.stringify({
    identifiedAs: 'boîte à pizza',
    grounded: true,
    answer: "D'après le guide de tri de Blainville : Papiers et cartons souilles d aliments — Les boites a pizza, assiettes en carton et essuie-tout vont dans le bac brun s ils ne contiennent pas de plastique ou de cire.",
    provider: 'vision+template',
    sources: [{ itemId: 6, name: 'Papiers et cartons souilles d aliments',
                destinationType: 'organic', binColor: 'brown',
                sourceUrl: 'https://blainville.ca/', score: 8.9 }]
  })
}));

await page.goto('http://localhost:5173/tri', { waitUntil: 'networkidle' });
await page.waitForTimeout(500);
await page.screenshot({ path: `${dir}/16-photo-ask.png`, clip: { x: 0, y: 60, width: 1280, height: 520 } });

// A real JPEG, chosen through the real file input.
const jpeg = fs.readFileSync('/tmp/shots/sample.jpg');
await page.setInputFiles('.photo-button input', {
  name: 'pizza-box.jpg', mimeType: 'image/jpeg', buffer: jpeg });

await page.waitForSelector('.photo-ask .assistant-answer', { timeout: 15000 });
await page.waitForTimeout(400);
await page.screenshot({ path: `${dir}/17-photo-answer.png`, clip: { x: 0, y: 60, width: 1280, height: 620 } });
console.log('identified:', (await page.textContent('.photo-identified')).trim());
console.log('answer:', (await page.textContent('.photo-ask .assistant-answer p:nth-of-type(2)')).slice(0, 90));

await browser.close();
console.log('done');
