/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.foundation.metrics.event;

public class InvocationFinishedEvent implements MetricsEvent {
  private final String operationName;

  private final long startProcessingTime;

  private final long processElapsedTime;

  public String getOperationName() {
    return operationName;
  }

  public long getStartProcessingTime() {
    return startProcessingTime;
  }

  public long getProcessElapsedTime() {
    return processElapsedTime;
  }

  public InvocationFinishedEvent(String operationName, long startProcessingTime, long timeProcess) {
    this.operationName = operationName;
    this.startProcessingTime = startProcessingTime;
    this.processElapsedTime = timeProcess;
  }
}
