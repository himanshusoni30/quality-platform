import { test as base, expect, type Browser } from '@playwright/test';
import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(stealthPlugin());

type StealthWorkerFixtures = { stealthBrowser: Browser };

export const test = base.extend<{}, StealthWorkerFixtures>({
  stealthBrowser: [
    async ({}, use) => {
      const browser = await chromium.launch({
        headless: !!process.env.CI,
        args: [
          '--disable-blink-features=AutomationControlled',
          ...(process.env.CI ? ['--no-sandbox', '--disable-dev-shm-usage'] : []),
        ],
      });
      await use(browser);
      await browser.close();
    },
    { scope: 'worker' },
  ],

  context: async ({ stealthBrowser, storageState, viewport, permissions, baseURL, locale, userAgent }, use) => {
    const context = await stealthBrowser.newContext({
      storageState,
      viewport,
      permissions,
      baseURL,
      locale,
      userAgent,
    });
    await use(context);
    await context.close();
  },

  page: async ({ context }, use) => {
    await use(await context.newPage());
  },
});

export { expect };
