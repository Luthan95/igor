/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.config

import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.WerckerProperties.WerckerHost
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.wercker.WerckerCache
import com.netflix.spinnaker.igor.wercker.WerckerClient
import com.netflix.spinnaker.igor.wercker.WerckerService
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.util.concurrent.TimeUnit

import javax.validation.Valid

import retrofit.Endpoints
import retrofit.RestAdapter

@Configuration
@Slf4j
@CompileStatic
@ConditionalOnProperty("wercker.enabled")
@EnableConfigurationProperties(WerckerProperties)
class WerckerConfig {
    @Bean
    Map<String, WerckerService> werckerMasters(
        BuildServices buildServices,
        WerckerCache cache,
        IgorConfigurationProperties igorConfigurationProperties,
        OkHttpClientProvider clientProvider,
        @Valid WerckerProperties werckerProperties,
        RestAdapter.LogLevel retrofitLogLevel) {
        log.debug "creating werckerMasters"
        Map<String, WerckerService> werckerMasters = werckerProperties?.masters?.collectEntries { WerckerHost host ->
            log.debug "bootstrapping Wercker ${host.address} as ${host.name}"
            [(host.name): new WerckerService(host, cache, werckerClient(host, igorConfigurationProperties.getClient().timeout, clientProvider, retrofitLogLevel), host.permissions.build())]
        }

        buildServices.addServices(werckerMasters)
        werckerMasters
    }

    static WerckerClient werckerClient(WerckerHost host, int timeout = 30000, OkHttpClientProvider clientProvider, RestAdapter.LogLevel retrofitLogLevel) {
        OkHttpClient client = clientProvider.getClient(new DefaultServiceEndpoint(host.name, host.address, false))
        client = client.newBuilder().readTimeout(timeout, TimeUnit.MILLISECONDS).build()
        return new RestAdapter.Builder()
                .setLog(new Slf4jRetrofitLogger(WerckerService))
                .setLogLevel(retrofitLogLevel)
                .setEndpoint(Endpoints.newFixedEndpoint(host.address))
                .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
                .setClient(new Ok3Client(client))
                .build()
                .create(WerckerClient)
    }
}
