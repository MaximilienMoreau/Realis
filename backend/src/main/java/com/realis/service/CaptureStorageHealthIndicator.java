package com.realis.service;
import com.realis.config.StorageProperties;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;
import java.nio.file.*;
@Component
public class CaptureStorageHealthIndicator implements HealthIndicator {
    private final Path path;
    public CaptureStorageHealthIndicator(StorageProperties props) { path = Path.of(props.path()); }
    @Override public Health health() {
        try {
            if (!Files.isWritable(path) || Files.getFileStore(path).getUsableSpace() < 1073741824L)
                return Health.down().withDetail("reason", "Storage unavailable or less than 1 GiB free").build();
            return Health.up().build();
        } catch (Exception e) { return Health.down().build(); }
    }
}
