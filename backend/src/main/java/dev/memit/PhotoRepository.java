package dev.memit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PhotoRepository extends JpaRepository<Photo,Long> {
 Optional<Photo> findFirstByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);
}
