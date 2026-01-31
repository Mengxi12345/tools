import { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Table, Spin, message, Button } from 'antd';
import { FileTextOutlined, UserOutlined, StarOutlined, ReloadOutlined } from '@ant-design/icons';
import { contentApi, statsApi, getApiErrorMessage } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';

function Analytics() {
  const [contentStats, setContentStats] = useState<{ total: number; unread: number; favorite: number } | null>(null);
  const [platformDist, setPlatformDist] = useState<Record<string, number>>({});
  const [userStats, setUserStats] = useState<{ totalUsers: number; activeUsers: number } | null>(null);
  const [loading, setLoading] = useState(true);

  const loadAnalytics = async () => {
    setLoading(true);
    try {
      const [csRes, pdRes, usRes] = await Promise.all([
        contentApi.getStats().catch(() => ({ code: 0, data: null })),
        statsApi.getPlatformDistribution().catch(() => ({ code: 0, data: {} })),
        statsApi.getUserStats().catch(() => ({ code: 0, data: null })),
      ]);
      const cs = csRes as any;
      const pd = pdRes as any;
      const us = usRes as any;
      if (cs?.code === 200 && cs?.data) setContentStats(cs.data);
      if (pd?.code === 200 && pd?.data) setPlatformDist(typeof pd.data === 'object' ? pd.data : {});
      if (us?.code === 200 && us?.data) setUserStats(us.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载统计数据失败'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAnalytics();
  }, []);

  if (loading && !contentStats && !Object.keys(platformDist).length) return <MainLayout><Spin /></MainLayout>;

  const platformData = Object.entries(platformDist).map(([name, count]) => ({ key: name, name, count }));

  return (
    <MainLayout>
      <div>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0 }}>数据分析</h2>
          <Button icon={<ReloadOutlined />} onClick={loadAnalytics} loading={loading}>
            刷新
          </Button>
        </div>
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={6}>
            <Card>
              <Statistic title="总内容数" value={contentStats?.total ?? 0} prefix={<FileTextOutlined />} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="未读内容" value={contentStats?.unread ?? 0} prefix={<FileTextOutlined />} valueStyle={{ color: '#ff4d4f' }} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="收藏数" value={contentStats?.favorite ?? 0} prefix={<StarOutlined />} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="追踪用户 / 启用" value={`${userStats?.totalUsers ?? 0} / ${userStats?.activeUsers ?? 0}`} prefix={<UserOutlined />} />
            </Card>
          </Col>
        </Row>
        <Card title="平台内容分布">
          <Table
            dataSource={platformData}
            columns={[
              { title: '平台', dataIndex: 'name', key: 'name' },
              { title: '内容数', dataIndex: 'count', key: 'count' },
            ]}
            pagination={false}
            rowKey="name"
          />
        </Card>
      </div>
    </MainLayout>
  );
}

export default Analytics;
