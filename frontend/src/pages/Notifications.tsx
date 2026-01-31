import { useEffect, useState } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, Switch, message, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { notificationRuleApi, getApiErrorMessage } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';

interface Rule {
  id: string;
  name: string;
  ruleType?: string;
  config?: Record<string, unknown>;
  isEnabled: boolean;
  createdAt: string;
}

const RULE_TYPES = [
  { value: 'KEYWORD', label: '关键词' },
  { value: 'AUTHOR', label: '作者' },
  { value: 'PLATFORM', label: '平台' },
];

function Notifications() {
  const [rules, setRules] = useState<Rule[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRule, setEditingRule] = useState<Rule | null>(null);
  const [form] = Form.useForm();

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

  useEffect(() => {
    loadRules(1, 10);
  }, []);

  const handleCreate = () => {
    setEditingRule(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (r: Rule) => {
    setEditingRule(r);
    form.setFieldsValue({
      name: r.name,
      ruleType: r.ruleType,
      isEnabled: r.isEnabled,
    });
    setModalVisible(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingRule) {
        const res: any = await notificationRuleApi.update(editingRule.id, values);
        if (res?.code === 200) {
          message.success('更新成功');
          setModalVisible(false);
          loadRules(pagination.current, pagination.pageSize);
        } else {
          message.error(res?.message || '更新失败');
        }
      } else {
        const res: any = await notificationRuleApi.create(values);
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
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
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
          width={480}
        >
          <Form form={form} layout="vertical">
            <Form.Item name="name" label="规则名称" rules={[{ required: true }]}>
              <Input placeholder="例如：重要关键词提醒" />
            </Form.Item>
            <Form.Item name="ruleType" label="规则类型">
              <Select placeholder="选择类型" options={RULE_TYPES} allowClear />
            </Form.Item>
            <Form.Item name="isEnabled" label="启用" valuePropName="checked" initialValue={true}>
              <Switch checkedChildren="启用" unCheckedChildren="禁用" />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </MainLayout>
  );
}

export default Notifications;
