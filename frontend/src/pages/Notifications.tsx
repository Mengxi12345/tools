import { useEffect, useState } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, Switch, message, Popconfirm, Card, Dropdown } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import { notificationRuleApi, notificationChannelConfigApi, userApi, getApiErrorMessage } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';

interface Rule {
  id: string;
  name: string;
  ruleType?: string;
  config?: Record<string, unknown>;
  isEnabled: boolean;
  createdAt: string;
}

interface UserOption {
  id: string;
  username: string;
  displayName?: string;
  platform?: { name: string };
}

const RULE_TYPES = [
  { value: 'KEYWORD', label: '关键词' },
  { value: 'AUTHOR', label: '作者' },
  { value: 'PLATFORM', label: '平台' },
  { value: 'QQ_GROUP', label: '发送到 QQ 群（监听指定用户新消息）' },
  { value: 'FEISHU', label: '发送到飞书（监听指定用户新消息）' },
];

/** 监听用户选项展示：作者名 · 平台（账号） */
function formatUserOptionLabel(u: UserOption): string {
  const authorName = u.displayName || u.username;
  const platformPart = u.platform?.name ? ` · ${u.platform.name}` : '';
  const accountPart = u.displayName && u.username && u.displayName !== u.username ? `（${u.username}）` : '';
  return `${authorName}${platformPart}${accountPart}`;
}

/** 根据 userId 列表解析为作者名展示（用于表格等） */
function formatUserIdsToAuthorNames(userIds: string[] | undefined, users: UserOption[]): string {
  if (!Array.isArray(userIds) || userIds.length === 0) return '—';
  const names = userIds.map((id) => {
    const u = users.find((x) => x.id === id);
    return u ? (u.displayName || u.username) : id;
  });
  return names.join('、');
}

