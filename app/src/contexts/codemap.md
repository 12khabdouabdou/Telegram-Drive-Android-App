# Codemap: `app/src/contexts/`

**Responsibility**: 
Handles highly specific or isolated contextual states, specifically related to drag-and-drop actions.

**Design Patterns**:
- **Context API**: Follows the React Context provider/consumer pattern.

**Data & Control Flow**:
- Tracks boolean or stateful statuses of current drag-and-drop events over the application window.
- Injects a context object to components that need to react to file drops.

**Integration Points**:
- Used by main container components (like `App.tsx` or layout components) to style drag overlays.
- Complements `app/src/hooks/useFileDrop.ts`.
