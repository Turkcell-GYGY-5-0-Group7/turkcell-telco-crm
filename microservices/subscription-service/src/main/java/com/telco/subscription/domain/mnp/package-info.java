/**
 * Mobile Number Portability (MNP) scaffold (FR-16, POST-MVP, DEFERRED).
 *
 * <p>Reserved extension point for number portability. Contains only a state enum
 * ({@link com.telco.subscription.domain.mnp.MnpPortState}) and a state-machine interface
 * ({@link com.telco.subscription.domain.mnp.MnpPortStateMachine}). There is deliberately no
 * implementation, no Spring bean, no command/handler, and no endpoint; nothing in the MVP
 * subscription flow depends on this package. The full port lifecycle is delivered post-MVP.
 */
package com.telco.subscription.domain.mnp;
