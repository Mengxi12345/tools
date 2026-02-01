import { useState, useEffect } from 'react';
import { Card, Button, Switch, message, Avatar, Typography, Empty } from 'antd';
import {
  FileTextOutlined,
  UserOutlined,
  AppstoreOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LinkOutlined,
  CalendarOutlined,
  StarFilled,
  EyeOutlined,
} from '@ant-design/icons';
import { contentApi, userApi, platformApi, taskApi, getApiErrorMessage } from '../services/api';
import { getContentOriginalUrl, parseContentMetadata } from '../utils/contentUtils';
import MainLayout from '../components/Layout/MainLayout';
import { useNavigate } from 'react-router-dom';

const { Text } = Typography;

function AuthorColumn({
  col,
  formatFriendlyTime,
  getContentOriginalUrl,
  navigate,
}: {
  col: { authorKey: string; authorName: string; authorAvatar?: string; items: ContentItem[] };
  formatFriendlyTime: (publishedAt: string) => string;
  getContentOriginalUrl: (record: ContentItem) => string | undefined;
  navigate: (path: string) => void;
}) {
  return (
    <>
      <div className="dashboard-author-column__head">
        {col.authorAvatar ? (
          <Avatar src={col.authorAvatar} size={40} className="dashboard-author-column__avatar" />
        ) : (
          <Avatar size={40} icon={<UserOutlined />} className="dashboard-author-column__avatar dashboard-author-column__avatar--default" />
        )}
        <div className="dashboard-author-column__info">
          <span className="dashboard-author-column__name">{col.authorName}</span>
          <span className="dashboard-author-column__count">{col.items.length} 篇</span>
        </div>
      </div>
      <div className="dashboard-author-column__list">
        {col.items.map((record, i) => {
          const originalUrl = getContentOriginalUrl(record);
          return (
            <article key={record.id} className="dashboard-article-card ds-fade-up" style={{ animationDelay: `${i * 0.05}s` }}>
              <h3 className="dashboard-article-card__title">
                <a href={originalUrl || '#'} target="_blank" rel="noopener noreferrer" onClick={(e) => !originalUrl && e.preventDefault()}>
                  {record.title || '无标题'}
                </a>
              </h3>
              <div className="dashboard-article-card__meta">
                <span className="dashboard-article-card__platform">{record.platform?.name ?? '—'}</span>
                <span className="dashboard-article-card__time">{formatFriendlyTime(record.publishedAt)}</span>
                {record.isFavorite && <StarFilled className="dashboard-article-card__star" />}
                <span className="dashboard-article-card__actions">
                  <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/contents/${record.id}`)} className="dashboard-article-card__btn">详情</Button>
                  {originalUrl && (
                    <Button type="text" size="small" icon={<LinkOutlined />} href={originalUrl} target="_blank" rel="noopener noreferrer" className="dashboard-article-card__btn">原文</Button>
                  )}
                </span>
              </div>
            </article>
          );
        })}
      </div>
    </>
  );
}

interface ContentItem {
  id: string;
  title: string;
  body?: string;
  url: string;
  contentId?: string;
  publishedAt: string;
  isRead: boolean;
  isFavorite: boolean;
  platform?: { id: string; name: string };
  user?: { id: string; username: string };
  metadata?: unknown;
}

function Dashboard() {
  const navigate = useNavigate();
  const [stats, setStats] = useState({
    totalContents: 0,
    totalUsers: 0,
    totalPlatforms: 0,
    unreadContents: 0,
  });
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [lastThreeDaysContents, setLastThreeDaysContents] = useState<ContentItem[]>([]);
  const [loading, setLoading] = useState(false);

  /** 最近三天的 startTime/endTime，格式 YYYY-MM-DDTHH:mm:ss 供后端 LocalDateTime 解析 */
  function getLastThreeDaysRange(): { startTime: string; endTime: string } {
    const pad = (n: number) => String(n).padStart(2, '0');
    const end = new Date();
    const start = new Date(end);
    start.setDate(start.getDate() - 3);
    const startStr = `${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())}T00:00:00`;
    const endStr = `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}T23:59:59`;
    return { startTime: startStr, endTime: endStr };
  }

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    setLoading(true);
    try {
      const [contentStatsRes, usersRes, platformsRes, scheduleRes] = await Promise.all([
        contentApi.getStats().catch(() => ({ code: 200, data: { total: 0, unread: 0 } })) as Promise<any>,
        userApi.getAll({ page: 0, size: 1 }).catch(() => ({ code: 200, data: { totalElements: 0 } })) as Promise<any>,
        platformApi.getAll().catch(() => ({ code: 200, data: [] })) as Promise<any>,
        taskApi.getScheduleStatus().catch(() => ({ code: 200, data: null })) as Promise<any>,
      ]);

      setStats({
        totalContents: contentStatsRes?.code === 200 ? (contentStatsRes.data?.total ?? 0) : 0,
        unreadContents: contentStatsRes?.code === 200 ? (contentStatsRes.data?.unread ?? 0) : 0,
        totalUsers: usersRes?.code === 200 ? (usersRes.data?.totalElements ?? 0) : 0,
        totalPlatforms: platformsRes?.code === 200
          ? (Array.isArray(platformsRes.data) ? platformsRes.data.length : (platformsRes.data?.content?.length ?? 0))
          : 0,
      });

      // 与定时任务页统一：有 data 用 data，否则用后端默认 true（无配置视为已启用）
      const enabled =
        scheduleRes?.code === 200 && scheduleRes.data != null
          ? (scheduleRes.data?.isEnabled ?? scheduleRes.data === true)
          : true;
      setScheduleEnabled(Boolean(enabled));

      const { startTime, endTime } = getLastThreeDaysRange();
      const contentsRes: any = await contentApi.getAll({
        page: 0,
        size: 200,
        startTime,
        endTime,
      }).catch(() => ({ code: 0, data: {} }));
      if (contentsRes?.code === 200 && contentsRes.data?.content) {
        setLastThreeDaysContents(Array.isArray(contentsRes.data.content) ? contentsRes.data.content : []);
      } else {
        setLastThreeDaysContents([]);
      }
    } catch (error) {
      console.error('加载仪表盘数据失败', error);
      message.error(getApiErrorMessage(error, '加载仪表盘数据失败'));
    } finally {
      setLoading(false);
    }
  };

  const handleToggleSchedule = async (enabled: boolean) => {
    try {
      const response: any = enabled
        ? await taskApi.enableSchedule()
        : await taskApi.disableSchedule();
      if (response.code === 200) {
        setScheduleEnabled(enabled);
        message.success(enabled ? '定时任务已启用' : '定时任务已禁用');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleRefreshAll = async () => {
    try {
      const usersRes: any = await userApi.getAll({ page: 0, size: 100 });
      if (usersRes.code === 200) {
        const activeUsers = (usersRes.data?.content || []).filter((u: any) => u.isActive);
        let successCount = 0;
        for (const user of activeUsers) {
          try {
            await userApi.fetchContent(user.id);
            successCount++;
          } catch (error) {
            console.error(`刷新用户 ${user.username} 失败`, error);
          }
        }
        message.success(`已提交 ${successCount} 个用户的刷新任务`);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '批量刷新失败'));
    }
  };

  /** 按作者分栏：同一作者的文章归为一栏，栏内按时间倒序；作者按文章数降序 */
  function groupByAuthor(items: ContentItem[]): { authorKey: string; authorName: string; authorAvatar?: string; items: ContentItem[] }[] {
    const map = new Map<string, ContentItem[]>();
    for (const item of items) {
      const key = item.user?.id ?? item.user?.username ?? 'unknown';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(item);
    }
    const result: { authorKey: string; authorName: string; authorAvatar?: string; items: ContentItem[] }[] = [];
    map.forEach((list, authorKey) => {
      const sorted = [...list].sort(
        (a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime()
      );
      const first = sorted[0];
      const { nickName, userAvatar } = parseContentMetadata(first?.metadata);
      result.push({
        authorKey,
        authorName: nickName ?? first?.user?.username ?? '未知作者',
        authorAvatar: userAvatar,
        items: sorted,
      });
    });
    result.sort((a, b) => b.items.length - a.items.length);
    return result;
  }

  /** 友好时间：今天显示 时:分，昨天/前天显示 昨天 14:30，更早显示 1月27日 */
  function formatFriendlyTime(publishedAt: string): string {
    const d = new Date(publishedAt);
    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const yesterdayStart = new Date(todayStart);
    yesterdayStart.setDate(yesterdayStart.getDate() - 1);
    const dayBeforeStart = new Date(yesterdayStart);
    dayBeforeStart.setDate(dayBeforeStart.getDate() - 1);

    const pad = (n: number) => String(n).padStart(2, '0');
    const timeStr = `${pad(d.getHours())}:${pad(d.getMinutes())}`;

    if (d >= todayStart) return timeStr;
    if (d >= yesterdayStart) return `昨天 ${timeStr}`;
    if (d >= dayBeforeStart) return `前天 ${timeStr}`;
    return `${d.getMonth() + 1}月${d.getDate()}日 ${timeStr}`;
  }

  const byAuthor = groupByAuthor(lastThreeDaysContents);
  const heroCol = byAuthor[0];
  const sideCol = byAuthor[1];
  const restCols = byAuthor.slice(2);

  return (
    <MainLayout>
      <div className="dashboard-page">
        {/* 顶部：标题 + 统计 + 操作 */}
        <header className="dashboard-header">
          <div className="dashboard-header__top">
            <h1 className="dashboard-title">仪表盘</h1>
            <span className="dashboard-subtitle">总览与近三日动态</span>
            <Button
              type="primary"
              size="small"
              icon={<ReloadOutlined />}
              onClick={loadDashboardData}
              loading={loading}
              className="dashboard-refresh-btn"
            >
              刷新
            </Button>
          </div>
          <div className="dashboard-header__stats">
            <div className="dashboard-stat-item dashboard-stat-item--total">
              <span className="dashboard-stat-icon"><FileTextOutlined /></span>
              <div>
                <span className="dashboard-stat-value">{stats.totalContents}</span>
                <span className="dashboard-stat-label">总内容</span>
              </div>
            </div>
            <div className="dashboard-stat-item dashboard-stat-item--unread">
              <span className="dashboard-stat-icon"><FileTextOutlined /></span>
              <div>
                <span className="dashboard-stat-value">{stats.unreadContents}</span>
                <span className="dashboard-stat-label">未读</span>
              </div>
            </div>
            <div className="dashboard-stat-item dashboard-stat-item--users">
              <span className="dashboard-stat-icon"><UserOutlined /></span>
              <div>
                <span className="dashboard-stat-value">{stats.totalUsers}</span>
                <span className="dashboard-stat-label">用户</span>
              </div>
            </div>
            <div className="dashboard-stat-item dashboard-stat-item--platforms">
              <span className="dashboard-stat-icon"><AppstoreOutlined /></span>
              <div>
                <span className="dashboard-stat-value">{stats.totalPlatforms}</span>
                <span className="dashboard-stat-label">平台</span>
              </div>
            </div>
            <div className="dashboard-header__actions">
              <div className="dashboard-schedule">
                <Text type="secondary" style={{ fontSize: 12 }}>定时任务</Text>
                <Switch
                  checked={scheduleEnabled}
                  onChange={handleToggleSchedule}
                  checkedChildren={<CheckCircleOutlined />}
                  unCheckedChildren={<CloseCircleOutlined />}
                  size="small"
                />
              </div>
              <Button type="default" size="small" icon={<ReloadOutlined />} onClick={handleRefreshAll}>
                批量刷新
              </Button>
            </div>
          </div>
        </header>

        {/* Bento：最近三天 · 按作者分栏 */}
        <section className="dashboard-section">
          <div className="dashboard-section__head">
            <span className="dashboard-section__title">
              <CalendarOutlined />
              最近三天 · 按作者分栏
            </span>
            {lastThreeDaysContents.length > 0 && (
              <span className="dashboard-section__extra">
                {lastThreeDaysContents.length} 篇 · {byAuthor.length} 位作者
              </span>
            )}
          </div>

          {loading ? (
            <div className="dashboard-skeleton">
              <div className="ds-skeleton dashboard-skeleton__block" style={{ height: 280 }} />
              <div className="ds-skeleton dashboard-skeleton__block" style={{ height: 280 }} />
            </div>
          ) : lastThreeDaysContents.length === 0 ? (
            <div className="dashboard-empty">
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="近三天暂无文章，可尝试批量刷新或调整时间范围"
              />
            </div>
          ) : (
            <>
              {/* Bento 首行：Hero(8) + Side(4)，仅一位作者时 Hero 占满 */}
              <div className="dashboard-bento">
                {heroCol && (
                  <div className={`dashboard-bento__hero ds-card ${!sideCol ? 'dashboard-bento__hero--full' : ''}`}>
                    <AuthorColumn col={heroCol} formatFriendlyTime={formatFriendlyTime} getContentOriginalUrl={getContentOriginalUrl} navigate={navigate} />
                  </div>
                )}
                {sideCol && (
                  <div className="dashboard-bento__side ds-card">
                    <AuthorColumn col={sideCol} formatFriendlyTime={formatFriendlyTime} getContentOriginalUrl={getContentOriginalUrl} navigate={navigate} />
                  </div>
                )}
              </div>
              {/* 其余作者：3 列网格 */}
              {restCols.length > 0 && (
                <div className="dashboard-grid">
                  {restCols.map((col) => (
                    <div key={col.authorKey} className="ds-card dashboard-author-column">
                      <AuthorColumn col={col} formatFriendlyTime={formatFriendlyTime} getContentOriginalUrl={getContentOriginalUrl} navigate={navigate} />
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </section>
      </div>

      <style>{`
        .dashboard-page {
          padding: 0 var(--space-md) var(--space-lg);
          max-width: 1280px;
          margin: 0 auto;
        }

        .dashboard-header {
          margin-bottom: var(--space-lg);
          padding: var(--space-md) var(--space-lg);
          border-radius: var(--radius-lg);
          border: 1px solid var(--color-border);
          background: var(--color-bg-elevated);
        }
        .dashboard-header__top {
          display: flex;
          align-items: center;
          gap: var(--space-md);
          margin-bottom: var(--space-md);
          flex-wrap: wrap;
        }
        .dashboard-title {
          margin: 0;
          font-size: var(--text-h1-size);
          font-weight: var(--text-h1-weight);
          letter-spacing: -0.02em;
          line-height: 1.25;
          color: var(--color-text-primary);
        }
        .dashboard-subtitle {
          font-size: var(--text-caption-size);
          color: var(--color-text-tertiary);
          margin-left: var(--space-xs);
        }
        .dashboard-refresh-btn { margin-left: auto; }

        .dashboard-header__stats {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: var(--space-md) var(--space-lg);
        }
        .dashboard-stat-item {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: var(--space-sm) var(--space-md);
          border-radius: var(--radius-md);
          background: var(--color-bg-card);
          border: 1px solid var(--color-border-light);
          min-width: 0;
        }
        .dashboard-stat-item .dashboard-stat-icon {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 32px;
          height: 32px;
          border-radius: var(--radius-sm);
          font-size: 14px;
          flex-shrink: 0;
        }
        .dashboard-stat-item .dashboard-stat-value {
          font-size: var(--text-h3-size);
          font-weight: 700;
          line-height: 1.2;
          display: block;
          color: var(--color-text-primary);
        }
        .dashboard-stat-item .dashboard-stat-label {
          font-size: var(--text-caption-size);
          color: var(--color-text-tertiary);
          font-weight: 500;
        }
        .dashboard-stat-item--total .dashboard-stat-icon { background: var(--color-primary-light); color: var(--color-primary); opacity: 0.9; }
        .dashboard-stat-item--unread .dashboard-stat-icon { background: var(--color-accent-light); color: var(--color-accent); opacity: 0.9; }
        .dashboard-stat-item--users .dashboard-stat-icon { color: var(--color-primary); opacity: 0.9; }
        .dashboard-stat-item--platforms .dashboard-stat-icon { color: var(--color-accent); opacity: 0.9; }
        .dashboard-header__actions {
          display: flex;
          align-items: center;
          gap: var(--space-md);
          margin-left: auto;
        }
        .dashboard-schedule { display: flex; align-items: center; gap: var(--space-sm); }
        .dashboard-schedule .ant-switch { margin: 0; }

        .dashboard-section {
          margin-top: var(--space-xl);
        }
        .dashboard-section__head {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: var(--space-lg);
          padding: 0 var(--space-xs);
        }
        .dashboard-section__title {
          display: inline-flex;
          align-items: center;
          gap: var(--space-sm);
          font-size: var(--text-h2-size);
          font-weight: var(--text-h2-weight);
          color: var(--color-text-primary);
        }
        .dashboard-section__title .anticon { color: var(--color-primary); }
        .dashboard-section__extra {
          font-size: var(--text-caption-size);
          color: var(--color-text-tertiary);
        }

        .dashboard-bento {
          display: grid;
          grid-template-columns: repeat(12, 1fr);
          gap: var(--space-lg);
          margin-bottom: var(--space-lg);
        }
        .dashboard-bento__hero {
          grid-column: span 8;
          padding: 0;
          overflow: hidden;
          display: flex;
          flex-direction: column;
        }
        .dashboard-bento__hero--full { grid-column: span 12; }
        .dashboard-bento__hero .dashboard-author-column__list { max-height: min(520px, 50vh); }
        .dashboard-bento__side {
          grid-column: span 4;
          padding: 0;
          overflow: hidden;
          display: flex;
          flex-direction: column;
        }
        .dashboard-bento__side .dashboard-author-column__list { max-height: min(520px, 50vh); }

        .dashboard-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
          gap: var(--space-lg);
        }
        .dashboard-grid .dashboard-author-column {
          padding: 0;
          overflow: hidden;
          display: flex;
          flex-direction: column;
        }
        .dashboard-grid .dashboard-author-column__list { max-height: min(400px, 40vh); }

        .dashboard-skeleton {
          display: grid;
          grid-template-columns: 2fr 1fr;
          gap: var(--space-lg);
        }
        .dashboard-skeleton__block { border-radius: var(--radius-lg); }

        .dashboard-empty {
          padding: var(--space-2xl) var(--space-lg);
          text-align: center;
        }
        .dashboard-empty .ant-empty-description { color: var(--color-text-tertiary); font-size: var(--text-body-sm-size); }

        .dashboard-author-column__head {
          display: flex;
          align-items: center;
          gap: var(--space-md);
          padding: var(--space-md) var(--space-lg);
          background: var(--color-bg-elevated);
          border-bottom: 1px solid var(--color-border);
        }
        .dashboard-author-column__avatar { flex-shrink: 0; }
        .dashboard-author-column__avatar--default { background: var(--color-border) !important; color: var(--color-text-tertiary) !important; }
        .dashboard-author-column__info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
        .dashboard-author-column__name {
          font-size: var(--text-body-sm-size);
          font-weight: 600;
          color: var(--color-text-primary);
          line-height: 1.3;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .dashboard-author-column__count {
          font-size: var(--text-caption-size);
          color: var(--color-text-tertiary);
        }
        .dashboard-author-column__list {
          display: flex;
          flex-direction: column;
          gap: var(--space-sm);
          padding: var(--space-md);
          overflow-y: auto;
        }
        .dashboard-author-column__list::-webkit-scrollbar { width: 6px; }
        .dashboard-author-column__list::-webkit-scrollbar-track { background: var(--color-border-light); border-radius: 3px; }
        .dashboard-author-column__list::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 3px; }

        .dashboard-article-card {
          padding: var(--space-md);
          border-radius: var(--radius-md);
          border: 1px solid var(--color-border-light);
          background: var(--color-bg-card);
          transition: border-color var(--transition-normal), box-shadow var(--transition-normal);
        }
        .dashboard-article-card:hover {
          border-color: var(--color-primary-light);
          box-shadow: var(--shadow-card-hover);
        }
        .dashboard-article-card__title {
          margin: 0 0 var(--space-sm);
          font-size: var(--text-body-sm-size);
          font-weight: 600;
          line-height: 1.45;
        }
        .dashboard-article-card__title a {
          color: var(--color-text-primary);
          transition: color var(--transition-fast);
          text-decoration: none;
        }
        .dashboard-article-card__title a:hover { color: var(--color-primary); }
        .dashboard-article-card__meta {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: var(--space-sm);
          font-size: var(--text-caption-size);
          color: var(--color-text-tertiary);
        }
        .dashboard-article-card__platform {
          font-size: 11px;
          padding: 2px 6px;
          border-radius: var(--radius-sm);
          background: var(--color-bg-elevated);
          color: var(--color-text-secondary);
        }
        .dashboard-article-card__time { font-size: 11px; }
        .dashboard-article-card__star { color: var(--color-accent); font-size: 12px; flex-shrink: 0; }
        .dashboard-article-card__actions {
          display: inline-flex;
          align-items: center;
          gap: 2px;
          margin-left: auto;
        }
        .dashboard-article-card__btn.ant-btn {
          color: var(--color-text-tertiary);
          font-size: 11px;
          padding: 0 2px;
        }
        .dashboard-article-card__btn.ant-btn:hover { color: var(--color-primary); }
      `}</style>
    </MainLayout>
  );
}

export default Dashboard;
