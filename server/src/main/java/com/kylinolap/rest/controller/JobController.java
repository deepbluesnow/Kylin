/*
 * Copyright 2013-2014 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kylinolap.rest.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.job.JobInstance;
import com.kylinolap.job.JobManager;
import com.kylinolap.job.constant.JobStatusEnum;
import com.kylinolap.rest.constant.Constant;
import com.kylinolap.rest.exception.InternalErrorException;
import com.kylinolap.rest.request.JobListRequest;
import com.kylinolap.rest.service.JobService;

/**
 * @author ysong1
 * @author Jack
 * 
 */
@Controller
@RequestMapping(value = "jobs")
public class JobController extends BasicController implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    @Autowired
    private JobService jobService;

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        String timeZone = jobService.getKylinConfig().getTimeZone();
        TimeZone tzone = TimeZone.getTimeZone(timeZone);
        TimeZone.setDefault(tzone);

        String serverMode = KylinConfig.getInstanceFromEnv().getServerMode();

        if (Constant.SERVER_MODE_JOB.equals(serverMode.toLowerCase()) || Constant.SERVER_MODE_ALL.equals(serverMode.toLowerCase())) {
            logger.info("Initializing Job Engine ....");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    JobManager jobManager = null;
                    try {
                        jobManager = jobService.getJobManager();
                        jobManager.startJobEngine();
                        metricsService.registerJobMetrics(jobManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }
    }

    /**
     * get all cube jobs
     * 
     * @param cubeName
     *            Cube ID
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "", method = { RequestMethod.GET })
    @ResponseBody
    public List<JobInstance> list(JobListRequest jobRequest) {

        List<JobInstance> jobInstanceList = Collections.emptyList();
        List<JobStatusEnum> statusList = new ArrayList<JobStatusEnum>();

        if (null != jobRequest.getStatus()) {
            for (int status : jobRequest.getStatus()) {
                statusList.add(JobStatusEnum.getByCode(status));
            }
        }

        try {
            jobInstanceList = jobService.listAllJobs(jobRequest.getCubeName(), jobRequest.getProjectName(), statusList, jobRequest.getLimit(), jobRequest.getOffset());
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new InternalErrorException(e);
        }
        return jobInstanceList;
    }

    /**
     * Get a cube job
     * 
     * @param cubeName
     *            Cube ID
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/{jobId}", method = { RequestMethod.GET })
    @ResponseBody
    public JobInstance get(@PathVariable String jobId) {
        JobInstance jobInstance = null;
        try {
            jobInstance = jobService.getJobInstance(jobId);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new InternalErrorException(e);
        }

        return jobInstance;
    }

    /**
     * Get a job step output
     * 
     * @param cubeName
     *            Cube ID
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/{jobId}/steps/{stepId}/output", method = { RequestMethod.GET })
    @ResponseBody
    public Map<String, String> getStepOutput(@PathVariable String jobId, @PathVariable int stepId) {
        Map<String, String> result = new HashMap<String, String>();
        result.put("jobId", jobId);
        result.put("stepId", String.valueOf(stepId));
        long start = System.currentTimeMillis();
        String output = "";

        try {
            output = jobService.getJobManager().getJobStepOutput(jobId, stepId);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new InternalErrorException(e);
        }

        result.put("cmd_output", output);
        long end = System.currentTimeMillis();
        logger.info("Complete fetching step " + jobId + ":" + stepId + " output in " + (end - start) + " seconds");
        return result;
    }

    /**
     * Resume a cube job
     * 
     * @param String
     *            Job ID
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/{jobId}/resume", method = { RequestMethod.PUT })
    @ResponseBody
    public JobInstance resume(@PathVariable String jobId) {
        JobInstance jobInstance = null;
        try {
            jobInstance = jobService.getJobInstance(jobId);
            jobService.resumeJob(jobInstance);
            jobInstance = jobService.getJobInstance(jobId);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new InternalErrorException(e);
        }

        return jobInstance;
    }

    /**
     * Cancel a job
     * 
     * @param String
     *            Job ID
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/{jobId}/cancel", method = { RequestMethod.PUT })
    @ResponseBody
    public JobInstance cancel(@PathVariable String jobId) {

        JobInstance jobInstance = null;
        try {
            jobInstance = jobService.getJobInstance(jobId);
            jobService.cancelJob(jobInstance);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new InternalErrorException(e);
        }

        return jobInstance;
    }

    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

}
