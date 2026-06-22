package com.telco.platform.autoconfigure.masking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.common.masking.MaskStrategy;
import com.telco.platform.common.masking.Sensitive;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingObjectMapperTest {

    private final ObjectMapper mapper =
            new MaskingAutoConfiguration().maskingObjectMapper(new MaskingProperties());

    record Customer(@Sensitive String tckn,
                    @Sensitive(MaskStrategy.EMAIL) String email,
                    String displayName) {
    }

    @Test
    void masksSensitiveFieldsAndKeepsOthers() throws Exception {
        String json = mapper.writeValueAsString(new Customer("12345678901", "john@telco.com", "John"));

        assertThat(json).contains("\"tckn\":\"*******8901\"");
        assertThat(json).contains("\"email\":\"j***@***.com\"");
        assertThat(json).contains("\"displayName\":\"John\"");
        assertThat(json).doesNotContain("12345678901");
    }
}
