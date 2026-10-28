package dev.memit;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
@SpringBootApplication
public class MemitApplication {
 public static void main(String[] args) { SpringApplication.run(MemitApplication.class,args); }
 @Bean ApplicationRunner seed(UserRepository users) { return args -> { if(!users.existsById(1L)) users.save(new DemoUser(1L,"Аня")); if(!users.existsById(2L)) users.save(new DemoUser(2L,"Максим")); }; }
}
