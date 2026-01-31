import { useEffect } from 'react';
import { Layout, Menu, Button } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  DashboardOutlined,
  AppstoreOutlined,
  UserOutlined,
  FileTextOutlined,
  SettingOutlined,
  TagsOutlined,
  TeamOutlined,
  DownloadOutlined,
  BarChartOutlined,
  BellOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { ReactNode } from 'react';
import { clearToken } from '../../services/api';

const { Header, Sider, Content } = Layout;

interface MainLayoutProps {
  children: ReactNode;
}

function MainLayout({ children }: MainLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const handler = () => navigate('/login', { replace: true });
    window.addEventListener('auth-required', handler);
    return () => window.removeEventListener('auth-required', handler);
  }, [navigate]);

  const handleLogout = () => {
    clearToken();
    navigate('/login', { replace: true });
  };

  const menuItems = [
    {
      key: '/',
      icon: <DashboardOutlined />,
      label: '仪表盘',
    },
    {
      key: '/platforms',
      icon: <AppstoreOutlined />,
      label: '平台管理',
    },
    {
      key: '/users',
      icon: <UserOutlined />,
      label: '用户管理',
    },
    {
      key: '/contents',
      icon: <FileTextOutlined />,
      label: '内容管理',
    },
    {
      key: '/tags',
      icon: <TagsOutlined />,
      label: '标签管理',
    },
    {
      key: '/groups',
      icon: <TeamOutlined />,
      label: '用户分组',
    },
    {
      key: '/export',
      icon: <DownloadOutlined />,
      label: '数据导出',
    },
    {
      key: '/analytics',
      icon: <BarChartOutlined />,
      label: '数据分析',
    },
    {
      key: '/notification-rules',
      icon: <BellOutlined />,
      label: '通知规则',
    },
    {
      key: '/settings',
      icon: <SettingOutlined />,
      label: '定时任务',
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={200}>
        <div style={{ 
          height: 64, 
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center',
          color: 'white',
          fontSize: '18px',
          fontWeight: 'bold'
        }}>
          内容聚合工具
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ 
          background: '#fff', 
          padding: '0 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
        }}>
          <h2 style={{ margin: 0 }}>内容聚合与归档工具</h2>
          <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout}>退出</Button>
        </Header>
        <Content style={{ 
          margin: '24px', 
          padding: '24px', 
          background: '#fff',
          borderRadius: '8px',
          minHeight: 'calc(100vh - 112px)'
        }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
}

export default MainLayout;
