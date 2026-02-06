import { useEffect, useState } from 'react';
import { Card, Space, Switch, Button, message } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import MainLayout from '../components/Layout/MainLayout';
import { taskApi, getApiErrorMessage } from '../services/api';

function SystemSettings() {
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [scheduleInterval, setScheduleInterval] = useState<string>('');
  const [loadingSchedule, setLoadingSchedule] = useState(false);

  const [contentAssetDownloadEnabled, setContentAssetDownloadEnabled] = useState(true);
  const [loadingContentAssetDownload, setLoadingContentAssetDownload] = useState(false);

  const loadStatus = async () => {
    try {
      const res: any = await taskApi.getScheduleStatusDetail();
      if (res?.code === 200 && res.data) {
        const data = res.data;
        const enabled = data != null ? (data?.isEnabled ?? data === true) : true;
        setScheduleEnabled(Boolean(enabled));
        if (typeof data.interval === 'string') {
          setScheduleInterval(data.interval);
        } else {
          setScheduleInterval('');
        }
        if (typeof data.contentAssetDownloadEnabled === 'boolean') {
          setContentAssetDownloadEnabled(data.contentAssetDownloadEnabled);
        } else {
          setContentAssetDownloadEnabled(true);
        }
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载系统设置失败'));
    }
  };

  useEffect(() => {
    loadStatus();
  }, []);

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
        message.success(
          enabled
            ? '已开启文章附件下载（将下载到本地并使用本地地址）'
            : '已关闭文章附件下载（使用平台原始地址）',
        );
      } else {
        message.error(res?.message || '操作失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    } finally {
      setLoadingContentAssetDownload(false);
    }
  };

  return (
    <MainLayout>
      <div>
        <div
          style={{
            marginBottom: 16,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <h2 style={{ margin: 0 }}>系统设置</h2>
          <Button icon={<ReloadOutlined />} onClick={loadStatus}>
            刷新
          </Button>
        </div>

        <Card title="全局开关">
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Space>
              <span>全局定时任务：</span>
              <Switch
                checked={scheduleEnabled}
                loading={loadingSchedule}
                onChange={handleToggleGlobalSchedule}
                checkedChildren={<CheckCircleOutlined />}
                unCheckedChildren={<CloseCircleOutlined />}
              />
              <span>{scheduleEnabled ? '已启用' : '已禁用'}</span>
              {scheduleInterval ? (
                <span style={{ color: '#666' }}>（执行周期：{scheduleInterval}）</span>
              ) : null}
            </Space>

            <Space>
              <span>文章附件下载到本地：</span>
              <Switch
                checked={contentAssetDownloadEnabled}
                loading={loadingContentAssetDownload}
                onChange={handleToggleContentAssetDownload}
                checkedChildren={<CheckCircleOutlined />}
                unCheckedChildren={<CloseCircleOutlined />}
              />
              <span>
                {contentAssetDownloadEnabled
                  ? '开启（下载到本地并使用本地地址）'
                  : '关闭（使用平台原始地址）'}
              </span>
            </Space>
          </Space>
        </Card>
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
          .ant-space {
            flex-wrap: wrap;
            gap: 8px !important;
          }
          .ant-space-item {
            flex: 1;
            min-width: 0;
          }
          .ant-space-item span {
            font-size: 13px;
            word-break: break-word;
          }
        }
      `}</style>
    </MainLayout>
  );
}

export default SystemSettings;

