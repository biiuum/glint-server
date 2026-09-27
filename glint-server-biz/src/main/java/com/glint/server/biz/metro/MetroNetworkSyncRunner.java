package com.glint.server.biz.metro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MetroNetworkSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MetroNetworkSyncRunner.class);

    private final GaodeProperties properties;
    private final MetroNetworkService metroNetworkService;

    public MetroNetworkSyncRunner(GaodeProperties properties, MetroNetworkService metroNetworkService) {
        this.properties = properties;
        this.metroNetworkService = metroNetworkService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSyncOnStartup()) {
            log.info("metro_sync_skipped reason=disabled");
            return;
        }
        try {
            if (metroNetworkService.cityCount() == 0) {
                metroNetworkService.syncAll();
            } else {
                log.info("metro_sync_skipped reason=snapshot_present cities={}", metroNetworkService.cityCount());
            }
        } catch (Exception exception) {
            log.warn("metro_sync_on_startup_failed message={}", exception.getMessage());
        }
    }
}
