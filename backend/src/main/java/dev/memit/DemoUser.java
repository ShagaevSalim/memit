package dev.memit;
import jakarta.persistence.*;
@Entity @Table(name="demo_users")
public class DemoUser {
 @Id public Long id;
 public String displayName;
 protected DemoUser() {} public DemoUser(Long id,String name){this.id=id;this.displayName=name;}
}
