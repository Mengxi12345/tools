import { useState, useEffect, useRef } from 'react';
import { Card, Select, Button, Space, message, Progress, List, Modal, Table, Tag, Tooltip, Popconfirm } from 'antd';
import { DownloadOutlined, ReloadOutlined, EyeOutlined, DeleteOutlined } from '@ant-design/icons';
import { userApi, getToken, exportApi, getApiErrorMessage } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';

const POLL_INTERVAL = 60000; // 60秒刷新一次，降低服务器压力
const TERMINAL_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED'];

function Export() {
  const [users, setUsers] = useState<any[]>([]);
  const [userId, setUserId] = useState<string | undefined>(undefined);
  const [sortOrder, setSortOrder] = useState<'ASC' | 'DESC'>('DESC');
  const [loading, setLoading] = useState<string | null>(null);
  const [exportTask, setExportTask] = useState<any | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const [taskList, setTaskList] = useState<any[]>([]);
  const [taskListLoading, setTaskListLoading] = useState(false);
  const [taskListPagination, setTaskListPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [deletingTaskId, setDeletingTaskId] = useState<string | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [batchDeleting, setBatchDeleting] = useState(false);
  const [deletingAll, setDeletingAll] = useState(false);

  const loadUsers = async () => {
    try {
      const res: any = await userApi.getAll({ page: 0, size: 500 });
      if (res?.code === 200) {
        setUsers(res.data?.content || []);
      } else {
        console.warn('加载用户列表失败:', res?.message);
        setUsers([]); // 确保即使失败也有空数组
      }
    } catch (error) {
      console.error('加载用户列表异常:', error);
      setUsers([]); // 确保即使失败也有空数组
      // 不显示错误消息，避免干扰页面显示
    }
  };

  useEffect(() => {
    loadUsers();
    loadTaskList(1, 10);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadTaskList = async (page: number = 1, size: number = 10) => {
    setTaskListLoading(true);
    try {
      const res: any = await exportApi.getTasks({ 
        ...(userId ? { userId } : {}), 
        page: page - 1, 
        size 
      });
      if (res?.code === 200 && res.data) {
        setTaskList(res.data.content || []);
        setTaskListPagination({
          current: page,
          pageSize: size,
          total: res.data.totalElements || 0,
        });
      }
    } catch (error) {
      console.error('加载任务列表失败:', error);
      // 即使失败也设置空列表，确保页面能正常显示
      setTaskList([]);
    } finally {
      setTaskListLoading(false);
    }
  };

  const handleViewTask = (task: any) => {
    setExportTask(task);
    // 如果任务还在运行中，开始轮询
    if (task.status === 'PENDING' || task.status === 'RUNNING') {
      pollTask(task.id);
      pollRef.current = setInterval(() => pollTask(task.id), POLL_INTERVAL);
    }
  };

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const pollTask = async (taskId: string) => {
    try {
      const res: any = await exportApi.getTask(taskId);
      if (res?.code === 200 && res.data) {
        setExportTask(res.data);
        // 更新任务列表中的对应任务
        setTaskList((prev) =>
          prev.map((t) => (t.id === taskId ? res.data : t))
        );
        if (TERMINAL_STATUSES.includes(res.data.status)) {
          stopPolling();
          setLoading(null); // 任务完成或失败时清除 loading
          // 刷新任务列表（延迟一下，确保后端已保存）
          setTimeout(() => {
            loadTaskList(taskListPagination.current, taskListPagination.pageSize);
          }, 500);
          if (res.data.status === 'COMPLETED') message.success('导出完成');
          if (res.data.status === 'FAILED') message.error(res.data.errorMessage || '导出失败');
        }
      }
    } catch (e) {
      // 如果获取任务状态失败，不立即停止轮询，可能是临时网络问题
      console.warn('获取任务状态失败:', e);
      // 只在连续失败多次后才停止轮询
    }
  };

  const handleDownload = async (format: 'json' | 'markdown' | 'csv' | 'html' | 'pdf' | 'word') => {
    if (format === 'pdf' || format === 'word') {
      if (!userId) {
        message.warning('PDF/Word 导出需先选择用户');
        return;
      }
      setLoading(format);
      let taskId: string | null = null;
      console.log('开始创建导出任务:', { format, userId, sortOrder });
      try {
        const fmt = format === 'pdf' ? 'PDF' : 'WORD';
        // 创建异步任务，使用60秒超时
        console.log('调用 createAsync API...');
        const res: any = await exportApi.createAsync({
          format: fmt,
          userId,
          sortOrder,
        });
        console.log('createAsync 响应:', res);
        if (res?.code === 200 && res.data?.id) {
          taskId = res.data.id;
          console.log('任务创建成功，taskId:', taskId);
          // 立即显示进度弹窗
          setExportTask(res.data);
          console.log('已设置 exportTask，开始轮询...');
          // 立即开始第一次轮询，然后设置定时轮询
          pollTask(res.data.id);
          pollRef.current = setInterval(() => pollTask(res.data.id), POLL_INTERVAL);
          console.log('轮询已启动');
          // 刷新任务列表
          loadTaskList(1, 10);
          // 任务创建成功，不清除 loading，让进度弹窗接管显示
          // loading 状态会在进度弹窗关闭或任务完成时清除
        } else {
          console.error('任务创建失败，响应:', res);
          setLoading(null);
          message.error(res?.message || '创建导出任务失败');
        }
      } catch (e: any) {
        console.error('创建导出任务异常:', e);
        console.error('错误详情:', {
          code: e?.code,
          message: e?.message,
          response: e?.response?.data,
          isTimeout: e?.code === 'ECONNABORTED' || e?.message?.includes('timeout'),
        });
        
        // 如果是超时错误，尝试从任务列表中查找最近创建的任务
        if ((e?.code === 'ECONNABORTED' || e?.message?.includes('timeout')) && userId) {
          console.log('请求超时，尝试查找最近创建的任务...');
          // 刷新任务列表，查找最近创建的任务
          try {
            const listRes: any = await exportApi.getTasks({ userId, page: 0, size: 5 });
            if (listRes?.code === 200 && listRes.data) {
              const tasks = listRes.data.content || [];
              // 查找最近创建的 PENDING 或 RUNNING 状态的任务
              const recentTask = tasks.find(
                (t: any) => t.userId === userId && (t.status === 'PENDING' || t.status === 'RUNNING')
              );
              if (recentTask) {
                console.log('找到最近创建的任务:', recentTask);
                setExportTask(recentTask);
                pollTask(recentTask.id);
                pollRef.current = setInterval(() => pollTask(recentTask.id), POLL_INTERVAL);
                message.warning('请求超时，但已找到最近创建的任务，正在显示进度...', 3);
                // 刷新任务列表
                loadTaskList(1, 10);
                return; // 成功找到任务，不显示错误
              }
            }
          } catch (taskListError) {
            console.warn('加载任务列表失败:', taskListError);
          }
          // 刷新任务列表，让用户可以看到
          loadTaskList(1, 10);
          message.warning('请求超时，任务可能已创建。请查看下方的任务列表。', 5);
        }
        
        setLoading(null);
        // 如果是超时错误，提示用户可能是网络问题
        const errorMsg = e?.code === 'ECONNABORTED' || e?.message?.includes('timeout')
          ? '创建导出任务超时（60秒），请检查网络连接或稍后重试。如果任务已创建，请刷新页面查看任务列表。'
          : getApiErrorMessage(e, '创建导出任务失败');
        message.error(errorMsg);
        console.error('创建导出任务失败:', e);
      }
      return;
    }

    // 同步下载：JSON/Markdown/CSV/HTML
    let url: string;
    let filename: string;
    url = format === 'json' ? exportApi.getJsonUrl({ userId })
      : format === 'markdown' ? exportApi.getMarkdownUrl({ userId })
      : format === 'csv' ? exportApi.getCsvUrl({ userId })
      : exportApi.getHtmlUrl({ userId });
    filename = `export.${format === 'json' ? 'json' : format === 'markdown' ? 'md' : format}`;
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
      a.download = filename;
      a.click();
      URL.revokeObjectURL(a.href);
      message.success('下载成功');
    } catch (e: any) {
      message.error(getApiErrorMessage(e, '下载失败'));
    } finally {
      setLoading(null);
    }
  };

  const handleDownloadFile = async () => {
    if (!exportTask?.id) return;
    const token = getToken();
    const url = exportApi.getDownloadUrl(exportTask.id);
    const ext = exportTask.exportFormat === 'PDF' ? 'pdf' : 'docx';
    // 使用后端返回的 filePath 中的文件名（username-平台-时间.ext），否则回退到 export.ext
    const filename = exportTask?.filePath
      ? exportTask.filePath.replace(/^.*[/\\]/, '') || `export.${ext}`
      : `export.${ext}`;
    try {
      const res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
      if (!res.ok) throw new Error('下载失败');
      const blob = await res.blob();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = filename;
      a.click();
      URL.revokeObjectURL(a.href);
      message.success('下载成功');
    } catch (e) {
      message.error(getApiErrorMessage(e, '下载失败'));
    }
  };

  const closeExportModal = () => {
    stopPolling();
    setExportTask(null);
    setLoading(null); // 关闭弹窗时清除 loading 状态
  };

  const handleDeleteTask = async (taskId: string) => {
    setDeletingTaskId(taskId);
    try {
      await exportApi.deleteTask(taskId);
      message.success('删除导出任务成功');
      // 刷新列表
      loadTaskList(taskListPagination.current, taskListPagination.pageSize);
      // 如果当前弹窗正在查看这个任务，也一并关闭
      if (exportTask?.id === taskId) {
        closeExportModal();
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '删除导出任务失败'));
    } finally {
      setDeletingTaskId(null);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择要删除的任务');
      return;
    }
    setBatchDeleting(true);
    try {
      const res: any = await exportApi.batchDeleteTasks(selectedRowKeys as string[]);
      if (res?.code === 200) {
        message.success(`成功删除 ${res.data} 个导出任务`);
        setSelectedRowKeys([]);
        // 刷新列表
        loadTaskList(taskListPagination.current, taskListPagination.pageSize);
        // 如果当前查看的任务被删除，关闭弹窗
        if (exportTask && selectedRowKeys.includes(exportTask.id)) {
          closeExportModal();
        }
      } else {
        message.error(res?.message || '批量删除失败');
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '批量删除导出任务失败'));
    } finally {
      setBatchDeleting(false);
    }
  };

  const handleDeleteAll = async () => {
    setDeletingAll(true);
    try {
      const res: any = await exportApi.deleteAllTasks();
      if (res?.code === 200) {
        message.success(`成功删除所有导出任务（共 ${res.data} 个）`);
        setSelectedRowKeys([]);
        // 刷新列表
        loadTaskList(taskListPagination.current, taskListPagination.pageSize);
        // 关闭弹窗
        closeExportModal();
      } else {
        message.error(res?.message || '一键全清失败');
      }
    } catch (e) {
      message.error(getApiErrorMessage(e, '一键全清导出任务失败'));
    } finally {
      setDeletingAll(false);
    }
  };

  useEffect(() => () => stopPolling(), []);

  const logMessages: string[] = (() => {
    if (!exportTask?.logMessages) return [];
    try {
      const arr = JSON.parse(exportTask.logMessages);
      return Array.isArray(arr) ? arr : [];
    } catch {
      return [];
    }
  })();

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
            <Space align="center">
              <span>按用户筛选（留空则导出全部，PDF/Word 需选择用户）：</span>
              <Select
                placeholder="选择用户"
                allowClear
                style={{ width: 260 }}
                value={userId}
                onChange={(v) => setUserId(v)}
                options={users.map((u) => ({ label: u.username, value: u.id }))}
              />
            </Space>
            <Space align="center">
              <span>日期排序（PDF/Word 生效）：</span>
              <Select
                value={sortOrder}
                onChange={(v) => setSortOrder(v)}
                style={{ width: 120 }}
                options={[
                  { value: 'DESC', label: '日期降序（新→旧）' },
                  { value: 'ASC', label: '日期升序（旧→新）' },
                ]}
              />
            </Space>
          </Space>
        </Card>
        <Card title="导出格式">
          <Space wrap>
            <Button icon={<DownloadOutlined />} loading={loading === 'json'} onClick={() => handleDownload('json')}>JSON</Button>
            <Button icon={<DownloadOutlined />} loading={loading === 'markdown'} onClick={() => handleDownload('markdown')}>Markdown</Button>
            <Button icon={<DownloadOutlined />} loading={loading === 'csv'} onClick={() => handleDownload('csv')}>CSV</Button>
            <Button icon={<DownloadOutlined />} loading={loading === 'html'} onClick={() => handleDownload('html')}>HTML</Button>
            <Button icon={<DownloadOutlined />} loading={loading === 'pdf'} onClick={() => handleDownload('pdf')}>PDF</Button>
            <Button icon={<DownloadOutlined />} loading={loading === 'word'} onClick={() => handleDownload('word')}>Word</Button>
          </Space>
        </Card>

        <Modal
          title={`导出进度 - ${exportTask?.exportFormat === 'PDF' ? 'PDF' : 'Word'}`}
          open={!!exportTask}
          onCancel={closeExportModal}
          footer={[
            exportTask?.status === 'COMPLETED' && exportTask?.filePath ? (
              <Button key="download" type="primary" icon={<DownloadOutlined />} onClick={handleDownloadFile}>
                下载文件
              </Button>
            ) : null,
            <Button key="close" onClick={closeExportModal}>
              {exportTask?.status === 'COMPLETED' || exportTask?.status === 'FAILED' ? '关闭' : '取消'}
            </Button>,
          ].filter(Boolean)}
          width={520}
          destroyOnClose
          maskClosable={false}
        >
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Progress
              percent={exportTask?.progress ?? 0}
              status={
                exportTask?.status === 'FAILED' ? 'exception' :
                exportTask?.status === 'COMPLETED' ? 'success' : 
                exportTask?.status === 'PENDING' ? 'active' : 'active'
              }
              format={(percent) => {
                if (exportTask?.status === 'PENDING') {
                  return '准备中...';
                }
                return `${percent ?? 0}%`;
              }}
            />
            {exportTask?.status === 'FAILED' && exportTask?.errorMessage && (
              <div style={{ color: 'var(--ant-color-error)' }}>{exportTask.errorMessage}</div>
            )}
            {logMessages.length > 0 && (
              <div>
                <div style={{ marginBottom: 8, fontWeight: 500 }}>导出日志</div>
                <List
                  size="small"
                  bordered
                  dataSource={logMessages}
                  renderItem={(msg) => <List.Item>{msg}</List.Item>}
                  style={{ maxHeight: 200, overflow: 'auto' }}
                />
              </div>
            )}
          </Space>
        </Modal>

        <Card
          title="导出任务列表"
          style={{ marginTop: 16 }}
          extra={
            <Space>
              {selectedRowKeys.length > 0 && (
                <Popconfirm
                  title={`确认删除选中的 ${selectedRowKeys.length} 个导出任务？`}
                  description="将删除任务记录及对应导出文件，此操作不可恢复。"
                  onConfirm={handleBatchDelete}
                  okText="删除"
                  cancelText="取消"
                >
                  <Button
                    danger
                    icon={<DeleteOutlined />}
                    loading={batchDeleting}
                  >
                    批量删除 ({selectedRowKeys.length})
                  </Button>
                </Popconfirm>
              )}
              <Popconfirm
                title="确认删除所有导出任务？"
                description="将删除所有任务记录及对应导出文件，此操作不可恢复。"
                onConfirm={handleDeleteAll}
                okText="删除"
                cancelText="取消"
              >
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  loading={deletingAll}
                >
                  一键全清
                </Button>
              </Popconfirm>
              <Button icon={<ReloadOutlined />} onClick={() => loadTaskList(taskListPagination.current, taskListPagination.pageSize)}>
                刷新
              </Button>
            </Space>
          }
        >
          <Table
            dataSource={taskList}
            loading={taskListLoading}
            rowKey="id"
            rowSelection={{
              selectedRowKeys,
              onChange: (keys) => setSelectedRowKeys(keys),
            }}
            pagination={{
              current: taskListPagination.current,
              pageSize: taskListPagination.pageSize,
              total: taskListPagination.total,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (page, size) => loadTaskList(page, size),
              onShowSizeChange: (page, size) => loadTaskList(page, size),
            }}
            columns={[
              {
                title: '任务ID',
                dataIndex: 'id',
                key: 'id',
                width: 280,
                render: (id: string) => (
                  <Tooltip title={id}>
                    <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
                      {id.substring(0, 8)}...
                    </span>
                  </Tooltip>
                ),
              },
              {
                title: '格式',
                dataIndex: 'exportFormat',
                key: 'exportFormat',
                width: 100,
                render: (format: string) => (
                  <Tag color={format === 'PDF' ? 'red' : format === 'WORD' ? 'blue' : 'default'}>
                    {format}
                  </Tag>
                ),
              },
              {
                title: '状态',
                dataIndex: 'status',
                key: 'status',
                width: 100,
                render: (status: string) => {
                  const statusConfig: Record<string, { color: string; text: string }> = {
                    PENDING: { color: 'default', text: '等待中' },
                    RUNNING: { color: 'processing', text: '执行中' },
                    COMPLETED: { color: 'success', text: '已完成' },
                    FAILED: { color: 'error', text: '失败' },
                  };
                  const config = statusConfig[status] || { color: 'default', text: status };
                  return <Tag color={config.color}>{config.text}</Tag>;
                },
              },
              {
                title: '进度',
                dataIndex: 'progress',
                key: 'progress',
                width: 150,
                render: (progress: number, record: any) => (
                  <Progress
                    percent={progress || 0}
                    size="small"
                    status={
                      record.status === 'FAILED' ? 'exception' :
                      record.status === 'COMPLETED' ? 'success' : 'active'
                    }
                  />
                ),
              },
              {
                title: '创建时间',
                dataIndex: 'createdAt',
                key: 'createdAt',
                width: 180,
                render: (time: string) => {
                  if (!time) return '-';
                  try {
                    const date = new Date(time);
                    return date.toLocaleString('zh-CN', {
                      year: 'numeric',
                      month: '2-digit',
                      day: '2-digit',
                      hour: '2-digit',
                      minute: '2-digit',
                      second: '2-digit',
                    });
                  } catch {
                    return time;
                  }
                },
              },
              {
                title: '文件名',
                dataIndex: 'filePath',
                key: 'filePath',
                width: 250,
                render: (filePath: string) => {
                  if (!filePath) {
                    return <span style={{ color: '#999' }}>未生成</span>;
                  }
                  // 从完整路径中提取文件名
                  const fileName = filePath.replace(/^.*[/\\]/, '');
                  return (
                    <Tooltip title={filePath}>
                      <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
                        {fileName}
                      </span>
                    </Tooltip>
                  );
                },
              },
              {
                title: '操作',
                key: 'action',
                width: 220,
                render: (_: any, record: any) => (
                  <Space>
                    <Button
                      type="link"
                      size="small"
                      icon={<EyeOutlined />}
                      onClick={() => handleViewTask(record)}
                    >
                      查看进度
                    </Button>
                    {record.status === 'COMPLETED' && record.filePath && (
                      <Button
                        type="link"
                        size="small"
                        icon={<DownloadOutlined />}
                        onClick={async () => {
                          const token = getToken();
                          const url = exportApi.getDownloadUrl(record.id);
                          const ext = record.exportFormat === 'PDF' ? 'pdf' : 'docx';
                          const filename = record.filePath
                            ? record.filePath.replace(/^.*[/\\]/, '') || `export.${ext}`
                            : `export.${ext}`;
                          try {
                            const res = await fetch(url, {
                              headers: token ? { Authorization: `Bearer ${token}` } : {},
                            });
                            if (!res.ok) throw new Error('下载失败');
                            const blob = await res.blob();
                            const a = document.createElement('a');
                            a.href = URL.createObjectURL(blob);
                            a.download = filename;
                            a.click();
                            URL.revokeObjectURL(a.href);
                            message.success('下载成功');
                          } catch (e) {
                            message.error(getApiErrorMessage(e, '下载失败'));
                          }
                        }}
                      >
                        下载
                      </Button>
                    )}
                    <Popconfirm
                      title="确认删除该导出任务？"
                      description="将删除任务记录及对应导出文件，此操作不可恢复。"
                      onConfirm={() => handleDeleteTask(record.id)}
                      okText="删除"
                      cancelText="取消"
                    >
                      <Button
                        type="link"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        loading={deletingTaskId === record.id}
                      >
                        删除
                      </Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        </Card>
      </div>
    </MainLayout>
  );
}

export default Export;
