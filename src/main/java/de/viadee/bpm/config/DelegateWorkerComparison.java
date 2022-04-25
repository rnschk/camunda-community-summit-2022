package de.viadee.bpm.config;

import de.viadee.bpm.service.SharedService;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.LocalTime.now;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@SuppressWarnings("unchecked")
@Slf4j
@Configuration
public class DelegateWorkerComparison {

    public static final String LOCL_KEY = "_local";
    public static final String GLOBL_KEY = "_global";

    @Bean
    public JavaDelegate ccs22Delegate(final SharedService service) {
        // execute(DelegateExecution execution)
        return execution -> {
            var sleep = execution.getVariable("sleep");
            var result = "d_" + service.executeLogic(sleep);
            execution.setVariableLocal(LOCL_KEY, result);
            log.info("finished delegate @ {}, result: {}", time(), result);
        };
    }


    @Bean
    @ExternalTaskSubscription(topicName = "ccs22")
    public ExternalTaskHandler ccs22Worker(final SharedService service) {
        // execute(ExternalTask task, ExternalTaskService taskService)
        return (task, taskService) -> {
            var sleep = task.getVariable("sleep");
            var result = "w_" + service.executeLogic(sleep);
            taskService.complete(task, null, Map.of(LOCL_KEY, result));
            log.info("finished worker @ {}, result: {}", time(), result);
        };
    }


    @Bean
    public ExecutionListener merge() {
        // notify(DelegateExecution execution)
        return execution -> {
            var item = (String) execution.getVariableLocal(LOCL_KEY);
            var list = (List<String>) execution.getVariable(GLOBL_KEY);
            execution.setVariable(GLOBL_KEY, addItemToList(item, list));
        };
    }


    @Bean
    public ExecutionListener results() {
        // notify(DelegateExecution execution)
        return execution -> log.info("results: {}", execution.getVariable(GLOBL_KEY));
    }


    static <T> List<T> addItemToList(final T item, List<T> list) {
        if (isNull(list)) list = new ArrayList<>();
        if (nonNull(item)) list.add(item);
        return list;
    }


    static String time() {
        return now().format(ofPattern("HH:mm:ss.SSS"));
    }


    @Bean
    public JavaDelegate delegate() {
        // execute(DelegateExecution execution)
        return execution -> error("regular delegate");
    }


    @Bean
    @ExternalTaskSubscription(topicName = "no-retry-settings")
    public ExternalTaskHandler workerNoRetrySettings() {
        // execute(ExternalTask task, ExternalTaskService taskService)
        return (task, taskService) -> {
            error("worker without retry-behaviour");
            taskService.complete(task);
        };
    }


    @Bean
    @ExternalTaskSubscription(topicName = "code-retry-settings")
    public ExternalTaskHandler workerWithRetrySettings() {
        // execute(ExternalTask task, ExternalTaskService taskService)
        return (task, taskService) -> {
            try {
                error("worker with retry-settings");
                taskService.complete(task);

            } catch (Exception e) {
                var retries = Optional.ofNullable(task.getRetries())
                                      .orElse(1000);
                taskService.handleFailure(task, e.getMessage(), "details", --retries, 60000L);
            }
        };
    }

    /**
     * Simply throw a {@link RuntimeException}
     */
    private void error(final String origin) {
        throw new RuntimeException("Some error occurred in " + origin);
    }
}
