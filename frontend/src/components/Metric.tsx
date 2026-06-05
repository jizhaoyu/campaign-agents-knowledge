export function Metric({ title, value, caption }: { title: string; value: string | number; caption: string }) {
  return (
    <article className="metric-card">
      <span>{title}</span>
      <strong>{value}</strong>
      <small>{caption}</small>
    </article>
  );
}
