package com.realis.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @Email @NotBlank @Size(max = 254) String email,
    // Borne haute pour éviter qu'un payload démesuré soit passé à BCrypt#matches (voir RegisterRequest.password).
    @NotBlank @Size(max = 128)        String password
) {}
