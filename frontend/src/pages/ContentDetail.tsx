import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Descriptions, Space, Button, Tag, message, Spin, Form, Input, Image, Modal } from 'antd';
import { ArrowLeftOutlined, LinkOutlined, StarOutlined, StarFilled, EyeOutlined, ReloadOutlined, LeftOutlined, RightOutlined, DeleteOutlined } from '@ant-design/icons';
import { contentApi, getApiErrorMessage, getPlatformAvatarSrc, getContentImageSrc, getContentAttachmentUrl } from '../services/api';
import MainLayout from '../components/Layout/MainLayout';
import { getContentOriginalUrl, parseContentMetadata } from '../utils/contentUtils';

interface ContentDetailData {
  id: string;
  title?: string;
  body?: string;
  url?: string;
  contentId?: string;
  contentType?: string;
  publishedAt?: string;
  isRead?: boolean;
  isFavorite?: boolean;
  platform?: { name?: string; avatarUrl?: string };
  user?: { username?: string; avatarUrl?: string };
  metadata?: any;
  mediaUrls?: string[];
  notes?: string;
}

/** 处理内容正文中的图片 URL，将本地路径转换为完整 URL */
function processContentBody(body: string): string {
  if (!body) return '';
  // 匹配 <img> 标签中的 src 属性
  return body.replace(/<img([^>]*)\ssrc=["']([^"']+)["']([^>]*)>/gi, (_match, before, src, after) => {
    const processedSrc = getContentImageSrc(src) || src;
    return `<img${before} src="${processedSrc}"${after}>`;
  });
}

function ContentDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [content, setContent] = useState<ContentDetailData | null>(null);
  const [loading, setLoading] = useState(false);
  const [savingNote, setSavingNote] = useState(false);
  const [adjacentContents, setAdjacentContents] = useState<{ previous: ContentDetailData | null; next: ContentDetailData | null }>({
    previous: null,
    next: null,
  });
  const [loadingAdjacent, setLoadingAdjacent] = useState(false);
  const [refreshingAssets, setRefreshingAssets] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    if (id) {
      loadContent(id);
      loadAdjacentContents(id);
    }
  }, [id]);

  const loadContent = async (contentId: string) => {
    setLoading(true);
    try {
      const response: any = await contentApi.getById(contentId);
      if (response?.code === 200 && response?.data) {
        setContent(response.data);
        form.setFieldsValue({ notes: response.data.notes ?? '' });
      } else {
        message.error(response?.message || '加载内容详情失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载内容详情失败'));
    } finally {
      setLoading(false);
    }
  };

  const loadAdjacentContents = async (contentId: string) => {
    setLoadingAdjacent(true);
    try {
      // sameUserOnly=true: 只在同一作者的所有文章中按时间顺序跳转
      const response: any = await contentApi.getAdjacent(contentId, true);
      if (response?.code === 200 && response?.data) {
        setAdjacentContents({
          previous: response.data.previous || null,
          next: response.data.next || null,
        });
      }
    } catch (error) {
      // 静默失败，不影响主要内容显示
      console.warn('加载相邻内容失败:', error);
    } finally {
      setLoadingAdjacent(false);
    }
  };

  const handleToggleFavorite = async () => {
    if (!content || !id) return;
    try {
      const response: any = await contentApi.update(id, { isFavorite: !content.isFavorite });
      if (response.code === 200) {
        message.success(content.isFavorite ? '已取消收藏' : '已收藏');
        setContent({ ...content, isFavorite: !content.isFavorite });
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleMarkRead = async () => {
    if (!content || !id || content.isRead) return;
    try {
      const response: any = await contentApi.update(id, { isRead: true });
      if (response.code === 200) {
        message.success('已标记为已读');
        setContent({ ...content, isRead: true });
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleSaveNote = async () => {
    if (!content || !id) return;
    try {
      const values = await form.validateFields();
      setSavingNote(true);
      const response: any = await contentApi.update(id, { notes: values.notes || '' });
      if (response.code === 200) {
        message.success('备注已保存');
        setContent({ ...content, notes: values.notes || '' });
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      message.error(getApiErrorMessage(error, '保存备注失败'));
    } finally {
      setSavingNote(false);
    }
  };

  const handleRefreshAssets = async () => {
    if (!id) return;
    try {
      setRefreshingAssets(true);
      const res: any = await contentApi.refreshAssets(id);
      if (res?.code === 200 && res?.data) {
        message.success('图片已刷新到本地');
        setContent(res.data);
      } else {
        message.error(res?.message || '刷新图片失败');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '刷新图片失败'));
    } finally {
      setRefreshingAssets(false);
    }
  };

  const handleDelete = () => {
    if (!id) return;
    Modal.confirm({
      title: '确认删除',
      content: '将删除该文章的文字内容、图片、附件等全部内容，此操作不可恢复。确定要删除吗？',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          setDeleting(true);
          const res: any = await contentApi.delete(id);
          if (res?.code === 200) {
            message.success('已删除');
            navigate(-1);
          } else {
            message.error(res?.message || '删除失败');
          }
        } catch (error) {
          message.error(getApiErrorMessage(error, '删除失败'));
        } finally {
          setDeleting(false);
        }
      },
    });
  };

  return (
    <MainLayout>
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} type="link" onClick={() => navigate(-1)}>
              返回
            </Button>
            <span>内容详情</span>
            {id && (
              <Button icon={<ReloadOutlined />} type="link" onClick={() => loadContent(id)} loading={loading}>
                刷新
              </Button>
            )}
          </Space>
        }
      >
        {loading ? (
          <Spin />
        ) : !content ? (
          <div>暂无数据</div>
        ) : (
          (() => {
            const meta = parseContentMetadata(content.metadata);
            const authorName = meta.nickName ?? content.user?.username ?? '-';
            // 优先使用 user.avatarUrl，其次使用 metadata 中的 userAvatar
            const authorAvatar = content.user?.avatarUrl || meta.userAvatar;
            const authorAvatarSrc = authorAvatar ? (getPlatformAvatarSrc(authorAvatar) || authorAvatar) : undefined;
            const originalUrl = getContentOriginalUrl({ url: content.url, contentId: content.contentId, platform: content.platform });

            return (
          <Space direction="vertical" style={{ width: '100%' }} size="large">
            <Space>
              <h2 style={{ margin: 0 }}>{content.title || '无标题'}</h2>
              {content.contentType && <Tag>{content.contentType}</Tag>}
              {content.isRead ? (
                <Tag color="green">已读</Tag>
              ) : (
                <Tag color="orange">未读</Tag>
              )}
              {content.isFavorite && <Tag color="red">收藏</Tag>}
            </Space>

            <Space wrap>
              {originalUrl && (
                <Button
                  icon={<LinkOutlined />}
                  type="default"
                  onClick={() => window.open(originalUrl, '_blank')}
                >
                  打开原文
                </Button>
              )}
              <Button
                icon={content.isFavorite ? <StarFilled /> : <StarOutlined />}
                type="default"
                onClick={handleToggleFavorite}
              >
                {content.isFavorite ? '取消收藏' : '收藏'}
              </Button>
              {!content.isRead && (
                <Button
                  icon={<EyeOutlined />}
                  type="default"
                  onClick={handleMarkRead}
                >
                  标记已读
                </Button>
              )}
              <Button
                icon={<ReloadOutlined />}
                type="default"
                loading={refreshingAssets}
                onClick={handleRefreshAssets}
              >
                刷新图片
              </Button>
              <Button
                icon={<DeleteOutlined />}
                type="primary"
                danger
                loading={deleting}
                onClick={handleDelete}
              >
                删除文章
              </Button>
            </Space>

            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="平台">
                {content.platform?.name || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="作者">
                <Space>
                  {authorAvatarSrc && (
                    <img
                      src={authorAvatarSrc}
                      alt=""
                      style={{ width: 24, height: 24, borderRadius: '50%', objectFit: 'cover' }}
                    />
                  )}
                  <span>{authorName}</span>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="发布时间">
                {content.publishedAt
                  ? new Date(content.publishedAt).toLocaleString('zh-CN')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="链接">
                {originalUrl || content.url || '-'}
              </Descriptions.Item>
            </Descriptions>

            {/* 上一篇/下一篇导航 */}
            <Card size="small" style={{ backgroundColor: '#fafafa' }}>
              <Space style={{ width: '100%', justifyContent: 'space-between' }} size="middle">
                <Button
                  icon={<LeftOutlined />}
                  disabled={!adjacentContents.previous}
                  onClick={() => {
                    if (adjacentContents.previous?.id) {
                      navigate(`/contents/${adjacentContents.previous.id}`);
                    }
                  }}
                  loading={loadingAdjacent}
                  style={{ flex: 1, textAlign: 'left' }}
                >
                  {adjacentContents.previous ? (
                    <span style={{ display: 'inline-block', maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      上一篇：{((adjacentContents.previous.title || '无标题').length > 8 
                        ? (adjacentContents.previous.title || '无标题').substring(0, 8) + '...'
                        : (adjacentContents.previous.title || '无标题'))}
                    </span>
                  ) : (
                    '上一篇'
                  )}
                </Button>
                <Button
                  icon={<RightOutlined />}
                  disabled={!adjacentContents.next}
                  onClick={() => {
                    if (adjacentContents.next?.id) {
                      navigate(`/contents/${adjacentContents.next.id}`);
                    }
                  }}
                  loading={loadingAdjacent}
                  style={{ flex: 1, textAlign: 'right' }}
                >
                  {adjacentContents.next ? (
                    <span style={{ display: 'inline-block', maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {((adjacentContents.next.title || '无标题').length > 8 
                        ? (adjacentContents.next.title || '无标题').substring(0, 8) + '...'
                        : (adjacentContents.next.title || '无标题'))}：下一篇
                    </span>
                  ) : (
                    '下一篇'
                  )}
                </Button>
              </Space>
            </Card>

            {content.body && (
              <Card title="正文" size="small">
                <div 
                  style={{ whiteSpace: 'pre-wrap' }}
                  dangerouslySetInnerHTML={{
                    __html: processContentBody(content.body)
                  }}
                />
              </Card>
            )}

            {content.mediaUrls && content.mediaUrls.length > 0 && (
              <Card title="图片" size="small">
                <Image.PreviewGroup>
                  <Space wrap size="small">
                    {content.mediaUrls.map((src, i) => (
                      <Image
                        key={i}
                        src={getContentImageSrc(src) || src}
                        alt=""
                        width={200}
                        height={200}
                        style={{ objectFit: 'cover', borderRadius: 4 }}
                        fallback="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200'%3E%3Crect fill='%23f0f0f0' width='200' height='200'/%3E%3Ctext x='50%25' y='50%25' fill='%23999' text-anchor='middle' dy='.3em'%3E加载失败%3C/text%3E%3C/svg%3E"
                      />
                    ))}
                  </Space>
                </Image.PreviewGroup>
                <div style={{ marginTop: 8, fontSize: 12, color: '#888' }}>
                  点击可放大查看。
                </div>
              </Card>
            )}

            {(() => {
              const metaObj = typeof content.metadata === 'string' ? (() => { try { return JSON.parse(content.metadata); } catch { return {}; } })() : (content.metadata || {});
              const downloadedFiles: { file_id?: string; local_url?: string }[] = Array.isArray(metaObj.downloaded_file_urls) ? metaObj.downloaded_file_urls : [];
              if (downloadedFiles.length === 0) return null;
              return (
                <Card title="附件" size="small">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {downloadedFiles.map((f, i) => {
                      const localUrl = f.local_url;
                      if (!localUrl) return null;
                      const fullUrl = getContentAttachmentUrl(localUrl) || localUrl;
                      const isPdf = /\.pdf$/i.test(localUrl);
                      return (
                        <div key={f.file_id || i}>
                          {isPdf ? (
                            <>
                              <div style={{ marginBottom: 8 }}>
                                <a href={fullUrl} target="_blank" rel="noopener noreferrer">在新窗口打开 PDF</a>
                              </div>
                              <iframe
                                title={`PDF ${i + 1}`}
                                src={fullUrl}
                                style={{ width: '100%', height: 600, border: '1px solid #eee', borderRadius: 4 }}
                              />
                            </>
                          ) : (
                            <a href={fullUrl} target="_blank" rel="noopener noreferrer" download>
                              下载文件 {f.file_id || i + 1}
                            </a>
                          )}
                        </div>
                      );
                    })}
                  </Space>
                </Card>
              );
            })()}

            {content.metadata && (
              <Card title="元数据（完整文章 JSON）" size="small">
                <pre style={{ whiteSpace: 'pre-wrap', maxHeight: 400, overflow: 'auto' }}>
                  {typeof content.metadata === 'string'
                    ? content.metadata
                    : JSON.stringify(content.metadata, null, 2)}
                </pre>
                {!authorAvatar && !meta.nickName && (
                  <div style={{ marginTop: 8, fontSize: 12, color: '#888' }}>
                    若此处仅显示部分字段，可在「用户管理」对该用户重新执行「刷新内容」以拉取完整文章 JSON（含作者昵称、头像等）。
                  </div>
                )}
              </Card>
            )}

            <Card title="我的备注" size="small">
              <Form form={form} layout="vertical">
                <Form.Item name="notes">
                  <Input.TextArea
                    rows={4}
                    placeholder="在这里记录你对这篇内容的想法、TODO 或摘要..."
                  />
                </Form.Item>
                <Button type="primary" onClick={handleSaveNote} loading={savingNote}>
                  保存备注
                </Button>
              </Form>
            </Card>
          </Space>
            );
          })()
        )}
      </Card>
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
          .ant-descriptions-item-label,
          .ant-descriptions-item-content {
            padding: 6px 8px !important;
            font-size: 12px;
          }
          .ant-image {
            width: 100% !important;
            max-width: 100%;
          }
          .ant-image-img {
            width: 100% !important;
            height: auto !important;
          }
          pre {
            font-size: 11px;
            max-height: 300px;
          }
          .ant-space {
            gap: 8px !important;
          }
          .ant-space-wrap {
            flex-wrap: wrap;
          }
        }
      `}</style>
    </MainLayout>
  );
}

export default ContentDetail;

