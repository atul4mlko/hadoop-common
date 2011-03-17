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

package org.apache.hadoop.yarn.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.ApplicationID;
import org.apache.hadoop.yarn.ContainerID;
import org.apache.hadoop.yarn.URL;

/**
 * This class contains a set of utilities which help converting data structures
 * from/to avro to/from hadoop/nativejava data structures.
 *
 */
public class AvroUtil {

  /**
   * return a hadoop path from a given avro url
   * 
   * @param url
   *          avro url to convert
   * @return
   * @throws URISyntaxException
   */
  public static Path getPathFromYarnURL(URL url) throws URISyntaxException {
    String scheme = url.scheme == null ? "" : url.scheme.toString();
    String authority = url.host != null ? url.host.toString() + ":" + url.port
        : "";
    return new Path(
        (new URI(scheme, authority, url.file.toString(), null, null))
            .normalize());
  }
  
  /**
   * change from CharSequence to string for map key and value
   * @param env
   * @return
   */
  public static Map<String, String> convertToString(
      Map<CharSequence, CharSequence> env) {
    
    Map<String, String> stringMap = new HashMap<String, String>();
    for (Entry<CharSequence, CharSequence> entry: env.entrySet()) {
      stringMap.put(entry.getKey().toString(), entry.getValue().toString());
    }
    return stringMap;
   }

  public static URL getYarnUrlFromPath(Path path) {
    return getYarnUrlFromURI(path.toUri());
  }
  
  public static URL getYarnUrlFromURI(URI uri) {
    URL url = new URL();
    if (uri.getHost() != null) {
      url.host = uri.getHost();
    }
    url.port = uri.getPort();
    url.scheme = uri.getScheme();
    url.file = uri.getPath();
    return url;
  }

  // TODO: Why thread local?
  private static final ThreadLocal<NumberFormat> appIdFormat =
    new ThreadLocal<NumberFormat>() {
      @Override
      public NumberFormat initialValue() {
        NumberFormat fmt = NumberFormat.getInstance();
        fmt.setGroupingUsed(false);
        fmt.setMinimumIntegerDigits(4);
        return fmt;
      }
    };

  // TODO: Why thread local?
  private static final ThreadLocal<NumberFormat> containerIdFormat =
      new ThreadLocal<NumberFormat>() {
        @Override
        public NumberFormat initialValue() {
          NumberFormat fmt = NumberFormat.getInstance();
          fmt.setGroupingUsed(false);
          fmt.setMinimumIntegerDigits(6);
          return fmt;
        }
      };

  public static String toString(ApplicationID appId) {
    StringBuilder sb = new StringBuilder();
    sb.append("application_").append(appId.clusterTimeStamp).append("_");
    sb.append(appIdFormat.get().format(appId.id));
    return sb.toString();
  }

  public static String toString(ContainerID cId) {
    StringBuilder sb = new StringBuilder();
    ApplicationID appId = cId.appID;
    sb.append("container_").append(appId.clusterTimeStamp).append("_");
    sb.append(appIdFormat.get().format(appId.id)).append("_");
    sb.append(containerIdFormat.get().format(cId.id));
    return sb.toString();
  }
}