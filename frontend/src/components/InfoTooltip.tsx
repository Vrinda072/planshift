export function InfoTooltip({ text }: { text: string }) {
  return (
    <span className="info-tooltip" tabIndex={0}>
      i
      <span className="tooltip-bubble">{text}</span>
    </span>
  );
}
