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

package software.amazon.awssdk.core.auth;

import static software.amazon.awssdk.core.interceptor.AwsExecutionAttributes.AWS_CREDENTIALS;
import static software.amazon.awssdk.core.interceptor.AwsExecutionAttributes.TIME_OFFSET;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import software.amazon.awssdk.core.SdkClientException;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.util.CredentialUtils;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Signer implementation responsible for signing an AWS query string request
 * according to the various signature versions and hashing algorithms.
 */
public class QueryStringSigner extends AbstractAwsSigner {
    /**
     * Date override for testing only.
     */
    private Date overriddenDate;

    /**
     * This signer will add "Signature" parameter to the request. Default
     * signature version is "2" and default signing algorithm is "HmacSHA256".
     *
     * AWSAccessKeyId SignatureVersion SignatureMethod Timestamp Signature
     *
     * @param request     request to be signed.
     * @param credentials The credentials used to use to sign the request.
     */
    public SdkHttpFullRequest sign(Context.BeforeTransmission execution, ExecutionAttributes executionAttributes)
            throws SdkClientException {
        // anonymous credentials, don't sign
        if (CredentialUtils.isAnonymous(executionAttributes.getAttribute(AWS_CREDENTIALS))) {
            return execution.httpRequest();
        }

        SdkHttpFullRequest.Builder mutableRequest = execution.httpRequest().toBuilder();

        AwsCredentials sanitizedCredentials = sanitizeCredentials(executionAttributes.getAttribute(AWS_CREDENTIALS));
        mutableRequest.rawQueryParameter("AWSAccessKeyId", sanitizedCredentials.accessKeyId());
        mutableRequest.rawQueryParameter("SignatureVersion", "2");

        int timeOffset = executionAttributes.getAttribute(TIME_OFFSET) == null ? 0 :
                         executionAttributes.getAttribute(TIME_OFFSET);
        mutableRequest.rawQueryParameter("Timestamp", getFormattedTimestamp(timeOffset));

        if (sanitizedCredentials instanceof AwsSessionCredentials) {
            addSessionCredentials(mutableRequest, (AwsSessionCredentials) sanitizedCredentials);
        }

        mutableRequest.rawQueryParameter("SignatureMethod", SigningAlgorithm.HmacSHA256.toString());
        String stringToSign = calculateStringToSignV2(mutableRequest);

        String signatureValue = signAndBase64Encode(stringToSign,
                                                    sanitizedCredentials.secretAccessKey(),
                                                    SigningAlgorithm.HmacSHA256);
        mutableRequest.rawQueryParameter("Signature", signatureValue);
        return mutableRequest.build();
    }

    /**
     * Calculate string to sign for signature version 2.
     *
     * @param request The request being signed.
     * @return String to sign
     * @throws SdkClientException If the string to sign cannot be calculated.
     */
    private String calculateStringToSignV2(SdkHttpFullRequest.Builder requestBuilder) throws SdkClientException {
        SdkHttpFullRequest request = requestBuilder.build();
        return "POST" + "\n" +
               getCanonicalizedEndpoint(request) + "\n" +
               getCanonicalizedResourcePath(request) + "\n" +
               getCanonicalizedQueryString(request.rawQueryParameters());
    }

    private String getCanonicalizedResourcePath(SdkHttpFullRequest request) {
        return StringUtils.isEmpty(request.encodedPath()) ? "/" : request.encodedPath();
    }

    /**
     * Formats date as ISO 8601 timestamp
     */
    private String getFormattedTimestamp(int offset) {
        SimpleDateFormat df = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

        if (overriddenDate != null) {
            return df.format(overriddenDate);
        } else {
            return df.format(getSignatureDate(offset));
        }
    }

    /**
     * For testing purposes only, to control the date used in signing.
     */
    void overrideDate(Date date) {
        this.overriddenDate = date;
    }

    @Override
    protected void addSessionCredentials(SdkHttpFullRequest.Builder request, AwsSessionCredentials credentials) {
        request.rawQueryParameter("SecurityToken", credentials.sessionToken());
    }
}
