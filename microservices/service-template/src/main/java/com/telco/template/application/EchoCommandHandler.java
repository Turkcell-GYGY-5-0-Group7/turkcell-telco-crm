package com.telco.template.application;

import com.telco.platform.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

/**
 * Handles {@link EchoCommand}. Registered automatically by starter-mediator. In a real service the
 * command would mutate an aggregate and publish a domain event via the outbox (see reference-service).
 */
@Component
public class EchoCommandHandler implements CommandHandler<EchoCommand, EchoResponse> {

    @Override
    public EchoResponse handle(EchoCommand command) {
        String message = command.message();
        return new EchoResponse(message, message.length());
    }
}
