package org.courseflow.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "profiles")
public class Profile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(length = 2000)
  private String bio;

  @Column(name = "avatar_url", length = 2048)
  private String avatarUrl;

  protected Profile() {}

  public Profile(User user) {
    this.user = user;
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getBio() {
    return bio;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void update(String bio, String avatarUrl) {
    this.bio = bio;
    this.avatarUrl = avatarUrl;
  }

  public void anonymize() {
    this.bio = null;
    this.avatarUrl = null;
  }
}
