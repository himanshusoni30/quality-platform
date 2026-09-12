import { test, expect } from "@playwright/test"
import dotenv from "dotenv"

test.describe("Challenge - API test suite", () => {
    dotenv.config();
    
    test.use({ignoreHTTPSErrors: true,});

    test("GET /products", async ({request}) => {
        const base_url = process.env.API_BASE_URL;
        const productName = 'Thor Hammer';
        
        const response = await request.get(base_url + '/products/search', {
            params: {'q': productName}
        });
        expect(response.status()).toBe(200);
        const body = await response.json();
        expect(body.data[0].name).toBe(productName);
        const id = body.data[0].id;

        const base_url_with_product_id = base_url + '/products/' + id;
        const resp = await request.get(base_url_with_product_id);
        const respBody = await resp.json();
        expect(resp.status()).toBe(200);
        expect(respBody.id).toBe(id);
        expect(respBody.name).toBe(productName);
    });
});
