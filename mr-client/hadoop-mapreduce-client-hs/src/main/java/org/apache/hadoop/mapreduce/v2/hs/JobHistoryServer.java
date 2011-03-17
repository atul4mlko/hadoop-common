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

package org.apache.hadoop.mapreduce.v2.hs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.service.CompositeService;

/******************************************************************
 * {@link JobHistoryServer} is responsible for servicing all job history
 * related requests from client.
 *
 *****************************************************************/
public class JobHistoryServer extends CompositeService {
  private static final Log LOG = LogFactory.getLog(JobHistoryServer.class);

  static{
    Configuration.addDefaultResource("mapred-default.xml");
    Configuration.addDefaultResource("mapred-site.xml");
  }

  public JobHistoryServer() {
    super(JobHistoryServer.class.getName());
  }

  public synchronized void init(Configuration conf) {
    Configuration config = new YarnConfiguration(conf);
    HistoryContext history = new JobHistory(conf);
    addService(new HistoryClientService(history));
    //TODO: add HistoryCleaner service
    super.init(config);
  }

  public static void main(String[] args) {
    StringUtils.startupShutdownMessage(JobHistoryServer.class, args, LOG);
    try {
      JobHistoryServer server = new JobHistoryServer();
      YarnConfiguration conf = new YarnConfiguration(new JobConf());
      server.init(conf);
      server.start();
    } catch (Throwable e) {
      LOG.fatal(StringUtils.stringifyException(e));
      System.exit(-1);
    }
  }

}
