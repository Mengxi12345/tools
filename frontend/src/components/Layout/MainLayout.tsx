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
  BulbOutlined,
  BulbFilled,
} from '@ant-design/icons';
import { ReactNode } from 'react';
import { clearToken } from '../../services/api';
import { useTheme } from '../../contexts/ThemeContext';

const { Header, Sider, Content } = Layout;

interface MainLayoutProps {
  children: ReactNode;
}

function MainLayout({ children }: MainLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();

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
    {
      key: '/system-settings',
      icon: <SettingOutlined />,
      label: '系统设置',
    },
  ];

  return (
    <Layout className="main-layout" style={{ minHeight: '100vh', background: 'var(--color-bg-page)' }}>
      <Sider className="main-layout__sider" width={200} style={{ background: 'var(--color-bg-elevated)' }}>
        <div className="main-layout__logo">
          内容聚合工具
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          className="main-layout__menu"
          style={{ background: 'transparent', borderRight: 0 }}
        />
      </Sider>
      <Layout style={{ background: 'transparent' }}>
        <Header className="main-layout__header ds-nav-glass">
          <h2 className="main-layout__title">内容聚合与归档工具</h2>
          <div className="main-layout__actions">
            <Button
              type="text"
              icon={theme === 'dark' ? <BulbFilled /> : <BulbOutlined />}
              onClick={toggleTheme}
              className="main-layout__theme-btn"
              title={theme === 'dark' ? '切换浅色' : '切换深色'}
            />
            <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout}>退出</Button>
          </div>
        </Header>
        <Content className="main-layout__content">
          {children}
        </Content>
      </Layout>
      <style>{`
        .main-layout__sider .ant-layout-sider-children { background: var(--color-bg-elevated); }
        .main-layout__menu.ant-menu { background: transparent !important; color: var(--color-text-primary); }
        .main-layout__menu.ant-menu .ant-menu-item { color: var(--color-text-secondary); }
        .main-layout__menu.ant-menu .ant-menu-item:hover { color: var(--color-primary); }
        .main-layout__menu.ant-menu .ant-menu-item-selected { background: var(--color-primary) !important; color: #fff !important; }
        .main-layout__menu.ant-menu .ant-menu-item .anticon { color: inherit; }
        .main-layout__logo {
          height: 64px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--color-text-primary);
          font-size: var(--text-h3-size);
          font-weight: var(--text-h2-weight);
        }
        .main-layout__header {
          padding: 0 24px;
          display: flex;
          align-items: center;
          justify-content: space-between;
          height: 56px;
          line-height: 56px;
        }
        .main-layout__title {
          margin: 0;
          font-size: var(--text-h2-size);
          font-weight: var(--text-h2-weight);
          color: var(--color-text-primary);
        }
        .main-layout__actions { display: flex; align-items: center; gap: 8px; }
        .main-layout__theme-btn { color: var(--color-text-secondary); }
        .main-layout__theme-btn:hover { color: var(--color-primary); }
        .main-layout__content {
          margin: var(--space-lg);
          padding: var(--space-lg);
          background: var(--color-bg-card);
          border-radius: var(--radius-lg);
          min-height: calc(100vh - 56px - var(--space-lg) * 2);
          box-shadow: var(--shadow-card);
        }
      `}</style>
    </Layout>
  );
}

export default MainLayout;
