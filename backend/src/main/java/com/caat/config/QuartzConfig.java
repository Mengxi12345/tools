package com.caat.config;

import com.caat.job.ContentFetchJob;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

/**
 * Quartz 定时任务配置
 */
@Configuration
public class QuartzConfig {
    
    /**
     * 内容拉取任务 JobDetail
     */
    @Bean
    public JobDetailFactoryBean contentFetchJobDetail() {
        JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(ContentFetchJob.class);
        factoryBean.setName("contentFetchJob");
        factoryBean.setDescription("定时拉取用户内容任务");
        factoryBean.setDurability(true);
        factoryBean.setRequestsRecovery(true);
        return factoryBean;
    }
    
    /**
     * 内容拉取任务 Trigger
     * 每 10 分钟执行一次
     */
    @Bean
    public SimpleTriggerFactoryBean contentFetchTrigger() {
        SimpleTriggerFactoryBean factoryBean = new SimpleTriggerFactoryBean();
        factoryBean.setJobDetail(contentFetchJobDetail().getObject());
        factoryBean.setName("contentFetchTrigger");
        factoryBean.setDescription("内容拉取任务触发器");
        factoryBean.setRepeatInterval(10 * 60 * 1000L); // 10 minutes in milliseconds
        factoryBean.setRepeatCount(-1); // Repeat indefinitely
        factoryBean.setMisfireInstruction(org.quartz.SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
        return factoryBean;
    }
}
