package com.telco.ticket.application.command;

import com.telco.platform.cqrs.Command;

public record DetectSlaBreachCommand() implements Command<Integer> {}
