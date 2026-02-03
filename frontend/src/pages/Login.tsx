import { useState } from 'react';
import { Form, Input, Button, Card, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { authApi, setToken, getApiErrorMessage } from '../services/api';

function Login() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res: any = await authApi.login(values);
      if (res?.code === 200 && res?.data?.token) {
        setToken(res.data.token);
        message.success('登录成功');
        navigate('/', { replace: true });
      } else {
        message.error(res?.message || '登录失败');
      }
    } catch (err: any) {
      message.error(getApiErrorMessage(err, '登录失败'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card title="内容聚合工具 - 登录" style={{ width: 360 }} headStyle={{ textAlign: 'center' }}>
        <Form name="login" onFinish={onFinish} autoComplete="off" size="large">
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Link to="/register">没有账号？去注册</Link>
          </div>
        </Form>
      </Card>
    </div>
  );
}

export default Login;
