/*
 * #%L
 * Wildfly Camel :: Subsystem
 * %%
 * Copyright (C) 2013 - 2014 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.jboss.fuse.wsdl2rest.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.handler.CamelNamespaceHandler;
import org.springframework.beans.factory.xml.NamespaceHandler;
import org.springframework.beans.factory.xml.NamespaceHandlerResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

/**
 * A {@link CamelContext} factory utility.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 19-Apr-2013
 */
public final class SpringCamelContextFactory {

    // Hide ctor
    private SpringCamelContextFactory() {
    }

    /**
     * Create a single {@link SpringCamelContext} from the given URL
     * @throws IllegalStateException if the given URL does not contain a single context definition
     */
    public static SpringCamelContext createSingleCamelContext(URL contextUrl, ClassLoader classsLoader) throws Exception {
        List<SpringCamelContext> list = createCamelContextList(new UrlResource(contextUrl), classsLoader);
        IllegalStateAssertion.assertEquals(1, list.size(), "Single context expected in: " + contextUrl);
        return list.get(0);
    }

    /**
     * Create a {@link SpringCamelContext} list from the given URL
     */
    public static List<SpringCamelContext> createCamelContextList(URL contextUrl, ClassLoader classsLoader) throws Exception {
        return createCamelContextList(new UrlResource(contextUrl), classsLoader);
    }

    /**
     * Create a {@link SpringCamelContext} list from the given bytes
     */
    public static List<SpringCamelContext> createCamelContextList(byte[] bytes, ClassLoader classsLoader) throws Exception {
        return createCamelContextList(new ByteArrayResource(bytes), classsLoader);
    }

    private static List<SpringCamelContext> createCamelContextList(Resource resource, ClassLoader classLoader) throws Exception {
        
        if (classLoader == null) 
            classLoader = SpringCamelContextFactory.class.getClassLoader();
        
        GenericApplicationContext appContext = new GenericApplicationContext();
        appContext.setClassLoader(classLoader);
        
        XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(appContext) {
            @Override
            protected NamespaceHandlerResolver createDefaultNamespaceHandlerResolver() {
                NamespaceHandlerResolver defaultResolver = super.createDefaultNamespaceHandlerResolver();
                return new CamelNamespaceHandlerResolver(defaultResolver);
            }
        };
        xmlReader.loadBeanDefinitions(resource);

        SpringCamelContext.setNoStart(true);
        ProxyUtils.invokeProxied(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                appContext.refresh();
                return null;
            }
        }, classLoader);
        SpringCamelContext.setNoStart(false);

        List<SpringCamelContext> result = new ArrayList<>();
        for (String name : appContext.getBeanNamesForType(SpringCamelContext.class)) {
            result.add(appContext.getBean(name, SpringCamelContext.class));
        }

        return Collections.unmodifiableList(result);
    }

    private static class CamelNamespaceHandlerResolver implements NamespaceHandlerResolver {

        private final NamespaceHandlerResolver delegate;
        private final NamespaceHandler camelHandler;

        CamelNamespaceHandlerResolver(NamespaceHandlerResolver delegate) {
            this.delegate = delegate;
            this.camelHandler = new CamelNamespaceHandler();
            this.camelHandler.init();
        }

        @Override
        public NamespaceHandler resolve(String namespaceUri) {
            if ("http://camel.apache.org/schema/spring".equals(namespaceUri)) {
                return camelHandler;
            } else {
                return delegate.resolve(namespaceUri);
            }
        }
    }

    /**
     * A utility class to run arbitrary code via a {@link Proxy} instance.
     */
    private static class ProxyUtils {

        private ProxyUtils() {
            // Hide ctor
        }

        /**
         * Runs a {@link Callable} within a {@link Proxy} instance. See the following issues for information
         * around its primary use case.
         *
         * https://issues.jboss.org/browse/ENTESB-7117
         * https://github.com/wildfly-extras/wildfly-camel/issues/1919
         *
         * @param callable A callable instance to invoke within a {@link Proxy} instance
         * @param classLoader The ClassLoader used to create the {@link Proxy} instance
         * @throws Exception
         */
        public static void invokeProxied(final Callable<?> callable, final ClassLoader classLoader) throws Exception {
            Callable<?> callableProxy = (Callable<?>) Proxy.newProxyInstance(classLoader, new Class<?>[] { Callable.class }, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    callable.call();
                    return null;
                }
            });
            callableProxy.call();
        }
    }}
