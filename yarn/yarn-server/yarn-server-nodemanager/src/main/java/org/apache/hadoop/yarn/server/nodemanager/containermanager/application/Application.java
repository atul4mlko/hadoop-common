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

package org.apache.hadoop.yarn.server.nodemanager.containermanager.application;

import java.io.IOException;
import java.util.Map;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.yarn.event.EventHandler;

import org.apache.hadoop.yarn.ApplicationID;
import org.apache.hadoop.yarn.LocalResource;
import org.apache.hadoop.yarn.LocalResourceVisibility;


public interface Application extends EventHandler<ApplicationEvent> {

  Map<CharSequence,CharSequence> getEnvironment();

  Map<String,LocalResource> getResources(LocalResourceVisibility vis);

  Map<Path,String> getLocalizedResources();

  String getUser();

  ApplicationID getAppId();

  Credentials getCredentials() throws IOException;
}
