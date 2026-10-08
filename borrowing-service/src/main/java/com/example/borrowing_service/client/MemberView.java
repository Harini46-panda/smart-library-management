package com.example.borrowing_service.client;

/**
 * Mirrors only the fields Borrowing Service actually needs from Member
 * Service's GET /members/{id} response. Unknown JSON properties are ignored
 * (Spring Boot's default Jackson config disables fail-on-unknown-properties),
 * so this stays compatible even if Member Service's Member entity gains more
 * fields later.
 */
public class MemberView {

    private Long id;
    private String name;
    private Boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
