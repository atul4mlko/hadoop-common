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

package org.apache.hadoop.yarn.server.resourcemanager.applicationsmanager;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.hadoop.yarn.security.ApplicationTokenSecretManager;
import org.apache.hadoop.yarn.security.client.ClientToAMSecretManager;
import org.apache.hadoop.yarn.server.resourcemanager.ResourceManager.ASMContext;
import org.apache.hadoop.yarn.server.resourcemanager.applicationsmanager.events.ASMEvent;
import org.apache.hadoop.yarn.server.resourcemanager.applicationsmanager.events.ApplicationMasterEvents.AMLauncherEventType;
import org.apache.hadoop.yarn.service.AbstractService;


class ApplicationMasterLauncher extends AbstractService implements EventHandler<ASMEvent<AMLauncherEventType>> {
  private static final Log LOG = LogFactory.getLog(
      ApplicationMasterLauncher.class);
  private final ThreadPoolExecutor launcherPool;
  private final EventHandler handler;
  private Thread launcherHandlingThread;
  
  private final Queue<Runnable> masterEvents
    = new LinkedBlockingQueue<Runnable>();
  
  private ApplicationTokenSecretManager applicationTokenSecretManager;
  private ClientToAMSecretManager clientToAMSecretManager;
  private final ASMContext context;
  
  public ApplicationMasterLauncher(ApplicationTokenSecretManager 
      applicationTokenSecretManager, ClientToAMSecretManager clientToAMSecretManager,
      ASMContext context) {
    super(ApplicationMasterLauncher.class.getName());
    this.context = context;
    this.handler = context.getDispatcher().getEventHandler();
    /* register to dispatcher */
    this.context.getDispatcher().register(AMLauncherEventType.class, this);
    this.launcherPool = new ThreadPoolExecutor(1, 10, 1, 
        TimeUnit.HOURS, new LinkedBlockingQueue<Runnable>());
    this.launcherHandlingThread = new LauncherThread();
    this.applicationTokenSecretManager = applicationTokenSecretManager;
    this.clientToAMSecretManager = clientToAMSecretManager;
  }
  
  public void start() {
    launcherHandlingThread.start();
    super.start();
  }
  
  protected Runnable createRunnableLauncher(AppContext masterInfo, AMLauncherEventType event) {
    Runnable launcher = new AMLauncher(context, masterInfo, event,
        applicationTokenSecretManager, clientToAMSecretManager, getConfig());
    return launcher;
  }
  
  private void launch(AppContext appContext) {
    Runnable launcher = createRunnableLauncher(appContext, AMLauncherEventType.LAUNCH);
    masterEvents.add(launcher);
  }
  

  public void stop() {
    launcherHandlingThread.interrupt();
    try {
      launcherHandlingThread.join(1000);
    } catch (InterruptedException ie) {
      LOG.info(launcherHandlingThread.getName() + " interrupted during join ", 
          ie);    }
    launcherPool.shutdown();
    super.stop();
  }

  private class LauncherThread extends Thread {
    @Override
    public void run() {
      while (!this.isInterrupted()) {
        Runnable toLaunch = masterEvents.poll();
        if (toLaunch != null) {
          launcherPool.execute(toLaunch);
        }
      }
    }
  }    

  private void cleanup(AppContext appContext) {
    Runnable launcher = createRunnableLauncher(appContext, AMLauncherEventType.CLEANUP);
    masterEvents.add(launcher);
  } 
  
  @Override
  public synchronized void  handle(ASMEvent<AMLauncherEventType> appEvent) {
    AMLauncherEventType event = appEvent.getType();
    AppContext appContext = appEvent.getAppContext();
    switch (event) {
    case LAUNCH:
      launch(appContext);
      break;
    case CLEANUP:
      cleanup(appContext);
    default:
      break;
    }
  }
}
