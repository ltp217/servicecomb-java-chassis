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
package org.apache.servicecomb.registry.swagger;

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.apache.servicecomb.foundation.common.concurrent.ConcurrentHashMapEx;
import org.apache.servicecomb.foundation.common.utils.JvmUtils;
import org.apache.servicecomb.foundation.common.utils.ResourceUtil;
import org.apache.servicecomb.registry.DiscoveryManager;
import org.apache.servicecomb.registry.RegistrationManager;
import org.apache.servicecomb.registry.api.registry.Microservice;
import org.apache.servicecomb.registry.api.registry.MicroserviceInstance;
import org.apache.servicecomb.registry.definition.MicroserviceNameParser;
import org.apache.servicecomb.swagger.SwaggerUtils;
import org.apache.servicecomb.swagger.generator.SwaggerGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import io.swagger.v3.oas.models.OpenAPI;

public class SwaggerLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(SwaggerLoader.class);

  // first key : appId
  // second key: microservice short name
  // third key : schemaId
  private final Map<String, Map<String, Map<String, OpenAPI>>> apps = new ConcurrentHashMapEx<>();

  public SwaggerLoader() {
  }

  // result length is 64
  public static String calcSchemaSummary(String schemaContent) {
    return Hashing.sha256().newHasher().putString(schemaContent, Charsets.UTF_8).hash().toString();
  }

  public void registerSwaggersInLocation(String microserviceName, String swaggersLocation) {
    LOGGER.info("register schemas in location [{}], microserviceName=[{}]", swaggersLocation, microserviceName);
    try {
      List<URI> resourceUris = ResourceUtil.findResourcesBySuffix(swaggersLocation, ".yaml");
      if (resourceUris.isEmpty()) {
        LOGGER.error("register swagger in not exist location: \"{}\".", swaggersLocation);
        return;
      }
      for (URI uri : resourceUris) {
        URL url = uri.toURL();
        OpenAPI swagger = SwaggerUtils.parseAndValidateSwagger(url);
        String schemaId = FilenameUtils.getBaseName(url.getPath());
        registerSwagger(microserviceName, schemaId, swagger);
      }
    } catch (Throwable e) {
      throw new IllegalStateException(String.format(
          "failed to register swaggers, microserviceName=%s, location=%s.", microserviceName, swaggersLocation),
          e);
    }
  }

  public void registerSwagger(String microserviceName, String schemaId, OpenAPI swagger) {
    MicroserviceNameParser parser = new MicroserviceNameParser(
        RegistrationManager.INSTANCE.getMicroservice().getAppId(), microserviceName);
    registerSwagger(parser.getAppId(), parser.getShortName(), schemaId, swagger);
  }

  public OpenAPI registerSwagger(String appId, String shortName, String schemaId, Class<?> cls) {
    OpenAPI swagger = SwaggerGenerator.generate(cls);
    registerSwagger(appId, shortName, schemaId, swagger);
    return swagger;
  }

  public void registerSwagger(String appId, String shortName, String schemaId, OpenAPI swagger) {
    apps.computeIfAbsent(appId, k -> new ConcurrentHashMapEx<>())
        .computeIfAbsent(shortName, k -> new ConcurrentHashMapEx<>())
        .put(schemaId, swagger);
    LOGGER.info("register swagger appId={}, name={}, schemaId={}.", appId, shortName, schemaId);
  }

  public void unregisterSwagger(String appId, String shortName, String schemaId) {
    apps.getOrDefault(appId, Collections.emptyMap())
        .getOrDefault(shortName, Collections.emptyMap())
        .remove(schemaId);
  }

  public OpenAPI loadSwagger(Microservice microservice, Collection<MicroserviceInstance> instances, String schemaId) {
    OpenAPI swagger = loadLocalSwagger(microservice.getAppId(), microservice.getServiceName(), schemaId);
    if (swagger != null) {
      return swagger;
    }

    return loadFromRemote(microservice, instances, schemaId);
  }

  public OpenAPI loadLocalSwagger(String appId, String shortName, String schemaId) {
    LOGGER.info("try to load schema locally, appId=[{}], serviceName=[{}], schemaId=[{}]",
        appId, shortName, schemaId);
    OpenAPI swagger = loadFromMemory(appId, shortName, schemaId);
    if (swagger != null) {
      LOGGER.info("load schema from memory");
      return swagger;
    }

    return loadFromResource(appId, shortName, schemaId);
  }

  @VisibleForTesting
  public OpenAPI loadFromMemory(String appId, String shortName, String schemaId) {
    return Optional.ofNullable(apps.get(appId))
        .map(microservices -> microservices.get(shortName))
        .map(schemas -> schemas.get(schemaId))
        .orElse(null);
  }

  private OpenAPI loadFromResource(String appId, String shortName, String schemaId) {
    if (appId.equals(RegistrationManager.INSTANCE.getMicroservice().getAppId())) {
      OpenAPI swagger = loadFromResource(String.format("microservices/%s/%s.yaml", shortName, schemaId));
      if (swagger != null) {
        return swagger;
      }
    }

    return loadFromResource(String.format("applications/%s/%s/%s.yaml", appId, shortName, schemaId));
  }

  private OpenAPI loadFromResource(String path) {
    URL url = JvmUtils.findClassLoader().getResource(path);
    if (url == null) {
      return null;
    }

    LOGGER.info("load schema from path [{}]", path);
    return SwaggerUtils.parseAndValidateSwagger(url);
  }

  private OpenAPI loadFromRemote(Microservice microservice, Collection<MicroserviceInstance> instances,
      String schemaId) {
    String schemaContent = DiscoveryManager.INSTANCE.getSchema(microservice.getServiceId(), instances, schemaId);
    if (schemaContent != null) {
      LOGGER.info(
          "load schema from service center, appId={}, microserviceName={}, version={}, serviceId={}, schemaId={}.",
          microservice.getAppId(),
          microservice.getServiceName(),
          microservice.getVersion(),
          microservice.getServiceId(),
          schemaId);
      LOGGER.debug(schemaContent);
      return SwaggerUtils.parseAndValidateSwagger(schemaContent);
    }

    LOGGER.warn("no schema in local, and can not get schema from service center, "
            + "appId={}, microserviceName={}, version={}, serviceId={}, schemaId={}.",
        microservice.getAppId(),
        microservice.getServiceName(),
        microservice.getVersion(),
        microservice.getServiceId(),
        schemaId);

    return null;
  }
}
