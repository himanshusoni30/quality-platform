import { test as setup, expect, request as playwrightRequest } from "@playwright/test";
import fs from "fs";
import path from "path";

// Auth is a plain `auth-token` entry in localStorage, so the storage state can be
// minted straight from the API. Logging in through the UI puts Cloudflare's bot
// challenge on the critical path, which blocks firefox/webkit on CI runner IPs.
setup("auth setup", async () => {
    const email = process.env.CUSTOMER_EMAIL;
    const password = process.env.CUSTOMER_PASSWORD;
    if (!email || !password) throw new Error("CUSTOMER_EMAIL / CUSTOMER_PASSWORD env vars are not set");
    const customer01AuthFile = ".auth/customer1.json";

    const api = await playwrightRequest.newContext();
    const response = await api.post(process.env.API_BASE_URL + "/users/login", {
        data: { email, password },
    });
    expect(response.status(), await response.text()).toBe(200);
    const { access_token } = await response.json();
    expect(access_token).toBeTruthy();
    await api.dispose();

    const storageState = {
        cookies: [],
        origins: [
            {
                origin: String(process.env.UI_BASE_URL),
                localStorage: [{ name: "auth-token", value: access_token }],
            },
        ],
    };

    fs.mkdirSync(path.dirname(customer01AuthFile), { recursive: true });
    fs.writeFileSync(customer01AuthFile, JSON.stringify(storageState, null, 2));
});
