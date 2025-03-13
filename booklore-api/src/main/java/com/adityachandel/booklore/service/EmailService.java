package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.request.SendBookByEmailRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.EmailProviderEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.EmailProviderRepository;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Properties;

@Slf4j
@Service
@AllArgsConstructor
public class EmailService {

    private final EmailProviderRepository emailProviderRepository;
    private final BookRepository bookRepository;

    public void sendBook(SendBookByEmailRequest request) {
        EmailProviderEntity emailProvider = emailProviderRepository.findById(request.getProviderId()).orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(request.getProviderId()));

        BookEntity book = bookRepository.findById(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));

        JavaMailSenderImpl dynamicMailSender = new JavaMailSenderImpl();
        dynamicMailSender.setHost(emailProvider.getHost());
        dynamicMailSender.setPort(emailProvider.getPort());
        dynamicMailSender.setUsername(emailProvider.getUsername());
        dynamicMailSender.setPassword(emailProvider.getPassword());

        Properties mailProps = dynamicMailSender.getJavaMailProperties();
        mailProps.put("mail.smtp.auth", emailProvider.isAuth());
        mailProps.put("mail.smtp.starttls.enable", emailProvider.isStartTls());

        try {
            MimeMessage message = dynamicMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(emailProvider.getUsername());
            helper.setTo(request.getSendToEmail());
            helper.setSubject("Your Book from Booklore: " + book.getMetadata().getTitle());
            helper.setText(generateEmailBody(book.getMetadata().getTitle()));
            File bookFile = new File(FileUtils.getBookFullPath(book));
            helper.addAttachment(bookFile.getName(), bookFile);
            dynamicMailSender.send(message);
            log.info("Book sent successfully to {}", request.getSendToEmail());

        } catch (MessagingException e) {
            log.error("Failed to send book: {}", e.getMessage(), e);
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Failed to send book: {}" + e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error occurred while sending the book: {}", e.getMessage(), e);
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Unexpected error occurred while sending the book: {}" + e.getMessage());
        }
    }

    private String generateEmailBody(String bookTitle) {
        return """
                Hello,
                
                You have received a book from Booklore. Please find the attached file titled '%s' for your reading pleasure.
                
                Thank you for using Booklore! We hope you enjoy your book.
                """.formatted(bookTitle);
    }
}
