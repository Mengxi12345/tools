import { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, Form, Input, InputNumber, message, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { groupApi, getApiErrorMessage } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';

interface UserGroup {
  id: string;
  name: string;
  description?: string;
  sortOrder: number;
  createdAt: string;
}

function Groups() {
  const [groups, setGroups] = useState<UserGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingGroup, setEditingGroup] = useState<UserGroup | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadGroups();
  }, []);

  const loadGroups = async () => {
    setLoading(true);
    try {
      const response: any = await groupApi.getAll();
      if (response?.code === 200 && response?.data != null) {
        const list = Array.isArray(response.data) ? response.data : response.data?.content ?? [];
        setGroups(list);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载分组列表失败'));
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingGroup(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (g: UserGroup) => {
    setEditingGroup(g);
    form.setFieldsValue({ name: g.name, description: g.description, sortOrder: g.sortOrder });
    setModalVisible(true);
  };

  const handleDelete = async (id: string) => {
    try {
      const response: any = await groupApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        loadGroups();
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败'));
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingGroup) {
        const response: any = await groupApi.update(editingGroup.id, values);
        if (response.code === 200) {
          message.success('更新成功');
          setModalVisible(false);
          loadGroups();
        }
      } else {
        const response: any = await groupApi.create(values);
        if (response.code === 200) {
          message.success('创建成功');
          setModalVisible(false);
          loadGroups();
        }
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(getApiErrorMessage(e, editingGroup ? '更新失败' : '创建失败'));
    }
  };

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
    { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (t: string) => new Date(t).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: UserGroup) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除该分组？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <MainLayout>
      <div>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0 }}>用户分组</h2>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadGroups} loading={loading}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>添加分组</Button>
          </Space>
        </div>
        <Table rowKey="id" loading={loading} dataSource={groups} columns={columns} />
        <Modal
          title={editingGroup ? '编辑分组' : '添加分组'}
          open={modalVisible}
          onOk={handleSubmit}
          onCancel={() => setModalVisible(false)}
          width={520}
        >
          <Form form={form} layout="vertical">
            <Form.Item name="name" label="名称" rules={[{ required: true }]}>
              <Input placeholder="分组名称" />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={2} placeholder="可选" />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序" initialValue={0}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </MainLayout>
  );
}

export default Groups;
