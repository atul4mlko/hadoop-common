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
import java.util.List;

import junit.framework.Assert;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.v2.app.AppContext;
import org.apache.hadoop.mapreduce.v2.app.client.ClientService;
import org.apache.hadoop.mapreduce.v2.app.client.MRClientService;
import org.apache.hadoop.mapreduce.v2.app.job.Job;
import org.apache.hadoop.mapreduce.v2.app.job.Task;
import org.apache.hadoop.mapreduce.v2.app.job.TaskAttempt;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptDiagnosticsUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEventType;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptStatusUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptStatusUpdateEvent.TaskAttemptStatus;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.apache.hadoop.mapreduce.v2.api.JobState;
import org.apache.hadoop.mapreduce.v2.api.MRClientProtocol;
import org.apache.hadoop.mapreduce.v2.api.Phase;
import org.apache.hadoop.mapreduce.v2.api.TaskAttemptState;
import org.apache.hadoop.mapreduce.v2.api.TaskReport;
import org.apache.hadoop.mapreduce.v2.api.TaskState;
import org.apache.hadoop.mapreduce.v2.api.TaskType;
import org.junit.Test;

public class TestMRClientService {

  @Test
  public void test() throws Exception {
    MRAppWithClientService app = new MRAppWithClientService(1, 0, false);
    Configuration conf = new Configuration();
    Job job = app.submit(conf);
    app.waitForState(job, JobState.RUNNING);
    Assert.assertEquals("No of tasks not correct", 1, job.getTasks().size());
    Iterator<Task> it = job.getTasks().values().iterator();
    Task task = it.next();
    app.waitForState(task, TaskState.RUNNING);
    TaskAttempt attempt = task.getAttempts().values().iterator().next();
    app.waitForState(attempt, TaskAttemptState.RUNNING);

    // send the diagnostic
    String diagnostic1 = "Diagnostic1";
    String diagnostic2 = "Diagnostic2";
    app.getContext().getEventHandler().handle(
        new TaskAttemptDiagnosticsUpdateEvent(attempt.getID(), diagnostic1));

    // send the status update
    TaskAttemptStatus taskAttemptStatus = new TaskAttemptStatus();
    taskAttemptStatus.id = attempt.getID();
    taskAttemptStatus.progress = 0.5f;
    taskAttemptStatus.diagnosticInfo = diagnostic2;
    taskAttemptStatus.stateString = "RUNNING";
    taskAttemptStatus.phase = Phase.MAP;
    taskAttemptStatus.outputSize = 3;
    // send the status update
    app.getContext().getEventHandler().handle(
        new TaskAttemptStatusUpdateEvent(attempt.getID(), taskAttemptStatus));

    
    //verify that all object are fully populated by invoking RPCs.
    YarnRPC rpc = YarnRPC.create(conf);
    MRClientProtocol proxy =
      (MRClientProtocol) rpc.getProxy(MRClientProtocol.class,
          app.clientService.getBindAddress(), conf);
    Assert.assertNotNull("Counters is null", proxy.getCounters(job.getID()));
    Assert.assertNotNull("JobReport is null", proxy.getJobReport(job.getID()));
    Assert.assertNotNull("TaskCompletionEvents is null", 
        proxy.getTaskAttemptCompletionEvents(job.getID(), 0, 10));
    Assert.assertNotNull("Diagnostics is null", 
        proxy.getDiagnostics(attempt.getID()));
    Assert.assertNotNull("TaskAttemptReport is null", 
        proxy.getTaskAttemptReport(attempt.getID()));
    Assert.assertNotNull("TaskReport is null", 
        proxy.getTaskReport(task.getID()));
    
    Assert.assertNotNull("TaskReports for map is null", 
        proxy.getTaskReports(job.getID(), 
        TaskType.MAP));
    Assert.assertNotNull("TaskReports for reduce is null", 
        proxy.getTaskReports(job.getID(), 
        TaskType.REDUCE));
    
    List<CharSequence> diag = proxy.getDiagnostics(attempt.getID());
    Assert.assertEquals("No of diagnostic not correct" , 2 , diag.size());
    Assert.assertEquals("Diag 1 not correct" , 
        diagnostic1, diag.get(0).toString());
    Assert.assertEquals("Diag 2 not correct" , 
        diagnostic2, diag.get(1).toString());
    
    TaskReport taskReport = proxy.getTaskReport(task.getID());
    Assert.assertEquals("No of diagnostic not correct", 2, 
        taskReport.diagnostics.size());
    
    //send the done signal to the task
    app.getContext().getEventHandler().handle(
        new TaskAttemptEvent(
            task.getAttempts().values().iterator().next().getID(),
            TaskAttemptEventType.TA_DONE));
    
    app.waitForState(job, JobState.SUCCEEDED);
    
  }

  class MRAppWithClientService extends MRApp {
    MRClientService clientService = null;
    MRAppWithClientService(int maps, int reduces, boolean autoComplete) {
      super(maps, reduces, autoComplete);
    }
    @Override
    protected ClientService createClientService(AppContext context) {
      clientService = new MRClientService(context);
      return clientService;
    }
  }
  
  public static void main(String[] args) throws Exception {
    TestMRClientService t = new TestMRClientService();
    t.test();
  }
}
