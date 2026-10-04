package com.vnhackers.dotconn.developers;

import com.vnhackers.dotconn.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "developer_profiles")
public class DeveloperProfile {

  @Id
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "user_id")
  private User user;

  @Column(length = 100)
  private String displayName;

  @Column(length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private DeveloperRole role;

  @ElementCollection
  @CollectionTable(name = "developer_technologies", joinColumns = @JoinColumn(name = "developer_id"))
  @Column(name = "technology", length = 50)
  private List<String> technologies = new ArrayList<>();

  private Integer experienceYears;

  @Column(length = 255)
  private String githubUrl;

  @Enumerated(EnumType.STRING)
  @Column(length = 25)
  private Availability availability;

  @Column(precision = 10, scale = 2)
  private BigDecimal hourlyRate;

  protected DeveloperProfile() {}

  public DeveloperProfile(User user) {
    this.user = user;
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public DeveloperRole getRole() {
    return role;
  }

  public void setRole(DeveloperRole role) {
    this.role = role;
  }

  public List<String> getTechnologies() {
    return technologies;
  }

  public void setTechnologies(List<String> technologies) {
    this.technologies = technologies == null ? new ArrayList<>() : new ArrayList<>(technologies);
  }

  public Integer getExperienceYears() {
    return experienceYears;
  }

  public void setExperienceYears(Integer experienceYears) {
    this.experienceYears = experienceYears;
  }

  public String getGithubUrl() {
    return githubUrl;
  }

  public void setGithubUrl(String githubUrl) {
    this.githubUrl = githubUrl;
  }

  public Availability getAvailability() {
    return availability;
  }

  public void setAvailability(Availability availability) {
    this.availability = availability;
  }

  public BigDecimal getHourlyRate() {
    return hourlyRate;
  }

  public void setHourlyRate(BigDecimal hourlyRate) {
    this.hourlyRate = hourlyRate;
  }
}
