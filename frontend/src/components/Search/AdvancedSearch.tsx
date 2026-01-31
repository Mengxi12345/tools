import { useState } from 'react';
import { Modal, Form, Input, Select, Button, Space } from 'antd';
import { SearchOutlined } from '@ant-design/icons';

interface AdvancedSearchProps {
  visible: boolean;
  onCancel: () => void;
  onSearch: (params: AdvancedSearchParams) => void;
}

export interface AdvancedSearchParams {
  query: string;
  contentType?: string;
  platformId?: string;
  userId?: string;
  regexPattern?: string;
  startDate?: string;
  endDate?: string;
}

function AdvancedSearch({ visible, onCancel, onSearch }: AdvancedSearchProps) {
  const [form] = Form.useForm();
  const [searchType, setSearchType] = useState<'normal' | 'regex' | 'advanced'>('normal');

  const handleSubmit = () => {
    form.validateFields().then((values) => {
      const params: AdvancedSearchParams = {
        query: values.query,
        contentType: values.contentType,
        platformId: values.platformId,
        userId: values.userId,
        regexPattern: values.regexPattern,
        startDate: values.dateRange?.[0]?.format('YYYY-MM-DD'),
        endDate: values.dateRange?.[1]?.format('YYYY-MM-DD'),
      };
      onSearch(params);
      form.resetFields();
      onCancel();
    });
  };

  return (
    <Modal
      title="高级搜索"
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={600}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        <Form.Item label="搜索类型">
          <Select
            value={searchType}
            onChange={setSearchType}
            options={[
              { label: '普通搜索', value: 'normal' },
              { label: '正则表达式', value: 'regex' },
              { label: '高级搜索', value: 'advanced' },
            ]}
          />
        </Form.Item>

        {searchType === 'normal' && (
          <Form.Item
            name="query"
            label="搜索关键词"
            rules={[{ required: true, message: '请输入搜索关键词' }]}
          >
            <Input placeholder="输入搜索关键词" />
          </Form.Item>
        )}

        {searchType === 'regex' && (
          <Form.Item
            name="regexPattern"
            label="正则表达式"
            rules={[{ required: true, message: '请输入正则表达式' }]}
          >
            <Input placeholder="例如: ^test.*" />
          </Form.Item>
        )}

        {searchType === 'advanced' && (
          <>
            <Form.Item
              name="query"
              label="搜索关键词"
              rules={[{ required: true, message: '请输入搜索关键词' }]}
            >
              <Input placeholder="输入搜索关键词" />
            </Form.Item>
            <Form.Item name="contentType" label="内容类型">
              <Select
                placeholder="选择内容类型"
                options={[
                  { label: '文本', value: 'TEXT' },
                  { label: '图片', value: 'IMAGE' },
                  { label: '视频', value: 'VIDEO' },
                  { label: '链接', value: 'LINK' },
                ]}
              />
            </Form.Item>
          </>
        )}

        <Form.Item name="platformId" label="平台">
          <Select placeholder="选择平台" allowClear />
        </Form.Item>

        <Form.Item name="userId" label="作者">
          <Select placeholder="选择作者" allowClear />
        </Form.Item>

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              搜索
            </Button>
            <Button onClick={onCancel}>取消</Button>
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  );
}

export default AdvancedSearch;
