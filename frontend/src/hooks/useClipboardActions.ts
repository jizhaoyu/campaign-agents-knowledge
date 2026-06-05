import { Citation } from '../api';

type Notice = {
  tone: 'ok' | 'warn' | 'error';
  text: string;
};

export function useClipboardActions({ setNotice }: { setNotice: (notice: Notice) => void }) {
  async function copyText(text: string, successMessage: string, fallbackMessage: string) {
    try {
      if (!navigator.clipboard) {
        throw new Error('Clipboard API is unavailable');
      }
      await navigator.clipboard.writeText(text);
      setNotice({ tone: 'ok', text: successMessage });
    } catch {
      setNotice({ tone: 'warn', text: fallbackMessage });
    }
  }

  async function copyTraceId(traceId: string) {
    await copyText(traceId, `traceId 已复制：${traceId}`, `请手动复制 traceId：${traceId}`);
  }

  async function copyCitation(citation: Citation) {
    const text = `引用：${citation.documentName}#chunk-${citation.chunkId}\n${citation.snippet}`;
    await copyText(text, `引用已复制：${citation.documentName}`, `请手动复制引用：${citation.documentName}`);
  }

  async function copyTicketId(ticketId: number) {
    await copyText(String(ticketId), `工单号已复制：#${ticketId}`, `请手动复制工单号：#${ticketId}`);
  }

  return {
    copyTraceId,
    copyCitation,
    copyTicketId
  };
}
