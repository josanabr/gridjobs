/*
 * Copyright 2007 Peter Ledbrook.
 *
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
 */
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.ServiceArtefactHandler
import org.codehaus.groovy.grails.plugins.remoting.DummyHttpExporter
import org.codehaus.groovy.grails.plugins.remoting.InterceptorWrapper
import org.codehaus.groovy.grails.plugins.remoting.RemotingUrlHandlerMapping
import org.codehaus.groovy.grails.plugins.support.GrailsPluginUtils

import org.aopalliance.intercept.MethodInterceptor
import org.springframework.aop.target.HotSwappableTargetSource
import org.springframework.aop.framework.ProxyFactoryBean

class RemotingGrailsPlugin {
    static remoteExporters = [
        'rmi': org.springframework.remoting.rmi.RmiServiceExporter,
        'hessian': org.springframework.remoting.caucho.HessianServiceExporter,
        'burlap': org.springframework.remoting.caucho.BurlapServiceExporter,
        'httpinvoker': org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter ]
    static proxyFactories = [
        'rmi': org.springframework.remoting.rmi.RmiProxyFactoryBean,
        'hessian': org.springframework.remoting.caucho.HessianProxyFactoryBean,
        'burlap': org.springframework.remoting.caucho.BurlapProxyFactoryBean,
        'httpinvoker': org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean ]

    def version = '0.3'
    def author = 'Peter Ledbrook'
    def authorEmail = 'peter@cacoethes.co.uk'
    def title = 'Adds easy-to-use server-side and client-side RPC support.'
    def description = '''\
This plugin makes it easy to expose your Grails services to remote
clients via RMI, Hessian, Burlap and Spring's HttpInvoker protocol.
In addition, you can easily access remote services via the same set
of protocols.
'''
    def documentation = 'http://grails.codehaus.org/Remoting+Plugin'

    def artefacts = [ org.codehaus.groovy.grails.plugins.remoting.InterceptorArtefactHandler ]
    def grailsVersion = GrailsPluginUtils.grailsVersion
    def dependsOn = [ services: grailsVersion ]
    def observe = [ 'services' ]
    def watchedResources = "file:./grails-app/remoting/*RemotingInterceptor.groovy"

    def doWithSpring = {
        def interceptorMap = [:]
        if (application.remotingInterceptorClasses) {
            application.remotingInterceptorClasses.each { interceptorClass ->
                log.info "Registering remoting interceptor: ${interceptorClass.fullName}"

                // What next? Get the pointcut patterns from the interceptor,
                // split each one into class and method patterns, convert the
                // patterns to regular expressions, and add to map of class
                // patterns -> interceptors.

                // Register the user-defined interceptor.
                "${interceptorClass.propertyName}"(interceptorClass.clazz)

                // Find out which classes/methods the interceptor applies
                // to.
                def pointcuts = GrailsClassUtils.getStaticPropertyValue(interceptorClass.clazz, 'pointcuts')
                pointcuts.each {
                    def pos = it.lastIndexOf('.')
                    if (pos > 0) {
                        def classPattern = it.substring(0, pos)
                        def methodPattern = it.substring(pos + 1)

                        // Configure an interceptor bean that can be used
                        // on proxies.
                        configureInterceptor.delegate = delegate
                        def beanName = configureInterceptor.call(methodPattern, interceptorClass.propertyName)

                        // Save the bean name for the interceptor against
                        // the class pattern.
                        def interceptorBeans = interceptorMap[classPattern]
                        if (!interceptorBeans) {
                            interceptorBeans = []
                            interceptorMap[classPattern] = interceptorBeans
                        }

                        interceptorBeans << "${beanName}Proxy".toString()
                    }
                    else {
                        log.error "Invalid pointcut expression: ${it}"
                    }
                }
            }
        }

        if (application.serviceClasses) {
            // A map of invoker types to services.
            def invokerMap = [:]

            // Iterate through each of the declared services and
            // configure them for remoting.
            configureNewService.delegate = delegate
            application.serviceClasses.each { serviceWrapper ->
                configureNewService.call(serviceWrapper, interceptorMap)
            }

            // Finally add the custom HandlerMapping.
            remotingUrlHandlerMapping(RemotingUrlHandlerMapping) {
                invokerTypes = remoteExporters.keySet()
            }
        }
    }

