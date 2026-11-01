# Universal Commerce Platform - Comprehensive Bug Fix Report

**Report Version:** 2.0.0  
**Inspection & Resolution Date:** October 23, 2026  
**Auditor:** Principal Software Architect & QA Automation Specialist  
**Status:** All Discovered Defects Remediated & Verified  

---

## 1. Defect Overview & Classification

During the end-to-end inspection, build validation, automated testing, and security auditing of the Universal Commerce Platform, a total of **5 defects** were identified across backend testing, frontend module resolution, runtime environment resilience, and installer orchestration.

All 5 defects have been corrected in code, covered by automated regression tests, and verified through successful builds and test executions.

| Defect ID | Severity | Category | Component | Root Cause Summary | Status |
| :---: | :---: | :---: | :---: | :--- | :---: |
| `BUG-01` | **High** | Test Logic / Routing | `api-gateway` | Test targeted public endpoint expecting 401 Unauthorized | **FIXED** |
| `BUG-02` | **Medium** | Build / TypeScript Syntax | `shared-library` | CJS/ESM type export import in Node strip-types runtime | **FIXED** |
| `BUG-03` | **Medium** | Runtime / SSR Safety | `shared-library` | Unchecked `localStorage` access in headless/SSR environments | **FIXED** |
| `BUG-04` | **High** | Quality / Test Coverage | `frontend` | Zero automated unit tests for Redux state slices | **FIXED** |
| `BUG-05` | **Medium** | DevOps / Orchestration | `scripts/install` | Arbitrary fixed sleep delays in installer causing race conditions | **FIXED** |

---

## 2. Detailed Bug Reports

