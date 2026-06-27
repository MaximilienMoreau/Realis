package com.realis.config;

import com.realis.service.timestamp.FreeTsaTimestampAuthority;
import com.realis.service.timestamp.NoOpTimestampAuthority;
import com.realis.service.timestamp.TimestampAuthority;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TsaConfig {

    @Bean
    public TimestampAuthority timestampAuthority(TsaProperties props) {
        return switch (props.provider()) {
            case "freetsa" -> {
                yield new FreeTsaTimestampAuthority(props);
            }
            // Incrément futur : "eidas-qualified" -> new EidasTsaTimestampAuthority(props)
            default -> new NoOpTimestampAuthority();
        };
    }
}
