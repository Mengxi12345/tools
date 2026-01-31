/**
 * TimeStore 旧版文章链接（会 404），需改为前端路由
 */
const TIMESTORE_OLD_URL_PATTERN = /timestore\.vip\/post\//i;
const TIMESTORE_DETAIL_URL = 'https://web.timestore.vip/#/time/pages/timeDetail/index?timeid=';

export interface ContentRecord {
  url?: string;
  contentId?: string;
  platform?: { name?: string; type?: string };
}

/**
 * 获取正确的原文链接。TimeStore 旧数据可能存的是 /post/{id}，会 404，
 * 需用 contentId 拼成官方详情页 #/time/pages/timeDetail/index?timeid={id}
 */
export function getContentOriginalUrl(record: ContentRecord): string | undefined {
  const url = record.url?.trim();
  const contentId = record.contentId;
  if (!url) return undefined;
  if (TIMESTORE_OLD_URL_PATTERN.test(url) && contentId) {
    return TIMESTORE_DETAIL_URL + contentId;
  }
  return url;
}

/**
 * 从 content.metadata 解析出 nickName、userAvatar（兼容 string/object）
 */
export function parseContentMetadata(metadata: unknown): { nickName?: string; userAvatar?: string } {
  if (metadata == null) return {};
  let obj: Record<string, unknown>;
  if (typeof metadata === 'string') {
    try {
      obj = JSON.parse(metadata || '{}') as Record<string, unknown>;
    } catch {
      return {};
    }
  } else if (typeof metadata === 'object' && metadata !== null) {
    obj = metadata as Record<string, unknown>;
  } else {
    return {};
  }
  // userAvatar：优先 userAvatar，兼容 ZSXQ 的 author_avatar_url
  const userAvatar =
    typeof obj.userAvatar === 'string' ? obj.userAvatar
    : typeof obj.author_avatar_url === 'string' ? obj.author_avatar_url
    : undefined;
  // nickName：优先 nickName，兼容 ZSXQ 的 talk.owner.alias/name
  let nickName = typeof obj.nickName === 'string' ? obj.nickName : undefined;
  if (!nickName && typeof obj.talk === 'object' && obj.talk !== null) {
    const talk = obj.talk as Record<string, unknown>;
    const owner = talk.owner as Record<string, unknown> | undefined;
    if (owner) {
      nickName = typeof owner.alias === 'string' ? owner.alias : undefined;
      if (!nickName && typeof owner.name === 'string') nickName = owner.name;
    }
  }
  return { nickName, userAvatar };
}
