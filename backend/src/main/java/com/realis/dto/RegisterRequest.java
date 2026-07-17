package com.realis.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 254)
    String email,

    // Le maximum évite qu'un payload démesuré soit haché par BCrypt (coût CPU
    // proportionnel à la taille de l'entrée avant troncature interne) : sans borne,
    // un client pourrait soumettre plusieurs Mo de mot de passe en déni de service.
    @NotBlank @Size(min = 8, max = 128, message = "Le mot de passe doit comporter entre 8 et 128 caractères")
    String password
) {}
