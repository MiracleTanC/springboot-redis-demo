package warmer.redisapp.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RedisappApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedisappApplication.class, args);
    }

}
