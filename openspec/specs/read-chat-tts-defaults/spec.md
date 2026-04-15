## ADDED Requirements

### Requirement: Read command shall default to ChatTTS generation
The system SHALL use the `chat-tts` backend for `/read` generation by default during normal execution when no explicit supported backend has been resolved from runtime state.

#### Scenario: No saved backend preference exists
- **WHEN** the user executes `/read` and no supported backend preference has been saved
- **THEN** the system SHALL generate audio through `chat-tts`

#### Scenario: Unsupported saved backend is encountered
- **WHEN** the system resolves a saved `/read` backend value that is missing or unsupported
- **THEN** the system SHALL fall back to `chat-tts` instead of another backend

### Requirement: Read command shall use a fixed built-in ChatTTS speaker embedding
The system SHALL include one built-in ChatTTS speaker parameter vector in application code and SHALL use that vector for every `/read` generation request that runs through `chat-tts`.

#### Scenario: ChatTTS inference starts for read command
- **WHEN** the system invokes ChatTTS for a `/read` request
- **THEN** the ChatTTS inference call SHALL include the built-in speaker parameter vector rather than leaving speaker selection implicit

#### Scenario: Multiple read requests use the same speaker profile
- **WHEN** two different `/read` requests are generated with `chat-tts`
- **THEN** both requests SHALL use the same built-in speaker parameter vector unless the code is changed

### Requirement: Read backend status shall reflect ChatTTS-first behavior
The system SHALL present `/read` backend status and usage messaging in a way that does not imply `edge-tts` is the default backend when the application is operating in the ChatTTS-first configuration.

#### Scenario: User checks backend status
- **WHEN** the user runs `/read backend status`
- **THEN** the reported backend SHALL be `chat-tts` when no supported persisted override has been selected

#### Scenario: User requests read usage help
- **WHEN** the system shows `/read` usage guidance
- **THEN** that guidance SHALL remain valid for ChatTTS-first execution behavior
