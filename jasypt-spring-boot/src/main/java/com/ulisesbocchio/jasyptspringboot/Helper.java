package com.ulisesbocchio.jasyptspringboot;

import com.ulisesbocchio.jasyptspringboot.exception.DecryptionException;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.jasypt.properties.PropertyValueEncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

public class Helper {

    private static final Logger LOG = LoggerFactory.getLogger(Helper.class);

    public static Object getProperty(StringEncryptor encryptor, PropertySource source, String name) {
        Object value = source.getProperty(name);
        if(value instanceof String) {
            String stringValue = String.valueOf(value);
            if(PropertyValueEncryptionUtils.isEncryptedValue(stringValue)) {
                try {
                    value = PropertyValueEncryptionUtils.decrypt(stringValue, encryptor);
                } catch (EncryptionOperationNotPossibleException e) {
                    throw new DecryptionException("Decryption of Properties failed,  make sure encryption/decryption " +
                            "passwords match", e);
                }
            }
        }
        return value;
    }

    public static String getProperty(Environment environment, String key, String defaultValue) {
        if (!propertyExists(environment, key)) {
            LOG.info("Encryptor config not found for property {}, using default value: {}", key, defaultValue);
        }
        return environment.getProperty(key, defaultValue);
    }

    private static boolean propertyExists(Environment environment, String key) {
        return environment.getProperty(key) != null;
    }

    public static String getRequiredProperty(Environment environment, String key) {
        if (!propertyExists(environment, key)) {
            throw new IllegalStateException(String.format("Required Encryption configuration property missing: %s", key));
        }
        return environment.getProperty(key);
    }
}
