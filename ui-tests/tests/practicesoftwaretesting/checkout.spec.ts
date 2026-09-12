import { test, expect } from "@playwright/test"
import dotenv from "dotenv"

test.describe('Verify add item to cart and confirm payment', () => {
    dotenv.config();
    test.use({storageState: ".auth/customer1.json"});

    test.beforeEach(async ({ page }) => {
        await page.goto(String(process.env.UI_BASE_URL));
    });

    test('Search the product "Thor Hammer" and verify the result in grid', async ({page}) => {
        const products = page.locator('.col-md-9');
        await page.getByPlaceholder('Search').fill('Thor Hammer');
        await page.getByRole('button').getByText('Search').click();
        await expect(products.getByRole('link')).toHaveCount(1);
        await expect(page.locator('[data-test="product-name"]').getByText('Thor Hammer')).toBeVisible();
        await expect(page.getByAltText('Thor Hammer')).toBeVisible();
        await expect(page.getByAltText('Thor Hammer')).toHaveAttribute('src');
        await products.getByRole('link').click();
        await expect(page.locator('input[type=number]')).toHaveValue('1');
        
        const productDescription = ' The legendary Thor Hammer combines premium craftsmanship with raw striking power in a tool that is truly built to last generations. Forged from Uru metal with a hand-wrapped leather grip, this extraordinary 5kg masterpiece delivers unmatched impact force for the most demanding demolition, forging, and heavy construction tasks. The perfectly balanced oversized head ensures every swing transfers maximum energy to the target, while the enlarged striking face covers more surface area than any conventional hammer. Despite its impressive mass, the ergonomic handle design and precise weight distribution allow for surprisingly controlled, accurate swings. This is not merely a tool but a statement piece for those who accept nothing less than the absolute best in their workshop. Comes with a lifetime warranty because true legends never fade. Please note: only one Thor Hammer is permitted per customer.';
        const productPrice = '11.14';
        await expect(page.locator('#description')).toHaveText(productDescription);
        await expect(page.locator('[data-test="unit-price"]')).toHaveText(productPrice);

        const add_to_cart_button = page.locator('[data-test="add-to-cart"]');
        await expect(add_to_cart_button).toHaveText('Add to cart ');
        await add_to_cart_button.click();
        
        expect(page.locator('div').filter({ hasText: 'Product added to shopping' }).nth(2)).toBeTruthy();
        await expect(page.locator('#lblCartCount')).toHaveText('1');

        await page.locator('[data-test="nav-cart"]').click();
        await expect(page.locator('span.product-title')).toHaveText('Thor Hammer ');
        await expect(page.locator('input[type=number]')).toHaveValue('1');
        await expect(page.locator('[data-test="product-price"]')).toHaveText('$11.14');
        await expect(page.locator('[data-test="line-price"]')).toHaveText('$11.14');
        const proceedToCheckoutButton = page.locator('[data-test="proceed-1"]');
        await expect(proceedToCheckoutButton).toHaveText('Proceed to checkout');
        await proceedToCheckoutButton.click();

        await expect(page.locator('.row p')).toHaveText('Hello Jane Doe, you are already logged in. You can proceed to checkout.');
        await page.locator('[data-test="proceed-2"]').click();

        await page.locator('#address').fill('Test street 100');
        await page.locator('#city').fill('Vienna');
        await page.locator('#state').fill('KA');
        await page.locator('#country').fill('Austria');
        await page.locator('#postcode').fill('00000');
        await page.locator('[data-test="proceed-3"]').click();

        const select = page.locator('#payment-method');
        await select.selectOption({label: 'Cash on Delivery'});
        await page.locator('[data-test="finish"]').click();
        await expect(page.locator('.help-block')).toHaveText('Payment was successful');
    });
})

