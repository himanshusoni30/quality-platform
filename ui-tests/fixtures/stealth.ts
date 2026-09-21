import { test as base, expect, type Browser, type BrowserType } from '@playwright/test';
import { chromium, firefox, webkit } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

const launchers = { chromium, firefox, webkit };

// Apply stealth evasions to every engine, not just chromium.
// user-agent-override needs CDP, so it throws on firefox/webkit — drop it there.
Object.entries(launchers).forEach(([engine, launcher]) => {
  const plugin = stealthPlugin();
  if (engine !== 'chromium') {
    plugin.enabledEvasions.delete('user-agent-override');
    plugin.enabledEvasions.delete('navigator.webdriver');
  }
  launcher.use(plugin);
});

// Chromium-only permissions; firefox/webkit reject them with "Unknown permission".
const CHROMIUM_ONLY_PERMISSIONS = ['clipboard-read', 'clipboard-write', 'storage-access'];

type StealthWorkerFixtures = { stealthBrowser: Browser };

export const test = base.extend<{}, StealthWorkerFixtures>({
  stealthBrowser: [
    async ({}, use, workerInfo) => {
      const engine =
        workerInfo.project.use.defaultBrowserType ??
        (process.env.BROWSER as keyof typeof launchers) ??
        'chromium';
      const launcher = (launchers[engine as keyof typeof launchers] ??
        launchers.chromium) as unknown as BrowserType;

      const browser = await launcher.launch({
        headless: !!process.env.CI,
        // Always an array: stealth's navigator.webdriver evasion mutates options.args.
        args:
          engine === 'chromium'
            ? [
                '--disable-blink-features=AutomationControlled',
                ...(process.env.CI ? ['--no-sandbox', '--disable-dev-shm-usage'] : []),
              ]
            : [],
      });
      await use(browser);
      await browser.close();
    },
    { scope: 'worker' },
  ],

  context: async ({ stealthBrowser, storageState, viewport, permissions, baseURL, locale, userAgent }, use) => {
    const engine = stealthBrowser.browserType().name();
    const context = await stealthBrowser.newContext({
      storageState,
      viewport,
      permissions:
        engine === 'chromium'
          ? permissions
          : permissions?.filter((p) => !CHROMIUM_ONLY_PERMISSIONS.includes(p)),
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
