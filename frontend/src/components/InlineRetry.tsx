export function InlineRetry({
  title,
  message,
  actionLabel,
  onRetry,
  loading
}: {
  title: string;
  message: string;
  actionLabel: string;
  onRetry: () => void;
  loading?: boolean;
}) {
  return (
    <div className="result-box inline-retry" role="alert">
      <strong>{title}</strong>
      <span>{message}</span>
      <button type="button" onClick={onRetry} disabled={loading}>
        {loading ? '重试中...' : actionLabel}
      </button>
    </div>
  );
}
