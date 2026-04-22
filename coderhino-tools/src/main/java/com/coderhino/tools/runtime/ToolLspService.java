package com.coderhino.tools.runtime;

import com.coderhino.services.lsp.LspLocationDescriptor;
import com.coderhino.services.lsp.LspSymbolDescriptor;

import java.util.List;
import java.util.Optional;

public interface ToolLspService {
    Optional<List<LspSymbolDescriptor>> workspaceSymbols(String language, String query);

    Optional<List<LspSymbolDescriptor>> documentSymbols(String language, String uri);

    Optional<List<LspLocationDescriptor>> definition(String language, String uri, int line, int character);

    Optional<String> hover(String language, String uri, int line, int character);
}
