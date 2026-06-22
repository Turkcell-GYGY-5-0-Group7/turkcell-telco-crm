package com.telco.platform.mediator.behavior;

import com.telco.platform.common.exception.ValidationException;
import com.telco.platform.mediator.pipeline.PipelineBehavior;
import com.telco.platform.mediator.pipeline.PipelineOrder;
import com.telco.platform.mediator.pipeline.RequestHandlerDelegate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates the incoming request with a jakarta-validation {@link Validator}, throwing a
 * {@link ValidationException} (field violations in {@code details}) before the handler runs.
 */
public final class ValidationBehavior implements PipelineBehavior {

    private final Validator validator;

    public ValidationBehavior(Validator validator) {
        this.validator = validator;
    }

    @Override
    public boolean supports(Object request) {
        return request != null;
    }

    @Override
    public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            for (ConstraintViolation<Object> violation : violations) {
                details.put(violation.getPropertyPath().toString(), violation.getMessage());
            }
            throw new ValidationException("Validation failed for " + request.getClass().getSimpleName(), details);
        }
        return next.invoke();
    }

    @Override
    public int order() {
        return PipelineOrder.VALIDATION;
    }
}
