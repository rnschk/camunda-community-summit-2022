package de.viadee.bpm.service;

import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SharedService {

    @SneakyThrows(InterruptedException.class)
    public <T> String executeLogic(final T sleep) {
        Thread.sleep((Long.valueOf((Integer) sleep)));
        return UUID.randomUUID().toString().split("-")[0];
    }

}
