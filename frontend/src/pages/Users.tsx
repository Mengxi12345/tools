import { useEffect, useRef, useState } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, message, Popconfirm, Switch, Progress, Upload } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, UserOutlined, UserAddOutlined, TeamOutlined, UploadOutlined } from '@ant-design/icons';
import { Avatar, Typography } from 'antd';
import { userApi, platformApi, taskApi, getApiErrorMessage, getAvatarSrc, getPlatformAvatarSrc } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';

interface Platform {
  id: string;
  name: string;
  type: string;
  avatarUrl?: string;
}

interface User {
  id: string;
  username: string;
  userId: string;
  displayName?: string;
  avatarUrl?: string;
  selfIntroduction?: string;
  platform: Platform;
  isActive: boolean;
  createdAt: string;
  groupId?: string;
  tags?: string[];
}

/** 当前页各用户的文章总数（由 content-counts 接口填充） */
type ContentCountsMap = Record<string, number>;

function Users() {
  const [users, setUsers] = useState<User[]>([]);
  const [platforms, setPlatforms] = useState<Platform[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [refreshModalVisible, setRefreshModalVisible] = useState(false);
  const [refreshUser, setRefreshUser] = useState<User | null>(null);
  const [progressModalVisible, setProgressModalVisible] = useState(false);
  const [currentTask, setCurrentTask] = useState<any | null>(null);
  const [currentProgress, setCurrentProgress] = useState(0);
  const progressTimerRef = useRef<number | null>(null);
  const [refreshingProfileId, setRefreshingProfileId] = useState<string | null>(null);
  const [contentCounts, setContentCounts] = useState<ContentCountsMap>({});
  const [form] = Form.useForm();

  useEffect(() => {
    loadPlatforms();
    loadUsers(1, 10);
  }, []);

  const loadPlatforms = async () => {
    try {
      const response: any = await platformApi.getAll();
      if (response.code === 200) {
        const data = Array.isArray(response.data) ? response.data : response.data?.content || [];
        setPlatforms(data);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载平台列表失败'));
    }
  };

  const loadUsers = async (page: number, size: number, sortBy: string = 'createdAt', sortDir: string = 'DESC') => {
    setLoading(true);
    try {
      const response: any = await userApi.getAll({ page: page - 1, size, sortBy, sortDir });
      if (response.code === 200) {
        const data = response.data?.content || [];
        setUsers(data);
        setPagination({
          current: page,
          pageSize: size,
          total: response.data?.totalElements || 0,
        });
        const ids = data.map((u: User) => u.id);
        try {
          const countsRes: any = await userApi.getContentCounts(ids);
          if (countsRes?.data && typeof countsRes.data === 'object') {
            const map: ContentCountsMap = {};
            for (const [k, v] of Object.entries(countsRes.data)) {
              map[k] = typeof v === 'number' ? v : Number(v) || 0;
            }
            setContentCounts(map);
          }
        } catch {
          setContentCounts({});
        }
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载用户列表失败'));
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingUser(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (user: User) => {
    setEditingUser(user);
    form.setFieldsValue({
      platformId: user.platform?.id,
      username: user.username,
      userId: user.userId,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl ?? '',
      selfIntroduction: user.selfIntroduction ?? '',
      isActive: user.isActive,
    });
    setModalVisible(true);
  };

  const handleRefreshProfile = async (user: User) => {
    setRefreshingProfileId(user.id);
    try {
      const response: any = await userApi.refreshProfile(user.id);
      if (response.code === 200) {
        message.success('已拉取并更新头像与简介');
        loadUsers(pagination.current, pagination.pageSize);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '拉取资料失败'));
    } finally {
      setRefreshingProfileId(null);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      const response: any = await userApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        loadUsers(pagination.current, pagination.pageSize);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败'));
    }
  };

  const handleToggleStatus = async (id: string, isActive: boolean) => {
    try {
      const response: any = await userApi.toggleStatus(id, !isActive);
      if (response.code === 200) {
        message.success(isActive ? '已禁用' : '已启用');
        loadUsers(pagination.current, pagination.pageSize);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };
  
  const openRefreshModal = (user: User) => {
    setRefreshUser(user);
    setRefreshModalVisible(true);
  };

  const pollTaskOnce = async (taskId: string) => {
    try {
      const res: any = await taskApi.getFetchTask(taskId);
      if (res?.code === 200 && res.data) {
        const t = res.data;
        setCurrentTask(t);
        setCurrentProgress(t?.progress ?? 0);
        return t;
      }
    } catch {
      // ignore
    }
    return null;
  };

  const startProgressPolling = (task: any) => {
    const taskId = task?.id;
    if (!taskId) return;
    setCurrentTask(task);
    setCurrentProgress(task?.progress ?? 0);
    if (progressTimerRef.current) {
      window.clearInterval(progressTimerRef.current);
    }
    // 立即请求一次任务详情，避免弹窗一直显示 PENDING（后端异步启动后状态会变为 RUNNING）
    pollTaskOnce(taskId).then((t) => {
      if (t?.status === 'COMPLETED' || t?.status === 'FAILED' || t?.status === 'CANCELLED') {
        if (progressTimerRef.current) {
          window.clearInterval(progressTimerRef.current);
          progressTimerRef.current = null;
        }
        if (t.status === 'COMPLETED') message.success('刷新完成');
        else if (t.status === 'FAILED') message.error(t.errorMessage || '刷新失败');
      }
    });
    const timer = window.setInterval(async () => {
      try {
        const t = await pollTaskOnce(taskId);
        if (t?.status === 'COMPLETED' || t?.status === 'FAILED' || t?.status === 'CANCELLED') {
          window.clearInterval(timer);
          progressTimerRef.current = null;
          if (t.status === 'COMPLETED') {
            message.success('刷新完成');
          } else if (t.status === 'FAILED') {
            message.error(t.errorMessage || '刷新失败');
          }
        }
      } catch {
        // ignore polling errors
      }
    }, 1000);
    progressTimerRef.current = timer;
  };

  const handleConfirmRefresh = async () => {
    if (!refreshUser) return;
    try {
      const response: any = await userApi.fetchContent(refreshUser.id);
      if (response.code === 200) {
        message.success('刷新任务已提交');
        setRefreshModalVisible(false);
        const task = response.data;
        if (task?.id) {
          startProgressPolling(task);
          setProgressModalVisible(true);
        }
      }
    } catch (error: any) {
      message.error(getApiErrorMessage(error, '提交刷新任务失败'));
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingUser) {
        const response: any = await userApi.update(editingUser.id, values);
        if (response.code === 200) {
          message.success('更新成功');
          setModalVisible(false);
          loadUsers(pagination.current, pagination.pageSize);
        }
      } else {
        const response: any = await userApi.create(values);
        if (response.code === 200) {
          message.success('创建成功');
          setModalVisible(false);
          loadUsers(pagination.current, pagination.pageSize);
        }
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      message.error(getApiErrorMessage(error, editingUser ? '更新失败' : '创建失败'));
    }
  };

  const columns = [
    {
      title: '用户',
      key: 'user',
      width: 180,
      render: (_: any, record: User) => {
        const src = getAvatarSrc(record.avatarUrl);
        return (
          <div className="users-table-user-cell">
            <Avatar src={src} size={40} icon={<UserOutlined />} className={!src ? 'users-table-avatar-placeholder' : ''} />
            <div className="users-table-user-cell__text">
              <span className="users-table-username">{record.username}</span>
              {record.displayName && <span className="users-table-displayname">{record.displayName}</span>}
            </div>
          </div>
        );
      },
    },
    {
      title: '用户ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 110,
      render: (v: string) => <Typography.Text type="secondary" className="users-table-userid">{v}</Typography.Text>,
    },
    {
      title: '简介',
      dataIndex: 'selfIntroduction',
      key: 'selfIntroduction',
      width: 280,
      render: (text: string) => {
        if (!text) return <span className="users-table-empty">-</span>;
        return <div className="users-table-intro-full">{text}</div>;
      },
    },
    {
      title: '文章数',
      key: 'contentCount',
      width: 88,
      align: 'center' as const,
      render: (_: any, record: User) => (
        <Typography.Text type="secondary" className="users-table-count">
          {contentCounts[record.id] ?? 0}
        </Typography.Text>
      ),
    },
    {
      title: '平台',
      key: 'platform',
      width: 140,
      render: (_: any, record: User) => {
        const platform = record.platform;
        const platformMeta = platforms.find((p) => p.id === platform?.id);
        const avatarSrc = getPlatformAvatarSrc(platformMeta?.avatarUrl);
        const name = platform?.name;
        if (!name) return <span className="users-table-empty">-</span>;
        return (
          <div className="users-table-platform-cell">
            {avatarSrc && <Avatar src={avatarSrc} size={24} className="users-table-platform-avatar" />}
            <span className="users-table-platform">{name}</span>
          </div>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'isActive',
      key: 'isActive',
      width: 72,
      align: 'center' as const,
      render: (isActive: boolean, record: User) => (
        <Switch
          checked={isActive}
          onChange={() => handleToggleStatus(record.id, isActive)}
          className="users-table-switch"
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 168,
      render: (text: string) => <Typography.Text type="secondary" className="users-table-date">{new Date(text).toLocaleString('zh-CN')}</Typography.Text>,
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right' as const,
      render: (_: any, record: User) => (
        <Space size="small" wrap className="users-table-actions">
          <Button type="link" size="small" icon={<UserAddOutlined />} onClick={() => handleRefreshProfile(record)} loading={refreshingProfileId === record.id} className="users-table-action-btn">
            拉取资料
          </Button>
          <Button type="link" size="small" icon={<ReloadOutlined />} onClick={() => openRefreshModal(record)} className="users-table-action-btn">
            刷新内容
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} className="users-table-action-btn">
            编辑
          </Button>
          <Popconfirm title="确定要删除这个用户吗？" onConfirm={() => handleDelete(record.id)} okText="删除" cancelText="取消" okButtonProps={{ danger: true }}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} className="users-table-action-btn">删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <MainLayout>
      <div className="users-page users-page--premium">
        <header className="users-page__header">
          <div className="users-page__title-wrap">
            <h1 className="users-page__title">用户管理</h1>
            <p className="users-page__subtitle">管理追踪用户、平台与拉取任务</p>
          </div>
          <Space size="middle">
            <Button
              icon={<ReloadOutlined />}
              onClick={() => loadUsers(pagination.current, pagination.pageSize)}
              loading={loading}
              className="users-page__refresh-btn"
            >
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate} className="users-page__add-btn">
              添加用户
            </Button>
          </Space>
        </header>

        <div className="users-page__table-wrap">
          {users.length === 0 && !loading ? (
            <div className="users-empty-card">
              <div className="users-empty">
                <div className="users-empty__icon">
                  <TeamOutlined />
                </div>
                <p className="users-empty__title">暂无用户</p>
                <p className="users-empty__desc">点击「添加用户」创建第一个追踪用户</p>
                <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate} className="users-empty__action">
                  添加用户
                </Button>
              </div>
            </div>
          ) : (
            <div className="users-table-card">
              <Table
                className="users-table"
                columns={columns}
                dataSource={users}
                rowKey="id"
                loading={loading}
                size="middle"
                bordered={false}
                rowClassName={(_, index) => (index % 2 === 0 ? 'users-table-row-even' : 'users-table-row-odd')}
                pagination={{
                  current: pagination.current,
                  pageSize: pagination.pageSize,
                  total: pagination.total,
                  showSizeChanger: true,
                  showTotal: (total) => `共 ${total} 人`,
                  onChange: (page, size) => loadUsers(page, size ?? pagination.pageSize),
                  className: 'users-table-pagination',
                }}
                scroll={{ x: 1200 }}
              />
            </div>
          )}
        </div>

        <Modal
          title={editingUser ? '编辑用户' : '添加用户'}
          open={modalVisible}
          onOk={handleSubmit}
          onCancel={() => setModalVisible(false)}
          width={600}
        >
          <Form form={form} layout="vertical">
            <Form.Item
              name="platformId"
              label="平台"
              rules={[{ required: true, message: '请选择平台' }]}
            >
              <Select placeholder="选择平台">
                {platforms.map(platform => (
                  <Select.Option key={platform.id} value={platform.id}>
                    {platform.name} ({platform.type})
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item
              name="username"
              label="用户名"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input placeholder="例如：octocat" />
            </Form.Item>
            <Form.Item
              name="userId"
              label="用户ID"
              rules={[{ required: true, message: '请输入用户ID' }]}
            >
              <Input placeholder="例如：octocat" />
            </Form.Item>
            <Form.Item
              name="displayName"
              label="显示名称"
            >
              <Input placeholder="例如：The Octocat" />
            </Form.Item>
            <Form.Item
              label="用户头像"
              extra="上传图片或填写图片 URL；填写 URL 时保存后会自动下载到本地"
            >
              <Space direction="vertical" style={{ width: '100%' }}>
                <Upload
                  accept="image/png,image/jpeg,image/jpg,image/gif,image/webp,image/svg+xml"
                  maxCount={1}
                  showUploadList={false}
                  beforeUpload={(file: File) => {
                    const isLt2M = file.size / 1024 / 1024 < 2;
                    if (!isLt2M) {
                      message.error('图片大小不能超过 2MB');
                      return false;
                    }
                    userApi.uploadAvatar(file).then((res: any) => {
                      if (res?.code === 200 && res?.data?.url) {
                        form.setFieldValue('avatarUrl', res.data.url);
                        message.success('上传成功');
                      }
                    }).catch((e) => {
                      message.error(getApiErrorMessage(e, '上传失败'));
                    });
                    return false;
                  }}
                >
                  <Button icon={<UploadOutlined />}>选择图片上传</Button>
                </Upload>
                <Form.Item name="avatarUrl" noStyle>
                  <Input
                    placeholder="或填写图片 URL，如 https://example.com/avatar.png（保存时将下载到本地）"
                    allowClear
                  />
                </Form.Item>
              </Space>
            </Form.Item>
            {editingUser && (editingUser.avatarUrl || editingUser.selfIntroduction) && (
              <Form.Item label="已拉取资料">
                <Space direction="vertical" style={{ width: '100%' }} size="small">
                  {editingUser.avatarUrl && (
                    <div>
                      <span style={{ marginRight: 8 }}>头像：</span>
                      <Avatar src={getAvatarSrc(editingUser.avatarUrl)} size={40} icon={<UserOutlined />} />
                    </div>
                  )}
                  {editingUser.selfIntroduction && (
                    <div>
                      <span style={{ marginRight: 8 }}>简介：</span>
                      <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ display: 'inline-block', maxWidth: 400, marginBottom: 0 }}>
                        {editingUser.selfIntroduction}
                      </Typography.Paragraph>
                    </div>
                  )}
                </Space>
              </Form.Item>
            )}
            <Form.Item name="selfIntroduction" label="简介">
              <Input.TextArea rows={3} placeholder="可从平台拉取后在此编辑" />
            </Form.Item>
            <Form.Item
              name="isActive"
              label="状态"
              valuePropName="checked"
              initialValue={true}
            >
              <Switch checkedChildren="启用" unCheckedChildren="禁用" />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title="刷新内容"
          open={refreshModalVisible}
          onOk={handleConfirmRefresh}
          onCancel={() => setRefreshModalVisible(false)}
          okText="提交刷新"
        >
          <p>用户：{refreshUser?.username}</p>
          <p style={{ color: 'var(--ant-color-text-secondary)', fontSize: 12, marginTop: 8 }}>
            将按页请求接口，直至无更多数据，全部保存到本地。
          </p>
        </Modal>

        <Modal
          title="刷新进度"
          open={progressModalVisible}
          footer={null}
          onCancel={() => {
            setProgressModalVisible(false);
            if (progressTimerRef.current) {
              window.clearInterval(progressTimerRef.current);
              progressTimerRef.current = null;
            }
          }}
        >
          {(currentTask || refreshUser) && (
            (() => {
              const status = currentTask?.status ?? 'PENDING';
              const fetched = currentTask?.fetchedCount ?? 0;
              const total = currentTask?.totalCount ?? 0;
              const percent = currentProgress ?? 0;
              const progressStatus = status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'exception' : 'active';
              const showCountProgress = total > 0 && (status === 'RUNNING' || status === 'COMPLETED');
              const countPercent = total > 0 ? Math.min(100, Math.round((fetched / total) * 100)) : percent;
              return (
                <Space direction="vertical" style={{ width: '100%' }} size="middle">
                  <div>用户：{currentTask?.user?.username ?? refreshUser?.username ?? '-'}</div>
                  <div>状态：{status}</div>
                  {status === 'FAILED' && currentTask?.errorMessage && (
                    <div style={{ color: '#ff4d4f', marginBottom: 4 }}>
                      失败原因：{currentTask.errorMessage}
                    </div>
                  )}
                  {status === 'COMPLETED' && fetched === 0 && (
                    <div style={{ color: '#faad14', marginBottom: 4 }}>
                      未拉取到新内容，可能接口无数据、时间范围内无新文章，或响应格式与预期不符。可查看后端日志确认。
                    </div>
                  )}
                  <div>
                    <div style={{ marginBottom: 4, fontSize: 12, color: '#666' }}>
                      {showCountProgress ? `已拉取 ${fetched} / ${total} 条` : '进度'}
                    </div>
                    <Progress
                      percent={showCountProgress ? countPercent : percent}
                      status={progressStatus}
                      strokeColor={status === 'RUNNING' ? { '0%': '#1890ff', '100%': '#52c41a' } : undefined}
                    />
                  </div>
                </Space>
              );
            })()
          )}
        </Modal>
      </div>

      <style>{`
        .users-page--premium { padding: 0 8px 24px; max-width: 1400px; margin: 0 auto; }
        .users-page__header {
          display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 20px;
          margin-bottom: 24px; padding-bottom: 20px; border-bottom: 1px solid rgba(0,0,0,0.06);
        }
        .users-page__title-wrap { margin: 0; }
        .users-page__title {
          margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -0.02em;
          background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
          -webkit-background-clip: text; -webkit-text-fill-color: transparent;
          background-clip: text;
        }
        .users-page__subtitle { margin: 6px 0 0; font-size: 14px; color: #8c8c8c; font-weight: 400; }
        .users-page__refresh-btn { min-width: 88px; }
        .users-page__add-btn { min-width: 100px; box-shadow: 0 2px 4px rgba(15, 52, 96, 0.2); }

        .users-page__table-wrap { min-height: 320px; }

        .users-empty-card {
          border-radius: 12px; overflow: hidden;
          box-shadow: 0 1px 3px rgba(0,0,0,0.05); border: 1px solid rgba(0,0,0,0.06);
          background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
        }
        .users-empty { text-align: center; padding: 64px 24px; }
        .users-empty__icon {
          width: 80px; height: 80px; margin: 0 auto 20px; border-radius: 50%;
          background: linear-gradient(135deg, #e8eef4 0%, #dce6f0 100%);
          display: flex; align-items: center; justify-content: center; font-size: 36px; color: #8c8c8c;
        }
        .users-empty__title { margin: 0; font-size: 18px; font-weight: 600; color: #262626; }
        .users-empty__desc { margin: 10px 0 20px; font-size: 14px; color: #8c8c8c; }
        .users-empty__action { border-radius: 8px; }

        .users-table-card {
          border-radius: 12px; overflow: hidden;
          box-shadow: 0 1px 3px rgba(0,0,0,0.05); border: 1px solid rgba(0,0,0,0.06);
        }
        .users-table-card .ant-table-wrapper { border-radius: 12px; }
        .users-table .ant-table-thead > tr > th {
          background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%) !important;
          font-weight: 600; color: #1a1a2e; font-size: 13px; padding: 14px 20px;
          border-bottom: 1px solid rgba(0,0,0,0.06);
        }
        .users-table .ant-table-tbody > tr > td {
          padding: 14px 20px; font-size: 13px; border-bottom: 1px solid #f5f5f5;
          vertical-align: middle;
        }
        .users-table .ant-table-tbody > tr.users-table-row-even { background: #fff; }
        .users-table .ant-table-tbody > tr.users-table-row-odd { background: #fafbfc; }
        .users-table .ant-table-tbody > tr:hover > td { background: #f0f7ff !important; }
        .users-table-user-cell { display: flex; align-items: center; gap: 12px; }
        .users-table-user-cell__text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
        .users-table-avatar-placeholder { background: linear-gradient(135deg, #e8eef4 0%, #dce6f0 100%) !important; color: #8c8c8c; }
        .users-table-username { font-weight: 500; color: #1a1a2e; font-size: 13px; }
        .users-table-displayname { font-size: 12px; color: #8c8c8c; }
        .users-table-platform-cell { display: flex; align-items: center; gap: 8px; }
        .users-table-platform-avatar { flex-shrink: 0; }
        .users-table-intro-full { font-size: 13px; color: #595959; line-height: 1.6; white-space: pre-wrap; word-break: break-word; max-height: 120px; overflow-y: auto; }
        .users-table-userid { font-size: 12px !important; font-family: ui-monospace, monospace; }
        .users-table-tags { font-size: 12px; color: #595959; }
        .users-table-platform { font-weight: 500; color: #262626; }
        .users-table-empty { color: #bfbfbf; }
        .users-table-date { font-size: 12px !important; }
        .users-table-actions { display: flex; flex-wrap: wrap; gap: 0; }
        .users-table-action-btn { padding: 0 6px; font-size: 12px; }
        .users-table-pagination { padding: 16px 20px !important; margin: 0 !important; }
        
        @media (max-width: 767px) {
          .users-page--premium {
            padding: 0 4px 16px;
          }
          .users-page__header {
            flex-direction: column;
            align-items: flex-start;
            gap: 12px;
            margin-bottom: 16px;
            padding-bottom: 12px;
          }
          .users-page__title {
            font-size: 20px;
          }
          .users-page__subtitle {
            font-size: var(--text-body-sm-size);
          }
          .users-table-card .ant-card-body {
            padding: 0;
          }
          .users-table {
            font-size: 12px;
          }
          .users-table .ant-table-thead > tr > th {
            padding: 8px 4px !important;
            font-size: 11px;
          }
          .users-table .ant-table-tbody > tr > td {
            padding: 8px 4px !important;
            font-size: 11px;
          }
          .users-table-user-cell {
            gap: 8px;
          }
          .users-table-actions {
            flex-direction: column;
            gap: 4px;
          }
        }
      `}</style>
    </MainLayout>
  );
}

export default Users;
