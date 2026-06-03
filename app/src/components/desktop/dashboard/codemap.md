# Codemap: Desktop Dashboard

**Responsibility**: Contains UI widgets and modals specifically for the desktop dashboard view (e.g., file explorer, media player, sidebar, queues).

**Design Patterns**: Presentational Components, Container Components (with specific context/state passed as props). React Hooks for local states (e.g., modals).

**Data & Control Flow**: Data enters via props from the main `DesktopDashboard` component. Callbacks are passed down to handle actions (upload, download, rename, move, delete).

**Integration Points**: Imported and consumed mainly by `DesktopDashboard.tsx`. Depends on shared types, utils, and icons.
