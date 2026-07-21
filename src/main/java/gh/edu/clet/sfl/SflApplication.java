package gh.edu.clet.sfl;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SflApplication {

	public static void main(String[] args) {
		SpringApplication.run(SflApplication.class, args);
	}

	@Bean
	Clock systemClock() {
		return Clock.systemUTC();
	}

}
