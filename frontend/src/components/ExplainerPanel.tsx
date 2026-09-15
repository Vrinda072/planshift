const STEPS = [
  "generate or import data",
  "pick a query",
  "measure baseline",
  "change the database",
  "measure candidate",
  "compare",
];

export function ExplainerPanel() {
  return (
    <div className="pipeline">
      {STEPS.map((step, i) => (
        <span key={step}>
          <span className="pipeline-step pipeline-step-in" style={{ animationDelay: `${i * 90}ms` }}>
            {step}
          </span>
          {i < STEPS.length - 1 && (
            <span className="pipeline-arrow pipeline-step-in" style={{ animationDelay: `${i * 90 + 45}ms` }}>
              &rarr;
            </span>
          )}
        </span>
      ))}
    </div>
  );
}
