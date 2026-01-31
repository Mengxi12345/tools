import { useState, useEffect } from 'react';
import { Card, Select, Button, Space, message } from 'antd';
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { userApi, getToken, exportApi, getApiErrorMessage } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';

function Export() {
  const [users, setUsers] = useState<any[]>([]);
  const [userId, setUserId] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState<string | null>(null);

  const loadUsers = async () => {
    try {
      const res: any = await userApi.getAll({ page: 0, size: 500 });
      if (res?.code === 200) setUsers(res.data?.content || []);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载用户列表失败'));
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const handleDownload = async (format: 'json' | 'markdown' | 'csv' | 'html') => {
    const url = format === 'json' ? exportApi.getJsonUrl({ userId })
      : format === 'markdown' ? exportApi.getMarkdownUrl({ userId })
      : format === 'csv' ? exportApi.getCsvUrl({ userId })
      : exportApi.getHtmlUrl({ userId });
    setLoading(format);
    try {
      const token = getToken();
      const res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err?.message || `下载失败 ${res.status}`);
      }
      const blob = await res.blob();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `export.${format === 'json' ? 'json' : format === 'markdown' ? 'md' : format}`;
      a.click();
      URL.revokeObjectURL(a.href);
      message.success('下载成功');
    } catch (e: any) {
      message.error(getApiErrorMessage(e, '下载失败'));
    } finally {
      setLoading(null);
    }
  };

  return (
    <MainLayout>
      <div>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0 }}>数据导出</h2>
          <Button icon={<ReloadOutlined />} onClick={loadUsers}>
            刷新
          </Button>
        </div>
        <Card title="导出配置" style={{ marginBottom: 16 }}>
          <Space direction="vertical" size="middle">
            <span>按用户筛选（留空则导出全部）：</span>
            <Select
              placeholder="选择用户"
              allowClear
              style={{ width: 260 }}
              onChange={(v) => setUserId(v)}
              options={users.map((u) => ({ label: u.username, value: u.id }))}
            />
          </Space>
        </Card>
        <Card title="导出格式">
          <Space wrap>
            <Button icon={<DownloadOutlined />} loading={loading === 'json'} onClick={() => handleDownload('json')}>JSON</Button>
            <Button icon={<DownloadOutlined />} loading={loading === 'markdown'} onClick={() => handleDownload('markdown')}>Markdown</Button>
            <Button icon={<DownloadOutlined />} loading={loading === 'csv'} onClick={() => handleDownload('csv')}>CSV</Button>
            <Button icon={<DownloadOutlined />} loading={loading === 'html'} onClick={() => handleDownload('html')}>HTML</Button>
          </Space>
        </Card>
      </div>
    </MainLayout>
  );
}

export default Export;
