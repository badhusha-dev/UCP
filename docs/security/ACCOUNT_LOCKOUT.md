# Account Lockout

Repeated failed sign-ins temporarily lock an account. Added 2026-11-01 (catalog PLT-01).

## Behaviour for users

- After **5** failed sign-in attempts in a row, the account is locked for **15 minutes**.
- While locked, sign-in is refused even with the correct password. The response is HTTP `423 Locked` with the message "Account temporarily locked after repeated failed sign-in attempts. Try again later."
- The lock lifts by itself when the period ends. There is no unlock button yet.
- A successful sign-in resets the count, so occasional typos never add up to a lock.
- The count and the lock are shared by the platform login (`POST /api/users/login`) and the application login (`POST /api/users/login/application`).

## API responses

| Status | When |
|---|---|
| 200 | Correct credentials, account not locked |
| 401 | Wrong password or unknown email (`"Invalid email or password"`) |
| 403 | Correct password, account deactivated |
| 423 | Account locked |

These are listed in the OpenAPI annotations on `AuthController`.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `auth.lockout.max-failed-attempts` | `5` | Consecutive failures that trigger a lock |
| `auth.lockout.duration` | `PT15M` | Lock length, as an ISO-8601 duration or Spring form such as `15m` |

Set them in the user-service configuration (config-repo `user-service.yml`) or as environment variables. Spring Boot drops the dashes, so the variables are `AUTH_LOCKOUT_MAXFAILEDATTEMPTS` and `AUTH_LOCKOUT_DURATION`.

## Data

Migration `V16__account_lockout.sql` adds two columns to `users`:

- `failed_login_attempts INTEGER NOT NULL DEFAULT 0`
- `locked_until TIMESTAMP WITH TIME ZONE`

Existing accounts start unlocked. The fields are not included in any API response.

## Operations

- A lock writes a WARN log: `User {id} locked until {time} after {n} failed sign-in attempts`.
- Until an admin unlock endpoint exists, an operator can unlock an account early with:

  ```sql
  UPDATE users SET locked_until = NULL, failed_login_attempts = 0 WHERE email = '<email>';
  ```

## Known limitations

- No per-IP rate limiting at the gateway yet. An attacker can still try one password against many accounts (catalog PLT-01, second part).
- Unknown emails return 401 slightly faster than wrong passwords, because no password hash is computed. The difference predates this change.
- `POST /api/users/register` already reveals whether an email is registered (409), so the 423 response does not add a new way to discover accounts.
- The migration has not yet been run against Postgres. Tests use H2 with Flyway disabled.
