package com.ulisesbocchio.jasyptspringboot.encryptor;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.core.env.Environment;

import static com.ulisesbocchio.jasyptspringboot.Helper.getProperty;
import static com.ulisesbocchio.jasyptspringboot.Helper.getRequiredProperty;

/**
 * String encryptor that delays pulling configuration properties to configure the encryptor until the moment when the
 * first encrypted property is retrieved. Thus allowing for late retrieval of
 * configuration when all property sources have been established, and avoids missing configuration properties errors
 * when no encrypted properties are present in configuration files.
 */
public final class LazyStringEncryptor implements StringEncryptor {

    private Environment environment;

    public LazyStringEncryptor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String encrypt(String message) {
        return configure(environment).encrypt(message);
    }

    @Override
    public String decrypt(String encryptedMessage) {
        return configure(environment).decrypt(encryptedMessage);
    }

    private StringEncryptor configure(final Environment e) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(getRequiredProperty(e, "jasypt.encryptor.password"));
        config.setAlgorithm(getProperty(e, "jasypt.encryptor.algorithm", "PBEWithMD5AndDES"));
        config.setKeyObtentionIterations(getProperty(e, "jasypt.encryptor.keyObtentionIterations", "1000"));
        config.setPoolSize(getProperty(e, "jasypt.encryptor.poolSize", "1"));
        config.setProviderName(getProperty(e, "jasypt.encryptor.providerName", "SunJCE"));
        config.setSaltGeneratorClassName(getProperty(e, "jasypt.encryptor.saltGeneratorClassname", "org.jasypt.salt.RandomSaltGenerator"));
        config.setStringOutputType(getProperty(e, "jasypt.encryptor.stringOutputType", "base64"));
        encryptor.setConfig(config);
        return encryptor;
    }
}
