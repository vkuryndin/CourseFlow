package org.courseflow.catalog.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tags")
public class Tag {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false, columnDefinition = "bigint default 0")
  private long version;

  public long getVersion() {
    return version;
  }

  @Column(nullable = false, unique = true, length = 50)
  private String name;

  protected Tag() {}

  public Tag(String name) {
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void rename(String newName) {
    this.name = newName;
  }
}
