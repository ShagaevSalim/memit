package dev.memit;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<DemoUser,Long> {}
