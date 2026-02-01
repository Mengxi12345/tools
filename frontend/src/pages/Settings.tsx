import { useEffect, useState, useRef } from 'react';
import { Card, Space, Switch, Table, Tag, Button, message, Progress, Popconfirm } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons';
import MainLayout from '../components/Layout/MainLayout';
import { taskApi, userApi, getApiErrorMessage } from '../services/api';

function Settings() {
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [scheduleInterval, setScheduleInterval] = useState<string>('');
  const [loadingSchedule, setLoadingSchedule] = useState(false);
  const [users, setUsers] = useState<any[]>([]);
  const [history, setHistory] = useState<any[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyPageSize, setHistoryPageSize] = useState(10);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [fetchHistory, setFetchHistory] = useState<any[]>([]);
  const [fetchHistoryLoading, setFetchHistoryLoading] = useState(false);
  const [fetchHistoryPage, setFetchHistoryPage] = useState(1);
  const [fetchHistoryPageSize, setFetchHistoryPageSize] = useState(10);
  const [fetchHistoryTotal, setFetchHistoryTotal] = useState(0);
  const [fetchHistorySelectedRowKeys, setFetchHistorySelectedRowKeys] = useState<string[]>([]);
  const [historySelectedRowKeys, setHistorySelectedRowKeys] = useState<string[]>([]);
  /** 各用户定时任务当前状态：userId -> isEnabled */
  const [userScheduleStatus, setUserScheduleStatus] = useState<Record<string, boolean>>({});

  const fetchHistoryPollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const POLL_INTERVAL_MS = 2000;

  useEffect(() => {
    loadScheduleStatus();
    loadUsers();
    loadHistory();
    loadFetchHistory();
  }, []);

  // 当存在 RUNNING 或 PENDING 的刷新任务时，自动轮询以实时更新进度
  useEffect(() => {
    const hasActive = (fetchHistory || []).some(
      (r: any) => r?.status === 'RUNNING' || r?.status === 'PENDING'
    );
    if (hasActive && !fetchHistoryPollTimerRef.current) {
      fetchHistoryPollTimerRef.current = setInterval(() => loadFetchHistory(), POLL_INTERVAL_MS);
    }
    if (!hasActive && fetchHistoryPollTimerRef.current) {
      clearInterval(fetchHistoryPollTimerRef.current);
      fetchHistoryPollTimerRef.current = null;
    }
    return () => {
      if (fetchHistoryPollTimerRef.current) {
        clearInterval(fetchHistoryPollTimerRef.current);
        fetchHistoryPollTimerRef.current = null;
      }
    };
  }, [fetchHistory]);

  const loadScheduleStatus = async () => {
    try {
      const [detailResult, usersStatusResult] = await Promise.allSettled([
        taskApi.getScheduleStatusDetail(),
        taskApi.getUsersScheduleStatus(),
      ]);
      // 详情接口含 isEnabled 与 interval，与仪表盘逻辑一致
      const detailRes = detailResult.status === 'fulfilled' ? detailResult.value : null;
      const data = detailRes?.code === 200 ? detailRes.data : null;
      const enabled = data != null ? (data?.isEnabled ?? data === true) : true;
      setScheduleEnabled(Boolean(enabled));
      if (data != null && typeof data.interval === 'string') {
        setScheduleInterval(data.interval);
      } else {
        setScheduleInterval('');
      }
      const usersStatusRes = usersStatusResult.status === 'fulfilled' ? usersStatusResult.value : null;
      if (usersStatusRes?.code === 200 && usersStatusRes.data != null) {
        setUserScheduleStatus(typeof usersStatusRes.data === 'object' ? usersStatusRes.data : {});
      }
    } catch {
      setScheduleEnabled(true);
      setScheduleInterval('');
    }
  };

  const loadUsers = async () => {
    try {
      const res: any = await userApi.getAll({ page: 0, size: 100 });
      if (res?.code === 200 && res?.data) {
        setUsers(Array.isArray(res.data.content) ? res.data.content : []);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载用户列表失败'));
    }
  };

  const loadHistory = async (page?: number, size?: number) => {
    const rawP = Number(page ?? historyPage);
    const rawS = Number(size ?? historyPageSize);
    const p = Number.isFinite(rawP) && rawP >= 1 ? Math.floor(rawP) : 1;
    const s = Number.isFinite(rawS) && rawS >= 1 ? Math.min(100, Math.floor(rawS)) : 10;
    setHistoryLoading(true);
    try {
      const res: any = await taskApi.getScheduleHistory({ page: p - 1, size: s });
      if (res?.code === 200 && res?.data) {
        setHistory(Array.isArray(res.data.content) ? res.data.content : []);
        setHistoryTotal(res.data.totalElements ?? 0);
        if (page != null) setHistoryPage(page);
        if (size != null) setHistoryPageSize(size);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载定时任务历史失败'));
    } finally {
      setHistoryLoading(false);
    }
  };

  const loadFetchHistory = async (page?: number, size?: number) => {
    const rawP = Number(page ?? fetchHistoryPage);
    const rawS = Number(size ?? fetchHistoryPageSize);
    const p = Number.isFinite(rawP) && rawP >= 1 ? Math.floor(rawP) : 1;
    const s = Number.isFinite(rawS) && rawS >= 1 ? Math.min(100, Math.floor(rawS)) : 10;
    setFetchHistoryLoading(true);
    try {
      const res: any = await taskApi.getFetchHistory({ page: p - 1, size: s, taskType: 'MANUAL' });
      if (res?.code === 200 && res?.data) {
        const list = Array.isArray(res.data.content) ? res.data.content : [];
        setFetchHistory(list);
        setFetchHistoryTotal(res.data.totalElements ?? 0);
        if (page != null) setFetchHistoryPage(page);
        if (size != null) setFetchHistoryPageSize(size);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载刷新任务记录失败'));
    } finally {
      setFetchHistoryLoading(false);
    }
  };

  const handleToggleGlobalSchedule = async (enabled: boolean) => {
    try {
      setLoadingSchedule(true);
      const res: any = enabled ? await taskApi.enableSchedule() : await taskApi.disableSchedule();
      if (res?.code === 200) {
        setScheduleEnabled(enabled);
        message.success(enabled ? '全局定时任务已启用' : '全局定时任务已禁用');
      } else {
        message.error(res?.message || '操作失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    } finally {
      setLoadingSchedule(false);
    }
  };

  const handleEnableUserSchedule = async (userId: string, enable: boolean) => {
    try {
      const res: any = enable
        ? await taskApi.enableUserSchedule(userId)
        : await taskApi.disableUserSchedule(userId);
      if (res?.code === 200) {
        message.success(enable ? '已启用该用户定时任务' : '已禁用该用户定时任务');
        setUserScheduleStatus((prev) => ({ ...prev, [userId]: enable }));
        loadScheduleStatus();
        loadHistory();
      } else {
        message.error(res?.message || '操作失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const historyColumns = [
    {
      title: '用户',
      dataIndex: ['user', 'username'],
      key: 'user',
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      key: 'startTime',
      render: (text: string) => (text ? new Date(text).toLocaleString('zh-CN') : '-'),
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      key: 'endTime',
      render: (text: string) => (text ? new Date(text).toLocaleString('zh-CN') : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        let color = 'blue';
        if (status === 'COMPLETED') color = 'green';
        else if (status === 'FAILED') color = 'red';
        else if (status === 'CANCELLED') color = 'default';
        else if (status === 'RUNNING') color = 'orange';
        return <Tag color={color}>{status}</Tag>;
      },
    },
    {
      title: '进度',
      dataIndex: 'progress',
      key: 'progress',
      width: 140,
      render: (p: number, record: any) => {
        const status = record?.status;
        const percent = p ?? 0;
        const statusProp = status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'exception' : undefined;
        return <Progress percent={percent} size="small" status={statusProp} showInfo={true} />;
      },
    },
    {
      title: '数量',
      key: 'count',
      render: (_: any, record: any) =>
        `${record.fetchedCount ?? 0}${record.totalCount ? ` / ${record.totalCount}` : ''}`,
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: any, record: any) => (
        <Popconfirm
          title="确定清除该条任务记录？"
          onConfirm={() => handleDeleteHistoryRecord(record.id)}
          okText="清除"
          cancelText="取消"
          okButtonProps={{ danger: true }}
        >
          <Button type="link" size="small" danger icon={<DeleteOutlined />}>
            清除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const fetchHistoryColumns = [
    { title: '用户', dataIndex: ['user', 'username'], key: 'user' },
    {
      title: '类型',
      dataIndex: 'taskType',
      key: 'taskType',
      render: (t: string) => (t === 'MANUAL' ? '手动刷新' : '定时'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        let color = 'blue';
        if (status === 'COMPLETED') color = 'green';
        else if (status === 'FAILED') color = 'red';
        else if (status === 'CANCELLED') color = 'default';
        else if (status === 'RUNNING') color = 'orange';
        return <Tag color={color}>{status}</Tag>;
      },
    },
    {
      title: '进度',
      dataIndex: 'progress',
      key: 'progress',
      width: 140,
      render: (p: number, record: any) => {
        const status = record?.status;
        const percent = p ?? 0;
        const statusProp = status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'exception' : undefined;
        return (
          <Progress percent={percent} size="small" status={statusProp} showInfo={true} />
        );
      },
    },
    {
      title: '数量',
      key: 'count',
      render: (_: any, record: any) =>
        `${record.fetchedCount ?? 0}${record.totalCount ? ` / ${record.totalCount}` : ''}`,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text: string) => (text ? new Date(text).toLocaleString('zh-CN') : '-'),
    },
    {
      title: '失败原因',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      ellipsis: true,
      render: (text: string) => text || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: any, record: any) => (
        <Popconfirm
          title="确定清除该条任务记录？"
          onConfirm={() => handleDeleteFetchRecord(record.id)}
          okText="清除"
          cancelText="取消"
          okButtonProps={{ danger: true }}
        >
          <Button type="link" size="small" danger icon={<DeleteOutlined />}>
            清除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const handleRefreshAll = () => {
    loadScheduleStatus();
    loadUsers();
    loadHistory();
    loadFetchHistory();
  };

  const handleDeleteFetchRecord = async (taskId: string) => {
    try {
      const res: any = await taskApi.deleteFetchTaskRecord(taskId);
      if (res?.code === 200) {
        message.success('已清除');
        loadFetchHistory();
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '清除失败'));
    }
  };

  const handleBatchDeleteFetchRecords = async () => {
    if (fetchHistorySelectedRowKeys.length === 0) return;
    try {
      const res: any = await taskApi.deleteFetchTaskRecords(fetchHistorySelectedRowKeys);
      if (res?.code === 200) {
        const n = res?.data ?? 0;
        message.success(`已清除 ${n} 条记录`);
        setFetchHistorySelectedRowKeys([]);
        loadFetchHistory();
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '批量清除失败'));
    }
  };

  const handleDeleteHistoryRecord = async (taskId: string) => {
    try {
      const res: any = await taskApi.deleteFetchTaskRecord(taskId);
      if (res?.code === 200) {
        message.success('已清除');
        loadHistory();
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '清除失败'));
    }
  };

  const handleBatchDeleteHistoryRecords = async () => {
    if (historySelectedRowKeys.length === 0) return;
    try {
      const res: any = await taskApi.deleteFetchTaskRecords(historySelectedRowKeys);
      if (res?.code === 200) {
        const n = res?.data ?? 0;
        message.success(`已清除 ${n} 条记录`);
        setHistorySelectedRowKeys([]);
        loadHistory();
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '批量清除失败'));
    }
  };

  const handleClearAllFetchRecords = async () => {
    try {
      const res: any = await taskApi.deleteAllFetchTaskRecordsByType('MANUAL');
      if (res?.code === 200) {
        const n = res?.data ?? 0;
        message.success(`已一键全清 ${n} 条手动刷新记录`);
        setFetchHistorySelectedRowKeys([]);
        loadFetchHistory();
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '一键全清失败'));
    }
  };

  const handleClearAllScheduleRecords = async () => {
    try {
      const res: any = await taskApi.deleteAllFetchTaskRecordsByType('SCHEDULED');
      if (res?.code === 200) {
        const n = res?.data ?? 0;
        message.success(`已一键全清 ${n} 条定时任务记录`);
        setHistorySelectedRowKeys([]);
        loadHistory();
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '一键全清失败'));
    }
  };

  return (
    <MainLayout>
      <div>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0 }}>定时任务</h2>
          <Button icon={<ReloadOutlined />} onClick={handleRefreshAll}>
            刷新
          </Button>
        </div>
        <Card title="全局定时任务" style={{ marginBottom: 16 }}>
          {scheduleInterval ? (
            <p style={{ marginBottom: 12, color: '#666' }}>
              执行周期：<strong>{scheduleInterval}</strong>
            </p>
          ) : null}
          <Space>
            <span>全局定时任务状态：</span>
            <Switch
              checked={scheduleEnabled}
              loading={loadingSchedule}
              onChange={handleToggleGlobalSchedule}
              checkedChildren={<CheckCircleOutlined />}
              unCheckedChildren={<CloseCircleOutlined />}
            />
            <span>{scheduleEnabled ? '已启用' : '已禁用'}</span>
          </Space>
        </Card>

        <Card title="用户定时任务管理" style={{ marginBottom: 16 }}>
          <Table
            dataSource={users}
            rowKey="id"
            pagination={false}
            columns={[
              {
                title: '用户名',
                dataIndex: 'username',
                key: 'username',
              },
              {
                title: '平台',
                dataIndex: ['platform', 'name'],
                key: 'platform',
              },
              {
                title: '定时任务状态',
                key: 'scheduleStatus',
                render: (_: any, record: any) => {
                  const enabled = userScheduleStatus[record.id] !== false;
                  return (
                    <Tag color={enabled ? 'success' : 'default'}>
                      {enabled ? '已启用' : '已禁用'}
                    </Tag>
                  );
                },
              },
              {
                title: '操作',
                key: 'action',
                render: (_: any, record: any) => (
                  <Space>
                    <Button size="small" onClick={() => handleEnableUserSchedule(record.id, true)}>
                      启用定时
                    </Button>
                    <Button
                      size="small"
                      danger
                      onClick={() => handleEnableUserSchedule(record.id, false)}
                    >
                      禁用定时
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        </Card>

        <Card
          title="定时任务执行历史"
          style={{ marginBottom: 16 }}
          extra={
            <Space>
              <Button icon={<ReloadOutlined />} type="link" onClick={loadHistory}>
                刷新
              </Button>
              <Button
                type="link"
                danger
                disabled={historySelectedRowKeys.length === 0}
                icon={<DeleteOutlined />}
                onClick={handleBatchDeleteHistoryRecords}
              >
                批量清除{historySelectedRowKeys.length > 0 ? ` (${historySelectedRowKeys.length})` : ''}
              </Button>
              <Popconfirm
                title="确定一键全清所有定时任务执行记录？此操作不可恢复。"
                onConfirm={handleClearAllScheduleRecords}
                okText="全清"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button type="link" danger icon={<DeleteOutlined />}>
                  一键全清
                </Button>
              </Popconfirm>
            </Space>
          }
        >
          <p style={{ color: '#666', marginBottom: 12 }}>仅展示由定时调度触发的任务（SCHEDULED），按创建时间倒序。</p>
          <Table
            dataSource={history}
            rowKey="id"
            loading={historyLoading}
            rowSelection={{
              selectedRowKeys: historySelectedRowKeys,
              onChange: (keys) => setHistorySelectedRowKeys(keys as string[]),
            }}
            columns={historyColumns}
            pagination={{
              current: Math.max(1, Number(historyPage) || 1),
              pageSize: Math.max(1, Number(historyPageSize) || 10),
              total: historyTotal,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (page, size) => loadHistory(page, size),
            }}
          />
        </Card>

        <Card
          title="刷新任务记录"
          extra={
            <Space>
              <Button icon={<ReloadOutlined />} type="link" onClick={loadFetchHistory}>
                刷新
              </Button>
              <Button
                type="link"
                danger
                disabled={fetchHistorySelectedRowKeys.length === 0}
                icon={<DeleteOutlined />}
                onClick={handleBatchDeleteFetchRecords}
              >
                批量清除{fetchHistorySelectedRowKeys.length > 0 ? ` (${fetchHistorySelectedRowKeys.length})` : ''}
              </Button>
              <Popconfirm
                title="确定一键全清所有手动刷新记录？此操作不可恢复。"
                onConfirm={handleClearAllFetchRecords}
                okText="全清"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button type="link" danger icon={<DeleteOutlined />}>
                  一键全清
                </Button>
              </Popconfirm>
            </Space>
          }
        >
          <p style={{ color: '#666', marginBottom: 12 }}>仅展示「用户管理」中点击「刷新内容」产生的手动任务，按创建时间倒序。</p>
          <Table
            dataSource={fetchHistory}
            rowKey="id"
            loading={fetchHistoryLoading}
            rowSelection={{
              selectedRowKeys: fetchHistorySelectedRowKeys,
              onChange: (keys) => setFetchHistorySelectedRowKeys(keys as string[]),
            }}
            columns={fetchHistoryColumns}
            pagination={{
              current: Math.max(1, Number(fetchHistoryPage) || 1),
              pageSize: Math.max(1, Number(fetchHistoryPageSize) || 10),
              total: fetchHistoryTotal,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (page, size) => loadFetchHistory(page, size),
            }}
          />
        </Card>
      </div>
    </MainLayout>
  );
}

export default Settings;