function Notifications() {
  const [rules, setRules] = useState<Rule[]>([]);
  const [users, setUsers] = useState<UserOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRule, setEditingRule] = useState<Rule | null>(null);
  const [testingRuleId, setTestingRuleId] = useState<string | null>(null);
  const [testWithConfigLoading, setTestWithConfigLoading] = useState(false);
  const [savedChannelConfigs, setSavedChannelConfigs] = useState<{ id: string; name: string; channelType: string; config: Record<string, unknown> }[]>([]);
  const [saveConfigModalVisible, setSaveConfigModalVisible] = useState(false);
  const [saveConfigLoading, setSaveConfigLoading] = useState(false);
  const [saveConfigForm] = Form.useForm();
  const [form] = Form.useForm();
  const [testForm] = Form.useForm();
  const currentRuleType = Form.useWatch('ruleType', form);

  const loadRules = async (page: number = 1, size: number = 10) => {
    setLoading(true);
    try {
      const res: any = await notificationRuleApi.getPage({ page: page - 1, size });
      if (res?.code === 200 && res?.data) {
        const content = res.data?.content ?? [];
        setRules(content);
        setPagination({
          current: page,
          pageSize: size,
          total: res.data?.totalElements ?? 0,
        });
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载通知规则失败'));
    } finally {
      setLoading(false);
    }
  };

  const loadUsers = async () => {
    try {
      const res: any = await userApi.getAll({ page: 0, size: 500 });
      if (res?.code === 200 && res?.data?.content) {
        setUsers(Array.isArray(res.data.content) ? res.data.content : []);
      }
    } catch {
      setUsers([]);
    }
  };

  useEffect(() => {
    loadRules(1, 10);
  }, []);

  useEffect(() => {
    loadUsers();
  }, []);

  useEffect(() => {
    if (modalVisible) loadUsers();
  }, [modalVisible]);

  useEffect(() => {
    if (!modalVisible) {
      setSavedChannelConfigs([]);
      return;
    }
    if (currentRuleType === 'FEISHU' || currentRuleType === 'QQ_GROUP') {
      notificationChannelConfigApi.list({ channelType: currentRuleType }).then((res: any) => {
        if (res?.code === 200 && Array.isArray(res.data)) setSavedChannelConfigs(res.data);
        else setSavedChannelConfigs([]);
      }).catch(() => setSavedChannelConfigs([]));
    } else {
      setSavedChannelConfigs([]);
    }
  }, [modalVisible, currentRuleType]);

  const handleCreate = () => {
    setEditingRule(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (r: Rule) => {
    setEditingRule(r);
    const c = (r.config || {}) as Record<string, unknown>;
    form.setFieldsValue({
      name: r.name,
      ruleType: r.ruleType,
      isEnabled: r.isEnabled,
      userIds: Array.isArray(c.userIds) ? c.userIds : [],
      qqGroupId: c.qqGroupId ?? '',
      qqBotType: c.qqBotType ?? 'go-cqhttp',
      qqSessionKey: c.qqSessionKey ?? '',
      qqApiUrl: c.qqApiUrl ?? '',
      messageTemplate: c.messageTemplate ?? '',
      authorDisplayName: c.authorDisplayName ?? '',
      feishuAppId: c.feishuAppId ?? '',
      feishuAppSecret: c.feishuAppSecret ?? '',
      feishuReceiveId: c.feishuReceiveId ?? '',
      feishuReceiveIdType: c.feishuReceiveIdType ?? 'chat_id',
      feishuMessageTemplate: c.messageTemplate ?? '',
    });
    setModalVisible(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const ruleType = values.ruleType;
      const payload: Record<string, unknown> = {
        name: values.name,
        ruleType,
        isEnabled: values.isEnabled ?? true,
      };
      if (ruleType === 'QQ_GROUP') {
        payload.config = {
          userIds: values.userIds ?? [],
          qqGroupId: values.qqGroupId?.trim() || '',
          qqBotType: values.qqBotType ?? 'go-cqhttp',
          qqSessionKey: values.qqSessionKey?.trim() || '',
          qqApiUrl: values.qqApiUrl?.trim() || '',
          messageTemplate: values.messageTemplate?.trim() || '',
          authorDisplayName: values.authorDisplayName?.trim() || '',
        };
      }
      if (ruleType === 'FEISHU') {
        payload.config = {
          userIds: values.userIds ?? [],
          feishuAppId: values.feishuAppId?.trim() || '',
          feishuAppSecret: values.feishuAppSecret?.trim() || '',
          feishuReceiveId: values.feishuReceiveId?.trim() || '',
          feishuReceiveIdType: values.feishuReceiveIdType ?? 'chat_id',
          messageTemplate: values.feishuMessageTemplate?.trim() || '',
          authorDisplayName: values.authorDisplayName?.trim() || '',
        };
      }
      if (editingRule) {
        const res: any = await notificationRuleApi.update(editingRule.id, payload);
        if (res?.code === 200) {
          message.success('更新成功');
          setModalVisible(false);
          loadRules(pagination.current, pagination.pageSize);
        } else {
          message.error(res?.message || '更新失败');
        }
      } else {
        const res: any = await notificationRuleApi.create(payload);
        if (res?.code === 200) {
          message.success('创建成功');
          setModalVisible(false);
          loadRules(pagination.current, pagination.pageSize);
        } else {
          message.error(res?.message || '创建失败');
        }
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(getApiErrorMessage(e, editingRule ? '更新失败' : '创建失败'));
    }
  };

  /** 页面上直接按规则类型 + 配置测试下发（不依赖已保存规则） */
  const handleTestWithConfig = async () => {
    try {
      const ruleType = testForm.getFieldValue('testRuleType');
      if (!ruleType || (ruleType !== 'QQ_GROUP' && ruleType !== 'FEISHU')) {
        message.warning('请选择规则类型（QQ 群 或 飞书）');
        return;
      }
      const testMode = testForm.getFieldValue('testMode') || 'default';
      const values = await testForm.validateFields();
      const config: Record<string, unknown> = {};
      if (ruleType === 'QQ_GROUP') {
        config.qqGroupId = values.testQqGroupId?.trim() || '';
        config.qqApiUrl = values.testQqApiUrl?.trim() || '';
        config.qqBotType = values.testQqBotType ?? 'go-cqhttp';
        config.qqSessionKey = values.testQqSessionKey?.trim() || '';
      } else {
        config.feishuAppId = values.testFeishuAppId?.trim() || '';
        config.feishuAppSecret = values.testFeishuAppSecret?.trim() || '';
        config.feishuReceiveId = values.testFeishuReceiveId?.trim() || '';
        config.feishuReceiveIdType = values.testFeishuReceiveIdType ?? 'chat_id';
      }
      const body: { ruleType: string; config: Record<string, unknown>; testMode?: string; userIds?: string[] } = { ruleType, config };
      if (testMode) body.testMode = testMode;
      if (testMode === 'random_content' && Array.isArray(values.testUserIds) && values.testUserIds.length > 0) {
        body.userIds = values.testUserIds;
      }
      setTestWithConfigLoading(true);
      const res: any = await notificationRuleApi.testWithConfig(body);
      const data = res?.data;
      if (data?.success) {
        message.success(data.message || '测试消息已发送');
      } else {
        message.error(data?.message || '测试发送失败');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      const msg = e?.response?.data?.message ?? getApiErrorMessage(e, '测试失败');
      message.error(msg);
    } finally {
      setTestWithConfigLoading(false);
    }
  };

  const handleTest = async (id: string, testMode: string = 'default') => {
    setTestingRuleId(id);
    try {
      const res: any = await notificationRuleApi.test(id, { testMode });
      const data = res?.data;
      if (data?.success) {
        message.success(data.message || '测试消息已发送');
      } else {
        message.error(data?.message || '测试发送失败');
      }
    } catch (error: any) {
      const msg = error?.response?.data?.message ?? getApiErrorMessage(error, '测试失败');
      message.error(msg);
    } finally {
      setTestingRuleId(null);
    }
  };

  /** 选择已保存的通道配置，填充表单（仅填充通道相关字段，不含 userIds） */
  const handleSelectSavedConfig = (configId: string) => {
    if (!configId) return;
    const saved = savedChannelConfigs.find((c) => c.id === configId);
    if (!saved?.config) return;
    const c = saved.config as Record<string, unknown>;
    if (currentRuleType === 'QQ_GROUP') {
      form.setFieldsValue({
        qqGroupId: c.qqGroupId ?? '',
        qqBotType: c.qqBotType ?? 'go-cqhttp',
        qqSessionKey: c.qqSessionKey ?? '',
        qqApiUrl: c.qqApiUrl ?? '',
        messageTemplate: c.messageTemplate ?? '',
        authorDisplayName: c.authorDisplayName ?? '',
      });
    }
    if (currentRuleType === 'FEISHU') {
      form.setFieldsValue({
        feishuAppId: c.feishuAppId ?? '',
        feishuAppSecret: c.feishuAppSecret ?? '',
        feishuReceiveId: c.feishuReceiveId ?? '',
        feishuReceiveIdType: c.feishuReceiveIdType ?? 'chat_id',
        feishuMessageTemplate: c.messageTemplate ?? '',
        authorDisplayName: c.authorDisplayName ?? '',
      });
    }
  };

  /** 打开「保存为通道配置」弹窗 */
  const handleOpenSaveConfig = () => {
    saveConfigForm.setFieldsValue({ saveConfigName: '', saveConfigShared: false });
    setSaveConfigModalVisible(true);
  };

  /** 提交保存为通道配置 */
  const handleSaveAsChannelConfig = async () => {
    try {
      const { saveConfigName, saveConfigShared } = await saveConfigForm.validateFields();
      const values = form.getFieldsValue();
      let config: Record<string, unknown> = {};
      if (currentRuleType === 'QQ_GROUP') {
        config = {
          qqGroupId: values.qqGroupId?.trim() ?? '',
          qqBotType: values.qqBotType ?? 'go-cqhttp',
          qqSessionKey: values.qqSessionKey?.trim() ?? '',
          qqApiUrl: values.qqApiUrl?.trim() ?? '',
          messageTemplate: values.messageTemplate?.trim() ?? '',
          authorDisplayName: values.authorDisplayName?.trim() ?? '',
        };
      } else if (currentRuleType === 'FEISHU') {
        config = {
          feishuAppId: values.feishuAppId?.trim() ?? '',
          feishuAppSecret: values.feishuAppSecret?.trim() ?? '',
          feishuReceiveId: values.feishuReceiveId?.trim() ?? '',
          feishuReceiveIdType: values.feishuReceiveIdType ?? 'chat_id',
          messageTemplate: values.feishuMessageTemplate?.trim() ?? '',
          authorDisplayName: values.authorDisplayName?.trim() ?? '',
        };
      }
      setSaveConfigLoading(true);
      const res: any = await notificationChannelConfigApi.create({
        name: saveConfigName.trim(),
        channelType: currentRuleType,
        config,
        isShared: !!saveConfigShared,
      });
      if (res?.code === 200) {
        message.success('已保存为通道配置');
        setSaveConfigModalVisible(false);
        if (currentRuleType === 'FEISHU' || currentRuleType === 'QQ_GROUP') {
          notificationChannelConfigApi.list({ channelType: currentRuleType }).then((r: any) => {
            if (r?.code === 200 && Array.isArray(r.data)) setSavedChannelConfigs(r.data);
          });
        }
      } else {
        message.error(res?.message || '保存失败');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(getApiErrorMessage(e, '保存失败'));
    } finally {
      setSaveConfigLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      const res: any = await notificationRuleApi.delete(id);
      if (res?.code === 200) {
        message.success('删除成功');
        loadRules(pagination.current, pagination.pageSize);
      } else {
        message.error('删除失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败'));
    }
  };

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '类型',
      dataIndex: 'ruleType',
      key: 'ruleType',
      render: (v: string) => RULE_TYPES.find(t => t.value === v)?.label ?? v,
    },
    {
      title: '监听用户（作者名）',
      key: 'userIds',
      render: (_: unknown, record: Rule) => {
        const type = record.ruleType;
        if (type !== 'QQ_GROUP' && type !== 'FEISHU') return '—';
        const userIds = Array.isArray((record.config as any)?.userIds) ? (record.config as any).userIds : [];
        return formatUserIdsToAuthorNames(userIds, users);
      },
    },
    {
      title: '启用',
      dataIndex: 'isEnabled',
      key: 'isEnabled',
      render: (v: boolean) => (v ? '是' : '否'),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (t: string) => (t ? new Date(t).toLocaleString('zh-CN') : ''),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Rule) => (
        <Space>
          {(record.ruleType === 'QQ_GROUP' || record.ruleType === 'FEISHU') && (
            <Dropdown
              menu={{
                items: [
                  { key: 'default', label: '默认语句', onClick: () => handleTest(record.id, 'default') },
                  { key: 'random_content', label: '随机文章（按当前格式）', onClick: () => handleTest(record.id, 'random_content') },
                ],
              }}
            >
              <Button
                type="default"
                size="small"
                icon={<SendOutlined />}
                loading={testingRuleId === record.id}
              >
                测试下发
              </Button>
            </Dropdown>
          )}
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const TEST_RULE_TYPES = [
    { value: 'QQ_GROUP', label: 'QQ 群' },
    { value: 'FEISHU', label: '飞书' },
  ];

  return (
    <MainLayout>
      <div>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
          <h2 style={{ margin: 0 }}>通知规则</h2>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadRules(pagination.current, pagination.pageSize)} loading={loading}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建规则</Button>
          </Space>
        </div>

        <Card title="测试下发" size="small" style={{ marginBottom: 16 }}>
          <Form form={testForm} layout="inline" style={{ flexWrap: 'wrap', gap: 0 }}>
            <Form.Item name="testRuleType" label="规则类型" rules={[{ required: true, message: '请选择' }]} style={{ marginRight: 16, marginBottom: 8 }}>
              <Select placeholder="选择类型" options={TEST_RULE_TYPES} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="testMode" label="测试模式" initialValue="default" style={{ marginRight: 16, marginBottom: 8 }}>
              <Select options={[
                { value: 'default', label: '默认语句' },
                { value: 'random_content', label: '随机文章（按当前格式）' },
              ]} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(prev, curr) => prev.testRuleType !== curr.testRuleType || prev.testMode !== curr.testMode}>
              {({ getFieldValue }) => {
                const type = getFieldValue('testRuleType');
                const testMode = getFieldValue('testMode');
                const needUserIds = testMode === 'random_content';
                return (
                  <>
                    {needUserIds && (
                      <Form.Item name="testUserIds" label="监听用户" rules={[{ required: true, message: '随机文章模式需选择用户' }]} style={{ marginRight: 16, marginBottom: 8 }}>
                        <Select
                          mode="multiple"
                          placeholder="选择用户（随机选其一的一篇文章）"
                          optionFilterProp="label"
                          options={users.map((u) => ({ value: u.id, label: formatUserOptionLabel(u) }))}
                          style={{ width: 240 }}
                        />
                      </Form.Item>
                    )}
                    {type === 'QQ_GROUP' && (
                      <>
                        <Form.Item name="testQqGroupId" label="QQ 群号" rules={[{ required: true, message: '必填' }]} style={{ marginRight: 16, marginBottom: 8 }}>
                          <Input placeholder="群号" style={{ width: 120 }} />
                        </Form.Item>
                        <Form.Item name="testQqApiUrl" label="API 地址" rules={[{ required: true, message: '必填' }]} style={{ marginRight: 16, marginBottom: 8 }}>
                          <Input placeholder="http://localhost:5700" style={{ width: 180 }} />
                        </Form.Item>
                        <Form.Item name="testQqBotType" initialValue="go-cqhttp" style={{ marginRight: 16, marginBottom: 8 }}>
                          <Select options={[{ value: 'go-cqhttp', label: 'go-cqhttp' }, { value: 'mirai', label: 'Mirai' }]} style={{ width: 100 }} />
                        </Form.Item>
                        <Form.Item noStyle shouldUpdate={(p, c) => p.testQqBotType !== c.testQqBotType}>
                          {({ getFieldValue: gf }) =>
                            gf('testQqBotType') === 'mirai' ? (
                              <Form.Item name="testQqSessionKey" label="Session Key" rules={[{ required: true }]} style={{ marginRight: 16, marginBottom: 8 }}>
                                <Input.Password placeholder="Mirai sessionKey" style={{ width: 140 }} />
                              </Form.Item>
                            ) : null
                          }
                        </Form.Item>
                      </>
                    )}
                    {type === 'FEISHU' && (
                      <>
                        <Form.Item name="testFeishuAppId" label="App ID" rules={[{ required: true, message: '必填' }]} style={{ marginRight: 16, marginBottom: 8 }}>
                          <Input placeholder="飞书 App ID" style={{ width: 160 }} />
                        </Form.Item>
                        <Form.Item name="testFeishuAppSecret" label="App Secret" rules={[{ required: true, message: '必填' }]} style={{ marginRight: 16, marginBottom: 8 }}>
                          <Input.Password placeholder="飞书 App Secret" style={{ width: 160 }} />
                        </Form.Item>
                        <Form.Item name="testFeishuReceiveId" label="接收 ID" rules={[{ required: true, message: '必填' }]} style={{ marginRight: 16, marginBottom: 8 }}>
                          <Input placeholder="chat_id / open_id" style={{ width: 160 }} />
                        </Form.Item>
                        <Form.Item name="testFeishuReceiveIdType" initialValue="chat_id" style={{ marginRight: 16, marginBottom: 8 }}>
                          <Select options={[{ value: 'chat_id', label: 'chat_id' }, { value: 'open_id', label: 'open_id' }, { value: 'user_id', label: 'user_id' }]} style={{ width: 100 }} />
                        </Form.Item>
                      </>
                    )}
                  </>
                );
              }}
            </Form.Item>
            <Form.Item style={{ marginBottom: 8 }}>
              <Button type="primary" icon={<SendOutlined />} loading={testWithConfigLoading} onClick={handleTestWithConfig}>
                发送测试消息
              </Button>
            </Form.Item>
          </Form>
        </Card>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={rules}
          loading={loading}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            onChange: (p, s) => loadRules(p, s),
          }}
        />
        <Modal
          title={editingRule ? '编辑规则' : '新建规则'}
          open={modalVisible}
          onOk={handleSubmit}
          onCancel={() => setModalVisible(false)}
          width={560}
          footer={
            (editingRule?.ruleType === 'QQ_GROUP' || editingRule?.ruleType === 'FEISHU') ? (
              [
                <Button key="cancel" onClick={() => setModalVisible(false)}>取消</Button>,
                <Dropdown
                  key="test"
                  menu={{
                    items: [
                      { key: 'default', label: '默认语句', onClick: () => editingRule && handleTest(editingRule.id, 'default') },
                      { key: 'random_content', label: '随机文章（按当前格式）', onClick: () => editingRule && handleTest(editingRule.id, 'random_content') },
                    ],
                  }}
                >
                  <Button icon={<SendOutlined />} loading={editingRule ? testingRuleId === editingRule.id : false}>
                    测试下发
                  </Button>
                </Dropdown>,
                <Button key="submit" type="primary" onClick={handleSubmit}>确定</Button>,
              ]
            ) : undefined
          }
        >
          <Form form={form} layout="vertical">
            <Form.Item name="name" label="规则名称" rules={[{ required: true }]}>
              <Input placeholder="例如：重要关键词提醒 / 某作者新文推送到 QQ 群" />
            </Form.Item>
            <Form.Item name="ruleType" label="规则类型">
              <Select placeholder="选择类型" options={RULE_TYPES} allowClear />
            </Form.Item>

            {/* 独立的规则类型配置模块：始终展示 Card，选择 QQ 群/飞书后显示对应表单项 */}
            <Card
              size="small"
              title="规则类型配置"
              style={{ marginBottom: 16, background: 'var(--color-bg-elevated, #fafafa)', border: '1px solid var(--color-border, #e8e8e8)' }}
            >
              <Form.Item noStyle shouldUpdate={(prev, curr) => prev.ruleType !== curr.ruleType}>
                {({ getFieldValue }) => {
                  const ruleType = getFieldValue('ruleType');
                  if (ruleType === 'QQ_GROUP') {
                    return (
                      <>
                        <div style={{ color: 'var(--color-text-secondary, #666)', marginBottom: 12, fontSize: 12 }}>QQ 群 · 监听指定用户的新内容并推送到 QQ 群</div>
                        <Form.Item label="使用已保存的配置" style={{ marginBottom: 12 }}>
                          <Select
                            allowClear
                            placeholder="选择已保存的配置以填充下方表单"
                            options={savedChannelConfigs.map((c) => ({ value: c.id, label: c.name }))}
                            onChange={handleSelectSavedConfig}
                            style={{ width: '100%' }}
                          />
                        </Form.Item>
                        <Form.Item
                          name="userIds"
                          label="监听用户（作者名 · 平台）"
                          rules={[{ required: true, message: '请选择至少一个用户' }]}
                        >
                          <Select
                            mode="multiple"
                            placeholder="选择要监听的用户，其新保存内容将推送到 QQ 群"
                            optionFilterProp="label"
                            options={users.map((u) => ({ value: u.id, label: formatUserOptionLabel(u) }))}
                          />
                        </Form.Item>
                        <Form.Item name="authorDisplayName" label="作者显示名（可选）">
                          <Input placeholder="下发时 {author} 使用此名称，留空则用用户配置的显示名/账号" />
                        </Form.Item>
                        <Form.Item name="qqGroupId" label="QQ 群号" rules={[{ required: true, message: '请输入 QQ 群号' }]}>
                          <Input placeholder="例如 123456789" />
                        </Form.Item>
                        <Form.Item name="qqBotType" label="QQ 群机器人类型" initialValue="go-cqhttp">
                          <Select options={[{ value: 'go-cqhttp', label: 'go-cqhttp' }, { value: 'mirai', label: 'Mirai（mirai-api-http）' }]} />
                        </Form.Item>
                        <Form.Item noStyle shouldUpdate={(prev: any, curr: any) => prev.qqBotType !== curr.qqBotType}>
                          {({ getFieldValue: gf }) =>
                            gf('qqBotType') === 'mirai' ? (
                              <Form.Item name="qqSessionKey" label="Mirai Session Key" rules={[{ required: true, message: 'Mirai 需填写 Session Key' }]}>
                                <Input.Password placeholder="mirai-api-http 认证后的 sessionKey" />
                              </Form.Item>
                            ) : null
                          }
                        </Form.Item>
                        <Form.Item name="qqApiUrl" label="QQ Bot API 地址（可选）">
                          <Input placeholder="go-cqhttp: http://localhost:5700；Mirai: http://localhost:8080，不填则使用系统默认" />
                        </Form.Item>
                        <Form.Item name="messageTemplate" label="消息模板（可选）">
                          <Input.TextArea rows={3} placeholder="支持占位符：{title} {author} {platform} {url}，留空使用默认模板" />
                        </Form.Item>
                        <Form.Item name="authorDisplayName" label="作者显示名（可选）">
                          <Input placeholder="留空则使用用户配置的显示名/账号；填写后 {author} 将显示此名称" />
                        </Form.Item>
                        <Form.Item>
                          <Button type="default" onClick={handleOpenSaveConfig}>保存为通道配置（可共享）</Button>
                        </Form.Item>
                      </>
                    );
                  }
                  if (ruleType === 'FEISHU') {
                    return (
                      <>
                        <div style={{ color: 'var(--color-text-secondary, #666)', marginBottom: 12, fontSize: 12 }}>飞书 · 监听指定用户的新内容并推送到飞书群/会话</div>
                        <Form.Item label="使用已保存的配置" style={{ marginBottom: 12 }}>
                          <Select
                            allowClear
                            placeholder="选择已保存的配置以填充下方表单"
                            options={savedChannelConfigs.map((c) => ({ value: c.id, label: c.name }))}
                            onChange={handleSelectSavedConfig}
                            style={{ width: '100%' }}
                          />
                        </Form.Item>
                        <Form.Item
                          name="userIds"
                          label="监听用户（作者名 · 平台）"
                          rules={[{ required: true, message: '请选择至少一个用户' }]}
                        >
                          <Select
                            mode="multiple"
                            placeholder="选择要监听的用户，其新保存内容将推送到飞书"
                            optionFilterProp="label"
                            options={users.map((u) => ({ value: u.id, label: formatUserOptionLabel(u) }))}
                          />
                        </Form.Item>
                        <Form.Item name="authorDisplayName" label="作者显示名（可选）">
                          <Input placeholder="下发时 {author} 使用此名称，留空则用用户配置的显示名/账号" />
                        </Form.Item>
                        <Form.Item name="feishuAppId" label="飞书 App ID" rules={[{ required: true, message: '请输入飞书应用 App ID' }]}>
                          <Input placeholder="开放平台应用凭证中的 App ID" />
                        </Form.Item>
                        <Form.Item name="feishuAppSecret" label="飞书 App Secret" rules={[{ required: true, message: '请输入飞书应用 App Secret' }]}>
                          <Input.Password placeholder="开放平台应用凭证中的 App Secret" />
                        </Form.Item>
                        <Form.Item name="feishuReceiveId" label="接收 ID（群聊 chat_id / 用户 open_id）" rules={[{ required: true, message: '请输入接收 ID' }]}>
                          <Input placeholder="群聊会话的 chat_id 或用户的 open_id" />
                        </Form.Item>
                        <Form.Item name="feishuReceiveIdType" label="接收 ID 类型" initialValue="chat_id">
                          <Select options={[{ value: 'chat_id', label: 'chat_id（群聊/单聊）' }, { value: 'open_id', label: 'open_id（用户）' }, { value: 'user_id', label: 'user_id（用户）' }]} />
                        </Form.Item>
                        <Form.Item name="feishuMessageTemplate" label="消息模板（可选）">
                          <Input.TextArea rows={3} placeholder="留空则三行：【作者+平台】、body、链接。自定义支持：{author} {platform} {body} {url} {title}" />
                        </Form.Item>
                        <Form.Item>
                          <Button type="default" onClick={handleOpenSaveConfig}>保存为通道配置（可共享）</Button>
                        </Form.Item>
                      </>
                    );
                  }
                  return (
                    <div style={{ color: 'var(--color-text-tertiary, #999)', fontSize: 13, padding: '8px 0' }}>
                      请先在上方选择规则类型（如「发送到 QQ 群」或「发送到飞书」），此处将显示该类型的配置项。
                    </div>
                  );
                }}
              </Form.Item>
            </Card>
            <Form.Item name="isEnabled" label="启用" valuePropName="checked" initialValue={true}>
              <Switch checkedChildren="启用" unCheckedChildren="禁用" />
            </Form.Item>
          </Form>
        </Modal>

        <Modal
          title="保存为通道配置"
          open={saveConfigModalVisible}
          onOk={handleSaveAsChannelConfig}
          onCancel={() => setSaveConfigModalVisible(false)}
          confirmLoading={saveConfigLoading}
          destroyOnClose
        >
          <Form form={saveConfigForm} layout="vertical">
            <Form.Item name="saveConfigName" label="配置名称" rules={[{ required: true, message: '请输入配置名称' }]}>
              <Input placeholder="例如：飞书-产品群" />
            </Form.Item>
            <Form.Item name="saveConfigShared" label="共享（他人可选）" valuePropName="checked" initialValue={false}>
              <Switch checkedChildren="共享" unCheckedChildren="仅自己" />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </MainLayout>
  );
}

export default Notifications;
