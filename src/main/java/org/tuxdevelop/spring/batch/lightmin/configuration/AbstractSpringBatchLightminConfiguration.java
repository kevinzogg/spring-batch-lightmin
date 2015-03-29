package org.tuxdevelop.spring.batch.lightmin.configuration;

import java.util.Collection;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.dao.JobExecutionDao;
import org.springframework.batch.core.repository.dao.JobInstanceDao;
import org.springframework.batch.core.repository.dao.StepExecutionDao;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.tuxdevelop.spring.batch.lightmin.admin.domain.JobConfiguration;
import org.tuxdevelop.spring.batch.lightmin.admin.repository.JobConfigurationRepository;
import org.tuxdevelop.spring.batch.lightmin.admin.scheduler.Scheduler;
import org.tuxdevelop.spring.batch.lightmin.controller.AdminController;
import org.tuxdevelop.spring.batch.lightmin.controller.IndexController;
import org.tuxdevelop.spring.batch.lightmin.controller.JobConfigurationController;
import org.tuxdevelop.spring.batch.lightmin.controller.JobController;
import org.tuxdevelop.spring.batch.lightmin.controller.JobLauncherController;
import org.tuxdevelop.spring.batch.lightmin.controller.StepController;
import org.tuxdevelop.spring.batch.lightmin.service.AdminService;
import org.tuxdevelop.spring.batch.lightmin.service.AdminServiceBean;
import org.tuxdevelop.spring.batch.lightmin.service.JobService;
import org.tuxdevelop.spring.batch.lightmin.service.SchedulerService;
import org.tuxdevelop.spring.batch.lightmin.service.SchedulerServiceBean;
import org.tuxdevelop.spring.batch.lightmin.service.StepService;
import org.tuxdevelop.spring.batch.lightmin.util.BeanRegistrar;
import org.tuxdevelop.spring.batch.lightmin.util.CommonJobFactory;

@Slf4j
@Import(value = { IndexController.class, JobController.class, StepController.class, AdminController.class,
		JobConfigurationController.class, JobLauncherController.class, SpringBatchLightminWebConfiguration.class })
public abstract class AbstractSpringBatchLightminConfiguration implements InitializingBean {

	@Autowired(required = false)
	private DataSource dataSource;

	@Autowired
	private ApplicationContext applicationContext;

	@Value("${table.prefix}")
	private String tablePrefix;

	@Autowired
	private JobConfigurationRepository jobConfigurationRepository;

	@Bean
	public DefaultSpringBatchLightminConfigurator defaultSpringBatchLightminConfigurator() {
		final DefaultSpringBatchLightminConfigurator configuration;
		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("Create DefaultSpringBatchLightminConfiguration ");
		if (dataSource != null) {
			if (tablePrefix != null) {
				configuration = new DefaultSpringBatchLightminConfigurator(dataSource, tablePrefix);
				stringBuilder.append("with dataSource and tablePrefix: ");
				stringBuilder.append(tablePrefix);
			} else {
				configuration = new DefaultSpringBatchLightminConfigurator(dataSource);
				stringBuilder.append("with dataSource");
			}
		} else if (tablePrefix != null) {
			configuration = new DefaultSpringBatchLightminConfigurator(tablePrefix);
			stringBuilder.append("with tablePrefix: ");
			stringBuilder.append(tablePrefix);
		} else {
			configuration = new DefaultSpringBatchLightminConfigurator();
		}
		log.info(stringBuilder.toString());
		return configuration;
	}

	@Bean
	public JobService jobService() {
		return defaultSpringBatchLightminConfigurator().getJobService();
	}

	@Bean
	public StepService stepService() {
		return defaultSpringBatchLightminConfigurator().getStepService();
	}

	@Bean
	public JobExecutionDao jobExecutionDao() {
		return defaultSpringBatchLightminConfigurator().getJobExecutionDao();
	}

	@Bean
	public JobInstanceDao jobInstanceDao() {
		return defaultSpringBatchLightminConfigurator().getJobInstanceDao();
	}

	@Bean
	public StepExecutionDao stepExecutionDao() {
		return defaultSpringBatchLightminConfigurator().getStepExecutionDao();
	}

	@Bean
	public JobOperator jobOperator() {
		return defaultSpringBatchLightminConfigurator().getJobOperator();
	}

	@Bean
	public JobLauncher jobLauncher() {
		return defaultSpringBatchLightminConfigurator().getJobLauncher();
	}

	@Bean
	public JobRegistry jobRegistry() {
		return defaultSpringBatchLightminConfigurator().getJobRegistry();
	}

	@Bean
	public JobExplorer jobExplorer() {
		return defaultSpringBatchLightminConfigurator().getJobExplorer();
	}

	@Bean
	public JobRepository jobRepository() {
		return defaultSpringBatchLightminConfigurator().getJobRepository();
	}

	@Bean
	public BeanRegistrar beanRegistrar() {
		return new BeanRegistrar();
	}

	@Bean
	public SchedulerService schedulerService() {
		return new SchedulerServiceBean(beanRegistrar(), jobRepository(), jobRegistry());
	}

	@Bean
	public AdminService adminService() {
		return new AdminServiceBean(jobConfigurationRepository, schedulerService());
	}

	@PostConstruct
	public void registerJobs() throws DuplicateJobException {
		// register jobs of the current application context
		final Map<String, Job> jobs = applicationContext.getBeansOfType(Job.class);
		if (jobs != null) {
			for (final Map.Entry<String, Job> jobEntry : jobs.entrySet()) {
				final Job job = jobEntry.getValue();
				final String jobName = job.getName();
				final CommonJobFactory commonJobFactory = new CommonJobFactory(job, jobName);
				jobRegistry().register(commonJobFactory);
			}
		}

		// register all stored jobConfigurations
		final Collection<JobConfiguration> jobConfigurations = adminService().getJobConfigurations();
		if (jobConfigurations != null) {
			for (final JobConfiguration jobConfiguration : jobConfigurations) {
				schedulerService().registerSchedulerForJob(jobConfiguration);
			}
		}
		// Schedule all registered schedulers
		final Map<String, Scheduler> schedulerMap = applicationContext.getBeansOfType(Scheduler.class);
		if (schedulerMap != null) {
			for (final Map.Entry<String, Scheduler> schedulerEntry : schedulerMap.entrySet()) {
				log.debug("scheduling bean: " + schedulerEntry.getKey());
				final Scheduler scheduler = schedulerEntry.getValue();
				scheduler.schedule();
			}
		}

	}

	@Override
	public void afterPropertiesSet() {
		assert jobConfigurationRepository != null;
	}

}