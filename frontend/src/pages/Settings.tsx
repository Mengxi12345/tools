import { useEffect, useState, useRef } from 'react';
import { Card, Space, Switch, Table, Tag, Button, message, Progress, Popconfirm } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons';
import MainLayout from '../components/Layout/MainLayout';
import { taskApi, userApi, getApiErrorMessage } from '../services/api';

function useScheduleStatus() {
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [scheduleInterval, setScheduleInterval] = useState<string>('');
  const [loadingSchedule, setLoadingSchedule] = useState(false);
  const [contentAssetDownloadEnabled, setContentAssetDownloadEnabled] = useState(true);
  const [loadingContentAssetDownload, setLoadingContentAssetDownload] = useState(false);
  /** 各用户定时任务当前状态：userId -> isEnabled */
  const [userScheduleStatus, setUserScheduleStatus] = useState<Record<string, boolean>>({});

  const loadScheduleStatus = async () => {
    try {
      const [detailResult, usersStatusResult] = await Promise.allSettled([
        taskApi.getScheduleStatusDetail(),
        taskApi.getUsersScheduleStatus(),
      ]);
      // 详情接口含 isEnabled 与 interval，与仪表盘逻辑一致
      const detailRes = detailResult.status === 'fulfilled' ? (detailResult.value as any) : null;
      const data = detailRes?.code === 200 ? detailRes.data : null;
      const enabled = data != null ? (data?.isEnabled ?? data === true) : true;
      setScheduleEnabled(Boolean(enabled));
      if (data != null && typeof data.contentAssetDownloadEnabled === 'boolean') {
        setContentAssetDownloadEnabled(data.contentAssetDownloadEnabled);
      } else {
        setContentAssetDownloadEnabled(true);
      }
      if (data != null && typeof data.interval === 'string') {
        setScheduleInterval(data.interval);
      } else {
        setScheduleInterval('');
      }
      const usersStatusRes = usersStatusResult.status === 'fulfilled' ? (usersStatusResult.value as any) : null;
      if (usersStatusRes?.code === 200 && usersStatusRes.data != null) {
        setUserScheduleStatus(typeof usersStatusRes.data === 'object' ? usersStatusRes.data : {});
      }
    } catch {
      setScheduleEnabled(true);
      setScheduleInterval('');
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

  const handleToggleContentAssetDownload = async (enabled: boolean) => {
    try {
      setLoadingContentAssetDownload(true);
      const res: any = enabled
        ? await taskApi.enableContentAssetDownload()
        : await taskApi.disableContentAssetDownload();
      if (res?.code === 200) {
        setContentAssetDownloadEnabled(enabled);
        message.success(enabled ? '已开启文章附件下载（将下载到本地并使用本地地址）' : '已关闭文章附件下载（使用平台原始地址）');
      } else {
        message.error(res?.message || '操作失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    } finally {
      setLoadingContentAssetDownload(false);
    }
  };

  return {
    scheduleEnabled,
    scheduleInterval,
    loadingSchedule,
    contentAssetDownloadEnabled,
    loadingContentAssetDownload,
    userScheduleStatus,
    setUserScheduleStatus,
    loadScheduleStatus,
    handleToggleGlobalSchedule,
    handleToggleContentAssetDownload,
  };
}

function useUsers() {
  const [users, setUsers] = useState<any[]>([]);

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

  return { users, loadUsers };
}

function useScheduleHistory() {
  const [history, setHistory] = useState<any[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyPageSize, setHistoryPageSize] = useState(10);
  const [historyTotal, setHistoryTotal] = useState(0);

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

  return {
    history,
    historyLoading,
    historyPage,
    historyPageSize,
    historyTotal,
    loadHistory,
  };
}

function useFetchHistory() {
  const [fetchHistory, setFetchHistory] = useState<any[]>([]);
  const [fetchHistoryLoading, setFetchHistoryLoading] = useState(false);
  const [fetchHistoryPage, setFetchHistoryPage] = useState(1);
  const [fetchHistoryPageSize, setFetchHistoryPageSize] = useState(10);
  const [fetchHistoryTotal, setFetchHistoryTotal] = useState(0);

  const fetchHistoryPollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const POLL_INTERVAL_MS = 2000;

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetchHistory]);

  return {
    fetchHistory,
    fetchHistoryLoading,
    fetchHistoryPage,
    fetchHistoryPageSize,
    fetchHistoryTotal,
    loadFetchHistory,
    setFetchHistory,
    setFetchHistoryPage,
    setFetchHistoryPageSize,
    setFetchHistoryTotal,
  };
}

function Settings() {
  const {
    scheduleEnabled,
    scheduleInterval,
    loadingSchedule,
    contentAssetDownloadEnabled,
    loadingContentAssetDownload,
    userScheduleStatus,
    setUserScheduleStatus,
    loadScheduleStatus,
    handleToggleGlobalSchedule,
    handleToggleContentAssetDownload,
  } = useScheduleStatus();

  const { users, loadUsers } = useUsers();

  const {
    history,
    historyLoading,
    historyPage,
    historyPageSize,
    historyTotal,
    loadHistory,
  } = useScheduleHistory();

  const {
    fetchHistory,
    fetchHistoryLoading,
    fetchHistoryPage,
    fetchHistoryPageSize,
    fetchHistoryTotal,
    loadFetchHistory,
    setFetchHistory,
    setFetchHistoryPage,
    setFetchHistoryPageSize,
    setFetchHistoryTotal,
  } = useFetchHistory();

  const [fetchHistorySelectedRowKeys, setFetchHistorySelectedRowKeys] = useState<string[]>([]);
  const [historySelectedRowKeys, setHistorySelectedRowKeys] = useState<string[]>([]);

  // 首次进入页面时加载当前状态、用户列表和历史记录
  useEffect(() => {
    loadScheduleStatus();
    loadUsers();
    loadHistory();
    loadFetchHistory();
    // 只在首次挂载时执行
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  const globalSettingsItems = [
    {
      key: 'globalSchedule',
      label: '全局定时任务',
      description: `当前执行间隔：${scheduleInterval || '默认'}`,
      checked: scheduleEnabled,
      loading: loadingSchedule,
      onChange: handleToggleGlobalSchedule,
    },
    {
      key: 'contentAssetDownload',
      label: '文章附件下载到本地',
      description: '开启后，拉取文章时会下载图片和附件到本地并使用本地地址；关闭则保留平台原始地址。',
      checked: contentAssetDownloadEnabled,
      loading: loadingContentAssetDownload,
      onChange: handleToggleContentAssetDownload,
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

        <ScheduleHistoryCard
          history={history}
          loading={historyLoading}
          selectedRowKeys={historySelectedRowKeys}
          page={historyPage}
          pageSize={historyPageSize}
          total={historyTotal}
          onRefresh={() => loadHistory()}
          onPageChange={loadHistory}
          onSelectedRowKeysChange={(keys) => setHistorySelectedRowKeys(keys)}
          onBatchDelete={handleBatchDeleteHistoryRecords}
          onClearAll={handleClearAllScheduleRecords}
          columns={historyColumns}
        />

        <FetchHistoryCard
          history={fetchHistory}
          loading={fetchHistoryLoading}
          selectedRowKeys={fetchHistorySelectedRowKeys}
          page={fetchHistoryPage}
          pageSize={fetchHistoryPageSize}
          total={fetchHistoryTotal}
          onRefresh={() => loadFetchHistory()}
          onPageChange={loadFetchHistory}
          onSelectedRowKeysChange={(keys) => setFetchHistorySelectedRowKeys(keys)}
          onBatchDelete={handleBatchDeleteFetchRecords}
          onClearAll={handleClearAllFetchRecords}
          columns={fetchHistoryColumns}
        />
      </div>
      <style>{`
        @media (max-width: 767px) {
          .ant-card {
            margin-bottom: 12px;
          }
          .ant-card-head {
            padding: 12px 16px;
          }
          .ant-card-body {
            padding: 12px;
          }
          .ant-card-extra {
            padding: 0;
          }
          .ant-card-extra .ant-space {
            flex-wrap: wrap;
            gap: 4px;
          }
          .ant-table {
            font-size: 12px;
          }
          .ant-table-thead > tr > th {
            padding: 8px 4px !important;
            font-size: 11px;
          }
          .ant-table-tbody > tr > td {
            padding: 8px 4px !important;
            font-size: 11px;
          }
          .ant-space {
            flex-wrap: wrap;
            gap: 8px !important;
          }
        }
      `}</style>
    </MainLayout>
  );
}

interface ScheduleHistoryCardProps {
  history: any[];
  loading: boolean;
  selectedRowKeys: string[];
  page: number;
  pageSize: number;
  total: number;
  columns: any[];
  onRefresh: () => void;
  onPageChange: (page: number, size?: number) => void;
  onSelectedRowKeysChange: (keys: string[]) => void;
  onBatchDelete: () => void;
  onClearAll: () => void;
}

function ScheduleHistoryCard({
  history,
  loading,
  selectedRowKeys,
  page,
  pageSize,
  total,
  columns,
  onRefresh,
  onPageChange,
  onSelectedRowKeysChange,
  onBatchDelete,
  onClearAll,
}: ScheduleHistoryCardProps) {
  return (
    <Card
      title="定时任务执行历史"
      style={{ marginBottom: 16 }}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} type="link" onClick={onRefresh}>
            刷新
          </Button>
          <Button
            type="link"
            danger
            disabled={selectedRowKeys.length === 0}
            icon={<DeleteOutlined />}
            onClick={onBatchDelete}
          >
            批量清除{selectedRowKeys.length > 0 ? ` (${selectedRowKeys.length})` : ''}
          </Button>
          <Popconfirm
            title="确定一键全清所有定时任务执行记录？此操作不可恢复。"
            onConfirm={onClearAll}
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
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => onSelectedRowKeysChange(keys as string[]),
        }}
        columns={columns}
        pagination={{
          current: Math.max(1, Number(page) || 1),
          pageSize: Math.max(1, Number(pageSize) || 10),
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => onPageChange(p, s),
        }}
      />
    </Card>
  );
}

interface FetchHistoryCardProps {
  history: any[];
  loading: boolean;
  selectedRowKeys: string[];
  page: number;
  pageSize: number;
  total: number;
  columns: any[];
  onRefresh: () => void;
  onPageChange: (page: number, size?: number) => void;
  onSelectedRowKeysChange: (keys: string[]) => void;
  onBatchDelete: () => void;
  onClearAll: () => void;
}

function FetchHistoryCard({
  history,
  loading,
  selectedRowKeys,
  page,
  pageSize,
  total,
  columns,
  onRefresh,
  onPageChange,
  onSelectedRowKeysChange,
  onBatchDelete,
  onClearAll,
}: FetchHistoryCardProps) {
  return (
    <Card
      title="刷新任务记录"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} type="link" onClick={onRefresh}>
            刷新
          </Button>
          <Button
            type="link"
            danger
            disabled={selectedRowKeys.length === 0}
            icon={<DeleteOutlined />}
            onClick={onBatchDelete}
          >
            批量清除{selectedRowKeys.length > 0 ? ` (${selectedRowKeys.length})` : ''}
          </Button>
          <Popconfirm
            title="确定一键全清所有手动刷新记录？此操作不可恢复。"
            onConfirm={onClearAll}
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
        dataSource={history}
        rowKey="id"
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => onSelectedRowKeysChange(keys as string[]),
        }}
        columns={columns}
        pagination={{
          current: Math.max(1, Number(page) || 1),
          pageSize: Math.max(1, Number(pageSize) || 10),
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => onPageChange(p, s),
        }}
      />
    </Card>
  );
}

export default Settings;

