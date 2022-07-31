/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.injection;

import io.vavr.CheckedFunction0;

public interface FaultInjectionDecorators {
  static <T> FaultInjectionDecorateCheckedSupplier<T> ofCheckedSupplier(CheckedFunction0<T> supplier) {
    return new FaultInjectionDecorateCheckedSupplier<>(supplier);
  }

  class FaultInjectionDecorateCheckedSupplier<T> {

    private CheckedFunction0<T> supplier;

    protected FaultInjectionDecorateCheckedSupplier(CheckedFunction0<T> supplier) {
      this.supplier = supplier;
    }

    public FaultInjectionDecorateCheckedSupplier<T> withFaultInjection(Fault fault) {
      supplier = Fault.decorateCheckedSupplier(fault, supplier);
      return this;
    }

    public T get() throws Throwable {
      return supplier.apply();
    }
  }

}