    def doWithWebDescriptor = { xml ->
        // Set up servlets for each of the exporter types.
        remoteExporters.each { key, value ->
            // Add a servlet definition for this invoker type.
            xml.servlet[xml.servlet.size() - 1] + {
                servlet {
                    'servlet-name'("${key}")
                    'servlet-class'('org.springframework.web.servlet.DispatcherServlet')
                    'init-param'() {
                        'param-name'('contextClass')
                        'param-value'('org.springframework.web.context.support.StaticWebApplicationContext')
                    }
                    'load-on-startup'(1)
                }
            }

            xml.'servlet-mapping'[xml.'servlet-mapping'.size() - 1] + {
                'servlet-mapping' {
                    'servlet-name'("${key}")
                    'url-pattern'("/${key}/*")
                }
            }
        }
    }

    def onChange = { event ->
        def context = event.ctx
        if (!context) {
            if (log.debugEnabled) log.debug("Application context not found - can't reload.")
            return
        }

        boolean isNew = event.application.getServiceClass(event.source?.name) == null
        def serviceClass = application.addArtefact(ServiceArtefactHandler.TYPE, event.source)

        if (isNew) {
            log.info "Service ${event.source} added. Configuring..."

            // Expose the new service.
            try {
                def beanDefinitions = beans(configureNewService.curry(serviceClass, [:]))
                beanDefinitions.registerBeans(context)
            }
            catch (MissingMethodException ex) {
                // This version of Grails does not support this.
                log.warn("Unable to register beans (Grails version < 0.5.5)")
            }
        }
        else {
            if (log.debugEnabled) log.debug("Service ${event.source} changed. Reloading...")

            // Is this a client proxy or an exporter?
            def remoteDef = GrailsClassUtils.getStaticPropertyValue(serviceClass.clazz, 'remote')
            if (remoteDef) {
                // Client proxy. Register new beans for it.
                def beanName = serviceClass.propertyName
                def beanDefinitions = beans {
                    configureClientProxy(log, delegate, remoteDef, beanName, serviceClass.shortName)
                }

                // Replace the old target (a proxy factory bean) with the
                // new one.
                // Note: removeBeanDefinition() not supported by 0.6!
//                event.ctx.removeBeanDefinition("${beanName}Target")
                context.registerBeanDefinition(
                        beanName,
                        beanDefinitions.getBeanDefinition(beanName))

                // Skip the exporter definition.
                return
            }

            // Check whether any protocols have been added to or removed
            // from this service's expose list. The expose list may have
            // been removed completely!
            def exposeList = GrailsClassUtils.getStaticPropertyValue(event.source, 'expose')
            def exposedName = serviceClass.shortName
            def newBeans = []
            def beanDefinitions = beans {
                exposeProtocol.delegate = delegate

                remoteExporters.each { type, clazz ->
                    // First check whether the service is already exposed
                    // via this protocol.
                    def exporterBeanName = "${type}.${exposedName}"
                    if (context.containsBean(exporterBeanName) &&
                            !(context.getBean(exporterBeanName) instanceof DummyHttpExporter)) {
                        // The service is currently exposed via 'type'. Is
                        // this still in the expose list? If not, then we
                        // need to unregister the exporter.
                        if (!exposeList.contains(type)) {
                            log.info "Removing exporter '${exporterBeanName}'"

                            // To unregister the RMI exporter we have to
                            // call its 'destroy' method.
                            if (type.equals('rmi')) {
                                context.getBean(exporterBeanName).destroy()
                            }

                            if (grailsVersion.startsWith('1.0')){
                                // Remove the exporter bean from the context.
                                context.removeBeanDefinition(exporterBeanName)
                            }
                            else{
                                // Replace the exporter bean with a dummy object.
                                "${exporterBeanName}"(DummyHttpExporter)
                                newBeans << exporterBeanName
                            }
                        }
                    }
                    else if (exposeList.contains(type)) {
                        // A new protocol has been added to the expose list.
                        exposeProtocol.call(type, exposedName, event.source.interfaces[0])
                        newBeans << exporterBeanName
                    }
                }
            }

            // Register any new bean definitions.
            newBeans.each { beanName ->
                context.registerBeanDefinition(beanName, beanDefinitions.getBeanDefinition(beanName))

                if (beanName.startsWith('rmi')) {
                    // Force the RMI exporter to bind this service to
                    // the registry.
                    context.getBean(beanName)
                }
            }

            // Make the modified service the target of all the exporters
            // by swapping it in to the target source.
            def target = context.getBean("${serviceClass.shortName}TargetSource")
            target.swap(context.getBean(serviceClass.propertyName))
        }
    }

