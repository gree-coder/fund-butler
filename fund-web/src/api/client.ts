import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { message } from 'antd';
import { apiLogger } from '../utils/logger';

const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

// 请求拦截器 - 记录请求日志
client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const startTime = Date.now();
    (config as unknown as { _startTime?: number })._startTime = startTime;
    
    apiLogger.apiRequest(
      config.method?.toUpperCase() || 'GET',
      config.url || '',
      config.params,
    );
    
    return config;
  },
  (error: AxiosError) => {
    apiLogger.error('请求配置错误', error);
    return Promise.reject(error);
  },
);

// 响应拦截器 - 记录响应日志
client.interceptors.response.use(
  (response) => {
    const config = response.config as InternalAxiosRequestConfig & { _startTime?: number };
    const duration = Date.now() - (config._startTime || 0);
    const res = response.data;
    
    apiLogger.apiResponse(
      config.method?.toUpperCase() || 'GET',
      config.url || '',
      response.status,
      duration,
      res,
    );
    
    if (res.code !== 200) {
      message.error(res.message || '请求失败');
      return Promise.reject(new Error(res.message));
    }
    return res.data;
  },
  (error: AxiosError) => {
    const config = error.config as InternalAxiosRequestConfig & { _startTime?: number };

    apiLogger.apiError(
      config?.method?.toUpperCase() || 'GET',
      config?.url || '',
      error,
    );

    if (error.response) {
      message.error(`请求失败: ${error.response.status}`);
    } else if (error.code === 'ECONNABORTED') {
      message.error('请求超时，请稍后重试');
    } else {
      message.error('网络错误，请检查网络连接');
    }
    return Promise.reject(error);
  },
);

export default client;
