package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.JobRunDto;
import com.canhlabs.funnyapp.entity.JobRun;
import com.canhlabs.funnyapp.enums.JobRunResult;
import com.canhlabs.funnyapp.enums.JobTrigger;
import com.canhlabs.funnyapp.repo.JobRunRepository;
import com.canhlabs.funnyapp.service.JobRunService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class JobRunServiceImpl implements JobRunService {

    static final int MAX_ERROR_LENGTH = 500;
    private static final String MASK = "***";

    /** {@code password=x}, {@code token: x}, {@code api_key=x}, … → value masked. */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(pass(?:word|wd)?|pwd|secret|client[_-]?secret|(?:access|refresh|id)?[_-]?token|"
                    + "(?:api|access)?[_-]?key|authorization|credentials?)(\\s*[=:]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+");
    /** {@code scheme://user:pass@host} → {@code scheme://***@host}. */
    private static final Pattern URL_USERINFO = Pattern.compile("://[^/\\s:@]+:[^/\\s@]+@");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final JobRunRepository repository;

    public JobRunServiceImpl(JobRunRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(String jobName, JobTrigger trigger, JobRunResult result, Instant startedAt, Instant finishedAt,
                       Throwable error) {
        repository.save(JobRun.builder()
                .jobName(jobName)
                .trigger(trigger)
                .result(result)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs(Math.max(0, Duration.between(startedAt, finishedAt).toMillis()))
                .error(error == null ? null : describe(error))
                .build());
        repository.deleteOlderThanNewest(jobName, KEEP_PER_JOB);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, JobRunDto> latestByJob() {
        return repository.findLatestPerJob().stream()
                .collect(Collectors.toMap(JobRun::getJobName, JobRunServiceImpl::toDto, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobRunDto> recent(String jobName, int limit) {
        return repository.findByJobNameOrderByStartedAtDescIdDesc(jobName,
                        PageRequest.of(0, Math.clamp(limit, 1, KEEP_PER_JOB)))
                .stream().map(JobRunServiceImpl::toDto).toList();
    }

    /**
     * {@code Class: message}, plus the root cause when it differs, on one line, secrets masked, at most
     * {@value #MAX_ERROR_LENGTH} chars. No stack trace: that stays in the log.
     */
    static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String text = oneLine(error);
        if (root != error) {
            text += " <- " + oneLine(root);
        }
        text = mask(WHITESPACE.matcher(text).replaceAll(" ").trim());
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH - 1) + "…" : text;
    }

    static String mask(String text) {
        String result = URL_USERINFO.matcher(text).replaceAll("://" + MASK + "@");
        result = BEARER.matcher(result).replaceAll("Bearer " + MASK);
        return KEY_VALUE.matcher(result).replaceAll("$1$2" + MASK);
    }

    private static String oneLine(Throwable e) {
        String message = e.getMessage();
        String name = e.getClass().getSimpleName();
        return message == null || message.isBlank() ? name : name + ": " + message;
    }

    private static JobRunDto toDto(JobRun run) {
        return JobRunDto.builder()
                .jobName(run.getJobName())
                .trigger(run.getTrigger() == null ? null : run.getTrigger().name())
                .result(run.getResult() == null ? null : run.getResult().name())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getDurationMs())
                .error(run.getError())
                .build();
    }
}