    /**
     * Configures all the spring beans that are required to expose
     * a new service via its specified remote protocols.
     * @param serviceWrapper The GrailsClass instance for the new
     * service.
     */
    def configureNewService = { serviceWrapper, interceptorMap -> 
        if (log.debugEnabled) log.debug "Configuring new service: ${serviceWrapper.shortName}"

        // If this service has a static 'remote' property, then it is
        // acting as a proxy for a remote service.
        def serviceClass = serviceWrapper.clazz
        def exposedName = serviceWrapper.shortName
        def remoteDef = GrailsClassUtils.getStaticPropertyValue(serviceClass, 'remote')
        if (remoteDef) {
            // Create a proxy for the configured remote service.
            configureClientProxy(log, delegate, remoteDef, serviceWrapper.propertyName, exposedName)
        }
        else {
            // OK, the service isn't configured as a proxy to a remote
            // service, so check whether it should be exposed as a
            // remote exporter by looking for a static 'expose' property.
            def exposeList = GrailsClassUtils.getStaticPropertyValue(serviceClass, 'expose')

            if (!exposeList || exposeList.size() == 0) {
                // This service has not been configured for remoting,
                // so skip it.
                return
            }

            // Check that the service has an interface to expose.
            if (serviceClass.interfaces.size() == 0 || serviceClass.interfaces[0] == GroovyObject) {
                log.error("Cannot expose service '${exposedName}' via remoting: service does not implement any interfaces.")
                return
            }

            // Set up Spring invokers for each type specified in
            // the 'expose' list.
            def exposedInterface = serviceClass.interfaces[0]
            if (exposeList && exposeList.size() > 0) {
                // Create a proxy to the service so that we can hot-swap
                // changes in.
                configureProxy.delegate = delegate
                configureProxy.call(serviceWrapper, exposedInterface, interceptorMap)
            }

            exposeProtocol.delegate = delegate
            exposeList?.each { type ->
                exposeProtocol.call(type, exposedName, exposedInterface)
            }
        }
    }

    /**
     * Configures a proxy bean for the given service class that allows
     * us to update the underlying service without forcing a server
     * restart.
     * @param serviceClass The GrailsClass instance for the service we
     * want to proxy.
     * @param iface The interface (instance of Class) that the service
     * will expose to the outside well.
     */
    def configureProxy = { serviceClass, iface, interceptorMap ->
        if (log.debugEnabled) log.debug "Configuring proxy for: ${serviceClass.shortName}"

        // Find out which interceptors should be added.
        def matchingInterceptors = []
        interceptorMap.each { classPattern, interceptorBeans ->
            if (serviceClass.fullName.matches(classPattern)) {
                matchingInterceptors.addAll(interceptorBeans)
            }
        }

        def exposedName = serviceClass.shortName
        "${exposedName}TargetSource"(HotSwappableTargetSource, ref(serviceClass.propertyName))

        "${exposedName}Proxy"(ProxyFactoryBean) {
            targetSource = ref("${exposedName}TargetSource")
            proxyInterfaces = [iface]

            if (!matchingInterceptors.isEmpty()) {
                interceptorNames = matchingInterceptors
            }
        }
    }

