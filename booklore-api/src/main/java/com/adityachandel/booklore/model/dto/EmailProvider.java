package com.adityachandel.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailProvider {
    private Long id;
    private String name;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private Boolean auth;
    private Boolean startTls;
    private Boolean defaultProvider;
}
