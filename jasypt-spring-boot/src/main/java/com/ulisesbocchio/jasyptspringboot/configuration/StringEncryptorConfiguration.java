package com.ulisesbocchio.jasyptspringboot.configuration;

import com.ulisesbocchio.jasyptspringboot.encryptor.LazyStringEncryptor;
import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.regex.Pattern;

/**
 * @author Ulises Bocchio
 */
@Configuration
public class StringEncryptorConfiguration {

    public static final String ENCRYPTOR_BEAN_PLACEHOLDER = "${jasypt.encryptor.bean:jasyptStringEncryptor}";

    private static final Logger LOG = LoggerFactory.getLogger(StringEncryptorConfiguration.class);

    @Conditional(OnMissingEncryptorBean.class)
    @Bean
    public static BeanNamePlaceholderRegistryPostProcessor beanNamePlaceholderRegistryPostProcessor(Environment environment) {
        return new BeanNamePlaceholderRegistryPostProcessor(environment);
    }

    @Conditional(OnMissingEncryptorBean.class)
    @Bean(name = ENCRYPTOR_BEAN_PLACEHOLDER)
    public StringEncryptor stringEncryptor(Environment environment) {
        String encryptorBeanName = environment.resolveRequiredPlaceholders(ENCRYPTOR_BEAN_PLACEHOLDER);
        LOG.info("String Encryptor custom Bean not found with name '{}'. Initializing String Encryptor based on properties with name '{}'",
                encryptorBeanName, encryptorBeanName);
        return new LazyStringEncryptor(environment);
    }

    /**
     * Condition that checks whether the StringEncryptor specified by placeholder: {@link #ENCRYPTOR_BEAN_PLACEHOLDER} exists.
     * ConditionalOnMissingBean does not support placeholder resolution.
     */
    private static class OnMissingEncryptorBean implements ConfigurationCondition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !context.getBeanFactory().containsBean(context.getEnvironment().resolveRequiredPlaceholders(ENCRYPTOR_BEAN_PLACEHOLDER));
        }

        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.REGISTER_BEAN;
        }
    }

    /**
     * Bean Definition Registry Post Processor that looks for placeholders in bean names and resolves them, re-defining those beans
     * with the new names.
     */
    private static class BeanNamePlaceholderRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, Ordered {

        // Look for beans with placeholders name format: '${placeholder}' or '${placeholder:defaultValue}'
        private final String regex = "\\$\\{[\\w.-]+(?>:[\\w.-]+)?}";
        private final Pattern pattern = Pattern.compile(regex);
        private Environment environment;

        private BeanNamePlaceholderRegistryPostProcessor(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) registry;
            for (final String beanName : beanFactory.getBeanDefinitionNames()) {
                if (pattern.matcher(beanName).matches()) {
                    String actualName = environment.resolveRequiredPlaceholders(beanName);
                    BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                    beanFactory.removeBeanDefinition(beanName);
                    beanFactory.registerBeanDefinition(actualName, bd);
                    LOG.debug("Registering new name '{}' for Bean definition with placeholder name: {}", actualName, beanName);
                }
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE - 1;
        }
    }
}
