package com.telco.platform.mediator.behavior;

import com.telco.platform.common.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import jakarta.validation.metadata.BeanDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationBehaviorTest {

    @Test
    void throwsValidationExceptionWithViolationDetails() {
        Validator validator = new FakeValidator(Set.of(violation("name", "must not be blank")));
        ValidationBehavior behavior = new ValidationBehavior(validator);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> behavior.handle("request", () -> "should-not-run"));

        assertTrue(ex.details().containsKey("name"));
        assertEquals("must not be blank", ex.details().get("name"));
    }

    @Test
    void proceedsWhenNoViolations() {
        Validator validator = new FakeValidator(Set.of());
        ValidationBehavior behavior = new ValidationBehavior(validator);
        AtomicBoolean ran = new AtomicBoolean();

        String result = behavior.handle("request", () -> {
            ran.set(true);
            return "ok";
        });

        assertEquals("ok", result);
        assertTrue(ran.get());
    }

    private static ConstraintViolation<Object> violation(String path, String message) {
        return new ConstraintViolation<>() {
            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public String getMessageTemplate() {
                return message;
            }

            @Override
            public Object getRootBean() {
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<Object> getRootBeanClass() {
                return (Class<Object>) (Class<?>) Object.class;
            }

            @Override
            public Object getLeafBean() {
                return null;
            }

            @Override
            public Object[] getExecutableParameters() {
                return new Object[0];
            }

            @Override
            public Object getExecutableReturnValue() {
                return null;
            }

            @Override
            public Path getPropertyPath() {
                return new SimplePath(path);
            }

            @Override
            public Object getInvalidValue() {
                return null;
            }

            @Override
            public jakarta.validation.metadata.ConstraintDescriptor<?> getConstraintDescriptor() {
                return null;
            }

            @Override
            public <U> U unwrap(Class<U> type) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** Minimal Validator that returns a fixed violation set. */
    private record FakeValidator(Set<ConstraintViolation<Object>> violations) implements Validator {
        @Override
        @SuppressWarnings("unchecked")
        public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
            return (Set<ConstraintViolation<T>>) (Set<?>) violations;
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName, Class<?>... groups) {
            return Set.of();
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateValue(Class<T> beanType, String propertyName, Object value, Class<?>... groups) {
            return Set.of();
        }

        @Override
        public BeanDescriptor getConstraintsForClass(Class<?> clazz) {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.validation.executable.ExecutableValidator forExecutables() {
            throw new UnsupportedOperationException();
        }
    }

    /** Single-node property path. */
    private record SimplePath(String name) implements Path {
        @Override
        public java.util.Iterator<Node> iterator() {
            return java.util.Collections.emptyIterator();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
