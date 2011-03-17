/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.hadoop.mapreduce.v2.app;

import java.util.Iterator;
import java.util.Map;

import junit.framework.Assert;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.TaskAttemptListenerImpl;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.v2.app.AppContext;
import org.apache.hadoop.mapreduce.v2.app.TaskAttemptListener;
import org.apache.hadoop.mapreduce.v2.app.job.Job;
import org.apache.hadoop.mapreduce.v2.app.job.Task;
import org.apache.hadoop.mapreduce.v2.app.job.TaskAttempt;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEventType;
import org.apache.hadoop.mapreduce.v2.api.JobState;
import org.apache.hadoop.mapreduce.v2.api.TaskAttemptID;
import org.apache.hadoop.mapreduce.v2.api.TaskAttemptState;
import org.apache.hadoop.mapreduce.v2.api.TaskID;
import org.apache.hadoop.mapreduce.v2.api.TaskState;
import org.junit.Test;

/**
 * Tests the state machine with respect to Job/Task/TaskAttempt failure 
 * scenarios.
 */
public class TestFail {

  @Test
  //First attempt is failed and second attempt is passed
  //The job succeeds.
  public void testFailTask() throws Exception {
    MRApp app = new MockFirstFailingAttemptMRApp(1, 0);
    Job job = app.submit(new Configuration());
    app.waitForState(job, JobState.SUCCEEDED);
    Map<TaskID,Task> tasks = job.getTasks();
    Assert.assertEquals("No of tasks is not correct", 1, 
        tasks.size());
    Task task = tasks.values().iterator().next();
    Assert.assertEquals("Task state not correct", TaskState.SUCCEEDED, 
        task.getReport().state);
    Map<TaskAttemptID, TaskAttempt> attempts = 
      tasks.values().iterator().next().getAttempts();
    Assert.assertEquals("No of attempts is not correct", 2, 
        attempts.size());
    //one attempt must be failed 
    //and another must have succeeded
    Iterator<TaskAttempt> it = attempts.values().iterator();
    Assert.assertEquals("Attempt state not correct", TaskAttemptState.FAILED, 
          it.next().getReport().state);
    Assert.assertEquals("Attempt state not correct", TaskAttemptState.SUCCEEDED, 
        it.next().getReport().state);
  }

  @Test
  public void testMapFailureMaxPercent() throws Exception {
    MRApp app = new MockFirstFailingTaskMRApp(4, 0);
    Configuration conf = new Configuration();
    
    //reduce the no of attempts so test run faster
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, 2);
    conf.setInt(MRJobConfig.REDUCE_MAX_ATTEMPTS, 1);
    
