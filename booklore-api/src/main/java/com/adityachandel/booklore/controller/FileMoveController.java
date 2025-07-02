package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.service.file.FileOperationsService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/files")
public class FileMoveController {

    private final FileOperationsService fileOperationsService;

    @PostMapping("/move")
    public ResponseEntity<?> moveFiles(@RequestBody FileMoveRequest request) {
        fileOperationsService.moveFiles(request);
        return ResponseEntity.ok().build();
    }
}
