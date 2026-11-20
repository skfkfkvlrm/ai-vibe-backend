package com.oraclejava.hanabank;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 하나은행 경기 일정. game: 홈경기 '1', 원정 '0'. */
@Entity
@Table(name = "schedule")
public class Schedule {
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "gdate", nullable = false)
    private LocalDate gdate;

    @Column(name = "game", length = 1, columnDefinition = "char(1)")
    private String game;

    @Column(name = "team", length = 20)
    private String team;

    protected Schedule() {}

    public void change(LocalDate gdate, String game, String team) {
        this.gdate = gdate; this.game = game; this.team = team;
    }

    public Schedule(Long id, LocalDate gdate, String game, String team) {
        this.id = id; this.gdate = gdate; this.game = game; this.team = team;
    }

    public Long getId() { return id; }
    public LocalDate getGdate() { return gdate; }
    public String getGame() { return game; }
    public String getTeam() { return team; }
}
