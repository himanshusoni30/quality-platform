import { test, expect } from '@playwright/test';
import dotenv from "dotenv";

test.describe('Home Page Tests without login', () => {
    dotenv.config();

    test.beforeEach('Launch browser', async ({page})=>{
        await page.goto(String(process.env.UI_BASE_URL));
    });

    test('Check sign in link.', async ({page}) => {
        await expect(page.locator('[data-test="nav-sign-in"]')).toHaveText('Sign in');
    });

    test.skip("Visual validation of home page without auth", async ({ page }) => {
        await page.waitForLoadState('networkidle');
        await expect(page).toHaveScreenshot(
            "home-page-without-auth.png", 
            {   mask: [page.getByTitle('Practice Software Testing - Toolshop')],
                maskColor: 'light-dark(green, blue)'
            },
        );
    });

    test('Check product page has 9 products in a grid.', async ({page}) => {
        const products = page.locator('.col-md-9');
        await expect(products.getByRole('link')).toHaveCount(9);
    });

    test('Search the product "Thor Hammer" and verify the result in grid', async ({page}) => {
        const products = page.locator('.col-md-9');
        await page.getByPlaceholder('Search').fill('Thor Hammer');
        await page.getByRole('button').getByText('Search').click();
        await expect(products.getByRole('link')).toHaveCount(1);
        // expect(await products.getByRole('link').count()).toBe(9);
        // page.locator('[data-test="product-name"]').getByText('Thor Hammer').click();
        await expect(page.locator('[data-test="product-name"]').getByText('Thor Hammer')).toBeVisible();
        await expect(page.getByAltText('Thor Hammer')).toBeVisible();
        await expect(page.getByAltText('Thor Hammer')).toHaveAttribute('src');
    });
});


test.describe("Home page tests with login using customer01", () => {
    test.use({ storageState: ".auth/customer1.json" });
    dotenv.config();

    test.beforeEach(async ({ page }) => {
        await page.goto(String(process.env.UI_BASE_URL));
    });

    test('Check sign in link.', async ({page}) => {
        await expect(page.locator('[data-test="nav-sign-in"]')).not.toBeVisible();
        await expect(page.locator('[data-test="nav-menu"]')).toContainText("Jane Doe");
    });

    test('Click on Home page link', async  ({page}) => {
        await page.locator('[data-test="nav-home"]').click();
        await expect(page.getByAltText("Banner")).toBeVisible();
    });

    test.skip("Visual validation of home page with auth of customer01", async ({ page }) => {
        await page.waitForLoadState('networkidle');
        await expect(page).toHaveScreenshot(
            "home-page-with-auth.png",
            {
                mask: [page.getByTitle('Practice Software Testing - Toolshop')],
                maskColor: 'light-dark(yellow, orange)'
            }
        );
    });

    test('Check product page has 9 products in a grid.', async ({page}) => {
        const products = page.locator('.col-md-9');
        await expect(products.getByRole('link')).toHaveCount(9);
    });

    test('Search the product "Thor Hammer" and verify the result in grid', async ({page}) => {
        const products = page.locator('.col-md-9');
        await page.getByPlaceholder('Search').fill('Thor Hammer');
        await page.getByRole('button').getByText('Search').click();
        await expect(products.getByRole('link')).toHaveCount(1);
        await expect(page.locator('[data-test="product-name"]').getByText('Thor Hammer')).toBeVisible();
        await expect(page.getByAltText('Thor Hammer')).toBeVisible();
        await expect(page.getByAltText('Thor Hammer')).toHaveAttribute('src');
    });
})
