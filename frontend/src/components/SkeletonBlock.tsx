export function SkeletonBlock({
  label,
  lines = 2,
  variant = 'card'
}: {
  label: string;
  lines?: number;
  variant?: 'card' | 'panel';
}) {
  return (
    <div className={`skeleton-block ${variant}`} aria-label={label} aria-busy="true">
      {Array.from({ length: lines }, (_, index) => (
        <span key={index} />
      ))}
    </div>
  );
}
