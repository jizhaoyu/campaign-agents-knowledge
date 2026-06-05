export function ListEmpty({
  show,
  text,
  title,
  variant = 'plain'
}: {
  show: boolean;
  text: string;
  title?: string;
  variant?: 'plain' | 'box';
}) {
  if (!show) {
    return null;
  }

  if (variant === 'box') {
    return (
      <div className="result-box">
        {title && <strong>{title}</strong>}
        <span>{text}</span>
      </div>
    );
  }

  return <p className="muted compact">{text}</p>;
}
