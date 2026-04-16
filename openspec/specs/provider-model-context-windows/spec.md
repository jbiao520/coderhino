# provider-model-context-windows Specification

## Purpose
Define how provider model configurations store and resolve context window metadata for accurate request sizing.

## Requirements
### Requirement: Provider models SHALL store context window metadata
The system SHALL persist provider models as structured entries that include the model identifier and a context window value for that model. When previously saved provider data does not include context window metadata, the system SHALL normalize the entry to use `128000`.

#### Scenario: Legacy provider data is loaded
- **WHEN** credentials are loaded from persisted provider data that only contains model names without context window metadata
- **THEN** the system converts each model into a structured entry and assigns `128000` as its context window

#### Scenario: Blank context window is normalized
- **WHEN** a provider model is loaded or saved with a blank, missing, or invalid context window value
- **THEN** the system stores and resolves that model with a context window of `128000`

### Requirement: Provider settings SHALL ask for a context window per model
The provider settings experience SHALL let users add and edit models as individual entries, and each entry SHALL include a context window input. New model entries SHALL start with a default context window value of `128000`.

#### Scenario: User adds a provider model
- **WHEN** a user creates a new model entry in provider settings
- **THEN** the UI shows a context window field for that model and pre-fills it with `128000`

#### Scenario: User saves provider settings with explicit context windows
- **WHEN** a user saves provider settings containing one or more configured models
- **THEN** the credentials update payload includes each model's identifier and context window value

### Requirement: Model requests SHALL use the selected model context window
The runtime configuration layer SHALL resolve the selected model's context window together with the provider credentials, and outgoing model requests SHALL use that resolved value. If the selected model has no stored context window, the runtime SHALL use `128000`.

#### Scenario: Selected model has configured context window
- **WHEN** a request is sent using a model whose provider configuration includes a context window value
- **THEN** the runtime passes that exact context window into the model client request configuration

#### Scenario: Selected model metadata is missing
- **WHEN** a request is sent using a model name that is not present in the provider's structured model list or whose context window is absent
- **THEN** the runtime uses `128000` as the request context window instead of failing
