# Quality Platform

Automation practice repo containing UI/API test suites and their CI workflows.

```
.
├── .github/workflows     # CI: ui_tests.yml, api_tests.yml, build_and_test.yaml
├── api-tests
└── ui-tests              # Playwright + TypeScript suite
```

## UI Tests (`ui-tests`)

Web and API automation built with [Playwright](https://playwright.dev) and TypeScript against
[practicesoftwaretesting.com](https://practicesoftwaretesting.com) and the Swagger Petstore API.

### Structure

```
ui-tests
├── playwright.config.ts
├── fixtures/stealth.ts              # playwright-extra + stealth plugin fixture
├── .auth/                           # storage state produced by auth.setup.ts
└── tests
    ├── apipetstore
    │   ├── api.petstore.create.spec.ts
    │   └── api.petstore.create.display.del.spec.ts
    └── practicesoftwaretesting
        ├── auth.setup.ts            # API login, saves storage state
        ├── api.spec.ts
        ├── api.challenge.spec.ts
        ├── checkout.spec.ts
        ├── checkout.challenge.spec.ts
        └── thorhammer.spec.ts
```

### Configuration

Projects: `setup` (runs `*.setup.ts`) → `chromium`, `firefox`, `webkit`.
Reporters: `html`, `list`, `github`. Trace is captured on first retry; CI uses 1 retry and 3 workers.

Create `ui-tests/.env`:

```
API_BASE_URL=
UI_BASE_URL=
AUTH_BASE_URL=
PET_STORE_API_BASE_URL=
CUSTOMER_EMAIL=
CUSTOMER_PASSWORD=
```

### Running

```bash
cd ui-tests
npm ci
npx playwright install --with-deps

npx playwright test                          # all projects
npx playwright test --project=chromium       # single browser
npx playwright test tests/practicesoftwaretesting/checkout.spec.ts
npx playwright test --ui                     # UI mode
npx playwright show-report                   # last HTML report
```

### CI

`.github/workflows/ui_tests.yml` runs on push/PR to `main` in the `QA` environment, with a
`fail-fast: false` matrix over `chromium`, `firefox`, `webkit`. Credentials come from
`vars.CUSTOMER_EMAIL` and `secrets.CUSTOMER_PASSWORD`; the HTML report and `test-results/`
are uploaded as per-browser artifacts.
