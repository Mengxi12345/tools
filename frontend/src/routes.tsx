import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import { getToken } from './services/api'

// 代码分割：使用 React.lazy 实现路由级别的懒加载
const Login = lazy(() => import('./pages/Login'))
const Register = lazy(() => import('./pages/Register'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Platforms = lazy(() => import('./pages/Platforms'))
const Users = lazy(() => import('./pages/Users'))
const Contents = lazy(() => import('./pages/Contents'))
const ContentDetail = lazy(() => import('./pages/ContentDetail'))
const Settings = lazy(() => import('./pages/Settings'))
const Tags = lazy(() => import('./pages/Tags'))
const Groups = lazy(() => import('./pages/Groups'))
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
        <Route path="/register" element={<Register />} />
        <Route path="/" element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/platforms" element={<RequireAuth><Platforms /></RequireAuth>} />
        <Route path="/users" element={<RequireAuth><Users /></RequireAuth>} />
        <Route path="/contents" element={<RequireAuth><Contents /></RequireAuth>} />
        <Route path="/contents/:id" element={<RequireAuth><ContentDetail /></RequireAuth>} />
        <Route path="/groups" element={<RequireAuth><Groups /></RequireAuth>} />
        <Route path="/export" element={<RequireAuth><Export /></RequireAuth>} />
        <Route path="/analytics" element={<RequireAuth><Analytics /></RequireAuth>} />
        <Route path="/notification-rules" element={<RequireAuth><Notifications /></RequireAuth>} />
        <Route path="/settings" element={<RequireAuth><Settings /></RequireAuth>} />
        <Route path="/tags" element={<RequireAuth><Tags /></RequireAuth>} />
      </Routes>
    </Suspense>
  )
}

export default AppRoutes
