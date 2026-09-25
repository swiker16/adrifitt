package com.adrifit.backend.jobs;

import com.adrifit.backend.subscription.service.SubscriptionService;
import com.adrifit.backend.task.service.TaskService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily housekeeping: subscription renewals (monthly charges / end of cancelled subscriptions)
 * and periodic review reminders. Runs on {@code adrifit.jobs.daily-cron} and can also be launched
 * by the trainer from the app.
 */
@Component
public class DailyJobs {

    private static final Logger log = LoggerFactory.getLogger(DailyJobs.class);

    private final SubscriptionService subscriptionService;
    private final TaskService taskService;

    public DailyJobs(SubscriptionService subscriptionService, TaskService taskService) {
        this.subscriptionService = subscriptionService;
        this.taskService = taskService;
    }

    @Scheduled(cron = "${adrifit.jobs.daily-cron:0 0 6 * * *}")
    public void scheduledRun() {
        try {
            run(LocalDate.now());
        } catch (RuntimeException ex) {
            log.error("Daily jobs failed", ex);
        }
    }

    public DailyJobResult run(LocalDate today) {
        int renewals = subscriptionService.processRenewals(today);
        int reviews = taskService.generateReviewTasks(today);
        log.info("Daily jobs done: {} renewals, {} review tasks", renewals, reviews);
        return new DailyJobResult(renewals, reviews);
    }

    public record DailyJobResult(int subscriptionsProcessed, int reviewTasksCreated) {
    }
}
