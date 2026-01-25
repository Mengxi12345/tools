import { Layout } from 'antd'

const { Header, Content } = Layout

function Dashboard() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ color: 'white' }}>
        <h1>内容聚合与归档工具</h1>
      </Header>
      <Content style={{ padding: '24px' }}>
        <h2>欢迎使用内容聚合与归档工具</h2>
        <p>系统正在初始化中...</p>
      </Content>
    </Layout>
  )
}

export default Dashboard
