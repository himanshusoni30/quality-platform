import { test as setup, expect } from "@playwright/test"
import dotenv from 'dotenv';
import path from 'path'
dotenv.config({ path: path.resolve(__dirname, '.env')});

setup("auth setup", async ({ page, context }) => {
    const email = process.env.CUSTOMER_EMAIL;
    const password = process.env.CUSTOMER_PASSWORD;
    if (!email || !password) throw new Error("CUSTOMER_EMAIL / CUSTOMER_PASSWORD env vars are not set");
    const customer01AuthFile = ".auth/customer1.json";

    await page.goto(String(process.env.AUTH_BASE_URL));
    await page.locator('[data-test="email"]').fill(email);
    await page.locator('[data-test="password"]').fill(password);
    await page.locator('[data-test="login-submit"]').click();

    await expect(page.locator('[data-test="nav-menu"]')).toContainText("Jane Doe");
    await context.storageState({path: customer01AuthFile});
});
