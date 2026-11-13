// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Active le support des annotations de resilience de Spring Framework 7 (@Retryable, @ConcurrencyLimit).
 */
@Configuration
@EnableResilientMethods
public class RetryConfiguration {
}
