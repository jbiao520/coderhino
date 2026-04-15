package com.coderhino.web.controller;

import com.coderhino.web.dto.DirectoryListing;
import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.dto.FileContent;
import com.coderhino.web.files.FileExplorerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileExplorerService fileExplorerService;

    public FileController(FileExplorerService fileExplorerService) {
        this.fileExplorerService = fileExplorerService;
    }

    @GetMapping(value = "/tree", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listDirectory(
            @RequestParam("projectPath") String projectPath,
            @RequestParam(value = "dirPath", defaultValue = "") String dirPath) {
        try {
            Path root = Path.of(projectPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Invalid project path: " + projectPath));
            }
            DirectoryListing listing = fileExplorerService.listDirectory(root, dirPath);
            return ResponseEntity.ok(listing);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Path escapes project root"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping(value = "/content", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> readFileContent(
            @RequestParam("projectPath") String projectPath,
            @RequestParam("filePath") String filePath) {
        try {
            Path root = Path.of(projectPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Invalid project path: " + projectPath));
            }
            FileContent content = fileExplorerService.readFileContent(root, filePath);
            return ResponseEntity.ok(content);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Path escapes project root"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }
}
