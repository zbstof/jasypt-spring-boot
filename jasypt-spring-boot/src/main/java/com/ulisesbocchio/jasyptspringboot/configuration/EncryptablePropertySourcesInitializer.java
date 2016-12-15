package com.ulisesbocchio.jasyptspringboot.configuration;

import com.ulisesbocchio.jasyptspringboot.annotation.EncryptablePropertySource;
import com.ulisesbocchio.jasyptspringboot.annotation.EncryptablePropertySources;
import com.ulisesbocchio.jasyptspringboot.wrapper.EncryptableEnumerablePropertySourceWrapper;
import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.env.PropertySourcesLoader;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.ulisesbocchio.jasyptspringboot.configuration.StringEncryptorConfiguration.ENCRYPTOR_BEAN_PLACEHOLDER;

/**
 * @author Ulises Bocchio
 */
@Configuration
@Import(StringEncryptorConfiguration.class)
public class EncryptablePropertySourcesInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(EncryptablePropertySourcesInitializer.class);

    @Bean
    public static EncryptablePropertySourceAnnotationBeanFactoryPostProcessor encryptablePropertySourceAnnotationPostProcessor() {
        return new EncryptablePropertySourceAnnotationBeanFactoryPostProcessor();
    }

    private static class EncryptablePropertySourceAnnotationBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            ConfigurableEnvironment env = beanFactory.getBean(ConfigurableEnvironment.class);
            ResourceLoader ac = new DefaultResourceLoader();
            StringEncryptor encryptor = beanFactory.getBean(env.resolveRequiredPlaceholders(ENCRYPTOR_BEAN_PLACEHOLDER), StringEncryptor.class);
            MutablePropertySources propertySources = env.getPropertySources();
            List<AnnotationAttributes> source = getBeanDefinitionsForAnnotation(beanFactory, EncryptablePropertySource.class);
            List<AnnotationAttributes> sources = getBeanDefinitionsForAnnotation(beanFactory, EncryptablePropertySources.class);

            List<AnnotationAttributes> encryptablePropertiesMetadata = new ArrayList<AnnotationAttributes>(source);
            for (AnnotationAttributes resource : sources) {
                AnnotationAttributes[] nestedAnnotations = (AnnotationAttributes[]) resource.get("value");
                encryptablePropertiesMetadata.addAll(Arrays.asList(nestedAnnotations));
            }
            for (final AnnotationAttributes metadatum : encryptablePropertiesMetadata) {
                loadEncryptablePropertySource(metadatum, env, ac, encryptor, propertySources);
            }
        }

        private static void loadEncryptablePropertySource(AnnotationAttributes encryptablePropertySource, ConfigurableEnvironment env, ResourceLoader resourceLoader, StringEncryptor encryptor, MutablePropertySources propertySources) throws BeansException {
            try {
                PropertySource ps = createPropertySource(encryptablePropertySource, env, resourceLoader, encryptor);
                if (ps != null) {
                    propertySources.addLast(ps);
                    LOG.info("Created Encryptable Property Source '{}' from locations: {}", ps.getName(), Arrays.asList(encryptablePropertySource.getStringArray("value")));
                } else {
                    LOG.info("Ignoring NOT FOUND Encryptable Property Source '{}' from locations: {}", encryptablePropertySource.getString("name"), Arrays.asList(encryptablePropertySource.getStringArray("value")));
                }
            } catch (Exception e) {
                throw new ApplicationContextException("Exception Creating PropertySource", e);
            }
        }

        private static PropertySource createPropertySource(AnnotationAttributes attributes, ConfigurableEnvironment environment, ResourceLoader resourceLoader, StringEncryptor encryptor) throws Exception {
            String name = attributes.getString("name");
            String[] locations = attributes.getStringArray("value");
            boolean ignoreResourceNotFound = attributes.getBoolean("ignoreResourceNotFound");
            CompositePropertySource compositePropertySource = new CompositePropertySource(generateName(name));
            Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
            for (String location : locations) {
                String resolvedLocation = environment.resolveRequiredPlaceholders(location);
                Resource resource = resourceLoader.getResource(resolvedLocation);
                if (!resource.exists() && !ignoreResourceNotFound) {
                    throw new IllegalStateException("Resource not found: " + location);
                }
                PropertySourcesLoader loader = new PropertySourcesLoader();
                PropertySource propertySource = loader.load(resource, resolvedLocation, null);
                if (propertySource != null) {
                    compositePropertySource.addPropertySource(propertySource);
                }
            }
            return new EncryptableEnumerablePropertySourceWrapper<Object>(compositePropertySource, encryptor);
        }

        private static String generateName(String name) {
            return !StringUtils.isEmpty(name) ? name : "EncryptedPropertySource#" + System.currentTimeMillis();
        }

        private static List<AnnotationAttributes> getBeanDefinitionsForAnnotation(ConfigurableListableBeanFactory bf,
                                                                                  Class<? extends Annotation> annotation) {
            List<AnnotationAttributes> result = new ArrayList<AnnotationAttributes>();

            for (String beanNameForAnnotation : bf.getBeanNamesForAnnotation(annotation)) {
                BeanDefinition beanDefinition = bf.getBeanDefinition(beanNameForAnnotation);
                if (!(beanDefinition instanceof AnnotatedBeanDefinition)) {
                    continue;
                }
                AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDefinition;
                AnnotationMetadata metadata = annotatedBeanDefinition.getMetadata();
                if (!(metadata.hasAnnotation(annotation.getName()))) {
                    continue;
                }
                Map<String, Object> annotationAttributes = metadata.getAnnotationAttributes(annotation.getName());
                result.add((AnnotationAttributes) annotationAttributes);
            }
            return result;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
