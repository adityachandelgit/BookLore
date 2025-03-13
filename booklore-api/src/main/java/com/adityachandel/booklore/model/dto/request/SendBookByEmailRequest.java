package com.adityachandel.booklore.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendBookByEmailRequest {

    @NotNull(message = "Book ID cannot be null")
    private Long bookId;

    @NotNull(message = "Provider ID cannot be null")
    private Long providerId;

    @NotBlank(message = "Recipient email cannot be blank")
    @Email(message = "Invalid email format")
    private String sendToEmail;
}
