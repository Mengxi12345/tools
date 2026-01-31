import { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, message, Popconfirm, Tag as AntTag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import MainLayout from '../components/Layout/MainLayout';
import { tagApi, getApiErrorMessage } from '../services/api';

interface Tag {
  id: string;
  name: string;
  color?: string;
  category?: string;
  usageCount: number;
  createdAt: string;
}

function Tags() {
  const [tags, setTags] = useState<Tag[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTag, setEditingTag] = useState<Tag | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadTags();
  }, []);

  const loadTags = async () => {
    setLoading(true);
    try {
      const response: any = await tagApi.getAll();
      if (response?.code === 200 && response?.data != null) {
        const list = Array.isArray(response.data) ? response.data : response.data?.content ?? [];
        setTags(list);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载标签列表失败'));
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingTag(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (tag: Tag) => {
    setEditingTag(tag);
    form.setFieldsValue({
      name: tag.name,
      color: tag.color,
      category: tag.category,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: string) => {
    try {
      const response: any = await tagApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        loadTags();
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败'));
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingTag) {
        const response: any = await tagApi.update(editingTag.id, values);
        if (response.code === 200) {
          message.success('更新成功');
          setModalVisible(false);
          loadTags();
        }
      } else {
        const response: any = await tagApi.create(values);
        if (response.code === 200) {
          message.success('创建成功');
          setModalVisible(false);
          loadTags();
        }
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      message.error(getApiErrorMessage(error, editingTag ? '更新失败' : '创建失败'));
    }
  };

  const columns = [
    {
      title: '标签名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: Tag) => (
        <AntTag color={record.color}>{text}</AntTag>
      ),
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
    },
    {
      title: '使用次数',
      dataIndex: 'usageCount',
      key: 'usageCount',
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
      render: (_: any, record: Tag) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个标签吗？"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <MainLayout>
      <div>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0 }}>标签管理</h2>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadTags} loading={loading}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              添加标签
            </Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={tags}
          rowKey="id"
          loading={loading}
        />

        <Modal
          title={editingTag ? '编辑标签' : '添加标签'}
          open={modalVisible}
          onOk={handleSubmit}
          onCancel={() => setModalVisible(false)}
          width={600}
        >
          <Form form={form} layout="vertical">
            <Form.Item
              name="name"
              label="标签名称"
              rules={[{ required: true, message: '请输入标签名称' }]}
            >
              <Input placeholder="例如：技术" />
            </Form.Item>
            <Form.Item
              name="color"
              label="颜色"
            >
              <Select placeholder="选择颜色">
                <Select.Option value="red">红色</Select.Option>
                <Select.Option value="blue">蓝色</Select.Option>
                <Select.Option value="green">绿色</Select.Option>
                <Select.Option value="orange">橙色</Select.Option>
                <Select.Option value="purple">紫色</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item
              name="category"
              label="分类"
            >
              <Input placeholder="例如：技术、生活、学习" />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </MainLayout>
  );
}

export default Tags;
