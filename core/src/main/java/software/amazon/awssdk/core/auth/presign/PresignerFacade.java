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

package software.amazon.awssdk.core.auth.presign;

import static software.amazon.awssdk.utils.FunctionalUtils.invokeSafely;

import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.annotations.Immutable;
import software.amazon.awssdk.annotations.ReviewBeforeRelease;
import software.amazon.awssdk.annotations.SdkProtectedApi;
import software.amazon.awssdk.core.RequestConfig;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.auth.AwsCredentialsProvider;
import software.amazon.awssdk.core.auth.Presigner;
import software.amazon.awssdk.core.interceptor.AwsExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.InterceptorContext;
import software.amazon.awssdk.core.runtime.auth.SignerProvider;
import software.amazon.awssdk.core.runtime.auth.SignerProviderContext;
import software.amazon.awssdk.core.util.CredentialUtils;
import software.amazon.awssdk.http.SdkHttpFullRequest;

/**
 * Really thin facade over {@link Presigner} to deal with some common concerns like credential resolution, adding custom headers
 * and query params to be included in signing, and conversion to a usable URL.
 */
@Immutable
@SdkProtectedApi
public final class PresignerFacade {

    private final AwsCredentialsProvider credentialsProvider;
    private final SignerProvider signerProvider;

    public PresignerFacade(PresignerParams presignerParams) {
        this.credentialsProvider = presignerParams.credentialsProvider();
        this.signerProvider = presignerParams.signerProvider();
    }

    @ReviewBeforeRelease("Can this be cleaned up with the signer refactor?")
    public URL presign(SdkRequest request, SdkHttpFullRequest httpRequest, RequestConfig requestConfig, Date expirationDate) {
        final Presigner presigner = (Presigner) signerProvider.getSigner(SignerProviderContext.builder()
                                                                                              .withIsRedirect(false)
                                                                                              .withRequest(httpRequest)
                                                                                              .build());
        SdkHttpFullRequest.Builder mutableHttpRequest = httpRequest.toBuilder();
        addCustomQueryParams(mutableHttpRequest, requestConfig);
        addCustomHeaders(mutableHttpRequest, requestConfig);
        AwsCredentialsProvider credentialsProvider = resolveCredentials(requestConfig);

        ExecutionAttributes executionAttributes = new ExecutionAttributes();
        executionAttributes.putAttribute(AwsExecutionAttributes.AWS_CREDENTIALS, credentialsProvider.getCredentials());

        SdkHttpFullRequest signed = presigner.presign(InterceptorContext.builder()
                                                                        .request(request)
                                                                        .httpRequest(mutableHttpRequest.build())
                                                                        .build(),
                                                      executionAttributes,
                                                      expirationDate);
        return invokeSafely(() -> signed.getUri().toURL());
    }

    private void addCustomQueryParams(SdkHttpFullRequest.Builder request, RequestConfig requestConfig) {
        final Map<String, List<String>> queryParameters = requestConfig.getCustomQueryParameters();
        if (queryParameters == null || queryParameters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> param : queryParameters.entrySet()) {
            request.rawQueryParameter(param.getKey(), param.getValue());
        }
    }

    private void addCustomHeaders(SdkHttpFullRequest.Builder request, RequestConfig requestConfig) {
        final Map<String, String> headers = requestConfig.getCustomRequestHeaders();
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.header(header.getKey(), header.getValue());
        }
    }

    private AwsCredentialsProvider resolveCredentials(RequestConfig requestConfig) {
        return CredentialUtils.getCredentialsProvider(requestConfig, this.credentialsProvider);
    }

}
