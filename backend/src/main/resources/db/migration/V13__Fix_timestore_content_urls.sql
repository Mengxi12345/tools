-- 将 TimeStore 平台中旧版会 404 的文章链接（/post/{id}）修正为官方详情页
UPDATE contents c
SET url = 'https://web.timestore.vip/#/time/pages/timeDetail/index?timeid=' || c.content_id,
    updated_at = CURRENT_TIMESTAMP
FROM platforms p
WHERE c.platform_id = p.id
  AND p.type = 'TIMESTORE'
  AND c.url LIKE '%/post/%';
