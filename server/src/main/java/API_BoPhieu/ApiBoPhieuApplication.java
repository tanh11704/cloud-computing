package API_BoPhieu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class ApiBoPhieuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiBoPhieuApplication.class, args);
    }

}
