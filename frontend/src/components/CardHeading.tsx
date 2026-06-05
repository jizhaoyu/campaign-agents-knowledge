import { ReactNode } from 'react';

export function CardHeading({
  marker,
  title,
  action
}: {
  marker: string;
  title: string;
  action?: ReactNode;
}) {
  if (action) {
    return (
      <div className="card-heading split-heading">
        <div>
          <span>{marker}</span>
          <h2>{title}</h2>
        </div>
        {action}
      </div>
    );
  }

  return (
    <div className="card-heading">
      <span>{marker}</span>
      <h2>{title}</h2>
    </div>
  );
}