    conf.setInt(MRJobConfig.MAP_FAILURES_MAX_PERCENT, 20);
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, 1);
    Job job = app.submit(conf);
    app.waitForState(job, JobState.FAILED);
    
    //setting the failure percentage to 25% (1/4 is 25) will
    //make the Job successful
    app = new MockFirstFailingTaskMRApp(4, 0);
    conf = new Configuration();
    
    //reduce the no of attempts so test run faster
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, 2);
    conf.setInt(MRJobConfig.REDUCE_MAX_ATTEMPTS, 1);
    
    conf.setInt(MRJobConfig.MAP_FAILURES_MAX_PERCENT, 25);
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, 1);
    job = app.submit(conf);
    app.waitForState(job, JobState.SUCCEEDED);
  }

  @Test
  public void testReduceFailureMaxPercent() throws Exception {
    MRApp app = new MockFirstFailingTaskMRApp(2, 4);
    Configuration conf = new Configuration();
    
    //reduce the no of attempts so test run faster
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, 1);
    conf.setInt(MRJobConfig.REDUCE_MAX_ATTEMPTS, 2);
    
    conf.setInt(MRJobConfig.MAP_FAILURES_MAX_PERCENT, 50);//no failure due to Map
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, 1);
    conf.setInt(MRJobConfig.REDUCE_FAILURES_MAXPERCENT, 20);
    conf.setInt(MRJobConfig.REDUCE_MAX_ATTEMPTS, 1);
    Job job = app.submit(conf);
    app.waitForState(job, JobState.FAILED);
    
    //setting the failure percentage to 25% (1/4 is 25) will
    //make the Job successful
    app = new MockFirstFailingTaskMRApp(2, 4);
    conf = new Configuration();
    
    //reduce the no of attempts so test run faster
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, 1);
    conf.setInt(MRJobConfig.REDUCE_MAX_ATTEMPTS, 2);
    
    conf.setInt(MRJobConfig.MAP_FAILURES_MAX_PERCENT, 50);//no failure due to Map
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, 1);
    conf.setInt(MRJobConfig.REDUCE_FAILURES_MAXPERCENT, 25);
    conf.setInt(MRJobConfig.REDUCE_MAX_ATTEMPTS, 1);
    job = app.submit(conf);
    app.waitForState(job, JobState.SUCCEEDED);
  }

  @Test
  //All Task attempts are timed out, leading to Job failure
  public void testTimedOutTask() throws Exception {
    MRApp app = new TimeOutTaskMRApp(1, 0);
    Configuration conf = new Configuration();
    int maxAttempts = 2;
    conf.setInt(MRJobConfig.MAP_MAX_ATTEMPTS, maxAttempts);
    Job job = app.submit(conf);
    app.waitForState(job, JobState.FAILED);
    Map<TaskID,Task> tasks = job.getTasks();
    Assert.assertEquals("No of tasks is not correct", 1, 
        tasks.size());
    Task task = tasks.values().iterator().next();
    Assert.assertEquals("Task state not correct", TaskState.FAILED, 
        task.getReport().state);
    Map<TaskAttemptID, TaskAttempt> attempts = 
      tasks.values().iterator().next().getAttempts();
    Assert.assertEquals("No of attempts is not correct", maxAttempts, 
        attempts.size());
    for (TaskAttempt attempt : attempts.values()) {
      Assert.assertEquals("Attempt state not correct", TaskAttemptState.FAILED, 
          attempt.getReport().state);
    }
  }

  static class TimeOutTaskMRApp extends MRApp {
    TimeOutTaskMRApp(int maps, int reduces) {
      super(maps, reduces, false);
    }
    @Override
    protected TaskAttemptListener createTaskAttemptListener(AppContext context) {
      //This will create the TaskAttemptListener with TaskHeartbeatHandler
      //RPC servers are not started
      //task time out is reduced
      //when attempt times out, heartbeat handler will send the lost event
      //leading to Attempt failure
      return new TaskAttemptListenerImpl(getContext(), null) {
        public void startRpcServer(){};
        public void stopRpcServer(){};
        public void init(Configuration conf) {
          conf.setInt("mapreduce.task.timeout", 1*1000);//reduce timeout
          super.init(conf);
        }
      };
    }
  }

  //Attempts of first Task are failed
  static class MockFirstFailingTaskMRApp extends MRApp {

    MockFirstFailingTaskMRApp(int maps, int reduces) {
      super(maps, reduces, true);
    }

    @Override
    protected void attemptLaunched(TaskAttemptID attemptID) {
      if (attemptID.taskID.id == 0) {//check if it is first task
        // send the Fail event
        getContext().getEventHandler().handle(
            new TaskAttemptEvent(attemptID, 
                TaskAttemptEventType.TA_FAILMSG));
      } else {
        getContext().getEventHandler().handle(
            new TaskAttemptEvent(attemptID,
                TaskAttemptEventType.TA_DONE));
      }
    }
  }

  //First attempt is failed
  static class MockFirstFailingAttemptMRApp extends MRApp {
    MockFirstFailingAttemptMRApp(int maps, int reduces) {
      super(maps, reduces, true);
    }

    @Override
    protected void attemptLaunched(TaskAttemptID attemptID) {
      if (attemptID.taskID.id == 0 && attemptID.id == 0) {
        //check if it is first task's first attempt
        // send the Fail event
        getContext().getEventHandler().handle(
            new TaskAttemptEvent(attemptID, 
                TaskAttemptEventType.TA_FAILMSG));
      } else {
        getContext().getEventHandler().handle(
            new TaskAttemptEvent(attemptID,
                TaskAttemptEventType.TA_DONE));
      }
    }
  }

  public static void main(String[] args) throws Exception {
    TestFail t = new TestFail();
    t.testFailTask();
    t.testTimedOutTask();
    t.testMapFailureMaxPercent();
    t.testReduceFailureMaxPercent();
  }
}
