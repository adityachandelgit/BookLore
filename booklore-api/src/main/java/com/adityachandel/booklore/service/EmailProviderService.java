package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.EmailProviderMapper;
import com.adityachandel.booklore.model.dto.EmailProvider;
import com.adityachandel.booklore.model.dto.request.CreateEmailProviderRequest;
import com.adityachandel.booklore.model.entity.EmailProviderEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.EmailProviderRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class EmailProviderService {

    private final EmailProviderRepository emailProviderRepository;
    private final EmailProviderMapper emailProviderMapper;

    public EmailProvider getEmailProvider(Long id) {
        EmailProviderEntity emailProvider = emailProviderRepository.findById(id).orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));
        return emailProviderMapper.toDTO(emailProvider);
    }

    public EmailProvider createEmailProvider(CreateEmailProviderRequest request) {
        EmailProviderEntity emailProviderEntity = emailProviderMapper.toEntity(request);
        EmailProviderEntity savedEntity = emailProviderRepository.save(emailProviderEntity);
        return emailProviderMapper.toDTO(savedEntity);
    }

    public void deleteEmailProvider(Long id) {
        emailProviderRepository.deleteById(id);
    }
}