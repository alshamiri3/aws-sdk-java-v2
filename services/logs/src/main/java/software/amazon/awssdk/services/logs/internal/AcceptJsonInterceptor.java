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

package software.amazon.awssdk.services.logs.internal;

import software.amazon.awssdk.annotation.ReviewBeforeRelease;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.interceptor.Context;
import software.amazon.awssdk.interceptor.ExecutionAttributes;
import software.amazon.awssdk.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.interceptor.Priority;

@ReviewBeforeRelease("It's unclear if we need this, service seems to work fine without it.")
public final class AcceptJsonInterceptor implements ExecutionInterceptor {
    @Override
    public Priority priority() {
        return Priority.SERVICE;
    }

    @Override
    public SdkHttpFullRequest modifyHttpRequest(Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
        return context.httpRequest()
                      .toBuilder()
                      .header("Accept", "application/json")
                      .build();
    }
}
