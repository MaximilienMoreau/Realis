package com.realis.config;

import com.realis.service.timestamp.FreeTsaTimestampAuthority;
import com.realis.service.timestamp.NoOpTimestampAuthority;
import com.realis.service.timestamp.TimestampAuthority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TsaConfig {

    /**
     * "noop" doit être choisi explicitement (ex. profil de test) : il ne s'agit jamais
     * d'un repli implicite pour une valeur non reconnue, afin qu'une faute de frappe dans
     * TSA_PROVIDER ne désactive jamais silencieusement l'horodatage RFC 3161 — la valeur
     * centrale de Realis. Une valeur inconnue fait échouer le démarrage plutôt que de
     * basculer discrètement sur le no-op.
     */
    @Bean
    public TimestampAuthority timestampAuthority(TsaProperties props) {
        return switch (props.provider()) {
            case "freetsa" -> new FreeTsaTimestampAuthority(props);
            case "noop" -> {
                log.warn("realis.tsa.provider='noop' : l'horodatage RFC 3161 est DÉSACTIVÉ. " +
                    "Ne jamais laisser ce réglage en production.");
                yield new NoOpTimestampAuthority();
            }
            // Incrément futur : "eidas-qualified" -> new EidasTsaTimestampAuthority(props)
            default -> throw new IllegalStateException(
                "realis.tsa.provider='" + props.provider() + "' non reconnu " +
                "(valeurs attendues : 'freetsa', 'noop'). Vérifiez TSA_PROVIDER."
            );
        };
    }
}
