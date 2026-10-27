package com.revature.banking.model;

public class Customer {
    private Long customerId;
    private String fullname;
    private String email;
    private String phone;
    private String username;
    private String passwordHash;

    public Customer(
            Long customerId,
            String fullname,
            String email,
            String phone,
            String username,
            String passwordHash
    )  {
        this.customerId = customerId;
        this.fullname = fullname;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getCustomerId() {
        return customerId;
    }
    public String getFullname() {
        return fullname;
    }
    public String getEmail() {
        return email;
    }
    public String getPhone() {
        return phone;
    }
    public String getUsername() {
        return username;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
}
