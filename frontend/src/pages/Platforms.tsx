import { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, message, Popconfirm, Tag, Avatar, Upload, Card, Typography } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, CheckCircleOutlined, ReloadOutlined, UserOutlined, UploadOutlined, ToolOutlined } from '@ant-design/icons';
import { platformApi, contentApi, getApiErrorMessage, getPlatformAvatarSrc } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';

interface Platform {
  id: string;
  name: string;
  type: string;
  apiBaseUrl: string;
  avatarUrl?: string;
  config?: string;
  status: string;
  createdAt: string;
}

const { Text } = Typography;

function Platforms() {
  const [platforms, setPlatforms] = useState<Platform[]>([]);
  const [loading, setLoading] = useState(false);
  const [fixingImages, setFixingImages] = useState(false);
  const [fixingEncrypted, setFixingEncrypted] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingPlatform, setEditingPlatform] = useState<Platform | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadPlatforms();
  }, []);

  const loadPlatforms = async () => {
    setLoading(true);
    try {
      const response: any = await platformApi.getAll();
      if (response.code === 200) {
        const data = Array.isArray(response.data) ? response.data : response.data?.content || [];
        setPlatforms(data);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载平台列表失败'));
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingPlatform(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (platform: Platform) => {
    setEditingPlatform(platform);
    form.setFieldsValue({
      name: platform.name,
      type: platform.type,
      apiBaseUrl: platform.apiBaseUrl,
      avatarUrl: platform.avatarUrl,
      config: typeof platform.config === 'string' ? platform.config : (platform.config ? JSON.stringify(platform.config, null, 2) : undefined),
      status: platform.status,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: string) => {
    try {
      const response: any = await platformApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        loadPlatforms();
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败'));
    }
  };

  const handleFixTimestoreImages = async () => {
    setFixingImages(true);
    try {
      const response: any = await contentApi.fixTimestoreImages();
      if (response?.code === 200 && response?.data != null) {
        const fixed = response.data.fixedCount ?? 0;
        message.success(`修复完成，共更新 ${fixed} 篇文章的图片地址`);
      } else {
        message.error(response?.message || '修复失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '修复失败'));
    } finally {
      setFixingImages(false);
    }
  };

  const handleFixTimestoreEncrypted = async () => {
    setFixingEncrypted(true);
    try {
      const response: any = await contentApi.fixTimestoreEncrypted();
      if (response?.code === 200 && response?.data != null) {
        const fixed = response.data.fixedCount ?? 0;
        message.success(`修复完成，共更新 ${fixed} 篇加密文章`);
      } else {
        message.error(response?.message || '修复失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '修复失败'));
    } finally {
      setFixingEncrypted(false);
    }
  };

  const handleTestConnection = async (id: string) => {
    try {
      const response: any = await platformApi.testConnection(id);
      if (response.code === 200 && response.data) {
        message.success('连接测试成功');
      } else {
        message.error('连接测试失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '连接测试失败'));
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const configStr = values.config?.trim();
      let config = undefined;
      if (configStr) {
        try {
          JSON.parse(configStr);
          config = configStr;
        } catch {
          message.error('配置必须是合法 JSON');
          return;
        }
      }
      const payload = {
        name: values.name,
        type: values.type,
        apiBaseUrl: values.apiBaseUrl?.trim() || undefined,
        avatarUrl: values.avatarUrl?.trim() || undefined,
        config,
        status: values.status,
      };
      if (editingPlatform) {
        const response: any = await platformApi.update(editingPlatform.id, payload);
        if (response?.code === 200) {
          message.success('更新成功');
          setModalVisible(false);
          loadPlatforms();
        } else {
          message.error(response?.message || '更新失败');
        }
      } else {
        const response: any = await platformApi.create({ name: payload.name, type: payload.type, apiBaseUrl: payload.apiBaseUrl, avatarUrl: payload.avatarUrl, config: payload.config });
        if (response?.code === 200) {
          message.success('创建成功');
          setModalVisible(false);
          loadPlatforms();
        } else {
          message.error(response?.message || '创建失败');
        }
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      message.error(getApiErrorMessage(error, editingPlatform ? '更新失败' : '创建失败'));
    }
  };

  const columns = [
    {
      title: '平台',
      key: 'platform',
      render: (_: any, record: Platform) => (
        <Space size={8}>
          <Avatar
            src={getPlatformAvatarSrc(record.avatarUrl)}
            icon={!record.avatarUrl ? <UserOutlined /> : undefined}
            shape="square"
            size={28}
            alt={record.name}
          />
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span>{record.name}</span>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.type}
            </Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'API地址',
      dataIndex: 'apiBaseUrl',
      key: 'apiBaseUrl',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'red'}>
          {status === 'ACTIVE' ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text: string) => new Date(text).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, record: Platform) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <Button
            type="text"
            size="small"
            icon={<CheckCircleOutlined />}
            onClick={() => handleTestConnection(record.id)}
          >
            测试连接
          </Button>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑平台
          </Button>
          <Popconfirm
            title="确定要删除这个平台吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
            >
              删除平台
            </Button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <MainLayout>
      <div>
        {/* 顶部标题区 */}
        <div
          style={{
            marginBottom: 24,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div>
            <h2 style={{ margin: 0 }}>平台管理</h2>
            <Text type="secondary">
              统一管理内容来源平台，配置连接信息与状态。
            </Text>
          </div>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadPlatforms} loading={loading}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              添加平台
            </Button>
          </Space>
        </div>

        {/* TimeStore 专用工具区 */}
        {platforms.some((p) => p.type === 'TIMESTORE') && (
          <Card
            style={{ marginBottom: 16 }}
            title="TimeStore 工具"
            size="small"
          >
            <Space wrap>
              <Button
                icon={<ToolOutlined />}
                onClick={handleFixTimestoreImages}
                loading={fixingImages}
              >
                修复 TimeStore 图片
              </Button>
              <Button
                icon={<ToolOutlined />}
                onClick={handleFixTimestoreEncrypted}
                loading={fixingEncrypted}
              >
                修复 TimeStore 加密文章
              </Button>
            </Space>
          </Card>
        )}

        {/* 平台列表区 */}
        <Card
          title={`平台列表（${platforms.length}）`}
          bodyStyle={{ paddingTop: 8 }}
        >
          <Table
            columns={columns}
            dataSource={platforms}
            rowKey="id"
            loading={loading}
            size="small"
            pagination={{ pageSize: 20, showSizeChanger: false }}
          />
        </Card>

        <Modal
          title={editingPlatform ? '编辑平台' : '添加平台'}
          open={modalVisible}
          onOk={handleSubmit}
          onCancel={() => setModalVisible(false)}
          width={600}
        >
          <Form form={form} layout="vertical">
            <Form.Item
              name="name"
              label="平台名称"
              rules={[{ required: true, message: '请输入平台名称' }]}
            >
              <Input placeholder="例如：GitHub" />
            </Form.Item>
            <Form.Item
              name="type"
              label="平台类型"
              rules={[{ required: true, message: '请选择平台类型' }]}
            >
              <Select placeholder="选择平台类型">
                <Select.Option value="GITHUB">GitHub</Select.Option>
                <Select.Option value="TWITTER">Twitter/X</Select.Option>
                <Select.Option value="WEIBO">微博</Select.Option>
                <Select.Option value="ZHIHU">知乎</Select.Option>
                <Select.Option value="ZSXQ">知识星球</Select.Option>
                <Select.Option value="TIMESTORE">TimeStore (timestore.vip)</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item
              name="apiBaseUrl"
              label="API基础地址"
              rules={[{ required: true, message: '请输入API基础地址' }]}
              extra="TimeStore 请填站点根地址，如 https://xxx.timestore.vip"
            >
              <Input placeholder="例如：https://api.github.com 或 https://xxx.timestore.vip" />
            </Form.Item>
            <Form.Item
              label="平台头像"
              extra="上传图片或填写图片 URL；填写 URL 时保存后会自动下载到本地"
            >
              <Space direction="vertical" style={{ width: '100%' }}>
                <Upload
                  accept="image/png,image/jpeg,image/jpg,image/gif,image/webp,image/svg+xml"
                  maxCount={1}
                  showUploadList={false}
                  beforeUpload={(file) => {
                    const isLt2M = file.size / 1024 / 1024 < 2;
                    if (!isLt2M) {
                      message.error('图片大小不能超过 2MB');
                      return false;
                    }
                    platformApi.uploadAvatar(file).then((res: any) => {
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
                    placeholder="或填写图片 URL，如 https://example.com/logo.png（保存时将下载到本地）"
                    allowClear
                  />
                </Form.Item>
              </Space>
            </Form.Item>
            <Form.Item
              name="config"
              label="配置（JSON格式）"
              extra='TimeStore 需填写 mateAuth；知识星球需填写 accessToken、groupId'
            >
              <Input.TextArea 
                rows={4} 
                placeholder='GitHub: {"rateLimit": 5000}；TimeStore: {"mateAuth": "你的 mate-auth token"}；知识星球: {"accessToken": "zsxq_access_token", "groupId": "星球ID如88885511211582"}'
              />
            </Form.Item>
            <Form.Item
              name="status"
              label="状态"
              initialValue="ACTIVE"
            >
              <Select>
                <Select.Option value="ACTIVE">启用</Select.Option>
                <Select.Option value="INACTIVE">禁用</Select.Option>
              </Select>
            </Form.Item>
          </Form>
        </Modal>
      </div>
      <style>{`
        @media (max-width: 767px) {
          .ant-card {
            margin-bottom: 12px;
          }
          .ant-card-head {
            padding: 12px 16px;
          }
          .ant-card-body {
            padding: 12px;
          }
          .ant-table {
            font-size: 12px;
          }
          .ant-table-thead > tr > th {
            padding: 8px 4px !important;
            font-size: 11px;
          }
          .ant-table-tbody > tr > td {
            padding: 8px 4px !important;
            font-size: 11px;
          }
          .ant-modal {
            margin: 12px;
            max-width: calc(100vw - 24px);
          }
          .ant-modal-content {
            padding: 16px;
          }
          .ant-form-item-label > label {
            font-size: 13px;
          }
        }
      `}</style>
    </MainLayout>
  );
}

export default Platforms;
