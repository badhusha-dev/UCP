-- Existing versions stored unverified passwords in plain text. Force affected accounts
-- through the account recovery process instead of treating those values as credentials.
UPDATE users
SET active = FALSE,
    password = '!disabled-seeded-account!'
WHERE password NOT LIKE '$2%';