    /**
     * Configures an exporter bean for a named service that allows
     * remote clients to access the service via the specified protocol.
     * @param protocol The remote protocol to expose the service via.
     * @param exposedName The name of the service - this is used to
     * reference the appropriate proxy bean.
     * @param iface The interface (instance of Class) that the service
     * exposes to remote clients.
     */
    def exposeProtocol = { protocol, exposedName, iface ->
        if (log.debugEnabled) log.debug "Exposing protocol '${protocol}' for: ${exposedName}"

        if (remoteExporters.containsKey(protocol)) {
            if (log.infoEnabled) {
                log.info("Adding remote bean '${protocol}.${exposedName}' (class=${remoteExporters[protocol]})")
            }

            // Create the exporter bean.
            "${protocol}.${exposedName}"(remoteExporters[protocol]) {
                // The RMI exporter requires a service name in
                // addition to the standard service bean and
                // interface. We also use a non-standard port
                // to ensure that the service does not conflict
                // with an RMI registry on the server.
                if (protocol == 'rmi') {
                    serviceName = exposedName
                    registryPort = 1199
                }

                // The exporter references a proxy to the service
                // implementation rather than the service itself.
                service = ref("${exposedName}Proxy")
                serviceInterface = iface
            }
        }
        else {
            log.info("Unrecognised invoker protocol: ${protocol} - ignoring for this service ('${exposedName}').")
        }
    }

    def configureClientProxy(log, bb, config, beanName, exposedName) {
        // Create a remote proxy for the service, using the config
        // provided by the 'remote' property.
        if (!(config instanceof Map)) {
            log.error("Invalid value for 'remote' property in service '${exposedName}' - must be a map.")
            return
        }

        // Check that the service has an interface to proxy.
        if (!config['iface']) {
            log.error("Cannot access service '${exposedName}' via remoting: service does not specify an interface.")
            return
        }

        // Check a protocol has been specified.
        def protocol = config['protocol']
        if (!protocol) {
            log.error("Cannot access service '${exposedName}' via remoting: no protocol specified.")
            return
        }

        // Build the URL of the remote service if one hasn't been
        // specified.
        def host = config['host']
        if (!host) host = 'localhost'

        if (!config['url']) {
            switch (protocol) {
            case 'rmi':
                def port = config['port']
                if (!port) port = 1199
                config['url'] = "rmi://${host}:${port}/${exposedName}".toString()
                break

            case 'httpinvoker':
            case 'hessian':
            case 'burlap':
                def port = config['port']
                if (!port) port = 8080

                def context = config['webcontext']
                if (context) context += '/'

                config['url'] = "http://${host}:${port}/${context}${protocol}/${exposedName}".toString()
                break
            }
        }

        // Now create the proxy client.
        log.info("Creating proxy for '${beanName}'")
        bb."${beanName}"(proxyFactories[protocol]) { bean ->
            bean.autowire = 'byName'
            bb.serviceUrl = config['url']
            bb.serviceInterface = config['iface']
        }
    }

    def configureInterceptor = { methodPattern, interceptorName ->
        // Create a wrapper for the given interceptor.
        def wrapperName = "${interceptorName}Wrapper_${methodPattern}".toString()

        "${wrapperName}"(InterceptorWrapper) {
            interceptor = ref(interceptorName)
            pattern = methodPattern.replaceAll(/\*/, '.*')
        }

        // Hot-swapping for the interceptor.
        "${wrapperName}TargetSource"(HotSwappableTargetSource, ref(wrapperName))

        "${wrapperName}Proxy"(ProxyFactoryBean) {
            targetSource = ref("${wrapperName}TargetSource")
            proxyInterfaces = [ MethodInterceptor ]
        }

        return wrapperName
    }
}
