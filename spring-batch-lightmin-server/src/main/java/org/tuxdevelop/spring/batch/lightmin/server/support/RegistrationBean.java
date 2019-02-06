package org.tuxdevelop.spring.batch.lightmin.server.support;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.StringUtils;
import org.tuxdevelop.spring.batch.lightmin.client.api.LightminClientApplication;
import org.tuxdevelop.spring.batch.lightmin.client.api.LightminClientApplicationStatus;
import org.tuxdevelop.spring.batch.lightmin.exception.SpringBatchLightminApplicationException;
import org.tuxdevelop.spring.batch.lightmin.server.event.LightminClientApplicationDeleteRegistrationEvent;
import org.tuxdevelop.spring.batch.lightmin.server.event.LightminClientApplicationRegisteredEvent;
import org.tuxdevelop.spring.batch.lightmin.server.repository.LightminApplicationRepository;
import org.tuxdevelop.spring.batch.lightmin.server.support.validator.LightminApplicationValidator;

import java.util.Collection;
import java.util.Optional;

/**
 * @author Marcel Becker
 * @since 0.3
 */
@Slf4j
public class RegistrationBean implements ApplicationEventPublisherAware {

    private final LightminApplicationRepository lightminApplicationRepository;
    private ApplicationEventPublisher applicationEventPublisher;

    public RegistrationBean(final LightminApplicationRepository lightminApplicationRepository) {
        this.lightminApplicationRepository = lightminApplicationRepository;
    }

    @Override
    public void setApplicationEventPublisher(final ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public LightminClientApplication register(final LightminClientApplication lightminClientApplication) {

        LightminApplicationValidator.validate(lightminClientApplication);
        final String applicationId = ApplicationUrlIdGenerator.generateId(lightminClientApplication);
        LightminApplicationValidator.checkApplicationId(applicationId);
        final LightminClientApplicationStatus lightminClientApplicationStatus;
        if (lightminClientApplication.getLightminClientApplicationStatus() != null) {
            lightminClientApplicationStatus = lightminClientApplication.getLightminClientApplicationStatus();
        } else {
            lightminClientApplicationStatus = this.getExistingStatusInfo(applicationId);
        }
        lightminClientApplication.setId(applicationId);
        lightminClientApplication.setLightminClientApplicationStatus(lightminClientApplicationStatus);

        final LightminClientApplication savedLightminClientApplication = this.lightminApplicationRepository.save(lightminClientApplication);

        if (savedLightminClientApplication == null) {
            log.info("New LightminClientApplication {} registered ", lightminClientApplication);
            this.applicationEventPublisher.publishEvent(new LightminClientApplicationRegisteredEvent(lightminClientApplication));
        } else {
            if (lightminClientApplication.getId().equals(savedLightminClientApplication.getId())) {
                log.debug("LightminClientApplication {} refreshed", lightminClientApplication);
            } else {
                log.warn("LightminClientApplication {} replaced by LightminClientApplication {}", lightminClientApplication, savedLightminClientApplication);
            }
        }

        return lightminClientApplication;
    }

    public LightminClientApplication deleteRegistration(final String applicationId) {
        final LightminClientApplication deletedLightminClientApplication = this.lightminApplicationRepository.delete(applicationId);
        if (deletedLightminClientApplication != null) {
            log.info("Deleted LightminClientApplication Registration {}", deletedLightminClientApplication);
            this.applicationEventPublisher.publishEvent(new LightminClientApplicationDeleteRegistrationEvent(deletedLightminClientApplication));
        }
        return deletedLightminClientApplication;
    }

    public LightminClientApplication get(final String applicationId) {
        return this.lightminApplicationRepository.find(applicationId);
    }

    public Collection<LightminClientApplication> getAll() {
        return this.lightminApplicationRepository.findAll();
    }

    public String getIdByApplicationName(final String applicationName) {
        final Optional<LightminClientApplication> lightminClientApplication;
        if (StringUtils.hasText(applicationName)) {
            final Collection<LightminClientApplication> applications = this.getAll();
            if (applications != null && !applicationName.isEmpty()) {
                lightminClientApplication = applications
                        .stream()
                        .filter(a -> applicationName.equals(a.getName()))
                        .findFirst();
            } else {
                lightminClientApplication = Optional.empty();
            }
        } else {
            throw new SpringBatchLightminApplicationException("Could not find application id for null application name");
        }
        final String id;
        if (lightminClientApplication.isPresent()) {
            id = lightminClientApplication.get().getId();
        } else {
            throw new SpringBatchLightminApplicationException("Could not find application id for applicationName " + applicationName);
        }
        return id;
    }

    public void clear() {
        this.lightminApplicationRepository.clear();
    }

    private LightminClientApplicationStatus getExistingStatusInfo(final String applicationId) {
        final LightminClientApplication lightminClientApplication = this.get(applicationId);
        if (lightminClientApplication != null) {
            return lightminClientApplication.getLightminClientApplicationStatus();
        }
        return LightminClientApplicationStatus.ofUnknown();
    }
}