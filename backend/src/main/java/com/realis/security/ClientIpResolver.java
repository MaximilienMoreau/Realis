package com.realis.security;

import com.realis.config.NetworkProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Résout l'IP cliente utilisée pour la limitation de débit et le journal de consentement.
 *
 * Par défaut (trustForwardedFor=false), utilise directement request.getRemoteAddr() :
 * c'est le seul choix sûr sans reverse proxy de confiance devant l'application, un
 * en-tête X-Forwarded-For étant librement falsifiable par le client. Sans ce réglage,
 * derrière un reverse proxy, getRemoteAddr() renverrait toujours l'IP du proxy : la
 * limitation de débit deviendrait alors un quota global partagé par tous les
 * utilisateurs plutôt qu'un quota par IP cliente.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final NetworkProperties props;

    public String resolve(HttpServletRequest request) {
        if (props.trustForwardedFor()) {
            String header = request.getHeader("X-Forwarded-For");
            if (header != null && !header.isBlank()) {
                return header.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
