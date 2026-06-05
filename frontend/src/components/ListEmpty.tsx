export function ListEmpty({ show, text }: { show: boolean; text: string }) {
  return show ? <p className="muted compact">{text}</p> : null;
}
