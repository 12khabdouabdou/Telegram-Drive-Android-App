# Codemap: Shared Components

**Responsibility**: Reusable components shared across both desktop and mobile platforms (e.g., Auth Wizards, Error Boundaries, Theme Toggles).

**Design Patterns**: Platform-Agnostic UI Components, Utility Wrappers (like `ErrorBoundary`).

**Data & Control Flow**: Receives standard props. Often manages minimal internal state or relies purely on passed props and global contexts.

**Integration Points**: Exported for use in both `desktop/` and `mobile/` components.
