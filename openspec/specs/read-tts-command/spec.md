## ADDED Requirements

### Requirement: Read command shall preserve numeric content in ChatTTS mixed Chinese speech
When `/read` uses the `chat-tts` backend, the system SHALL preserve the meaning of Arabic numerals that appear in mixed Chinese text by preparing the text so numeric segments are included in generated speech instead of being skipped.

#### Scenario: Count and duration phrase is preserved
- **WHEN** the user executes `/read 600次模型调用 / 5小时` and `/read` uses `chat-tts`
- **THEN** the text sent to ChatTTS SHALL retain spoken equivalents for both `600` and `5`

#### Scenario: File input with numeric phrase is preserved
- **WHEN** the user executes `/read usage.txt`, the file resolves successfully, and the file content contains mixed Chinese numeric text
- **THEN** the ChatTTS generation input SHALL preserve the numeric content from that file content instead of dropping it

### Requirement: Read command numeric preservation shall not change non-ChatTTS behavior
The system SHALL limit numeric-preservation changes to the ChatTTS text-preparation path and SHALL NOT require changes to `/read` file resolution, playback flow, or non-ChatTTS backend behavior.

#### Scenario: Edge TTS behavior remains unchanged
- **WHEN** the user executes `/read 600次模型调用 / 5小时` with the `edge-tts` backend selected
- **THEN** the system SHALL continue to pass the resolved text through the existing `edge-tts` path without requiring ChatTTS-specific normalization behavior

#### Scenario: Playback flow remains unchanged after normalization
- **WHEN** ChatTTS successfully generates audio for normalized `/read` text
- **THEN** the system SHALL play the generated audio through the existing local playback flow without introducing a separate playback contract
