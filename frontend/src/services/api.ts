import axios from 'axios';

const API_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export const AUTH_TOKEN_KEY = 'caat_token';

export function getToken(): string | null {
  return localStorage.getItem(AUTH_TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(AUTH_TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(AUTH_TOKEN_KEY);
}

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器：添加 JWT
apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：统一返回 body；业务错误（code !== 200）也 reject 便于页面展示后端消息
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data as any;
    if (body && typeof body.code === 'number' && body.code !== 200) {
      const err: any = new Error(body.message || '请求失败');
      err.response = { data: body, status: response.status };
      err.code = body.code;
      return Promise.reject(err);
    }
    return body;
  },
  (error) => {
    if (error.response?.status === 401) {
      clearToken();
      window.dispatchEvent(new CustomEvent('auth-required'));
    }
    console.error('API Error:', error.response?.data?.message || error.message, error.response?.data || error);
    return Promise.reject(error);
  }
);

/** 从接口错误中取出可展示给用户的消息 */
export function getApiErrorMessage(error: any, fallback: string = '请求失败'): string {
  const msg = error?.response?.data?.message ?? error?.message;
  return (typeof msg === 'string' ? msg : fallback) || fallback;
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

// 平台相关API：附件/图片完整 URL 需指向后端，与 apiClient baseURL 同源
const API_BASE_ORIGIN = new URL(API_BASE_URL).origin;

export const platformApi = {
  getAll: () => apiClient.get<ApiResponse<any>>('/platforms'),
  getById: (id: string) => apiClient.get<ApiResponse<any>>(`/platforms/${id}`),
  create: (data: any) => apiClient.post<ApiResponse<any>>('/platforms', data),
  update: (id: string, data: any) => apiClient.put<ApiResponse<any>>(`/platforms/${id}`, data),
  delete: (id: string) => apiClient.delete<ApiResponse<void>>(`/platforms/${id}`),
  testConnection: (id: string) => apiClient.post<ApiResponse<boolean>>(`/platforms/${id}/test`),
  /** 上传平台头像，返回 { url: '/api/v1/uploads/platforms/xxx.png' } */
  uploadAvatar: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.post<ApiResponse<{ url: string }>>('/platforms/upload-avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

/** 平台头像完整 URL：相对路径则拼上后端 origin */
export function getPlatformAvatarSrc(avatarUrl: string | undefined): string | undefined {
  if (!avatarUrl) return undefined;
  if (avatarUrl.startsWith('http://') || avatarUrl.startsWith('https://')) return avatarUrl;
  return avatarUrl.startsWith('/') ? `${API_BASE_ORIGIN}${avatarUrl}` : avatarUrl;
}

/** 用户头像完整 URL（追踪用户头像，同平台头像逻辑） */
export function getAvatarSrc(avatarUrl: string | undefined): string | undefined {
  return getPlatformAvatarSrc(avatarUrl);
}

// 用户相关API
export const userApi = {
  getAll: (params?: { page?: number; size?: number; sortBy?: string; sortDir?: string }) =>
    apiClient.get<ApiResponse<any>>('/users', { params }),
  getById: (id: string) => apiClient.get<ApiResponse<any>>(`/users/${id}`),
  create: (data: any) => apiClient.post<ApiResponse<any>>('/users', data),
  update: (id: string, data: any) => apiClient.put<ApiResponse<any>>(`/users/${id}`, data),
  delete: (id: string) => apiClient.delete<ApiResponse<void>>(`/users/${id}`),
  toggleStatus: (id: string, isActive: boolean) =>
    apiClient.put<ApiResponse<any>>(`/users/${id}/toggle`, null, { params: { isActive } }),
  getStats: (id: string) => apiClient.get<ApiResponse<any>>(`/users/${id}/stats`),
  /** 上传用户头像，返回 { url: '/api/v1/uploads/users/xxx.png' } */
  uploadAvatar: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.post<ApiResponse<{ url: string }>>('/users/upload-avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  /** 从平台拉取用户头像与简介并更新（仅 TimeStore 等支持 profile 的平台有效） */
  refreshProfile: (id: string) => apiClient.post<ApiResponse<any>>(`/users/${id}/refresh-profile`),
  /** options.fetchMode: 'fast' 快速拉取（单页少量），'normal' 完整拉取 */
  fetchContent: (id: string, options?: { startTime?: string; endTime?: string; fetchMode?: 'fast' | 'normal' }) =>
    apiClient.post<ApiResponse<any>>(`/users/${id}/fetch`, options ?? {}),
  getFetchHistory: (id: string, params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<any>>(`/users/${id}/fetch-history`, { params }),
  /** 批量获取各用户的文章总数（用于列表展示）。需用 ids=uuid1&ids=uuid2 格式，Spring 才能绑定 List */
  getContentCounts: (ids: string[]) => {
    if (!ids.length) return Promise.resolve({ data: {} as Record<string, number> });
    const params = new URLSearchParams();
    ids.forEach((id) => params.append('ids', id));
    return apiClient.get<ApiResponse<Record<string, number>>>('/users/content-counts', { params });
  },
};

// 内容相关API
export const contentApi = {
  getAll: (params?: {
    page?: number;
    size?: number;
    platformId?: string;
    userId?: string;
    keyword?: string;
    startTime?: string;
    endTime?: string;
  }) => apiClient.get<ApiResponse<any>>('/contents', { params }),
  /** 按平台→用户→月聚合数量，用于内容管理树形展示（仅数量） */
  getGroupedCounts: () => apiClient.get<ApiResponse<{ total: number; platforms: any[] }>>('/contents/grouped-counts'),
  getById: (id: string) => apiClient.get<ApiResponse<any>>(`/contents/${id}`),
  /** 获取上一篇和下一篇内容 */
  getAdjacent: (id: string, sameUserOnly?: boolean) =>
    apiClient.get<ApiResponse<{ previous: any | null; next: any | null }>>(`/contents/${id}/adjacent`, {
      params: { sameUserOnly: sameUserOnly ?? false },
    }),
  update: (id: string, data: any) => apiClient.put<ApiResponse<any>>(`/contents/${id}`, data),
  delete: (id: string) => apiClient.delete<ApiResponse<void>>(`/contents/${id}`),
  /** 按作者删除：删除指定作者（追踪用户）下的全部内容，返回删除条数 */
  deleteContentsByAuthor: (userId: string) => apiClient.delete<ApiResponse<number>>(`/contents/by-author/${userId}`),
  getStats: (userId?: string) => apiClient.get<ApiResponse<any>>('/contents/stats', { params: userId ? { userId } : {} }),
  search: (query: string, params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<any>>('/contents/search', { params: { query, ...params } }),
  searchByRegex: (pattern: string, params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<any>>('/contents/search/regex', { params: { pattern, ...params } }),
  advancedSearch: (query: string, contentType?: string, params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<any>>('/contents/search/advanced', { params: { query, contentType, ...params } }),
  getSearchHistory: (params?: { limit?: number; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<any>>('/contents/search/history', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? params?.limit ?? 20,
      },
    }),
  getPopularSearchQueries: (params?: { limit?: number }) =>
    apiClient.get<ApiResponse<any>>('/contents/search/popular', { params }),
  getRecentSearchQueries: (params?: { limit?: number }) =>
    apiClient.get<ApiResponse<any>>('/contents/search/recent', { params }),
  /** 修复 TimeStore 已保存文章中的外部图片：下载到本地并更新地址 */
  fixTimestoreImages: () => apiClient.post<ApiResponse<{ fixedCount: number }>>('/contents/fix-timestore-images'),
  /** 修复 TimeStore 加密文章：重新拉取包含"……"的文章并更新 */
  fixTimestoreEncrypted: () => apiClient.post<ApiResponse<{ fixedCount: number }>>('/contents/fix-timestore-encrypted'),
};

// 任务相关API
export const taskApi = {
  getScheduleStatus: () => apiClient.get<ApiResponse<any>>('/tasks/schedule/status'),
  /** 定时任务状态详情（含执行周期 interval） */
  getScheduleStatusDetail: () =>
    apiClient.get<ApiResponse<{ isEnabled?: boolean; interval?: string; [k: string]: any }>>('/tasks/schedule/status/detail'),
  enableSchedule: () => apiClient.put<ApiResponse<void>>('/tasks/schedule/enable'),
  disableSchedule: () => apiClient.put<ApiResponse<void>>('/tasks/schedule/disable'),
  enableUserSchedule: (userId: string) => 
    apiClient.put<ApiResponse<void>>(`/tasks/schedule/users/${userId}/enable`),
  disableUserSchedule: (userId: string) => 
    apiClient.put<ApiResponse<void>>(`/tasks/schedule/users/${userId}/disable`),
  /** 各用户定时任务当前状态：userId -> isEnabled */
  getUsersScheduleStatus: () =>
    apiClient.get<ApiResponse<Record<string, boolean>>>('/tasks/schedule/users/status'),
  getFetchQueue: () => apiClient.get<ApiResponse<any>>('/tasks/fetch/queue'),
  getFetchTask: (taskId: string) => apiClient.get<ApiResponse<any>>(`/tasks/fetch/${taskId}`),
  /** 刷新任务记录。taskType=MANUAL 仅手动，SCHEDULED 仅定时，不传则全部 */
  getFetchHistory: (params?: { page?: number; size?: number; taskType?: 'MANUAL' | 'SCHEDULED' }) =>
    apiClient.get<ApiResponse<any>>('/tasks/fetch/history', { params }),
  getScheduleHistory: (params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<any>>('/tasks/schedule/history', { params }),
  /** 清除单条任务记录（POST 固定路径，避免 Spring Boot 3.2 下 DELETE 未匹配导致 NoResourceFoundException） */
  deleteFetchTaskRecord: (taskId: string) =>
    apiClient.post<ApiResponse<void>>('/tasks/fetch/delete-record', { taskId }),
  /** 批量清除任务记录 */
  deleteFetchTaskRecords: (taskIds: string[]) =>
    apiClient.post<ApiResponse<number>>('/tasks/fetch/delete-records', taskIds),
  /** 按类型一键全清：taskType 为 MANUAL 或 SCHEDULED，返回删除条数 */
  deleteAllFetchTaskRecordsByType: (taskType: 'MANUAL' | 'SCHEDULED') =>
    apiClient.post<ApiResponse<number>>('/tasks/fetch/delete-all-by-type', { taskType }),
};

// 标签相关API
export const tagApi = {
  getAll: () => apiClient.get<ApiResponse<any>>('/tags'),
  getPage: (params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<any>>('/tags/page', { params }),
  getById: (id: string) => apiClient.get<ApiResponse<any>>(`/tags/${id}`),
  create: (data: any) => apiClient.post<ApiResponse<any>>('/tags', data),
  update: (id: string, data: any) => apiClient.put<ApiResponse<any>>(`/tags/${id}`, data),
  delete: (id: string) => apiClient.delete<ApiResponse<void>>(`/tags/${id}`),
  getPopular: (limit?: number) =>
    apiClient.get<ApiResponse<any>>('/tags/popular', { params: { limit } }),
};

// 认证相关API
export const authApi = {
  login: (data: { username: string; password: string }) =>
    apiClient.post<ApiResponse<any>>('/auth/login', data),
  register: (data: { username: string; password: string; email?: string }) =>
    apiClient.post<ApiResponse<any>>('/auth/register', data),
};

// 用户分组API
export const groupApi = {
  getAll: () => apiClient.get<ApiResponse<any>>('/groups'),
  getById: (id: string) => apiClient.get<ApiResponse<any>>(`/groups/${id}`),
  create: (data: { name: string; description?: string; sortOrder?: number }) =>
    apiClient.post<ApiResponse<any>>('/groups', data),
  update: (id: string, data: { name?: string; description?: string; sortOrder?: number }) =>
    apiClient.put<ApiResponse<any>>(`/groups/${id}`, data),
  delete: (id: string) => apiClient.delete<ApiResponse<void>>(`/groups/${id}`),
};

// 通知规则API
export const notificationRuleApi = {
  getPage: (params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<any>>('/notification-rules', { params }),
  getById: (id: string) => apiClient.get<ApiResponse<any>>(`/notification-rules/${id}`),
  create: (data: { name: string; ruleType?: string; config?: Record<string, unknown>; isEnabled?: boolean }) =>
    apiClient.post<ApiResponse<any>>('/notification-rules', data),
  update: (id: string, data: { name?: string; ruleType?: string; config?: Record<string, unknown>; isEnabled?: boolean }) =>
    apiClient.put<ApiResponse<any>>(`/notification-rules/${id}`, data),
  delete: (id: string) => apiClient.delete<ApiResponse<void>>(`/notification-rules/${id}`),
  /** 测试下发：testMode=default 默认语句，random_content 随机选监听用户一篇文章 */
  test: (id: string, body?: { testMode?: string }) =>
    apiClient.post<ApiResponse<{ success: boolean; message: string }>>(`/notification-rules/${id}/test`, body ?? {}),
  /** 按规则类型 + 配置直接测试下发；testMode=random_content 时需传 userIds */
  testWithConfig: (body: { ruleType: string; config: Record<string, unknown>; testMode?: string; userIds?: string[] }) =>
    apiClient.post<ApiResponse<{ success: boolean; message: string }>>('/notifications/test-with-config', body),
};

/** 通知通道配置（存储并可选共享，供规则表单复用） */
export const notificationChannelConfigApi = {
  list: (params?: { channelType?: string }) =>
    apiClient.get<ApiResponse<any[]>>('/notification-channel-configs', { params }),
  getById: (id: string) => apiClient.get<ApiResponse<any>>(`/notification-channel-configs/${id}`),
  create: (data: { name: string; channelType: string; config: Record<string, unknown>; isShared?: boolean }) =>
    apiClient.post<ApiResponse<any>>('/notification-channel-configs', data),
  update: (id: string, data: { name?: string; channelType?: string; config?: Record<string, unknown>; isShared?: boolean }) =>
    apiClient.put<ApiResponse<any>>(`/notification-channel-configs/${id}`, data),
  delete: (id: string) => apiClient.delete<ApiResponse<void>>(`/notification-channel-configs/${id}`),
};

// 导出API（返回下载链接）
export const exportApi = {
  getJsonUrl: (params?: { userId?: string }) =>
    `${API_BASE_URL}/export/json` + (params?.userId ? `?userId=${params.userId}` : ''),
  getMarkdownUrl: (params?: { userId?: string }) =>
    `${API_BASE_URL}/export/markdown` + (params?.userId ? `?userId=${params.userId}` : ''),
  getCsvUrl: (params?: { userId?: string }) =>
    `${API_BASE_URL}/export/csv` + (params?.userId ? `?userId=${params.userId}` : ''),
  getHtmlUrl: (params?: { userId?: string }) =>
    `${API_BASE_URL}/export/html` + (params?.userId ? `?userId=${params.userId}` : ''),
  /** PDF/Word 同步下载（无进度），需选中用户 */
  getPdfUrl: (params: { userId: string; sortOrder?: string }) =>
    `${API_BASE_URL}/export/pdf?userId=${params.userId}&sortOrder=${params.sortOrder ?? 'DESC'}`,
  getWordUrl: (params: { userId: string; sortOrder?: string }) =>
    `${API_BASE_URL}/export/word?userId=${params.userId}&sortOrder=${params.sortOrder ?? 'DESC'}`,
  /** 创建异步导出任务（支持进度与日志，PDF/Word 需 userId、sortOrder） */
  createAsync: (params: {
    format: 'JSON' | 'MARKDOWN' | 'CSV' | 'HTML' | 'PDF' | 'WORD';
    userId?: string;
    startTime?: string;
    endTime?: string;
    sortOrder?: string;
  }) =>
    apiClient.post<ApiResponse<any>>('/export/async', null, {
      params: {
        format: params.format,
        userId: params.userId,
        startTime: params.startTime,
        endTime: params.endTime,
        sortOrder: params.sortOrder ?? 'DESC',
      },
      timeout: 60000, // 创建任务时使用60秒超时，确保有足够时间完成数据库操作
    }),
  /** 获取导出任务详情（含 progress、logMessages） */
  getTask: (taskId: string) => apiClient.get<ApiResponse<any>>(`/export/tasks/${taskId}`),
  /** 获取导出任务列表（分页） */
  getTasks: (params?: { userId?: string; page?: number; size?: number }) => {
    const queryParams: any = {};
    if (params?.userId) queryParams.userId = params.userId;
    if (params?.page !== undefined) queryParams.page = params.page;
    if (params?.size !== undefined) queryParams.size = params.size;
    return apiClient.get<ApiResponse<any>>('/export/tasks', { params: queryParams });
  },
  /** 下载导出文件 URL（需带 token） */
  getDownloadUrl: (taskId: string) =>
    `${API_BASE_URL}/export/tasks/${taskId}/download`,
};

// 统计API
export const statsApi = {
  getContentStats: (userId?: string) =>
    apiClient.get<ApiResponse<any>>('/stats/content', { params: userId ? { userId } : {} }),
  getPlatformDistribution: () =>
    apiClient.get<ApiResponse<any>>('/stats/platform-distribution'),
  getUserStats: () =>
    apiClient.get<ApiResponse<any>>('/stats/users'),
  getTagStatistics: () =>
    apiClient.get<ApiResponse<any>>('/stats/tags'),
  getContentTimeDistribution: (days?: number) =>
    apiClient.get<ApiResponse<any>>('/stats/content-time-distribution', { params: { days } }),
  getContentTypeDistribution: () =>
    apiClient.get<ApiResponse<any>>('/stats/content-type-distribution'),
  getActiveUsersRanking: (limit?: number) =>
    apiClient.get<ApiResponse<any>>('/stats/active-users-ranking', { params: { limit } }),
  getContentGrowthTrend: (days?: number) =>
    apiClient.get<ApiResponse<any>>('/stats/content-growth-trend', { params: { days } }),
};

// AI功能API
export const aiApi = {
  generateSummary: (contentId: string) =>
    apiClient.get<ApiResponse<string>>(`/ai/content/${contentId}/summary`),
  analyzeSentiment: (contentId: string) =>
    apiClient.get<ApiResponse<any>>(`/ai/content/${contentId}/sentiment`),
  extractKeyInfo: (contentId: string) =>
    apiClient.get<ApiResponse<any>>(`/ai/content/${contentId}/key-info`),
  identifyHotTopics: (limit?: number, days?: number) =>
    apiClient.get<ApiResponse<any>>('/ai/topics/hot', { params: { limit, days } }),
  trackTopicEvolution: (keyword: string, days?: number) =>
    apiClient.get<ApiResponse<any>>('/ai/topics/evolution', { params: { keyword, days } }),
  recommendSimilarContent: (contentId: string, limit?: number) =>
    apiClient.get<ApiResponse<any>>(`/ai/recommendations/similar/${contentId}`, { params: { limit } }),
  recommendRelatedAuthors: (userId: string, limit?: number) =>
    apiClient.get<ApiResponse<any>>(`/ai/recommendations/authors/${userId}`, { params: { limit } }),
  personalizedRecommendation: (userId: string, limit?: number) =>
    apiClient.get<ApiResponse<any>>(`/ai/recommendations/personalized/${userId}`, { params: { limit } }),
};

// 备份API
export const backupApi = {
  performDatabaseBackup: () =>
    apiClient.post<ApiResponse<string>>('/backup/database'),
  performIncrementalBackup: (since?: string) =>
    apiClient.post<ApiResponse<string>>('/backup/incremental', null, { params: since ? { since } : {} }),
  listBackups: () =>
    apiClient.get<ApiResponse<string[]>>('/backup/list'),
};

// 安全审计API
export const securityAuditApi = {
  getSecurityEvents: (limit?: number) =>
    apiClient.get<ApiResponse<any>>('/security/audit/events', { params: { limit } }),
  getSecurityStatistics: () =>
    apiClient.get<ApiResponse<any>>('/security/audit/statistics'),
};
