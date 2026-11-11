package com.revature.banking.model;

import java.time.OffsetDateTime;

public final class Customer {
    private final long id;
    private final String name;
    private final String email;
    private final String phone;
    private final String username;
    private final PasswordHash passwordHash;
    private final CustomerStatus status;
    private final int failedAttempts;
    private final OffsetDateTime lockedUntil;

    public Customer(long id, String name, String email, String phone, String username, String passwordHash, CustomerStatus status, int failedAttempts, OffsetDateTime lockedUntil) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.passwordHash = new PasswordHash(passwordHash);
        this.status = status;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash.encoded();
    }

    public CustomerStatus status() {
        return status;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public OffsetDateTime lockedUntil() {
        return lockedUntil;
    }


    public static final class Profile {
        private final long id;
        private final String name;
        private final String email;
        private final String phone;
        private final String username;

        public Profile(long id, String name, String email, String phone, String username) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.username = username;
        }

        public long id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String email() {
            return email;
        }

        public String phone() {
            return phone;
        }

        public String username() {
            return username;
        }
    }

    public Profile profile() {
        return new Profile(id, name, email, phone, username);
    }

    @Override
    public String toString() {
        return "Customer[id=" + id + ", status=" + status + "]";
    }
}
