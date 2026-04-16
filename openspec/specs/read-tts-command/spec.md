## Purpose
Define `/read` speech generation behavior for ChatTTS numeric preservation without changing other backend flows.
## Requirements
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

### Requirement: Read command shall segment long-form input for speech generation
When `/read` resolves input that is long enough to benefit from chunking, the system SHALL split that input into ordered speech segments using natural text boundaries when possible instead of synthesizing the entire passage as one monolithic request.

#### Scenario: Long literal text is chunked before synthesis
- **WHEN** the user executes `/read` with a long text passage that exceeds the chunking threshold
- **THEN** the system SHALL create multiple ordered synthesis segments before invoking backend generation

#### Scenario: Long file input is chunked after file resolution
- **WHEN** the user executes `/read notes.txt`, the file resolves successfully, and the resolved text exceeds the chunking threshold
- **THEN** the system SHALL segment the resolved file content for synthesis without changing file-resolution behavior

### Requirement: Read command shall synthesize eligible segments in parallel
When `/read` creates multiple synthesis segments, the system SHALL generate segment audio concurrently with bounded parallelism to reduce total generation latency for long-form read-aloud requests.

#### Scenario: Multi-segment request runs concurrent synthesis work
- **WHEN** a `/read` request produces more than one synthesis segment
- **THEN** the system SHALL execute segment synthesis through parallel work instead of processing every segment strictly one after another

#### Scenario: Single-segment request avoids chunk orchestration overhead
- **WHEN** a `/read` request resolves to one synthesis segment
- **THEN** the system SHALL allow generation to complete without requiring a multi-segment merge workflow

### Requirement: Read command shall preserve ordered single-file playback output after chunking
After parallel segment generation completes successfully, the system SHALL merge the generated segment audio into one final playback artifact whose audible segment order matches the original text order so existing CLI playback and web audio delivery continue to consume one file per `/read` request.

#### Scenario: Merged output preserves source ordering
- **WHEN** a `/read` request generates multiple segments from one resolved input
- **THEN** the final merged audio SHALL play those segments in the same order as the source text

#### Scenario: Web and CLI flows still receive one generated file
- **WHEN** a chunked `/read` request completes successfully
- **THEN** the system SHALL expose one generated audio file to the existing playback or command-audio delivery flow

### Requirement: Read command shall fail the overall request if segment synthesis cannot complete
The system SHALL treat chunked `/read` generation as one logical request. If any required segment cannot be generated or merged, the command SHALL fail instead of returning a partial read-aloud result.

#### Scenario: Segment generation fails
- **WHEN** one segment in a chunked `/read` request fails during backend synthesis
- **THEN** the command SHALL report failure for the overall `/read` request

#### Scenario: Merge step fails
- **WHEN** all segments are generated but the final merge step cannot produce the single output file
- **THEN** the command SHALL report failure for the overall `/read` request

