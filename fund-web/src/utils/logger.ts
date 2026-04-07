/* eslint-disable no-console */
/**
 * 前端日志工具类
 * 支持不同日志级别，可在生产环境关闭调试日志
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LoggerConfig {
  /** 是否启用日志 */
  enabled: boolean;
  /** 最小日志级别 */
  minLevel: LogLevel;
  /** 是否输出到控制台 */
  console: boolean;
}

// 日志级别权重
const LOG_LEVEL_WEIGHT: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
};

// 默认配置
const defaultConfig: LoggerConfig = {
  enabled: import.meta.env.DEV, // 开发环境启用
  minLevel: import.meta.env.PROD ? 'warn' : 'debug', // 生产环境只记录警告和错误
  console: true,
};

class Logger {
  private context: string;
  private config: LoggerConfig;

  constructor(context: string, config: Partial<LoggerConfig> = {}) {
    this.context = context;
    this.config = { ...defaultConfig, ...config };
  }

  private shouldLog(level: LogLevel): boolean {
    if (!this.config.enabled) return false;
    return LOG_LEVEL_WEIGHT[level] >= LOG_LEVEL_WEIGHT[this.config.minLevel];
  }

  private formatMessage(level: LogLevel, message: string, ...args: unknown[]): unknown[] {
    const timestamp = new Date().toISOString();
    const prefix = `[${timestamp}] [${level.toUpperCase()}] [${this.context}]`;
    return [prefix, message, ...args];
  }

  debug(message: string, ...args: unknown[]): void {
    if (this.shouldLog('debug') && this.config.console) {
      console.debug(...this.formatMessage('debug', message, ...args));
    }
  }

  info(message: string, ...args: unknown[]): void {
    if (this.shouldLog('info') && this.config.console) {
      console.info(...this.formatMessage('info', message, ...args));
    }
  }

  warn(message: string, ...args: unknown[]): void {
    if (this.shouldLog('warn') && this.config.console) {
      console.warn(...this.formatMessage('warn', message, ...args));
    }
  }

  error(message: string, error?: Error | unknown, ...args: unknown[]): void {
    if (this.shouldLog('error') && this.config.console) {
      const formatted = this.formatMessage('error', message, ...args);
      if (error instanceof Error) {
        console.error(...formatted, error, error.stack);
      } else {
        console.error(...formatted, error);
      }
    }
  }

  /**
   * 记录 API 请求
   */
  apiRequest(method: string, url: string, params?: unknown): void {
    this.debug(`>>> ${method} ${url}`, params ? { params } : '');
  }

  /**
   * 记录 API 响应
   */
  apiResponse(method: string, url: string, status: number, duration: number, data?: unknown): void {
    const level = status >= 400 ? 'error' : 'debug';
    this[level](`<<< ${method} ${url} [${status}] ${duration}ms`, data);
  }

  /**
   * 记录 API 错误
   */
  apiError(method: string, url: string, error: unknown): void {
    this.error(`<<< ${method} ${url} [ERROR]`, error);
  }

  /**
   * 创建子日志器
   */
  child(subContext: string): Logger {
    return new Logger(`${this.context}:${subContext}`, this.config);
  }
}

/**
 * 创建日志器
 * @param context 日志上下文名称
 */
export function createLogger(context: string): Logger {
  return new Logger(context);
}

// 预创建的日志器
export const appLogger = createLogger('App');
export const apiLogger = createLogger('API');
export const storeLogger = createLogger('Store');

export default Logger;
