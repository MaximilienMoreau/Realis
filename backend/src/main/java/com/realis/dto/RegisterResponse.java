package com.realis.dto;

import java.util.UUID;

public record RegisterResponse(
    String token,
    UUID   userId,
    String email
) {}
