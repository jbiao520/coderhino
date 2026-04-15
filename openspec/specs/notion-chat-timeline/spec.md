# notion-chat-timeline

### Requirement: Flat timeline message layout
Chat messages SHALL render as a flat timeline with alignment indicating speaker. User messages SHALL render on the right side of the chat column, assistant messages SHALL render on the left side, and messages SHALL not use visible role-name labels such as `You` or `Claude`. The visual hierarchy SHALL come from typography, spacing, placement, and hover metadata rather than colored containers or speaker-name text.

#### Scenario: Messages render without bubble backgrounds
- **WHEN** a session has messages from user and assistant
- **THEN** each message renders as a flat block with no background color distinct from the page background and no border

#### Scenario: Messages are separated by spacing
- **WHEN** multiple messages exist
- **THEN** consecutive messages are separated by at least 20px vertical gap

### Requirement: Hovered messages expose quick actions and timestamp metadata
Persisted chat messages SHALL reveal a lightweight action row when hovered or keyboard-focused. That action row SHALL include a copy action for every persisted message, SHALL include a readable timestamp for that message, and SHALL include a rollback action for persisted user messages only.

#### Scenario: Hovered assistant message shows copy and timestamp
- **WHEN** the user hovers or focuses a persisted assistant message in the chat timeline
- **THEN** the message reveals a copy action and a readable timestamp and does not reveal a rollback action

#### Scenario: Hovered user message shows rollback, copy, and timestamp
- **WHEN** the user hovers or focuses a persisted user message in the chat timeline
- **THEN** the message reveals rollback and copy actions plus a readable timestamp for that message

#### Scenario: Idle message keeps actions hidden
- **WHEN** a persisted message is neither hovered nor keyboard-focused
- **THEN** its quick actions are not visibly rendered in the timeline

### Requirement: Rollback action reopens the selected user prompt for editing
The rollback action for a persisted user message SHALL immediately rewind the conversation to the state before that message and SHALL repopulate the chat composer with the clicked message content so the user can revise it before resubmitting.

#### Scenario: Rollback restores the composer from a prior user message
- **WHEN** the user activates rollback on a persisted user message
- **THEN** the session timeline rewinds to the state before that message and the composer input is populated with that message content

#### Scenario: Rollback removes later assistant responses from the visible history
- **WHEN** the user activates rollback on a persisted user message that already has later replies in the timeline
- **THEN** messages at and after that rewound point no longer appear in the visible chat history after the refresh completes

### Requirement: Sans-serif body text for messages
Message text content SHALL render in the system sans-serif font (from `--font-sans`), NOT in monospace. Only code blocks within messages SHALL use monospace.

#### Scenario: Regular message text uses sans-serif
- **WHEN** a message contains prose text without code blocks
- **THEN** the text renders in sans-serif font

#### Scenario: Monospace is NOT used for message prose
- **WHEN** a message renders in the chat timeline
- **THEN** the message body font-family resolves to `var(--font-sans)`, not a monospace stack

### Requirement: Code block detection in messages
The system SHALL detect triple-backtick fenced code blocks (`\`\`\``) in message content. Text between code-block delimiters SHALL render in monospace font with a surface-color background. Text outside code blocks SHALL render in sans-serif with no background. Inline single-backtick code SHALL render in sans-serif (no special formatting).

#### Scenario: Code block renders in monospace with background
- **WHEN** a message contains `\`\`\`java\nSystem.out.println("hi");\n\`\`\``
- **THEN** the fenced content renders in monospace font with `var(--surface)` background

#### Scenario: Prose before and after code block renders in sans-serif
- **WHEN** a message contains "Here is the code:\n\`\`\`java\ncode\n\`\`\`\nThat's it."
- **THEN** "Here is the code:" and "That's it." render in sans-serif, and the fenced block renders in monospace with background

#### Scenario: Message with no code blocks
- **WHEN** a message contains plain text with no triple-backtick fences
- **THEN** the entire message renders in sans-serif with no code-block backgrounds

#### Scenario: Inline backticks are not treated as code blocks
- **WHEN** a message contains "use the `main` function"
- **THEN** the text renders in sans-serif — inline single backticks do not trigger code-block formatting

### Requirement: Live streaming text renders in sans-serif
The live or streaming assistant text shown while the AI is generating SHALL render in sans-serif font and SHALL use the same left-aligned, label-free presentation as completed assistant messages.

#### Scenario: Streaming text uses sans-serif
- **WHEN** the assistant is actively generating a response and live text is displayed
- **THEN** the live text renders on the left side of the message column, uses sans-serif font, and does not display the text `Claude`
