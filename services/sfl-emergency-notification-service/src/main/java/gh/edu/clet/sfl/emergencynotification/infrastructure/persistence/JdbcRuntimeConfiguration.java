package gh.edu.clet.sfl.emergencynotification.infrastructure.persistence;

import gh.edu.clet.sfl.emergencynotification.application.port.RuntimeConfigurationPort;
import java.time.Duration;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Runtime configuration read from {@code runtime_configuration}, site override falling back to platform. */
@Component
public class JdbcRuntimeConfiguration implements RuntimeConfigurationPort {

    private final JdbcTemplate jdbc;

    public JdbcRuntimeConfiguration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> value(String key, String siteCode) {
        if (siteCode != null) {
            Optional<String> site = read(key, siteCode);
            if (site.isPresent()) {
                return site;
            }
        }
        return read(key, null);
    }

    @Override
    public Duration duration(String key, String siteCode, Duration fallback) {
        return value(key, siteCode).map(Duration::parse).orElse(fallback);
    }

    @Override
    public boolean flag(String key, String siteCode, boolean fallback) {
        return value(key, siteCode).map(Boolean::parseBoolean).orElse(fallback);
    }

    private Optional<String> read(String key, String siteCode) {
        try {
            String sql = siteCode == null
                    ? "SELECT config_value FROM emergency_notification.runtime_configuration WHERE config_key=? AND site_code IS NULL"
                    : "SELECT config_value FROM emergency_notification.runtime_configuration WHERE config_key=? AND site_code=?";
            String value = siteCode == null ? jdbc.queryForObject(sql, String.class, key)
                    : jdbc.queryForObject(sql, String.class, key, siteCode);
            return Optional.ofNullable(value);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
