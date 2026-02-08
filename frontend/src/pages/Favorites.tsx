import { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  message,
  Card,
  Typography,
  Avatar,
  Tooltip,
} from 'antd';
import {
  EyeOutlined,
  StarFilled,
  ReloadOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { contentApi, getApiErrorMessage, getAvatarSrc } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';
import { useNavigate } from 'react-router-dom';
import { parseContentMetadata } from '../utils/contentUtils';

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
  user: { id: string; username: string; avatarUrl?: string };
  metadata?: unknown;
}

function Favorites() {
  const navigate = useNavigate();
  const [contents, setContents] = useState<Content[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });

  const loadContents = async (page: number, size: number) => {
    setLoading(true);
    try {
      const response: any = await contentApi.getAll({
        page: page - 1,
        size,
        isFavorite: true,
      });
      if (response.code === 200) {
        const data = response.data?.content || [];
        const total = response.data?.totalElements ?? 0;
        setContents(data);
        setPagination({
          current: page,
          pageSize: size,
          total: total,
        });
      } else {
        message.error(response.message || '加载收藏列表失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载收藏列表失败'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadContents(pagination.current, pagination.pageSize);
  }, []);

  const handleToggleFavorite = async (id: string) => {
    try {
      const response: any = await contentApi.update(id, { isFavorite: false });
      if (response.code === 200) {
        message.success('已取消收藏');
        loadContents(pagination.current, pagination.pageSize);
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
        loadContents(pagination.current, pagination.pageSize);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const columns = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true,
      width: '28%',
      render: (text: string, record: Content) => (
        <a
          onClick={() => navigate(`/contents/${record.id}`)}
          style={{ fontWeight: 500, color: 'inherit', cursor: 'pointer' }}
        >
          {text || '无标题'}
        </a>
      ),
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
      width: 120,
      render: (_: any, record: Content) => {
        const { nickName } = parseContentMetadata(record.metadata);
        return (
          <Space size="small">
            {record.user?.avatarUrl && <Avatar src={getAvatarSrc(record.user.avatarUrl)} size={24} />}
            <span style={{ fontSize: 13 }}>{nickName ?? record.user?.username ?? '-'}</span>
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
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>{new Date(text).toLocaleString('zh-CN')}</Typography.Text>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_: any, record: Content) => (
        <Space size={4}>
          {record.isRead ? <Tag color="success">已读</Tag> : <Tag color="warning">未读</Tag>}
          <StarFilled style={{ color: '#faad14', fontSize: 14 }} />
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
          <Tooltip title="取消收藏">
            <Button
              type="text"
              size="small"
              icon={<StarFilled />}
              onClick={() => handleToggleFavorite(record.id)}
              style={{ color: '#faad14' }}
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
      <div className="favorites-page" style={{ padding: '0 8px 24px', maxWidth: 1400, margin: '0 auto' }}>
        <header style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: 16,
          marginBottom: 24,
          paddingBottom: 20,
          borderBottom: '1px solid rgba(0,0,0,0.06)',
        }}>
          <div>
            <h1 style={{ margin: 0, fontSize: 26, fontWeight: 700 }}>收藏管理</h1>
            <p style={{ margin: '6px 0 0', fontSize: 14, color: '#8c8c8c' }}>分页浏览已收藏的文章</p>
          </div>
          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={() => loadContents(1, pagination.pageSize)}
            loading={loading}
          >
            刷新
          </Button>
        </header>

        <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          {contents.length === 0 && !loading ? (
            <div style={{ textAlign: 'center', padding: 48 }}>
              <div style={{
                width: 72,
                height: 72,
                margin: '0 auto 16px',
                borderRadius: '50%',
                background: 'linear-gradient(135deg, #f0f4f8 0%, #e8eef4 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 32,
                color: '#8c8c8c',
              }}>
                <FileTextOutlined />
              </div>
              <p style={{ margin: 0, fontSize: 16, fontWeight: 600, color: '#262626' }}>暂无收藏</p>
              <p style={{ margin: '8px 0 0', fontSize: 14, color: '#8c8c8c' }}>在内容管理中点击星标可收藏文章</p>
            </div>
          ) : (
            <Table
              columns={columns}
              dataSource={contents}
              rowKey="id"
              loading={loading}
              size="middle"
              bordered={false}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: pagination.total,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条`,
                onChange: (page, size) => loadContents(page, size ?? pagination.pageSize),
              }}
              scroll={{ x: 1100 }}
            />
          )}
        </Card>
      </div>
    </MainLayout>
  );
}

export default Favorites;
