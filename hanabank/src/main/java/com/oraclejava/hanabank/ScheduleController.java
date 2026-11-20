package com.oraclejava.hanabank;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/hanabank/schedules")
public class ScheduleController {
    private final ScheduleRepository repository;

    public ScheduleController(ScheduleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ScheduleDto> list() {
        return repository.findAllByOrderByGdateAscIdAsc().stream().map(ScheduleDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ScheduleDto create(@RequestBody ScheduleRequest req) {
        req.validate();
        long id = repository.findTopByOrderByIdDesc().map(s -> s.getId() + 1).orElse(1L);
        return ScheduleDto.from(repository.save(new Schedule(id, req.date(), req.game(), req.team().trim())));
    }

    @PutMapping("/{id}")
    @Transactional
    public ScheduleDto update(@PathVariable Long id, @RequestBody ScheduleRequest req) {
        req.validate();
        Schedule s = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        s.change(req.date(), req.game(), req.team().trim());
        return ScheduleDto.from(s);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        repository.deleteById(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> handle(ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode())
            .body(java.util.Map.of("message", e.getReason() == null ? "요청에 실패했습니다." : e.getReason()));
    }

    public record ScheduleRequest(String gdate, String game, String team) {
        LocalDate date() { return LocalDate.parse(gdate); }

        void validate() {
            try {
                if (gdate == null) throw new DateTimeParseException("", "", 0);
                date();
            } catch (DateTimeParseException e) {
                throw bad("경기일자는 yyyy-MM-dd 형식이어야 합니다.");
            }
            if (!"1".equals(game) && !"0".equals(game)) throw bad("game은 1(홈) 또는 0(원정)이어야 합니다.");
            if (team == null || team.isBlank() || team.trim().length() > 20) throw bad("상대팀은 1~20자여야 합니다.");
        }

        private static ResponseStatusException bad(String m) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
        }
    }

    public record ScheduleDto(Long id, String gdate, String game, String team) {
        static ScheduleDto from(Schedule s) {
            return new ScheduleDto(s.getId(), s.getGdate().toString(), s.getGame(), s.getTeam());
        }
    }
}
