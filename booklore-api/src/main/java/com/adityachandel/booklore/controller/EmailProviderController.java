package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.EmailProvider;
import com.adityachandel.booklore.model.dto.request.CreateEmailProviderRequest;
import com.adityachandel.booklore.model.dto.request.SendBookByEmailRequest;
import com.adityachandel.booklore.service.EmailProviderService;
import com.adityachandel.booklore.service.EmailService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/email/providers")
public class EmailProviderController {

    private final EmailProviderService emailProviderService;

    @GetMapping
    public ResponseEntity<List<EmailProvider>> getEmailProviders() {
        return ResponseEntity.ok(emailProviderService.getEmailProviders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailProvider> getEmailProvider(@PathVariable Long id) {
        return ResponseEntity.ok(emailProviderService.getEmailProvider(id));
    }

    @PostMapping
    public ResponseEntity<EmailProvider> createEmailProvider(@RequestBody CreateEmailProviderRequest createEmailProviderRequest) {
        return ResponseEntity.ok(emailProviderService.createEmailProvider(createEmailProviderRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmailProvider(@PathVariable Long id) {
        emailProviderService.deleteEmailProvider(id);
        return ResponseEntity.noContent().build();
    }
}