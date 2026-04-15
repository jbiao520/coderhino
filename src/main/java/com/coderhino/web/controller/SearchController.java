package com.coderhino.web.controller;

import com.coderhino.web.dto.SearchResult;
import com.coderhino.web.search.DirectorySearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final DirectorySearchService searchService;

    public SearchController(DirectorySearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping(value = "/directories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchDirectories(@RequestParam(value = "query", required = false) String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query parameter must not be empty"));
        }

        List<SearchResult> results = searchService.searchDirectories(query);
        return ResponseEntity.ok(results);
    }
}
