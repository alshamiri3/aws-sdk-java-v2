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

package software.amazon.awssdk.core.config;

import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.http.SdkHttpClient;

/**
 * An interface that represents configuration only required by a sync AWS client in order to operate. Sync AWS clients accept
 * implementations of this interface when constructed.
 *
 * <p>Implementations of this interface are not necessarily immutable or thread safe. If thread safety is required, consider
 * creating an immutable representation with {@link ImmutableSyncClientConfiguration}.</p>
 */
@SdkInternalApi
public interface SyncClientConfiguration extends ClientConfiguration {

    /**
     * The HTTP client the SDK will use to make HTTP requests.
     */
    SdkHttpClient httpClient();
}
