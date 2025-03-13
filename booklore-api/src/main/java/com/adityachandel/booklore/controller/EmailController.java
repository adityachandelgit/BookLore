package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.SendBookByEmailRequest;
import com.adityachandel.booklore.service.EmailService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/email")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send-book")
    public ResponseEntity<?> sendEmail(@Validated @RequestBody SendBookByEmailRequest request) {
        emailService.sendBook(request);
        return ResponseEntity.noContent().build();
    }
}