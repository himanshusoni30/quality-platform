# Quality Platform

Automation practice repo containing UI/API test suites and their CI workflows.

```
.
├── .github/workflows     # CI: ui_tests.yml, api_tests.yml, build_and_test.yaml
├── api-tests
├── api-tests             # Spring Boot app + REST Assured API suite
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

## API Tests (`api-tests`)

A Maven / Spring Boot 4.1 (Java 17) module that ships both the application under test and its
API suite. Tests use REST Assured 6 with JUnit 5 and Datafaker for test data.

### Structure

```
api-tests
├── pom.xml
├── mvnw / mvnw.cmd
├── data/books.json                  # book store seed data
└── src
    ├── main/java/quality/platform/lab
    │   ├── LabApplication.java
    │   ├── controller/              # BookController, TrainController
    │   ├── service/                 # BookService, TrainService
    │   └── dto/                     # Book, Train, Reservation, Passenger, Tier, ...
    ├── main/resources
    │   ├── application.properties
    │   └── trains.json              # train seed data
    └── test/java/quality/platform/lab
        ├── LabApplicationTests.java
        ├── TestDataFiles.java
        ├── book/                    # TestBookEndpoints, TestCrudOpsInBookEntity
        └── train/                   # TrainApiTestBase, TestTrainEndpoints,
                                     # TestReservationEndpoints
```

### Configuration

`src/main/resources/application.properties`:

```
server.port=8080
books.data-file=data/books.json
trains.seed-file=trains.json
reservations.data-file=data/reservations.json
management.endpoints.web.exposure.include=health
```

Tests boot the app on a random port, so no separate server start is needed.

### Running

```bash
cd api-tests
./mvnw -B -ntp test                                  # full suite
./mvnw -B test -Dtest=TestBookEndpoints              # single class
./mvnw -B test -Dtest=TestTrainEndpoints#methodName  # single test
./mvnw surefire-report:report-only                   # HTML report in target/site
./mvnw spring-boot:run                               # run the app on :8080
```

Results land in `api-tests/target/surefire-reports/`.

### CI

`.github/workflows/api_tests.yml` runs on push/PR to `main`: Temurin JDK 17 with Maven caching,
`mvn -B -ntp test`, then `surefire-report:report-only`, uploading the reports as the
`surefire-reports` artifact.

Badges:
[![.github/workflows/ui_tests.yml](https://github.com/himanshusoni30/quality-platform/actions/workflows/ui_tests.yml/badge.svg)](https://github.com/himanshusoni30/quality-platform/actions/workflows/ui_tests.yml)

[![.github/workflows/api_tests.yml](https://github.com/himanshusoni30/quality-platform/actions/workflows/api_tests.yml/badge.svg)](https://github.com/himanshusoni30/quality-platform/actions/workflows/api_tests.yml)

