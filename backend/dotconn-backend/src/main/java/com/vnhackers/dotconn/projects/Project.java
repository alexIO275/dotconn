// Entitate JPA pentru un proiect creat dintr-o analiză confirmată de utilizator.
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
public class Project {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id")
  private User owner;

  @Column(length = 150, nullable = false)
  private String title;

  @Column(length = 5000, nullable = false)
  private String description;

  @Column(length = 1000)
  private String summary;

  @ElementCollection
  @CollectionTable(name = "project_roles", joinColumns = @JoinColumn(name = "project_id"))
  @Column(name = "role", length = 20)
  private List<String> roles = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "project_tasks", joinColumns = @JoinColumn(name = "project_id"))
  @Column(name = "task", length = 500)
  private List<String> tasks = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "project_stack", joinColumns = @JoinColumn(name = "project_id"))
  @Column(name = "technology", length = 100)
  private List<String> existingStack = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "project_required_tech", joinColumns = @JoinColumn(name = "project_id"))
  @Column(name = "technology", length = 50)
  private List<String> requiredTechnologies = new ArrayList<>();

  @Column(precision = 10, scale = 2)
  private BigDecimal maxHourlyRate;

  @Column(length = 500)
  private String repositoryUrl;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Project() {}

  public Project(User owner) {
    this.owner = owner;
  }

  public Long getId() {
    return id;
  }

  public User getOwner() {
    return owner;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
  }

  public List<String> getTasks() {
    return tasks;
  }

  public void setTasks(List<String> tasks) {
    this.tasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
  }

  public List<String> getExistingStack() {
    return existingStack;
  }

  public void setExistingStack(List<String> existingStack) {
    this.existingStack = existingStack == null ? new ArrayList<>() : new ArrayList<>(existingStack);
  }

  public List<String> getRequiredTechnologies() {
    return requiredTechnologies;
  }

  public void setRequiredTechnologies(List<String> requiredTechnologies) {
    this.requiredTechnologies =
        requiredTechnologies == null ? new ArrayList<>() : new ArrayList<>(requiredTechnologies);
  }

  public BigDecimal getMaxHourlyRate() {
    return maxHourlyRate;
  }

  public void setMaxHourlyRate(BigDecimal maxHourlyRate) {
    this.maxHourlyRate = maxHourlyRate;
  }

  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  public void setRepositoryUrl(String repositoryUrl) {
    this.repositoryUrl = repositoryUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
