export type PaginationState = {
  page: number;
  totalPages: number;
  totalItems: number;
  hasPrevious: boolean;
  hasNext: boolean;
};

export function PaginationBar({
  label,
  page,
  visibleCount,
  itemUnit = '条',
  onChangePage
}: {
  label: string;
  page: PaginationState | null;
  visibleCount: number;
  itemUnit?: string;
  onChangePage: (page: number) => void;
}) {
  const text = page
    ? `第 ${page.totalPages === 0 ? 0 : page.page + 1} / ${page.totalPages} 页，共 ${page.totalItems} ${itemUnit}`
    : `本页 ${visibleCount} ${itemUnit}`;

  return (
    <div className="pagination-bar" aria-label={label}>
      <span>{text}</span>
      <div className="button-row">
        <button type="button" onClick={() => page && onChangePage(page.page - 1)} disabled={!page?.hasPrevious}>
          上一页
        </button>
        <button type="button" onClick={() => page && onChangePage(page.page + 1)} disabled={!page?.hasNext}>
          下一页
        </button>
      </div>
    </div>
  );
}
