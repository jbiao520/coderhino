## ADDED Requirements

### Requirement: ChatTTS backend SHALL use a shared model directory
The system SHALL resolve ChatTTS local model assets from a user-scoped shared directory under `~/.coderhino-java` instead of relying on the current working directory of the `/read` command invocation.

#### Scenario: Running `/read` from a workspace without local ChatTTS assets
- **WHEN** a user runs `/read` with backend `chat-tts` from a project directory that does not contain a local ChatTTS `asset/` folder
- **THEN** the system SHALL still load ChatTTS from the shared model directory
- **AND** audio generation SHALL NOT require a per-project model download

#### Scenario: Running `/read` from different workspaces
- **WHEN** a user runs `/read` with backend `chat-tts` from multiple different working directories on the same machine
- **THEN** all invocations SHALL resolve the same shared ChatTTS model directory

### Requirement: Shared ChatTTS model storage SHALL bootstrap from an existing local model copy
When the shared ChatTTS model directory does not yet exist, the system SHALL populate it from an existing complete local ChatTTS asset copy if one is available.

#### Scenario: Shared model is absent and a complete local source exists
- **WHEN** `/read` with backend `chat-tts` is invoked and the shared ChatTTS asset directory is missing
- **AND** a complete existing local ChatTTS asset copy is available from a supported bootstrap source
- **THEN** the system SHALL copy that asset set into the shared ChatTTS model directory before loading the model

#### Scenario: Shared model already exists
- **WHEN** `/read` with backend `chat-tts` is invoked and the shared ChatTTS asset directory is already complete
- **THEN** the system SHALL use the existing shared model directory without copying from another workspace

### Requirement: Missing shared ChatTTS assets SHALL fail with a clear error
The system MUST return an actionable error when the shared ChatTTS model directory cannot be prepared or is incomplete, and it MUST NOT silently fall back to downloading models into the active workspace.

#### Scenario: No bootstrap source is available
- **WHEN** `/read` with backend `chat-tts` is invoked and neither a complete shared ChatTTS asset directory nor a supported bootstrap source is available
- **THEN** the command SHALL fail with an error that identifies the expected shared model location

#### Scenario: Shared asset copy is incomplete
- **WHEN** the shared ChatTTS asset directory exists but required model files are missing
- **THEN** the command SHALL fail with a clear error indicating that the shared ChatTTS model is incomplete
