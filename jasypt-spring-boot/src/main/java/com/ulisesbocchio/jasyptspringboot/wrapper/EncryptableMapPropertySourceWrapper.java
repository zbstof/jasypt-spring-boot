package com.ulisesbocchio.jasyptspringboot.wrapper;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertySource;
import com.ulisesbocchio.jasyptspringboot.Helper;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * @author Ulises Bocchio
 */
public class EncryptableMapPropertySourceWrapper extends MapPropertySource implements EncryptablePropertySource<Map<String, Object>> {
    private final StringEncryptor encryptor;
    private MapPropertySource delegate;

    public EncryptableMapPropertySourceWrapper(MapPropertySource delegate, StringEncryptor encryptor) {
        super(delegate.getName(), delegate.getSource());
        Assert.notNull(delegate, "PropertySource delegate cannot be null");
        Assert.notNull(encryptor, "StringEncryptor cannot be null");
        this.encryptor = encryptor;
        this.delegate = delegate;
    }

    @Override
    public Object getProperty(String name) {
        return Helper.getProperty(encryptor, delegate, name);
    }
}
