package com.ulisesbocchio.jasyptspringboot.environment;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertySource;
import com.ulisesbocchio.jasyptspringboot.InterceptionMode;
import com.ulisesbocchio.jasyptspringboot.encryptor.LazyStringEncryptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.*;

import java.util.Map;

import static com.ulisesbocchio.jasyptspringboot.EncryptablePropertySourceConverter.instantiatePropertySource;
import static com.ulisesbocchio.jasyptspringboot.EncryptablePropertySourceConverter.proxyPropertySource;

/**
 * @author Ulises Bocchio
 */
public class EncryptableEnvironmentProxy implements ConfigurableEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(EncryptableEnvironmentProxy.class);
    private final ConfigurableEnvironment delegate;
    private final ConfigurablePropertyResolver propertyResolver;
    private MutablePropertySources propertySources;

    public EncryptableEnvironmentProxy(ConfigurableEnvironment delegate) {
        this(delegate, discoverEncryptor(delegate));
    }

    public EncryptableEnvironmentProxy(ConfigurableEnvironment delegate, StringEncryptor encryptor) {
        super();
        this.delegate = delegate;
        propertySources = makeEncryptable(delegate.getPropertySources(), delegate, encryptor);
        propertyResolver = new PropertySourcesPropertyResolver(propertySources);
    }

    private static StringEncryptor discoverEncryptor(ConfigurableEnvironment delegate) {
        return new LazyStringEncryptor(delegate);
    }

    private MutablePropertySources makeEncryptable(MutablePropertySources propertySources, Environment environment, StringEncryptor encryptor) {
        for (final PropertySource<?> propertySource : propertySources) {
            if (!(propertySource instanceof EncryptablePropertySource)) {
                PropertySource<?> encryptable = makeEncryptable(propertySource, environment, encryptor);
                propertySources.replace(encryptable.getName(), encryptable);
            }
        }
        return proxy(propertySources, environment, encryptor);
    }

    private MutablePropertySources proxy(MutablePropertySources propertySources, final Environment environment, final StringEncryptor encryptor) {
        final ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTargetClass(MutablePropertySources.class);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.setTarget(propertySources);
        proxyFactory.addAdvice(new EncryptableMutablePropertySourcesInterceptor(new ConverterFunction() {
            @Override
            public PropertySource apply(final PropertySource source) {
                return makeEncryptable(source, environment, encryptor);
            }
        }));
        return (MutablePropertySources) proxyFactory.getProxy();
    }

    private <T> PropertySource<T> makeEncryptable(PropertySource<T> propertySource, Environment environment, StringEncryptor encryptor) {
        PropertySource<T> encryptablePropertySource = getInterceptionMode(environment) == InterceptionMode.PROXY
                ? proxyPropertySource(propertySource, encryptor) : instantiatePropertySource(propertySource, encryptor);
        LOG.info("Converting PropertySource {} [{}] to {}", propertySource.getName(), propertySource.getClass().getName(),
                AopUtils.isAopProxy(encryptablePropertySource) ? "AOP Proxy" : encryptablePropertySource.getClass().getSimpleName());
        return encryptablePropertySource;
    }

    private InterceptionMode getInterceptionMode(Environment environment) {
        return environment.getProperty("jasypt.encryptor.proxyPropertySources", Boolean.TYPE, false) ? InterceptionMode.PROXY : InterceptionMode.WRAPPER;
    }

    @Override
    public void addActiveProfile(String profile) {
        delegate.addActiveProfile(profile);
    }

    @Override
    public MutablePropertySources getPropertySources() {
        return propertySources;
    }

    @Override
    public Map<String, Object> getSystemEnvironment() {
        return delegate.getSystemEnvironment();
    }

    @Override
    public Map<String, Object> getSystemProperties() {
        return delegate.getSystemProperties();
    }

    @Override
    public void merge(ConfigurableEnvironment parent) {
        delegate.merge(parent);
    }

    @Override
    public ConfigurableConversionService getConversionService() {
        return propertyResolver.getConversionService();
    }

    @Override
    public void setConversionService(ConfigurableConversionService conversionService) {
        propertyResolver.setConversionService(conversionService);
    }

    @Override
    public void setPlaceholderPrefix(String placeholderPrefix) {
        propertyResolver.setPlaceholderPrefix(placeholderPrefix);
    }

    @Override
    public void setPlaceholderSuffix(String placeholderSuffix) {
        propertyResolver.setPlaceholderSuffix(placeholderSuffix);
    }

    @Override
    public void setValueSeparator(String valueSeparator) {
        propertyResolver.setValueSeparator(valueSeparator);
    }

    @Override
    public void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders) {
        propertyResolver.setIgnoreUnresolvableNestedPlaceholders(ignoreUnresolvableNestedPlaceholders);
    }

    @Override
    public void setRequiredProperties(String... requiredProperties) {
        propertyResolver.setRequiredProperties(requiredProperties);
    }

    @Override
    public void validateRequiredProperties() throws MissingRequiredPropertiesException {
        propertyResolver.validateRequiredProperties();
    }

    @Override
    public String[] getActiveProfiles() {
        return delegate.getActiveProfiles();
    }

    @Override
    public void setActiveProfiles(String... profiles) {
        delegate.setActiveProfiles(profiles);
    }

    @Override
    public String[] getDefaultProfiles() {
        return delegate.getDefaultProfiles();
    }

    @Override
    public void setDefaultProfiles(String... profiles) {
        delegate.setDefaultProfiles(profiles);
    }

    @Override
    public boolean acceptsProfiles(String... profiles) {
        return delegate.acceptsProfiles(profiles);
    }

    @Override
    public boolean containsProperty(String key) {
        return propertyResolver.containsProperty(key);
    }

    @Override
    public String getProperty(String key) {
        return propertyResolver.getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return propertyResolver.getProperty(key, defaultValue);
    }

    @Override
    public <T> T getProperty(String key, Class<T> targetType) {
        return propertyResolver.getProperty(key, targetType);
    }

    @Override
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        return propertyResolver.getProperty(key, targetType, defaultValue);
    }

    @Override
    public <T> Class<T> getPropertyAsClass(String key, Class<T> targetType) {
        return propertyResolver.getPropertyAsClass(key, targetType);
    }

    @Override
    public String getRequiredProperty(String key) throws IllegalStateException {
        return propertyResolver.getRequiredProperty(key);
    }

    @Override
    public <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        return propertyResolver.getRequiredProperty(key, targetType);
    }

    @Override
    public String resolvePlaceholders(String text) {
        return propertyResolver.resolvePlaceholders(text);
    }

    @Override
    public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
        return propertyResolver.resolveRequiredPlaceholders(text);
    }

    private interface ConverterFunction<T extends PropertySource<?>> {
        PropertySource<T> apply(PropertySource<T> source);
    }

    /**
     * @author Ulises Bocchio
     */
    public static class EncryptableMutablePropertySourcesInterceptor implements MethodInterceptor {

        private ConverterFunction converter;

        EncryptableMutablePropertySourcesInterceptor(ConverterFunction converter) {
            this.converter = converter;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            String method = invocation.getMethod().getName();
            Object[] arguments = invocation.getArguments();
            if (method.equals("addFirst")) {
                return invocation.getMethod().invoke(invocation.getThis(), makeEncryptable(arguments[0]));
            } else if (method.equals("addLast")) {
                return invocation.getMethod().invoke(invocation.getThis(), makeEncryptable(arguments[0]));
            } else if (method.equals("addBefore")) {
                return invocation.getMethod().invoke(invocation.getThis(), arguments[0], makeEncryptable(arguments[1]));
            } else if (method.equals("addAfter")) {
                return invocation.getMethod().invoke(invocation.getThis(), arguments[0], makeEncryptable(arguments[1]));
            } else if (method.equals("replace")) {
                return invocation.getMethod().invoke(invocation.getThis(), arguments[0], makeEncryptable(arguments[1]));
            } else {
                return invocation.proceed();
            }

        }

        private Object makeEncryptable(Object argument) {
            return converter.apply((PropertySource<?>) argument);
        }
    }
}
