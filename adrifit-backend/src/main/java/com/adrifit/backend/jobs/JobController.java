package com.adrifit.backend.jobs;

import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobController {

    private final DailyJobs dailyJobs;

    public JobController(DailyJobs dailyJobs) {
        this.dailyJobs = dailyJobs;
    }

    /** Lets the trainer run renewals and review reminders now instead of waiting for the cron. */
    @PostMapping("/api/jobs/daily/run")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<DailyJobs.DailyJobResult> runNow() {
        return ResponseEntity.ok(dailyJobs.run(LocalDate.now()));
    }
}
