import { isApiError } from '../api';
import { useState } from 'react';

export type Notice = {
  tone: 'ok' | 'warn' | 'error';
  text: string;
};

export function useNotice() {
  const [notice, setNotice] = useState<Notice | null>(null);

  function showError(error: unknown) {
    if (isApiError(error)) {
      const prefix =
        error.kind === 'auth'
          ? '登录已失效'
          : error.kind === 'validation'
            ? '提交内容无效'
            : error.kind === 'server'
              ? '后端异常'
              : error.kind === 'network'
                ? '网络请求失败'
                : '业务处理失败';
      const traceId = error.traceId ? `，traceId: ${error.traceId}` : '';
      setNotice({ tone: 'error', text: `${prefix}：${error.message}${traceId}` });
      return;
    }
    setNotice({ tone: 'error', text: error instanceof Error ? error.message : '未知错误' });
  }

  return { notice, setNotice, showError };
}
