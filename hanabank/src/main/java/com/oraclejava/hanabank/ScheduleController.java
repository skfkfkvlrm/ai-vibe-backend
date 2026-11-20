package com.oraclejava.hanabank;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScheduleController {
    private final ScheduleRepository repository;

    public ScheduleController(ScheduleRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/hanabank/schedules")
    public List<ScheduleDto> schedules() {
        return repository.findAllByOrderByGdateAscIdAsc().stream().map(ScheduleDto::from).toList();
    }

    public record ScheduleDto(Long id, String gdate, String game, String team) {
        static ScheduleDto from(Schedule s) {
            return new ScheduleDto(s.getId(), s.getGdate().toString(), s.getGame(), s.getTeam());
        }
    }
}
