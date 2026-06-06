package com.expensive.Expensive.Tracker.entity;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@Entity
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false,unique = true,length = 20)
    private String roleKey;

    @Column(length = 100)
    private String description;

    @ManyToMany(mappedBy = "roles")
    private List<User> users;


}
