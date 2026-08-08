package com.expensive.Expensive.Tracker.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "template")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Version
    @Column(name = "version",nullable = false)
    private Long version;

    @Column(name = "name",nullable = false,unique = true,length = 100)
    private String name;
}
