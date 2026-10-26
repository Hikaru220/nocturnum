import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./index.css";
import { installMock } from "./lib/mock";

if (import.meta.env.DEV) installMock();

class Boundary extends React.Component<{ children: React.ReactNode }, { err: Error | null }> {
  state = { err: null as Error | null };
  static getDerivedStateFromError(err: Error) {
    return { err };
  }
  render() {
    if (this.state.err) {
      return (
        <div style={{ padding: 32, color: "var(--text)" }}>
          <h2>Something went wrong / Что-то пошло не так</h2>
          <pre className="selectable" style={{ whiteSpace: "pre-wrap", color: "var(--danger)" }}>{String(this.state.err.stack ?? this.state.err)}</pre>
          <button onClick={() => location.reload()}>Reload</button>
        </div>
      );
    }
    return this.props.children;
  }
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Boundary>
      <App />
    </Boundary>
  </React.StrictMode>,
);
