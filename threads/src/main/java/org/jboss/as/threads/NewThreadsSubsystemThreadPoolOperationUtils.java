/*
* JBoss, Home of Professional Open Source.
* Copyright 2006, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.threads;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.threads.CommonAttributes.ALLOW_CORE_TIMEOUT;
import static org.jboss.as.threads.CommonAttributes.BLOCKING;
import static org.jboss.as.threads.CommonAttributes.CORE_THREADS;
import static org.jboss.as.threads.CommonAttributes.COUNT;
import static org.jboss.as.threads.CommonAttributes.HANDOFF_EXECUTOR;
import static org.jboss.as.threads.CommonAttributes.KEEPALIVE_TIME;
import static org.jboss.as.threads.CommonAttributes.MAX_THREADS;
import static org.jboss.as.threads.CommonAttributes.PER_CPU;
import static org.jboss.as.threads.CommonAttributes.PROPERTIES;
import static org.jboss.as.threads.CommonAttributes.QUEUE_LENGTH;
import static org.jboss.as.threads.CommonAttributes.THREAD_FACTORY;
import static org.jboss.as.threads.CommonAttributes.TIME;
import static org.jboss.as.threads.CommonAttributes.UNIT;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
class NewThreadsSubsystemThreadPoolOperationUtils {

    static <T> void addThreadFactoryDependency(final String threadFactory, final ServiceName serviceName, ServiceBuilder<T> serviceBuilder, Injector<ThreadFactory> injector, ServiceTarget target) {
        final ServiceName threadFactoryName;
        if (threadFactory == null) {
            threadFactoryName = serviceName.append("thread-factory");
            target.addService(threadFactoryName, new ThreadFactoryService())
                .install();
        } else {
            threadFactoryName = ThreadsServices.threadFactoryName(threadFactory);
        }
        serviceBuilder.addDependency(threadFactoryName, ThreadFactory.class, injector);
    }

    static BaseOperationParameters parseUnboundedQueueThreadPoolOperationParameters(ModelNode operation) {
        OperationParametersImpl params = new OperationParametersImpl();
        return parseBaseThreadPoolOperationParameters(operation, params);
    }

    static BaseOperationParameters parseScheduledThreadPoolOperationParameters(ModelNode operation) {
        OperationParametersImpl params = new OperationParametersImpl();
        return parseBaseThreadPoolOperationParameters(operation, params);
    }

    static QueuelessOperationParameters parseQueuelessThreadPoolOperationParameters(ModelNode operation) {
        OperationParametersImpl params = new OperationParametersImpl();
        parseBaseThreadPoolOperationParameters(operation, params);

        params.blocking = has(operation, BLOCKING) ? operation.get(BLOCKING).asBoolean() : false;
        params.handoffExecutor = has(operation, HANDOFF_EXECUTOR) ? operation.get(HANDOFF_EXECUTOR).asString() : null;

        return params;
    }

    static BoundedOperationParameters parseBoundedThreadPoolOperationParameters(ModelNode operation) {
        OperationParametersImpl params = new OperationParametersImpl();
        parseBaseThreadPoolOperationParameters(operation, params);

        params.blocking = has(operation, BLOCKING) ? operation.get(BLOCKING).asBoolean() : false;
        params.allowCoreTimeout = has(operation, ALLOW_CORE_TIMEOUT) ? operation.get(ALLOW_CORE_TIMEOUT).asBoolean() : false;
        params.handoffExecutor = has(operation, HANDOFF_EXECUTOR) ? operation.get(HANDOFF_EXECUTOR).asString() : null;
        params.coreThreads = getScaledCount(operation, CORE_THREADS);
        params.queueLength = getScaledCount(operation, QUEUE_LENGTH);

        return params;
    }


    private static OperationParametersImpl parseBaseThreadPoolOperationParameters(ModelNode operation, OperationParametersImpl params) {
        //Get/validate the properties
        params.name = operation.require(NAME).asString();
        params.threadFactory = has(operation, THREAD_FACTORY) ? operation.get(THREAD_FACTORY).asString() : null;
        params.properties = has(operation, PROPERTIES) ? operation.get(PROPERTIES) : null;
        if (params.properties != null) {
            if (params.properties.getType() != ModelType.LIST) {
                throw new IllegalArgumentException(PROPERTIES + " must be a list of properties"); //TODO i18n
            }
            for (ModelNode property : params.properties.asList()) {
                if (property.getType() != ModelType.PROPERTY) {
                    throw new IllegalArgumentException(PROPERTIES + " must be a list of properties"); //TODO i18n
                }
            }
        }
        params.maxThreads = getScaledCount(operation, MAX_THREADS);
        if (params.maxThreads == null) {
            throw new IllegalArgumentException(MAX_THREADS + " was not defined");
        }

        if (has(operation, KEEPALIVE_TIME)) {
            ModelNode keepaliveTime = operation.get(KEEPALIVE_TIME);
            if (!has(keepaliveTime, TIME)) {
                throw new IllegalArgumentException("Missing '" + TIME + "' for '" + KEEPALIVE_TIME + "'");
            }
            if (!has(keepaliveTime, UNIT)) {
                throw new IllegalArgumentException("Missing '" + UNIT + "' for '" + KEEPALIVE_TIME + "'");
            }
            params.keepAliveTime = new TimeSpec(Enum.valueOf(TimeUnit.class, keepaliveTime.get(UNIT).asString()), keepaliveTime.get(TIME).asLong());
        }

        return params;
    }

    private static ScaledCount getScaledCount(ModelNode operation, String paramName) {
        if (has(operation, paramName)) {
            ModelNode scaledCount = operation.get(paramName);
            if (!has(scaledCount, COUNT)) {
                throw new IllegalArgumentException("Missing '" + COUNT + "' for '" + paramName + "'");
            }
            if (!has(scaledCount, PER_CPU)) {
                throw new IllegalArgumentException("Missing '" + PER_CPU + "' for '" + paramName + "'");
            }
            return new ScaledCount(scaledCount.get(COUNT).asBigDecimal(), scaledCount.get(PER_CPU).asBigDecimal());
        }
        return null;
    }

    private static boolean has(ModelNode operation, String name) {
        return operation.has(name) && operation.get(name).isDefined();
    }

    interface BaseOperationParameters {
        String getName();

        String getThreadFactory();

        ModelNode getProperties();

        ScaledCount getMaxThreads();

        TimeSpec getKeepAliveTime();
    }

    interface QueuelessOperationParameters extends BaseOperationParameters {
        boolean isBlocking();

        String getHandoffExecutor();
    }

    interface BoundedOperationParameters extends QueuelessOperationParameters {
        boolean isAllowCoreTimeout();
        ScaledCount getCoreThreads();
        ScaledCount getQueueLength();
    }

    private static class OperationParametersImpl implements QueuelessOperationParameters, BoundedOperationParameters {
        String name;
        String threadFactory;
        ModelNode properties;
        ScaledCount maxThreads;
        TimeSpec keepAliveTime;
        boolean blocking;
        String handoffExecutor;
        boolean allowCoreTimeout;
        ScaledCount coreThreads;
        ScaledCount queueLength;

        public String getName() {
            return name;
        }

        public String getThreadFactory() {
            return threadFactory;
        }

        public ModelNode getProperties() {
            return properties;
        }

        public ScaledCount getMaxThreads() {
            return maxThreads;
        }

        public TimeSpec getKeepAliveTime() {
            return keepAliveTime;
        }

        public boolean isBlocking() {
            return blocking;
        }

        @Override
        public String getHandoffExecutor() {
            return handoffExecutor;
        }

        @Override
        public boolean isAllowCoreTimeout() {
            return allowCoreTimeout;
        }

        @Override
        public ScaledCount getCoreThreads() {
            return coreThreads;
        }

        @Override
        public ScaledCount getQueueLength() {
            return queueLength;
        }
    }

}
