/*
 * Copyright 2010-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.retry.backoff;

import static software.amazon.awssdk.utils.NumericUtils.assertIsPositive;

import java.time.Duration;
import java.util.Random;
import software.amazon.awssdk.retry.RetryPolicyContext;

/**
 * Backoff strategy that uses equal jitter for computing the delay before the next retry. An equal jitter
 * backoff strategy will first compute an exponential delay based on the current number of retries, base delay
 * and max delay. The final computed delay before the next retry will keep half of this computed delay plus
 * a random delay computed as a random number between 0 and half of the exponential delay plus one.
 *
 * For example, using a base delay of 100, a max backoff time of 10000 an exponential delay of 400 is computed
 * for a second retry attempt. The final computed delay before the next retry will be half of the computed exponential
 * delay, in this case 200, plus a random number between 0 and 201. Therefore the range for delay would be between
 * 200 and 401.
 *
 * This is in contrast to {@link FullJitterBackoffStrategy} where the final computed delay before the next retry will be
 * between 0 and the computed exponential delay.
 */
public final class EqualJitterBackoffStrategy implements BackoffStrategy {
    private final Duration baseDelay;
    private final Duration maxBackoffTime;
    private final int numRetries;
    private final Random random = new Random();

    public EqualJitterBackoffStrategy(final Duration baseDelay,
                                      final Duration maxBackoffTime,
                                      final int numRetries) {
        this.baseDelay = assertIsPositive(baseDelay, "baseDelay", true);
        this.maxBackoffTime = assertIsPositive(maxBackoffTime, "maxBackoffTime", true);
        this.numRetries = assertIsPositive(numRetries, "numRetries", false);
    }

    @Override
    public Duration computeDelayBeforeNextRetry(RetryPolicyContext context) {
        int ceil = calculateExponentialDelay(context.retriesAttempted(), baseDelay, maxBackoffTime, numRetries);
        return Duration.ofMillis((ceil / 2) + random.nextInt((ceil / 2) + 1));
    }
}
