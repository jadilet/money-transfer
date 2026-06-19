package com.example.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The owner of one or more accounts. */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String email;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Client() {
        // for JPA
    }

    private Client(String name, String email) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.createdAt = Instant.now();
    }

    public static Client of(String name, String email) {
        return new Client(name, email);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
