-- noinspection SqlResolve
CREATE VIEW migration_users AS
SELECT
    u.email,
    u.first_name,
    u.last_name,
    u.birth_date,
    u.postal_code,
    u.city
FROM users u;