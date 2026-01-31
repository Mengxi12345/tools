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

  return (
    <MainLayout>
      <div className="dashboard-page">
        {/* 顶部紧凑区：标题 + 统计 + 操作 一行/紧凑排布 */}
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

        {/* 最近三天文章 - 按作者分栏，主内容区占更多空间 */}
        <Card
          className="dashboard-articles-card"
          title={
            <span className="dashboard-articles-title">
              <CalendarOutlined />
              最近三天 · 按作者分栏
            </span>
          }
          extra={
            lastThreeDaysContents.length > 0 && (
              <span className="dashboard-articles-extra">
                <span className="dashboard-articles-extra__num">{lastThreeDaysContents.length}</span> 篇
                <span className="dashboard-articles-extra__divider">·</span>
                <span className="dashboard-articles-extra__num">{byAuthor.length}</span> 位作者
              </span>
            )
          }
          loading={loading}
          bordered={false}
        >
          {lastThreeDaysContents.length === 0 && !loading ? (
            <div className="dashboard-empty">
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="近三天暂无文章，可尝试批量刷新或调整时间范围"
              />
            </div>
          ) : (
            <div className="dashboard-articles-by-author">
              {byAuthor.map((col) => (
                <div key={col.authorKey} className="dashboard-author-column">
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
                    {col.items.map((record) => {
                      const originalUrl = getContentOriginalUrl(record);
                      return (
                        <article key={record.id} className="dashboard-article-card">
                          <h3 className="dashboard-article-card__title">
                            <a
                              href={originalUrl || '#'}
                              target="_blank"
                              rel="noopener noreferrer"
                              onClick={(e) => !originalUrl && e.preventDefault()}
                            >
                              {record.title || '无标题'}
                            </a>
                          </h3>
                          <div className="dashboard-article-card__meta">
                            <span className="dashboard-article-card__platform">{record.platform?.name ?? '—'}</span>
                            <span className="dashboard-article-card__time">{formatFriendlyTime(record.publishedAt)}</span>
                            {record.isFavorite && <StarFilled className="dashboard-article-card__star" />}
                            <span className="dashboard-article-card__actions">
                              <Button
                                type="text"
                                size="small"
                                icon={<EyeOutlined />}
                                onClick={() => navigate(`/contents/${record.id}`)}
                                className="dashboard-article-card__btn"
                              >
                                详情
                              </Button>
                              {originalUrl && (
                                <Button
                                  type="text"
                                  size="small"
                                  icon={<LinkOutlined />}
                                  href={originalUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="dashboard-article-card__btn"
                                >
                                  原文
                                </Button>
                              )}
                            </span>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <style>{`
        .dashboard-page {
          padding: 0 16px 24px;
          max-width: 1280px;
          margin: 0 auto;
        }

        /* 顶部紧凑区：标题 + 统计 + 操作 */
        .dashboard-header {
          margin-bottom: 16px;
          padding: 12px 16px 14px;
          border-radius: 12px;
          border: 1px solid rgba(0,0,0,0.06);
          background: #fafbfc;
        }
        .dashboard-header__top {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 12px;
          flex-wrap: wrap;
        }
        .dashboard-title {
          margin: 0;
          font-size: 20px;
          font-weight: 700;
          letter-spacing: -0.02em;
          line-height: 1.3;
          background: linear-gradient(135deg, #1a1a2e 0%, #0f3460 100%);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
        }
        .dashboard-subtitle {
          font-size: 12px;
          color: #8c8c8c;
          margin-left: 4px;
        }
        .dashboard-refresh-btn { margin-left: auto; }

        .dashboard-header__stats {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 16px 24px;
        }
        .dashboard-stat-item {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 8px 14px;
          border-radius: 10px;
          background: #fff;
          border: 1px solid rgba(0,0,0,0.05);
          min-width: 0;
        }
        .dashboard-stat-item .dashboard-stat-icon {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 32px;
          height: 32px;
          border-radius: 8px;
          font-size: 14px;
          flex-shrink: 0;
        }
        .dashboard-stat-item .dashboard-stat-value {
          font-size: 18px;
          font-weight: 700;
          line-height: 1.2;
          display: block;
        }
        .dashboard-stat-item .dashboard-stat-label {
          font-size: 11px;
          color: #8c8c8c;
          font-weight: 500;
        }
        .dashboard-stat-item--total .dashboard-stat-icon { background: #e8eef4; color: #1a1a2e; }
        .dashboard-stat-item--total .dashboard-stat-value { color: #1a1a2e; }
        .dashboard-stat-item--unread .dashboard-stat-icon { background: #fff0f6; color: #c41d7f; }
        .dashboard-stat-item--unread .dashboard-stat-value { color: #c41d7f; }
        .dashboard-stat-item--users .dashboard-stat-icon { background: #e6f4ff; color: #0f3460; }
        .dashboard-stat-item--users .dashboard-stat-value { color: #0f3460; }
        .dashboard-stat-item--platforms .dashboard-stat-icon { background: #f6ffed; color: #389e0d; }
        .dashboard-stat-item--platforms .dashboard-stat-value { color: #389e0d; }
        .dashboard-header__actions {
          display: flex;
          align-items: center;
          gap: 16px;
          margin-left: auto;
        }
        .dashboard-schedule {
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .dashboard-schedule .ant-switch { margin: 0; }

        /* 最近三天文章 - 主内容区占更多空间 */
        .dashboard-articles-card {
          border-radius: 12px;
          box-shadow: 0 1px 8px rgba(0,0,0,0.05);
          border: 1px solid rgba(0,0,0,0.06);
        }
        .dashboard-articles-card .ant-card-head {
          border-bottom: 1px solid rgba(0,0,0,0.06);
          padding: 12px 20px;
          min-height: 48px;
        }
        .dashboard-articles-title {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          font-size: 15px;
          font-weight: 600;
          color: #1a1a2e;
        }
        .dashboard-articles-title .anticon { color: #0f3460; font-size: 16px; }
        .dashboard-articles-extra {
          font-size: 12px;
          color: #8c8c8c;
        }
        .dashboard-articles-extra__num {
          font-weight: 600;
          color: #1a1a2e;
        }
        .dashboard-articles-extra__divider {
          margin: 0 4px;
          color: #bfbfbf;
        }
        .dashboard-articles-card .ant-card-body { padding: 16px 20px; }

        .dashboard-empty {
          padding: 40px 20px;
          text-align: center;
        }
        .dashboard-empty .ant-empty-description { color: #8c8c8c; font-size: 13px; }

        .dashboard-articles-by-author {
          display: flex;
          flex-wrap: wrap;
          gap: 16px;
          align-items: flex-start;
        }
        .dashboard-author-column {
          flex: 1 1 300px;
          min-width: 280px;
          max-width: 420px;
          border-radius: 12px;
          border: 1px solid rgba(0,0,0,0.06);
          background: #fafbfc;
          overflow: hidden;
        }
        .dashboard-author-column__head {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 12px 16px;
          background: linear-gradient(180deg, #fff 0%, #f5f7fa 100%);
          border-bottom: 1px solid rgba(0,0,0,0.06);
        }
        .dashboard-author-column__avatar {
          flex-shrink: 0;
        }
        .dashboard-author-column__avatar--default { background: #e8e8e8 !important; color: #8c8c8c !important; }
        .dashboard-author-column__info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
        .dashboard-author-column__name {
          font-size: 14px;
          font-weight: 600;
          color: #1a1a2e;
          line-height: 1.3;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .dashboard-author-column__count {
          font-size: 12px;
          color: #8c8c8c;
        }
        .dashboard-author-column__list {
          display: flex;
          flex-direction: column;
          gap: 8px;
          padding: 12px;
          max-height: min(900px, calc(100vh - 280px));
          overflow-y: auto;
        }
        .dashboard-author-column__list::-webkit-scrollbar { width: 6px; }
        .dashboard-author-column__list::-webkit-scrollbar-track { background: #f0f0f0; border-radius: 3px; }
        .dashboard-author-column__list::-webkit-scrollbar-thumb { background: #bfbfbf; border-radius: 3px; }
        .dashboard-author-column__list::-webkit-scrollbar-thumb:hover { background: #8c8c8c; }

        .dashboard-article-card {
          padding: 12px 14px;
          border-radius: 10px;
          border: 1px solid rgba(0,0,0,0.06);
          background: #fff;
          transition: background 0.2s, border-color 0.2s, box-shadow 0.2s;
        }
        .dashboard-article-card:hover {
          border-color: rgba(15, 52, 96, 0.12);
          box-shadow: 0 2px 12px rgba(15, 52, 96, 0.06);
        }
        .dashboard-article-card__title {
          margin: 0 0 8px;
          font-size: 14px;
          font-weight: 600;
          line-height: 1.45;
        }
        .dashboard-article-card__title a {
          color: #1a1a2e;
          transition: color 0.2s;
          text-decoration: none;
        }
        .dashboard-article-card__title a:hover { color: #0f3460; }
        .dashboard-article-card__meta {
          display: flex;
          flex-wrap: wrap;
          align-items: center;
          gap: 8px;
          font-size: 12px;
          color: #8c8c8c;
        }
        .dashboard-article-card__platform {
          font-size: 11px;
          padding: 2px 6px;
          border-radius: 4px;
          background: #f0f2f5;
          color: #595959;
        }
        .dashboard-article-card__time {
          font-size: 11px;
          color: #bfbfbf;
        }
        .dashboard-article-card__star { color: #faad14; font-size: 12px; flex-shrink: 0; }
        .dashboard-article-card__actions {
          display: inline-flex;
          align-items: center;
          gap: 2px;
          margin-left: auto;
        }
        .dashboard-article-card__btn.ant-btn {
          color: #8c8c8c;
          font-size: 11px;
          padding: 0 2px;
        }
        .dashboard-article-card__btn.ant-btn:hover { color: #0f3460; }
      `}</style>
    </MainLayout>
  );
}

export default Dashboard;