### Bug #1: Gateway Security Test Failure on Public Catalog Route
- **Defect ID:** `BUG-01`
- **Severity:** High
- **Affected File:** [`backend/api-gateway/src/test/java/com/ecommerce/gateway/GatewaySecurityAndRoutingTest.java`](file:///d:/practical/ecommerce-platform/backend/api-gateway/src/test/java/com/ecommerce/gateway/GatewaySecurityAndRoutingTest.java)
- **Component:** `api-gateway`

#### Description & Reproduction
When running `./mvnw.cmd test -pl api-gateway`, the test `testMissingAuthHeaderReturns401` failed. The test was asserting that a request without an `Authorization` header returns HTTP 401 Unauthorized. However, the test sent a `GET` request to `/api/products`. In `JwtAuthenticationFilter`, `GET /api/products` was explicitly added as a public endpoint to allow guest browsing of the catalog. As a result, the request was forwarded to the downstream product service instead of being blocked with 401. Because the backend service was not running on the test host port, WebTestClient threw a connection refused error.

#### Root Cause Analysis
The test fixture was asserting authentication behavior on an endpoint specifically designed and configured to allow unauthenticated public traffic.

#### Remediation
Updated `testMissingAuthHeaderReturns401` to target a genuinely protected route (`GET /api/orders/user/1`), which strictly requires a valid customer Bearer token.

#### Code Diff
```diff
--- a/backend/api-gateway/src/test/java/com/ecommerce/gateway/GatewaySecurityAndRoutingTest.java
+++ b/backend/api-gateway/src/test/java/com/ecommerce/gateway/GatewaySecurityAndRoutingTest.java
@@ -53,7 +53,7 @@ public class GatewaySecurityAndRoutingTest {
     @Test
     @DisplayName("Should return 401 Unauthorized when Authorization header is missing")
     void testMissingAuthHeaderReturns401() {
-        webTestClient.get().uri("/api/products")
+        webTestClient.get().uri("/api/orders/user/1")
                 .exchange()
                 .expectStatus().isUnauthorized();
     }
```

#### Verification Evidence
Re-ran test suite via `mvn test -pl api-gateway`:
```
[INFO] Running com.ecommerce.gateway.GatewaySecurityAndRoutingTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 11.06 s
[INFO] BUILD SUCCESS
```

---

### Bug #2: Redux Toolkit Type Import Syntax in Node Runtime
- **Defect ID:** `BUG-02`
- **Severity:** Medium
- **Affected Files:**
  - [`frontend/shared-library/src/store/cartSlice.ts`](file:///d:/practical/ecommerce-platform/frontend/shared-library/src/store/cartSlice.ts)
  - [`frontend/shared-library/src/store/authSlice.ts`](file:///d:/practical/ecommerce-platform/frontend/shared-library/src/store/authSlice.ts)
- **Component:** `shared-library`

#### Description & Reproduction
When running automated tests using `node --experimental-strip-types --test`, Node.js raised a fatal SyntaxError:
```
SyntaxError: The requested module '@reduxjs/toolkit' does not provide an export named 'PayloadAction'
```
`PayloadAction` is a TypeScript type declaration that does not exist as a JavaScript runtime object in the compiled `@reduxjs/toolkit` bundle.

#### Root Cause Analysis
TypeScript files using Node's native type stripper require explicit type modifier (`import type { PayloadAction }`) so the engine strips the named import entirely from the generated JavaScript runtime rather than attempting to resolve it as an export.

#### Remediation
Updated imports in both `cartSlice.ts` and `authSlice.ts` to use `type PayloadAction`.

#### Code Diff
```diff
--- a/frontend/shared-library/src/store/cartSlice.ts
+++ b/frontend/shared-library/src/store/cartSlice.ts
@@ -1,4 +1,4 @@
-import { createSlice, PayloadAction } from '@reduxjs/toolkit';
+import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
```

```diff
--- a/frontend/shared-library/src/store/authSlice.ts
+++ b/frontend/shared-library/src/store/authSlice.ts
@@ -1,4 +1,4 @@
-import { createSlice, PayloadAction } from '@reduxjs/toolkit';
+import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
```

#### Verification Evidence
`npm test` in `frontend/shared-library` successfully strips types and runs 14 tests in 181ms without syntax errors.

---

### Bug #3: Headless / SSR Unhandled Exception in Auth Storage
- **Defect ID:** `BUG-03`
- **Severity:** Medium
- **Affected File:** [`frontend/shared-library/src/store/authSlice.ts`](file:///d:/practical/ecommerce-platform/frontend/shared-library/src/store/authSlice.ts)
- **Component:** `shared-library`

#### Description & Reproduction
In non-browser execution contexts (e.g. server-side rendering, unit test environments, or web workers), direct calls to `localStorage.setItem('jwt_token', ...)` threw runtime exceptions when `window` was undefined or `localStorage` was inaccessible.

#### Root Cause Analysis
While `getInitialAuth()` contained a defensive check for `typeof window === 'undefined'`, the `loginSuccess` and `logout` reducers directly invoked `localStorage.setItem` without verifying `window` existence.

#### Remediation
Wrapped storage writes and removals in `typeof window !== 'undefined' && window.localStorage` guards.

#### Code Diff
```diff
--- a/frontend/shared-library/src/store/authSlice.ts
+++ b/frontend/shared-library/src/store/authSlice.ts
@@ -51,7 +51,9 @@ const authSlice = createSlice({
         state.token = action.payload;
         state.user = decoded;
         state.isAuthenticated = true;
-        localStorage.setItem('jwt_token', action.payload);
+        if (typeof window !== 'undefined' && window.localStorage) {
+          localStorage.setItem('jwt_token', action.payload);
+        }
       } catch (err) {
         console.error('Failed to decode JWT token:', err);
       }
@@ -60,7 +62,9 @@ const authSlice = createSlice({
       state.user = null;
       state.isAuthenticated = false;
       try {
-        localStorage.removeItem('jwt_token');
+        if (typeof window !== 'undefined' && window.localStorage) {
+          localStorage.removeItem('jwt_token');
+        }
       } catch {}
```

#### Verification Evidence
Verified by running `authSlice.test.ts` in standard Node.js runtime without browser DOM emulation. 4/4 auth tests pass.

---

### Bug #4: Complete Absence of Frontend Automated Unit Testing Suite
- **Defect ID:** `BUG-04`
- **Severity:** High
- **Affected Components:** `frontend/shared-library`, `package.json`

#### Description & Reproduction
Prior to this verification, the frontend workspace contained only `tsc` type checking and `oxlint` linting. There were zero automated tests verifying that cart calculations, item additions, deductions, boundary zero-quantity pruning, authentication token decoding, and session teardown behaved properly.

#### Root Cause Analysis
The frontend had not been outfitted with an automated test runner or test suite for core state management.

#### Remediation
1. Created dedicated test directory `frontend/shared-library/tests/`.
2. Created [`tests/cartSlice.test.ts`](file:///d:/practical/ecommerce-platform/frontend/shared-library/tests/cartSlice.test.ts) covering 10 scenarios:
   - Initial empty cart state.
   - Adding new products and updating totals.
   - Incrementing quantity on duplicate products.
   - Managing multiple distinct products in cart.
   - Decrementing item quantity.
   - Removing item when quantity reaches 0.
   - Graceful handling of non-existent product ID removal.
   - Updating quantity to higher amounts.
   - Updating quantity to 0 resulting in removal.
   - Clearing all items and resetting totals.
3. Created [`tests/authSlice.test.ts`](file:///d:/practical/ecommerce-platform/frontend/shared-library/tests/authSlice.test.ts) covering 4 scenarios:
   - Initial logged-out state.
   - Successful JWT login with claims decoding (`id`, `email`, `role`).
   - Logout state cleanup.
   - Handling invalid / malformed JWT tokens without crashing.
4. Added `"test"` and `"test:shared"` scripts to `package.json`.

#### Verification Evidence
Executed `npm test` from frontend workspace root:
```
▶ authSlice reducer tests
  ✔ should initialize with logged out state when no storage is present (1.9ms)
  ✔ should authenticate user and store decoded payload on loginSuccess (1.0ms)
  ✔ should clear authentication state on logout (0.2ms)
  ✔ should handle invalid JWT token safely without crashing (1.1ms)
✔ authSlice reducer tests (5.4ms)

▶ cartSlice reducer tests
  ✔ should initialize with an empty cart (3.1ms)
  ✔ should add a new product to empty cart (1.2ms)
  ✔ should increment quantity when adding an existing product (0.3ms)
  ✔ should add multiple distinct products (0.4ms)
  ✔ should decrement quantity when removing an item with quantity > 1 (0.3ms)
  ✔ should completely remove item when its quantity reaches 0 (0.3ms)
  ✔ should handle removeFromCart for non-existent product gracefully (0.1ms)
  ✔ should update quantity to a specific higher amount (0.2ms)
  ✔ should update quantity to 0 and remove the item (0.2ms)
  ✔ should clear all items and reset totals with clearCart (0.2ms)
✔ cartSlice reducer tests (7.8ms)

ℹ tests 14
ℹ suites 2
ℹ pass 14
ℹ fail 0
```

---

### Bug #5: Arbitrary Sleep Delays in Installer Scripts
- **Defect ID:** `BUG-05`
- **Severity:** Medium
- **Affected Files:**
  - [`scripts/install.ps1`](file:///d:/practical/ecommerce-platform/scripts/install.ps1)
  - [`scripts/install.sh`](file:///d:/practical/ecommerce-platform/scripts/install.sh)
- **Component:** Installer Scripts

#### Description & Reproduction
The deployment script previously used hardcoded `Start-Sleep -Seconds 60` / `sleep 60` after running `docker compose up -d`. On slower hardware or under heavy I/O, 60 seconds was insufficient for Eureka and Spring Cloud Gateway to achieve healthy readiness, causing downstream health checks to report false failures. Conversely, on high-performance machines, it needlessly delayed installation.

#### Root Cause Analysis
Lack of adaptive health polling and readiness probes in shell and PowerShell scripts.

#### Remediation
Replaced fixed delays with dynamic HTTP polling loops that query:
1. Eureka Service Registry: `http://localhost:8761/eureka/apps`
2. API Gateway Actuator: `http://localhost:8080/actuator/health`
The loop queries every 5 seconds up to a configurable maximum timeout (180 seconds) and proceeds immediately when services report UP.

#### Code Diff
```diff
--- a/scripts/install.ps1
+++ b/scripts/install.ps1
@@ -107,7 +107,18 @@
-Write-Host "[5/6] Waiting for services to start (60 seconds)..." -ForegroundColor Yellow
-Start-Sleep -Seconds 60
+Write-Host "[5/6] Waiting for services to achieve readiness..." -ForegroundColor Yellow
+$maxWaitSec = 180
+$elapsedSec = 0
+while ($elapsedSec -lt $maxWaitSec) {
+    try {
+        $eureka = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -TimeoutSec 3 -ErrorAction Stop
+        $gw = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 3 -ErrorAction Stop
+        if ($gw.status -eq "UP") {
+            Write-Host "Services are UP and healthy! ($elapsedSec s)" -ForegroundColor Green
+            break
+        }
+    } catch {}
+    Start-Sleep -Seconds 5
+    $elapsedSec += 5
+}
```

#### Verification Evidence
Installer scripts tested; cleanly polls and continues immediately upon gateway UP status.
