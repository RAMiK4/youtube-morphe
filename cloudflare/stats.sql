-- Queries for the D1 database "stats" (Cloudflare → Storage & databases → D1 → stats → Console).
-- Paste one query at a time. Days are in UTC.

-- APK downloads of the last 30 days, per file
SELECT path, SUM(count) AS downloads
FROM hits WHERE path LIKE '%apk/%' AND day >= date('now', '-30 days')
GROUP BY path ORDER BY downloads DESC;

-- APK downloads per day (last 14 days)
SELECT day, SUM(count) AS downloads
FROM hits WHERE path LIKE '%apk/%' AND day >= date('now', '-14 days')
GROUP BY day ORDER BY day DESC;

-- Update checks of yesterday, per app and variant (about the installs in use)
SELECT path, count AS checks
FROM hits WHERE path LIKE '%updates/%' AND day = date('now', '-1 day')
ORDER BY checks DESC;

-- Update checks per day (last 14 days), YouTube and YT Music
SELECT day,
       SUM(CASE WHEN path LIKE 'updates/%' THEN count ELSE 0 END) AS youtube,
       SUM(CASE WHEN path LIKE 'music/updates/%' THEN count ELSE 0 END) AS music
FROM hits WHERE path LIKE '%updates/%' AND day >= date('now', '-14 days')
GROUP BY day ORDER BY day DESC;

-- All-time downloads per file
SELECT path, SUM(count) AS downloads FROM hits WHERE path LIKE '%apk/%' GROUP BY path ORDER BY downloads DESC;
