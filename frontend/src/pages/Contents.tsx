import { useState, useEffect, useMemo } from 'react';
import {
  Table,
  Button,
  Space,
  Select,
  Input,
  Tag,
  message,
  Card,
  Popover,
  Segmented,
  Collapse,
  Typography,
  Avatar,
  Tooltip,
  Modal,
} from 'antd';
import {
  EyeOutlined,
  StarOutlined,
  StarFilled,
  FilterOutlined,
  DeleteOutlined,
  ReloadOutlined,
  CalendarOutlined,
  AppstoreOutlined,
  RightOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { contentApi, platformApi, userApi, getApiErrorMessage } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';
import { useNavigate } from 'react-router-dom';
import SearchBar from '../components/Search/SearchBar';
import AdvancedSearch, { AdvancedSearchParams } from '../components/Search/AdvancedSearch';
import { highlightText } from '../utils/highlight';
import { getContentOriginalUrl, parseContentMetadata } from '../utils/contentUtils';

const { Text } = Typography;

interface Content {
  id: string;
  title: string;
  body?: string;
  url: string;
  contentId?: string;
  contentType: string;
  publishedAt: string;
  isRead: boolean;
  isFavorite: boolean;
  platform: { id: string; name: string };
  user: { id: string; username: string };
  metadata?: unknown;
}

const PREVIEW_MAX_LEN = 280;

function getPreviewText(body: string | undefined): string {
  if (!body || typeof body !== 'string') return '';
  const plain = body.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
  return plain.length > PREVIEW_MAX_LEN ? plain.slice(0, PREVIEW_MAX_LEN) + '…' : plain;
}

type ViewMode = 'timeline' | 'table';

const CONTENTS_LIST_STATE_KEY = 'contents_list_state';

function getInitialContentsListState(): {
  viewMode: ViewMode;
  pagination: { current: number; pageSize: number; total: number };
  filters: { platformId?: string; userId?: string; search?: string };
  searchKeyword: string;
} {
  try {
    const s = sessionStorage.getItem(CONTENTS_LIST_STATE_KEY);
    if (s) {
      const j = JSON.parse(s);
      // 清除搜索状态，保留其他状态（viewMode、pagination、platformId、userId）
      const savedFilters = typeof j.filters === 'object' && j.filters ? j.filters : {};
      const { search, ...otherFilters } = savedFilters; // 移除 search 字段
      return {
        viewMode: (j.viewMode === 'table' ? 'table' : 'timeline') as ViewMode,
        pagination: typeof j.pagination === 'object' && j.pagination
          ? { current: Number(j.pagination.current) || 1, pageSize: Number(j.pagination.pageSize) || 50, total: Number(j.pagination.total) || 0 }
          : { current: 1, pageSize: 50, total: 0 },
        filters: otherFilters, // 不包含 search
        searchKeyword: '', // 清除搜索关键字
      };
    }
  } catch (_) {}
  return {
    viewMode: 'timeline',
    pagination: { current: 1, pageSize: 50, total: 0 },
    filters: {},
    searchKeyword: '',
  };
}

/** 某月首尾的 ISO 时间（本地 0 点与月末 23:59:59），格式 YYYY-MM-DDTHH:mm:ss 供后端 LocalDateTime 解析 */
function getMonthRange(year: number, month: number): { startTime: string; endTime: string } {
  const pad = (n: number) => String(n).padStart(2, '0');
  const start = new Date(year, month - 1, 1, 0, 0, 0, 0);
  const end = new Date(year, month, 0, 23, 59, 59, 999);
  const startStr = `${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())}T${pad(start.getHours())}:${pad(start.getMinutes())}:${pad(start.getSeconds())}`;
  const endStr = `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}T${pad(end.getHours())}:${pad(end.getMinutes())}:${pad(end.getSeconds())}`;
  return { startTime: startStr, endTime: endStr };
}

const MONTH_NAMES = ['', '一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月'];

function Contents() {
  const navigate = useNavigate();
  const [contents, setContents] = useState<Content[]>([]);
  const [platforms, setPlatforms] = useState<any[]>([]);
  const [users, setUsers] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>(() => getInitialContentsListState().viewMode);
  const [pagination, setPagination] = useState(() => getInitialContentsListState().pagination);
  const [filters, setFilters] = useState<{ platformId?: string; userId?: string; search?: string }>(() => getInitialContentsListState().filters);
  const [advancedSearchVisible, setAdvancedSearchVisible] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState(() => getInitialContentsListState().searchKeyword);
  /** 时间线：按平台→用户→月聚合数量（仅数量，点击某年再拉该年文章） */
  const [groupedData, setGroupedData] = useState<{ total: number; platforms: any[] } | null>(null);
  /** 某月已加载的文章列表，key 为 platformId|userId|year|month */
  const [monthContentsMap, setMonthContentsMap] = useState<Record<string, { list: Content[]; total: number }>>({});
  /** 正在加载某月的 key */
  const [loadingMonthKey, setLoadingMonthKey] = useState<string | null>(null);
  /** 时间线：当前展开的某月 key（用于显示文章列表） */
  const [expandedMonthKey, setExpandedMonthKey] = useState<string | null>(null);
  /** 时间线：当前展开的某年 key（用于显示该年下的月份） */
  const [expandedYearKey, setExpandedYearKey] = useState<string | null>(null);
  /** 表格：点击标题预显内容的 Modal */
  const [previewContent, setPreviewContent] = useState<Content | null>(null);
  /** 删除指定作者：弹窗与两步确认 */
  const [deleteByAuthorModalVisible, setDeleteByAuthorModalVisible] = useState(false);
  const [deleteByAuthorUserId, setDeleteByAuthorUserId] = useState<string | null>(null);
  const [deleteByAuthorStep, setDeleteByAuthorStep] = useState<1 | 2>(1);
  const [deleteByAuthorConfirmText, setDeleteByAuthorConfirmText] = useState('');
  const [deleteByAuthorCount, setDeleteByAuthorCount] = useState<number>(0);
  const [deleteByAuthorLoading, setDeleteByAuthorLoading] = useState(false);

  const timelinePageSize = 200;

  useEffect(() => {
    loadPlatforms();
    loadUsers();
  }, []);

  useEffect(() => {
    // 搜索时强制使用表格视图
    if (filters.search) {
      const pageSize = pagination.pageSize === timelinePageSize ? 20 : pagination.pageSize;
      loadContents(pagination.current, pageSize);
    } else if (viewMode === 'timeline') {
      loadGroupedCounts();
    } else {
      const pageSize = pagination.pageSize === timelinePageSize ? 20 : pagination.pageSize;
      loadContents(pagination.current, pageSize);
    }
  }, [viewMode, filters.platformId, filters.userId, filters.search]);

  useEffect(() => {
    sessionStorage.setItem(CONTENTS_LIST_STATE_KEY, JSON.stringify({
      viewMode,
      pagination,
      filters,
      searchKeyword,
    }));
  }, [viewMode, pagination, filters, searchKeyword]);

  const loadPlatforms = async () => {
    try {
      const response: any = await platformApi.getAll();
      if (response.code === 200) {
        const data = Array.isArray(response.data) ? response.data : response.data?.content || [];
        setPlatforms(data);
      }
    } catch (error) {
      console.error('加载平台列表失败', error);
    }
  };

  const loadUsers = async () => {
    try {
      const response: any = await userApi.getAll({ page: 0, size: 100 });
      if (response.code === 200) {
        const data = response.data?.content || [];
        setUsers(data);
      }
    } catch (error) {
      console.error('加载用户列表失败', error);
    }
  };

  const loadGroupedCounts = async () => {
    setLoading(true);
    try {
      const response: any = await contentApi.getGroupedCounts();
      if (response.code === 200 && response.data) {
        setGroupedData({ total: response.data.total ?? 0, platforms: response.data.platforms ?? [] });
      } else {
        setGroupedData({ total: 0, platforms: [] });
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载分组数量失败'));
      setGroupedData({ total: 0, platforms: [] });
    } finally {
      setLoading(false);
    }
  };

  const loadMonthContents = async (platformId: string, userId: string, year: number, month: number) => {
    const key = `${platformId}|${userId}|${year}|${month}`;
    if (monthContentsMap[key]) {
      setExpandedMonthKey((k) => (k === key ? null : key));
      return;
    }
    setLoadingMonthKey(key);
    try {
      const { startTime, endTime } = getMonthRange(year, month);
      const response: any = await contentApi.getAll({
        page: 0,
        size: 500,
        platformId: platformId || undefined,
        userId,
        startTime,
        endTime,
      });
      if (response.code === 200) {
        const list = response.data?.content ?? [];
        const total = response.data?.totalElements ?? list.length;
        setMonthContentsMap((prev) => ({ ...prev, [key]: { list, total } }));
        setExpandedMonthKey(key);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载该月文章失败'));
    } finally {
      setLoadingMonthKey(null);
    }
  };

  /** 时间线模式下根据平台/作者筛选过滤后的树 */
  const filteredPlatforms = useMemo(() => {
    if (!groupedData?.platforms) return [];
    let list = groupedData.platforms;
    if (filters.platformId) {
      list = list.filter((p: any) => (p.platformId ?? '') === filters.platformId);
    }
    if (filters.userId) {
      list = list.map((p: any) => ({
        ...p,
        users: (p.users ?? []).filter((u: any) => (u.userId ?? '') === filters.userId),
      })).filter((p: any) => (p.users?.length ?? 0) > 0);
    }
    return list;
  }, [groupedData, filters.platformId, filters.userId]);

  const loadContents = async (page: number, size: number) => {
    setLoading(true);
    try {
      const params: any = { page: page - 1, size };
      if (filters.platformId) params.platformId = filters.platformId;
      if (filters.userId) params.userId = filters.userId;
      if (filters.search) params.keyword = filters.search;

      const response: any = await contentApi.getAll(params);
      if (response.code === 200) {
        // Spring Data Page 对象序列化后，数据在 content 字段中
        const data = response.data?.content || [];
        const total = response.data?.totalElements ?? 0;
        
        // 调试日志
        if (filters.search) {
          console.log('搜索请求参数:', params);
          console.log('搜索结果:', { 
            code: response.code, 
            dataLength: data.length, 
            total, 
            dataStructure: Object.keys(response.data || {}) 
          });
        }
        
        setContents(data);
        setPagination({
          current: page,
          pageSize: size,
          total: total,
        });
        
        // 如果搜索但没有结果，给出提示
        if (filters.search && data.length === 0 && total === 0) {
          message.info(`未找到包含"${filters.search}"的内容`);
        }
      } else {
        message.error(response.message || '加载内容列表失败');
      }
    } catch (error) {
      console.error('加载内容列表失败:', error);
      message.error(getApiErrorMessage(error, '加载内容列表失败'));
    } finally {
      setLoading(false);
    }
  };

  const handleFilterChange = (key: string, value: string) => {
    const newFilters = { ...filters, [key]: value };
    setFilters(newFilters);
    if (viewMode === 'timeline') loadGroupedCounts();
    else loadContents(1, pagination.pageSize);
  };

  const handleSearch = (value: string) => {
    setSearchKeyword(value);
    setFilters({ ...filters, search: value });
    if (viewMode === 'timeline') loadGroupedCounts();
    else loadContents(1, pagination.pageSize);
  };

  const handleRefresh = () => {
    // 清除搜索条件，重置为默认页面
    setSearchKeyword('');
    setFilters({ platformId: filters.platformId, userId: filters.userId }); // 保留平台和用户筛选，清除搜索
    setPagination({ current: 1, pageSize: pagination.pageSize, total: 0 });
    if (viewMode === 'timeline') {
      loadGroupedCounts();
    } else {
      loadContents(1, pagination.pageSize);
    }
  };

  const handleAdvancedSearch = async (params: AdvancedSearchParams) => {
    setLoading(true);
    try {
      let response: any;
      if (params.regexPattern) {
        response = await contentApi.searchByRegex(params.regexPattern, { page: 0, size: pagination.pageSize });
      } else if (params.contentType) {
        response = await contentApi.advancedSearch(params.query, params.contentType, { page: 0, size: pagination.pageSize });
      } else {
        response = await contentApi.search(params.query, { page: 0, size: pagination.pageSize });
      }
      if (response.code === 200) {
        const data = response.data?.content || [];
        setContents(data);
        setSearchKeyword(params.query || params.regexPattern || '');
        setPagination({
          current: 1,
          pageSize: pagination.pageSize,
          total: response.data?.totalElements || 0,
        });
        setViewMode('table');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '高级搜索失败'));
    } finally {
      setLoading(false);
    }
  };

  const handleToggleFavorite = async (id: string, currentStatus: boolean) => {
    try {
      const response: any = await contentApi.update(id, { isFavorite: !currentStatus });
      if (response.code === 200) {
        message.success(currentStatus ? '已取消收藏' : '已收藏');
        if (viewMode === 'timeline') loadGroupedCounts();
        else loadContents(pagination.current, pagination.pageSize);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleMarkRead = async (id: string) => {
    try {
      const response: any = await contentApi.update(id, { isRead: true });
      if (response.code === 200) {
        message.success('已标记为已读');
        if (viewMode === 'timeline') loadGroupedCounts();
        else loadContents(pagination.current, pagination.pageSize);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const openDeleteByAuthorModal = () => {
    setDeleteByAuthorModalVisible(true);
    setDeleteByAuthorUserId(null);
    setDeleteByAuthorStep(1);
    setDeleteByAuthorConfirmText('');
    setDeleteByAuthorCount(0);
  };

  const onDeleteByAuthorUserSelect = async (userId: string | undefined) => {
    setDeleteByAuthorUserId(userId ?? null);
    if (!userId) {
      setDeleteByAuthorCount(0);
      return;
    }
    try {
      const res: any = await contentApi.getStats(userId);
      if (res?.code === 200 && res?.data != null) {
        const total = res.data.total ?? res.data.totalContents ?? 0;
        setDeleteByAuthorCount(typeof total === 'number' ? total : Number(total) || 0);
      } else {
        setDeleteByAuthorCount(0);
      }
    } catch {
      setDeleteByAuthorCount(0);
    }
  };

  const handleDeleteByAuthorNext = () => {
    if (deleteByAuthorUserId) setDeleteByAuthorStep(2);
  };

  const handleDeleteByAuthorConfirm = async () => {
    if (!deleteByAuthorUserId || deleteByAuthorConfirmText !== '删除') return;
    setDeleteByAuthorLoading(true);
    try {
      const response: any = await contentApi.deleteContentsByAuthor(deleteByAuthorUserId);
      if (response.code === 200) {
        const deleted = response.data ?? 0;
        message.success(`已删除该作者 ${deleted} 篇文章`);
        setDeleteByAuthorModalVisible(false);
        if (viewMode === 'timeline') {
          setMonthContentsMap({});
          setExpandedMonthKey(null);
          setExpandedYearKey(null);
          loadGroupedCounts();
        } else {
          loadContents(1, pagination.pageSize);
        }
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败'));
    } finally {
      setDeleteByAuthorLoading(false);
    }
  };

  const renderTitlePreview = (record: Content, titleNode: React.ReactNode) => {
    const preview = getPreviewText(record.body);
    if (!preview) return titleNode;
    return (
      <Popover
        trigger="hover"
        mouseEnterDelay={0.4}
        placement="topLeft"
        overlayInnerStyle={{ maxWidth: 400, padding: 12 }}
        content={
          <div style={{ maxHeight: 320, overflow: 'auto' }}>
            <div style={{ fontWeight: 600, marginBottom: 6, color: '#262626' }}>{record.title || '无标题'}</div>
            <div style={{ fontSize: 13, color: '#595959', lineHeight: 1.6, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {preview}
            </div>
          </div>
        }
      >
        <span style={{ display: 'inline-block', width: '100%' }}>{titleNode}</span>
      </Popover>
    );
  };

  const renderContentCard = (record: Content) => {
    const originalUrl = getContentOriginalUrl(record);
    const { nickName, userAvatar } = parseContentMetadata(record.metadata);
    const highlightedTitle = searchKeyword ? highlightText(record.title || '无标题', searchKeyword) : (record.title || '无标题');
    const titleLink = (
      <a
        href={originalUrl || '#'}
        target="_blank"
        rel="noopener noreferrer"
        onClick={(e) => !originalUrl && e.preventDefault()}
        style={{ fontWeight: 500, color: 'inherit' }}
      >
        <span dangerouslySetInnerHTML={{ __html: highlightedTitle }} />
      </a>
    );
    return (
      <Card
        key={record.id}
        size="small"
        hoverable
        className="content-timeline-card"
        styles={{ body: { padding: '14px 18px' } }}
      >
        <div className="content-timeline-card__inner">
          <div className="content-timeline-card__main">
            {renderTitlePreview(record, titleLink)}
            <div className="content-timeline-card__meta">
              <Tag className="content-timeline-card__platform">{record.platform?.name ?? '-'}</Tag>
              <Space size="small" className="content-timeline-card__author">
                {userAvatar && <Avatar src={userAvatar} size={22} />}
                <Text type="secondary" className="content-timeline-card__author-name">
                  {nickName ?? record.user?.username ?? '-'}
                </Text>
              </Space>
              <Text type="secondary" className="content-timeline-card__date">
                {new Date(record.publishedAt).toLocaleString('zh-CN')}
              </Text>
              {!record.isRead && <Tag color="orange" className="content-timeline-card__unread">未读</Tag>}
              {record.isFavorite && <StarFilled className="content-timeline-card__star" />}
            </div>
          </div>
          <Space size="small" wrap className="content-timeline-card__actions">
            <Tooltip title="查看详情">
              <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/contents/${record.id}`)} className="content-timeline-card__btn" />
            </Tooltip>
            <Tooltip title={record.isFavorite ? '取消收藏' : '收藏'}>
              <Button
                type="text"
                size="small"
                icon={record.isFavorite ? <StarFilled /> : <StarOutlined />}
                onClick={() => handleToggleFavorite(record.id, record.isFavorite)}
                className={`content-timeline-card__btn ${record.isFavorite ? 'content-timeline-card__btn--star' : ''}`}
              />
            </Tooltip>
            {!record.isRead && (
              <Tooltip title="标记已读">
                <Button type="text" size="small" onClick={() => handleMarkRead(record.id)} className="content-timeline-card__btn">已读</Button>
              </Tooltip>
            )}
          </Space>
        </div>
      </Card>
    );
  };

  const tableColumns = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true,
      width: '28%',
      render: (text: string, record: Content) => {
        const highlightedTitle = searchKeyword ? highlightText(text || '无标题', searchKeyword) : (text || '无标题');
        return (
          <span
            role="button"
            tabIndex={0}
            className="content-table-title-link content-table-title-preview"
            onClick={() => setPreviewContent(record)}
            onKeyDown={(e) => e.key === 'Enter' && setPreviewContent(record)}
          >
            <span dangerouslySetInnerHTML={{ __html: highlightedTitle }} />
          </span>
        );
      },
    },
    {
      title: '平台',
      key: 'platform',
      width: 90,
      render: (_: any, record: Content) => (
        <Tag color="blue" style={{ margin: 0 }}>{record.platform?.name ?? '-'}</Tag>
      ),
    },
    {
      title: '作者',
      key: 'author',
      width: 100,
      render: (_: any, record: Content) => (
        <Text style={{ fontSize: 13 }}>{record.user?.username ?? '-'}</Text>
      ),
    },
    {
      title: '平台昵称',
      key: 'platformNickname',
      width: 120,
      render: (_: any, record: Content) => {
        const { nickName, userAvatar } = parseContentMetadata(record.metadata);
        if (!nickName && !userAvatar) return <Text type="secondary">-</Text>;
        return (
          <Space size="small">
            {userAvatar && <Avatar src={userAvatar} size={24} />}
            <span style={{ fontSize: 13 }}>{nickName ?? '-'}</span>
          </Space>
        );
      },
    },
    {
      title: '类型',
      dataIndex: 'contentType',
      key: 'contentType',
      width: 72,
      align: 'center' as const,
      render: (type: string) => <Tag style={{ margin: 0 }}>{type}</Tag>,
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      key: 'publishedAt',
      width: 158,
      render: (text: string) => (
        <Text type="secondary" style={{ fontSize: 12 }}>{new Date(text).toLocaleString('zh-CN')}</Text>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_: any, record: Content) => (
        <Space size={4}>
          {record.isRead ? <Tag color="success">已读</Tag> : <Tag color="warning">未读</Tag>}
          {record.isFavorite && <StarFilled style={{ color: '#faad14', fontSize: 14 }} />}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      fixed: 'right' as const,
      render: (_: any, record: Content) => (
        <Space size={0}>
          <Tooltip title="查看详情">
            <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/contents/${record.id}`)} />
          </Tooltip>
          <Tooltip title={record.isFavorite ? '取消收藏' : '收藏'}>
          <Button
              type="text"
              size="small"
            icon={record.isFavorite ? <StarFilled /> : <StarOutlined />}
            onClick={() => handleToggleFavorite(record.id, record.isFavorite)}
              style={{ color: record.isFavorite ? '#faad14' : undefined }}
            />
          </Tooltip>
          {!record.isRead && (
            <Tooltip title="标记已读">
              <Button type="text" size="small" onClick={() => handleMarkRead(record.id)}>已读</Button>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <MainLayout>
      <div className="contents-page contents-page--premium">
        <header className="contents-page__header">
          <div className="contents-page__title-wrap">
            <h1 className="contents-page__title">内容管理</h1>
            <p className="contents-page__subtitle">按平台、用户、年份浏览，或使用表格筛选与搜索</p>
        </div>
          <Space wrap size="middle">
            {!filters.search && (
              <Segmented
                value={viewMode}
                onChange={(v) => setViewMode(v as ViewMode)}
                options={[
                  { label: <Space size={6}><CalendarOutlined />按平台 · 用户 · 年份</Space>, value: 'timeline' },
                  { label: <Space size={6}><AppstoreOutlined />表格</Space>, value: 'table' },
                ]}
                className="contents-page__view-switch"
              />
            )}
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              onClick={handleRefresh}
              loading={loading}
              className="contents-page__refresh-btn"
            >
              刷新
            </Button>
          </Space>
        </header>

        {!filters.search && (
          <Card className="contents-page__filter-card" bordered={false}>
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <Space wrap style={{ width: '100%' }} size="middle">
                <div className="contents-page__search-wrap">
                  <SearchBar onSearch={handleSearch} placeholder="搜索关键字（匹配标题和正文内容）" showHistory={true} />
                </div>
                <Button icon={<FilterOutlined />} onClick={() => setAdvancedSearchVisible(true)} className="contents-page__filter-btn">
                  高级搜索
                </Button>
                <Button danger icon={<DeleteOutlined />} onClick={openDeleteByAuthorModal}>
                  删除指定作者文章
                </Button>
              </Space>
              <Space wrap size="middle">
                <Select
                  placeholder="按平台"
                  allowClear
                  className="contents-page__select"
                  style={{ width: 180 }}
                  value={filters.platformId ?? undefined}
                  onChange={(value) => handleFilterChange('platformId', value)}
                >
                  {platforms.map((p) => (
                    <Select.Option key={p.id} value={p.id}>{p.name}</Select.Option>
                  ))}
                </Select>
                <Select
                  placeholder="按作者"
                  allowClear
                  className="contents-page__select"
                  style={{ width: 180 }}
                  value={filters.userId ?? undefined}
                  onChange={(value) => handleFilterChange('userId', value)}
                >
                  {users.map((u) => (
                    <Select.Option key={u.id} value={u.id}>{u.username}</Select.Option>
                  ))}
                </Select>
              </Space>
            </Space>
          </Card>
        )}
        
        {filters.search && (
          <Card className="contents-page__filter-card" bordered={false}>
            <Space wrap style={{ width: '100%' }} size="middle" justify="space-between">
              <div className="contents-page__search-wrap">
                <SearchBar onSearch={handleSearch} placeholder="搜索关键字（匹配标题和正文内容）" showHistory={true} />
              </div>
              <Button onClick={() => handleSearch('')} type="text">
                清除搜索
              </Button>
            </Space>
          </Card>
        )}

        <AdvancedSearch visible={advancedSearchVisible} onCancel={() => setAdvancedSearchVisible(false)} onSearch={handleAdvancedSearch} />

        {/* 删除指定作者：两步确认 */}
        <Modal
          title={deleteByAuthorStep === 1 ? '删除指定作者文章' : '再次确认'}
          open={deleteByAuthorModalVisible}
          onCancel={() => setDeleteByAuthorModalVisible(false)}
          footer={null}
          destroyOnClose
          afterClose={() => {
            setDeleteByAuthorStep(1);
            setDeleteByAuthorUserId(null);
            setDeleteByAuthorConfirmText('');
          }}
        >
          {deleteByAuthorStep === 1 ? (
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <div>
                <div style={{ marginBottom: 8 }}>选择作者（将删除该追踪用户下的全部内容）：</div>
                <Select
                  placeholder="请选择作者"
                  allowClear
                  style={{ width: '100%' }}
                  value={deleteByAuthorUserId ?? undefined}
                  onChange={onDeleteByAuthorUserSelect}
                >
                  {users.map((u) => (
                    <Select.Option key={u.id} value={u.id}>{u.username}</Select.Option>
                  ))}
                </Select>
              </div>
              {deleteByAuthorUserId && (
                <div style={{ color: '#666', fontSize: 13 }}>
                  作者「{users.find((u) => u.id === deleteByAuthorUserId)?.username ?? '未知'}」共有 {deleteByAuthorCount} 篇内容，删除后不可恢复。
                </div>
              )}
              <Space style={{ marginTop: 16 }}>
                <Button onClick={() => setDeleteByAuthorModalVisible(false)}>取消</Button>
                <Button
                  type="primary"
                  danger
                  disabled={!deleteByAuthorUserId || deleteByAuthorCount === 0}
                  onClick={handleDeleteByAuthorNext}
                >
                  下一步
                </Button>
              </Space>
            </Space>
          ) : (
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              <div style={{ color: '#262626' }}>
                将删除作者「{users.find((u) => u.id === deleteByAuthorUserId)?.username ?? '未知'}」的全部 {deleteByAuthorCount} 篇内容，此操作不可恢复。
              </div>
              <div>
                <div style={{ marginBottom: 8 }}>请输入「删除」以确认：</div>
                <Input
                  value={deleteByAuthorConfirmText}
                  onChange={(e) => setDeleteByAuthorConfirmText(e.target.value)}
                  placeholder="删除"
                  allowClear
                />
              </div>
              <Space style={{ marginTop: 16 }}>
                <Button onClick={() => setDeleteByAuthorStep(1)}>上一步</Button>
                <Button onClick={() => setDeleteByAuthorModalVisible(false)}>取消</Button>
                <Button
                  type="primary"
                  danger
                  loading={deleteByAuthorLoading}
                  disabled={deleteByAuthorConfirmText !== '删除'}
                  onClick={handleDeleteByAuthorConfirm}
                >
                  确认删除
                </Button>
              </Space>
            </Space>
          )}
        </Modal>

        {/* 表格：点击标题预显内容 */}
        <Modal
          title={previewContent?.title || '内容预览'}
          open={!!previewContent}
          onCancel={() => setPreviewContent(null)}
          footer={
            previewContent ? (
              <Space>
                <Button onClick={() => { setPreviewContent(null); navigate(`/contents/${previewContent.id}`); }}>
                  查看详情
                </Button>
                {getContentOriginalUrl(previewContent) && (
                  <Button type="primary" href={getContentOriginalUrl(previewContent)!} target="_blank" rel="noopener noreferrer">
                    打开原文
                  </Button>
                )}
                <Button onClick={() => setPreviewContent(null)}>关闭</Button>
              </Space>
            ) : null
          }
          width={640}
          destroyOnClose
        >
          {previewContent && (
            <div className="contents-preview-modal-body">
              <div className="contents-preview-modal-meta">
                <Tag>{previewContent.platform?.name ?? '-'}</Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {new Date(previewContent.publishedAt).toLocaleString('zh-CN')}
                </Text>
              </div>
              <div className="contents-preview-modal-content">
                {getPreviewText(previewContent.body) || '(无正文)'}
              </div>
            </div>
          )}
        </Modal>

        {filters.search ? (
          // 搜索时只显示表格视图
          <Card className="contents-table-card" bordered={false}>
            {contents.length === 0 && !loading ? (
              <div className="contents-empty contents-empty--table">
                <div className="contents-empty__icon">
                  <FileTextOutlined />
                </div>
                <p className="contents-empty__title">
                  未找到包含"{filters.search}"的内容
                </p>
                <p className="contents-empty__desc">
                  请尝试其他关键字或调整筛选条件
                </p>
              </div>
            ) : (
              <Table
                className="contents-table"
                columns={tableColumns}
                dataSource={contents}
                rowKey="id"
                loading={loading}
                size="middle"
                bordered={false}
                rowClassName={(_, index) => (index % 2 === 0 ? 'contents-table-row-even' : 'contents-table-row-odd')}
                pagination={{
                  current: pagination.current,
                  pageSize: pagination.pageSize,
                  total: pagination.total,
                  showSizeChanger: true,
                  showTotal: (total) => `共 ${total} 条`,
                  onChange: (page, size) => loadContents(page, size ?? pagination.pageSize),
                  className: 'contents-table-pagination',
                }}
                scroll={{ x: 1100 }}
              />
            )}
          </Card>
        ) : viewMode === 'timeline' ? (
          <div className="contents-timeline">
            {loading ? (
              <Card className="contents-timeline-loading" loading />
            ) : !groupedData || groupedData.platforms.length === 0 ? (
              <Card className="contents-empty-card" bordered={false}>
                <div className="contents-empty">
                  <div className="contents-empty__icon">
                    <FileTextOutlined />
                  </div>
                  <p className="contents-empty__title">暂无内容</p>
                  <p className="contents-empty__desc">可调整平台/作者筛选或点击刷新重新拉取</p>
                </div>
              </Card>
            ) : (
              <Collapse
                defaultActiveKey={filteredPlatforms.map((p: any) => p.platformId ?? p.platformName)}
                expandIcon={({ isActive }) => <RightOutlined rotate={isActive ? 90 : 0} className="contents-timeline-collapse__icon" />}
                className="contents-timeline-collapse"
              >
                {filteredPlatforms.map((platform: any) => (
                  <Collapse.Panel
                    key={platform.platformId ?? platform.platformName}
                    header={
                      <span className="contents-timeline-collapse__header">
                        <span className="contents-timeline-collapse__platform-name">{platform.platformName ?? '未分类'}</span>
                        <Tag className="contents-timeline-collapse__count">{platform.total ?? 0} 条</Tag>
                      </span>
                    }
                    className="contents-timeline-collapse__panel"
                  >
                    <div className="contents-timeline-users-row">
                      {(platform.users ?? []).map((user: any) => {
                        const months = (user.months ?? []).slice().sort((a: any, b: any) => {
                          const y = Number(b.year) - Number(a.year);
                          return y !== 0 ? y : Number(b.month) - Number(a.month);
                        });
                        const byYear = new Map<number, any[]>();
                        months.forEach((mo: any) => {
                          const y = Number(mo.year);
                          if (!byYear.has(y)) byYear.set(y, []);
                          byYear.get(y)!.push(mo);
                        });
                        const years = Array.from(byYear.entries()).sort((a, b) => b[0] - a[0]);
                        return (
                          <div key={user.userId ?? user.username} className="contents-timeline-user">
                            <div className="contents-timeline-user__title">
                              <span className="contents-timeline-user__name">{user.username ?? '-'}</span>
                              <Tag className="contents-timeline-user__count">{user.total ?? 0} 条</Tag>
                            </div>
                            {years.map(([year, yearMonths]) => {
                              const yearTotal = yearMonths.reduce((s: number, m: any) => s + Number(m.count ?? 0), 0);
                              const yearKey = `${platform.platformId ?? ''}|${user.userId ?? ''}|${year}`;
                              const isYearExpanded = expandedYearKey === yearKey;
                              return (
                                <div key={year} className="contents-timeline-year">
                                  <div
                                    className="contents-timeline-year__title contents-timeline-year__title--clickable"
                                    role="button"
                                    tabIndex={0}
                                    onClick={() => setExpandedYearKey((k) => (k === yearKey ? null : yearKey))}
                                    onKeyDown={(e) => e.key === 'Enter' && setExpandedYearKey((k) => (k === yearKey ? null : yearKey))}
                                  >
                                    <RightOutlined style={{ fontSize: 10, marginRight: 4, transform: isYearExpanded ? 'rotate(90deg)' : 'none' }} />
                                    <CalendarOutlined />
                                    {year} 年 · 共 {yearTotal} 条
                                  </div>
                                  {isYearExpanded && (
                                    <div className="contents-timeline-year__months">
                                      {yearMonths.map((mo: any) => {
                                        const month = Number(mo.month);
                                        const count = Number(mo.count ?? 0);
                                        const monthKey = `${platform.platformId ?? ''}|${user.userId ?? ''}|${year}|${month}`;
                                        const isMonthExpanded = expandedMonthKey === monthKey;
                                        const isLoading = loadingMonthKey === monthKey;
                                        const loaded = monthContentsMap[monthKey];
                                        return (
                                          <div key={`${year}-${month}`} className="contents-timeline-month">
                                            <div
                                              className="contents-timeline-month__title contents-timeline-month__title--clickable"
                                              role="button"
                                              tabIndex={0}
                                              onClick={() => (isLoading ? undefined : loadMonthContents(platform.platformId ?? '', user.userId ?? '', year, month))}
                                              onKeyDown={(e) => e.key === 'Enter' && !isLoading && loadMonthContents(platform.platformId ?? '', user.userId ?? '', year, month)}
                                            >
                                              <RightOutlined style={{ fontSize: 10, marginRight: 4, transform: isMonthExpanded ? 'rotate(90deg)' : 'none' }} />
                                              {MONTH_NAMES[month]} · {count} 条
                                            </div>
                                            {isMonthExpanded && (
                                              <div className="contents-timeline-month__list">
                                                {isLoading ? (
                                                  <div style={{ padding: 16, textAlign: 'center' }}><Text type="secondary">加载中…</Text></div>
                                                ) : loaded ? (
                                                  loaded.list.length === 0 ? (
                                                    <div style={{ padding: 16, color: '#8c8c8c' }}>该月暂无文章</div>
                                                  ) : (
                                                    loaded.list.map((c) => renderContentCard(c))
                                                  )
                                                ) : null}
                                              </div>
                                            )}
                                          </div>
                                        );
                                      })}
                                    </div>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        );
                      })}
                    </div>
                  </Collapse.Panel>
                ))}
              </Collapse>
            )}
            {viewMode === 'timeline' && groupedData && groupedData.total > 0 && (
              <div className="contents-timeline-footer">
                <Text type="secondary">
                  共 {groupedData.total} 条。展开年份可查看月份，点击某月可加载该月文章；可切换「表格」查看分页或使用筛选缩小范围。
                </Text>
              </div>
            )}
          </div>
        ) : (
          <Card className="contents-table-card" bordered={false}>
            {contents.length === 0 && !loading ? (
              <div className="contents-empty contents-empty--table">
                <div className="contents-empty__icon">
                  <FileTextOutlined />
                </div>
                <p className="contents-empty__title">
                  {filters.search ? `未找到包含"${filters.search}"的内容` : '暂无内容'}
                </p>
                <p className="contents-empty__desc">
                  {filters.search ? '请尝试其他关键字或调整筛选条件' : '可调整筛选或点击刷新'}
                </p>
              </div>
            ) : (
        <Table
                className="contents-table"
                columns={tableColumns}
          dataSource={contents}
          rowKey="id"
          loading={loading}
                size="middle"
                bordered={false}
                rowClassName={(_, index) => (index % 2 === 0 ? 'contents-table-row-even' : 'contents-table-row-odd')}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, size) => loadContents(page, size ?? pagination.pageSize),
            className: 'contents-table-pagination',
          }}
                scroll={{ x: 1100 }}
        />
            )}
          </Card>
        )}
      </div>
      <style>{`
        /* 页面整体 */
        .contents-page--premium { padding: 0 8px 24px; max-width: 1400px; margin: 0 auto; }
        .contents-page__header {
          display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px;
          margin-bottom: 24px; padding-bottom: 20px; border-bottom: 1px solid rgba(0,0,0,0.06);
        }
        .contents-page__title-wrap { margin: 0; }
        .contents-page__title {
          margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -0.02em;
          background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent;
          background-clip: text;
        }
        .contents-page__subtitle { margin: 6px 0 0; font-size: 14px; color: #8c8c8c; font-weight: 400; }
        .contents-page__view-switch .ant-segmented-item-label { font-size: 13px; }
        .contents-page__refresh-btn { min-width: 88px; }

        /* 筛选区卡片 */
        .contents-page__filter-card {
          margin-bottom: 24px; border-radius: 12px;
          box-shadow: 0 1px 2px rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.06);
          background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
        }
        .contents-page__filter-card .ant-card-body { padding: 20px 24px; }
        .contents-page__search-wrap { flex: 1; min-width: 280px; }
        .contents-page__select.ant-select { border-radius: 8px; }

        /* 时间线区域 */
        .contents-timeline { max-width: 960px; margin: 0 auto; }
        .contents-timeline-loading { border-radius: 12px; border: none; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
        .contents-timeline-footer { text-align: center; margin-top: 20px; padding: 12px; font-size: 13px; color: #8c8c8c; }

        /* 时间线折叠面板 */
        .contents-timeline-collapse { background: transparent !important; border: none !important; }
        .contents-timeline-collapse .ant-collapse-item { margin-bottom: 16px; }
        .contents-timeline-collapse .ant-collapse-item > .ant-collapse-header { align-items: center !important; padding: 14px 20px !important; font-size: 15px; }
        .contents-timeline-collapse__panel {
          border-radius: 12px !important; overflow: hidden;
          border: 1px solid rgba(0,0,0,0.06) !important;
          box-shadow: 0 1px 3px rgba(0,0,0,0.04) !important;
          background: #fff !important;
        }
        .contents-timeline-collapse__header { display: inline-flex; align-items: center; gap: 10px; }
        .contents-timeline-collapse__platform-name { font-weight: 600; color: #1a1a2e; }
        .contents-timeline-collapse__count { margin: 0; border-radius: 6px; font-size: 12px; }
        .contents-timeline-collapse__icon { color: #8c8c8c; transition: transform 0.2s; }
        .contents-timeline-users-row {
          display: flex; flex-wrap: wrap; gap: 24px; align-items: flex-start;
        }
        .contents-timeline-user {
          flex: 1 1 320px; min-width: 280px; max-width: 480px;
          margin-bottom: 0; padding: 16px; border-radius: 10px;
          border: 1px solid rgba(0,0,0,0.06); background: #fafbfc;
        }
        .contents-timeline-user__title { font-size: 14px; font-weight: 600; color: #262626; margin-bottom: 10px; display: flex; align-items: center; gap: 8px; }
        .contents-timeline-user__name { margin-right: 4px; }
        .contents-timeline-user__count { margin: 0; font-size: 12px; }
        .contents-timeline-year { margin-bottom: 12px; }
        .contents-timeline-year:last-child { margin-bottom: 0; }
        .contents-timeline-year__title {
          font-size: 14px; font-weight: 600; color: #595959; margin-bottom: 6px;
          display: flex; align-items: center; gap: 8px;
        }
        .contents-timeline-year__title--clickable { cursor: pointer; padding: 8px 12px; border-radius: 8px; }
        .contents-timeline-year__title--clickable:hover { background: #f5f5f5; color: #262626; }
        .contents-timeline-year__months { margin-left: 24px; padding-left: 8px; border-left: 2px solid #f0f0f0; }
        .contents-timeline-month { margin-bottom: 8px; }
        .contents-timeline-month__title {
          font-size: 13px; color: #8c8c8c; margin-bottom: 6px; display: flex; align-items: center; gap: 6px;
        }
        .contents-timeline-month__title--clickable { cursor: pointer; padding: 4px 0; border-radius: 4px; }
        .contents-timeline-month__title--clickable:hover { background: #f5f5f5; color: #262626; }
        .contents-timeline-month__list { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; margin-left: 18px; padding-left: 8px; border-left: 2px solid #f0f0f0; }

        /* 时间线内容卡片 */
        .content-timeline-card {
          margin: 0; border-radius: 10px; border: 1px solid rgba(0,0,0,0.06);
          border-left: 4px solid #0f3460; box-shadow: 0 1px 2px rgba(0,0,0,0.04);
          transition: box-shadow 0.2s, border-left-color 0.2s, transform 0.15s;
        }
        .content-timeline-card:hover {
          box-shadow: 0 4px 16px rgba(15, 52, 96, 0.12); border-left-color: #16213e;
        }
        .content-timeline-card__inner { display: flex; align-items: flex-start; gap: 16px; }
        .content-timeline-card__main { flex: 1; min-width: 0; }
        .content-timeline-card__main a { font-weight: 500; color: #1a1a2e; transition: color 0.2s; }
        .content-timeline-card__main a:hover { color: #0f3460; }
        .content-timeline-card__meta { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 10px; align-items: center; font-size: 12px; }
        .content-timeline-card__platform { margin: 0; border-radius: 6px; font-size: 12px; }
        .content-timeline-card__author-name { font-size: 12px !important; }
        .content-timeline-card__date { font-size: 12px !important; }
        .content-timeline-card__star { color: #faad14; font-size: 14px; }
        .content-timeline-card__btn.ant-btn { color: #8c8c8c; }
        .content-timeline-card__btn.ant-btn:hover { color: #0f3460; }
        .content-timeline-card__btn--star { color: #faad14 !important; }

        /* 空状态 */
        .contents-empty-card { border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
        .contents-empty { text-align: center; padding: 48px 24px; }
        .contents-empty__icon {
          width: 72px; height: 72px; margin: 0 auto 16px; border-radius: 50%;
          background: linear-gradient(135deg, #f0f4f8 0%, #e8eef4 100%);
          display: flex; align-items: center; justify-content: center; font-size: 32px; color: #8c8c8c;
        }
        .contents-empty__title { margin: 0; font-size: 16px; font-weight: 600; color: #262626; }
        .contents-empty__desc { margin: 8px 0 0; font-size: 14px; color: #8c8c8c; }
        .contents-empty--table { padding: 64px 24px; }

        /* 表格卡片 */
        .contents-table-card {
          border-radius: 12px; overflow: hidden;
          box-shadow: 0 1px 3px rgba(0,0,0,0.05); border: 1px solid rgba(0,0,0,0.06);
        }
        .contents-table-card .ant-card-body { padding: 0; overflow: hidden; }
        .contents-table .ant-table-thead > tr > th {
          background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%) !important;
          font-weight: 600; color: #1a1a2e; font-size: 13px; padding: 14px 20px;
          border-bottom: 1px solid rgba(0,0,0,0.06);
        }
        .contents-table .ant-table-tbody > tr > td {
          padding: 14px 20px; font-size: 13px; border-bottom: 1px solid #f5f5f5;
        }
        .contents-table .ant-table-tbody > tr.contents-table-row-even { background: #fff; }
        .contents-table .ant-table-tbody > tr.contents-table-row-odd { background: #fafbfc; }
        .contents-table .ant-table-tbody > tr:hover > td { background: #f0f7ff !important; }
        .contents-table .content-table-title-link { color: #0f3460; font-weight: 500; transition: color 0.2s; }
        .contents-table .content-table-title-link:hover { color: #16213e; }
        .contents-table .content-table-title-preview { cursor: pointer; }
        .contents-preview-modal-body { max-height: 60vh; overflow: auto; }
        .contents-preview-modal-meta { margin-bottom: 12px; display: flex; align-items: center; gap: 12px; }
        .contents-preview-modal-content { font-size: 14px; line-height: 1.7; color: #262626; white-space: pre-wrap; word-break: break-word; }
        .contents-table .ant-table-container table { border-radius: 0; }
        .contents-table-pagination { padding: 16px 20px !important; margin: 0 !important; }
        
        @media (max-width: 767px) {
          .contents-page--premium {
            padding: 0 4px 16px;
          }
          .contents-page__header {
            flex-direction: column;
            align-items: flex-start;
            gap: 12px;
            margin-bottom: 16px;
            padding-bottom: 12px;
          }
          .contents-page__subtitle {
            font-size: var(--text-body-sm-size);
          }
          .contents-page__filter-card {
            margin-bottom: 12px;
          }
          .contents-page__filter-card .ant-card-body {
            padding: 12px;
          }
          .contents-page__select {
            width: 100% !important;
          }
          .contents-timeline-card {
            margin-bottom: 8px;
          }
          .contents-timeline-card .ant-card-body {
            padding: 12px !important;
          }
          .content-timeline-card__title {
            font-size: var(--text-body-sm-size);
            margin-bottom: 8px;
          }
          .content-timeline-card__meta {
            flex-wrap: wrap;
            gap: 6px;
            font-size: 11px;
          }
          .content-timeline-card__actions {
            width: 100%;
            margin-top: 8px;
            justify-content: flex-end;
          }
          .contents-table-card .ant-card-body {
            padding: 0;
          }
          .contents-table {
            font-size: 12px;
          }
          .contents-table .ant-table-thead > tr > th {
            padding: 8px 4px !important;
            font-size: 11px;
          }
          .contents-table .ant-table-tbody > tr > td {
            padding: 8px 4px !important;
            font-size: 11px;
          }
          .contents-preview-modal-body {
            max-height: 50vh;
          }
        }
      `}</style>
    </MainLayout>
  );
}

export default Contents;
