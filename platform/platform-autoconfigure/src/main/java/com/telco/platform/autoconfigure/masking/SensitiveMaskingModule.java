package com.telco.platform.autoconfigure.masking;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.PiiMasker;
import com.telco.platform.common.masking.Sensitive;

import java.io.IOException;
import java.util.List;

/**
 * Jackson module that masks bean properties annotated with {@link Sensitive} (Layer A of ADR-021).
 *
 * <p>Registered only on the dedicated masking {@code ObjectMapper}. The default application
 * {@code ObjectMapper} does not use this module, so event payloads and API responses keep their
 * real values.
 */
public final class SensitiveMaskingModule extends SimpleModule {

    private final transient char maskChar;
    private final int defaultKeepLast;

    public SensitiveMaskingModule(char maskChar, int defaultKeepLast) {
        super("telco-sensitive-masking");
        this.maskChar = maskChar;
        this.defaultKeepLast = defaultKeepLast;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addBeanSerializerModifier(new MaskingBeanSerializerModifier(maskChar, defaultKeepLast));
    }

    /** Replaces the serializer of every {@link Sensitive}-annotated property with a masking one. */
    private static final class MaskingBeanSerializerModifier extends BeanSerializerModifier {

        private final char maskChar;
        private final int defaultKeepLast;

        MaskingBeanSerializerModifier(char maskChar, int defaultKeepLast) {
            this.maskChar = maskChar;
            this.defaultKeepLast = defaultKeepLast;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                         com.fasterxml.jackson.databind.BeanDescription beanDesc,
                                                         List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                Sensitive sensitive = writer.getAnnotation(Sensitive.class);
                if (sensitive != null) {
                    int keepLast = sensitive.keepLast() < 0 ? defaultKeepLast : sensitive.keepLast();
                    writer.assignSerializer(new MaskingSerializer(sensitive.value(), keepLast, maskChar));
                }
            }
            return beanProperties;
        }
    }

    /** Serializes any value as the masked form of its {@code toString()}. */
    private static final class MaskingSerializer extends JsonSerializer<Object> {

        private final MaskStrategy strategy;
        private final int keepLast;
        private final char maskChar;

        MaskingSerializer(MaskStrategy strategy, int keepLast, char maskChar) {
            this.strategy = strategy;
            this.keepLast = keepLast;
            this.maskChar = maskChar;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeString(PiiMasker.maskValue(value.toString(), strategy, keepLast, maskChar));
        }
    }
}
