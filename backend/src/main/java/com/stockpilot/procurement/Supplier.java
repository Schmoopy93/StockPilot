package com.stockpilot.procurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Email
    @Size(max = 320)
    @Column(length = 320)
    private String email;

    @Size(max = 50)
    @Column(length = 50)
    private String phone;

    @Size(max = 1000)
    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Supplier() {
    }

    public Supplier(UUID organizationId, String name, String email, String phone, String notes) {
        this.organizationId = organizationId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.notes = notes;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public void update(String name, String email, String phone, String notes, boolean active) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.notes = notes;
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
