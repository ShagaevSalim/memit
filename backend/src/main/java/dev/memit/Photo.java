package dev.memit;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(indexes=@Index(name="recipient_latest",columnList="recipientId,createdAt,id"))
public class Photo {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long senderId,recipientId;
 public String overlayText,filter,imagePath;
 public Instant createdAt;
}
