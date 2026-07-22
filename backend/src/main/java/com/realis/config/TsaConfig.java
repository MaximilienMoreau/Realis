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

    @Bean
    public TimestampAuthority timestampAuthority(TsaProperties props) {
        return switch (props.provider()) {
            case "freetsa" -> {
                yield new FreeTsaTimestampAuthority(props);
            }
            // Incrément futur : "eidas-qualified" -> new EidasTsaTimestampAuthority(props)
            default -> {
                // Une valeur de realis.tsa.provider non reconnue (ex. faute de frappe dans
                // TSA_PROVIDER) retomberait sinon silencieusement sur le no-op : l'horodatage
                // RFC 3161, qui est la valeur centrale de Realis, serait désactivé sans
                // aucune erreur ni log au démarrage.
                log.warn("realis.tsa.provider='{}' non reconnu (valeur attendue : 'freetsa') : " +
                    "bascule sur NoOpTimestampAuthority, l'horodatage RFC 3161 est DÉSACTIVÉ. " +
                    "Ne jamais laisser ce réglage en production.", props.provider());
                yield new NoOpTimestampAuthority();
            }
        };
    }
}
