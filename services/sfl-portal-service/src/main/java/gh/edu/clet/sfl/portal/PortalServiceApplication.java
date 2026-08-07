package gh.edu.clet.sfl.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The SFL Operations portal: one bundle, one address, no service's name in the URL. */
@SpringBootApplication
public class PortalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalServiceApplication.class, args);
    }
}
