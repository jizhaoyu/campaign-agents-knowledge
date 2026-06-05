import { Notice } from '../hooks/useNotice';

export function NoticeBar({ notice }: { notice: Notice | null }) {
  return notice ? <div className={`notice ${notice.tone}`}>{notice.text}</div> : null;
}
