import { Component, type ReactNode } from "react";

export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  override state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  override componentDidCatch(e: unknown) { console.error("ui-error", (e as Error)?.message); }
  override render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="state-card err" style={{ height: "100%" }}>
        <h2>Ultra TV</h2>
        <p>Une erreur inattendue est survenue. / An unexpected error occurred.</p>
        <button className="btn primary" onClick={() => location.reload()}>OK</button>
      </div>
    );
  }
}
