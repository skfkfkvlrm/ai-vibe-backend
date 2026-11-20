package com.oraclejava.hanabank;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 테이블이 비어 있을 때만 예시 일정을 넣는다. */
@Component
public class DataSeeder implements ApplicationRunner {
    private final ScheduleRepository repository;

    public DataSeeder(ScheduleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) return;
        repository.saveAll(List.of(
            new Schedule(1L, LocalDate.of(2026, 11, 20), "1", "삼성생명"),
            new Schedule(2L, LocalDate.of(2026, 11, 23), "0", "신한은행"),
            new Schedule(3L, LocalDate.of(2026, 11, 28), "0", "우리은행"),
            new Schedule(4L, LocalDate.of(2026, 12, 2), "1", "BNK썸")));
    }
}
