import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import { getToken } from './services/api'

// 代码分割：使用 React.lazy 实现路由级别的懒加载
const Login = lazy(() => import('./pages/Login'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Platforms = lazy(() => import('./pages/Platforms'))
const Users = lazy(() => import('./pages/Users'))
const Contents = lazy(() => import('./pages/Contents'))
const ContentDetail = lazy(() => import('./pages/ContentDetail'))
const Favorites = lazy(() => import('./pages/Favorites'))
const Settings = lazy(() => import('./pages/Settings'))
const SystemSettings = lazy(() => import('./pages/SystemSettings'))
const Export = lazy(() => import('./pages/Export'))
const Analytics = lazy(() => import('./pages/Analytics'))
const Notifications = lazy(() => import('./pages/Notifications'))

// 加载中组件
const LoadingFallback = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
    <Spin size="large" />
  </div>
)

function RequireAuth({ children }: { children: React.ReactNode }) {
  if (!getToken()) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function AppRoutes() {
  return (
    <Suspense fallback={<LoadingFallback />}>
      <Routes>
        <Route path="/login" element={<Login />} />
        {/* 已下线用户注册 */}
        <Route path="/register" element={<Navigate to="/login" replace />} />
        <Route path="/" element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/platforms" element={<RequireAuth><Platforms /></RequireAuth>} />
        <Route path="/users" element={<RequireAuth><Users /></RequireAuth>} />
        <Route path="/contents" element={<RequireAuth><Contents /></RequireAuth>} />
        <Route path="/contents/:id" element={<RequireAuth><ContentDetail /></RequireAuth>} />
        <Route path="/favorites" element={<RequireAuth><Favorites /></RequireAuth>} />
        <Route path="/export" element={<RequireAuth><Export /></RequireAuth>} />
        <Route path="/analytics" element={<RequireAuth><Analytics /></RequireAuth>} />
        <Route path="/notification-rules" element={<RequireAuth><Notifications /></RequireAuth>} />
        <Route path="/settings" element={<RequireAuth><Settings /></RequireAuth>} />
        <Route path="/system-settings" element={<RequireAuth><SystemSettings /></RequireAuth>} />
      </Routes>
    </Suspense>
  )
}

export default AppRoutes
