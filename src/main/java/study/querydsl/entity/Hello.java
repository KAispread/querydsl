package study.querydsl.entity;

import lombok.Getter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

// hello
@Entity
@Getter
public class Hello {
    @Id @GeneratedValue
    private Long id;
}
