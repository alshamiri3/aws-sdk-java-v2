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

package software.amazon.awssdk.services.cloudfront;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static software.amazon.awssdk.core.util.StringUtils.UTF8;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;
import java.util.Date;
import software.amazon.awssdk.core.AmazonClientException;
import software.amazon.awssdk.core.util.DateUtils;
import software.amazon.awssdk.services.cloudfront.util.SignerUtils;

// The source code originated from the .NET implementation at
// https://github.com/aws/aws-sdk-net/blob/master/AWSSDK_DotNet35/Amazon.CloudFront/AmazonCloudFrontUrlSigner.cs

/**
 * Utility class for generating pre-signed URLs for serving private CloudFront
 * content. All dates must be in UTC. Use {@link Calendar} to set the timezone
 * specifically before converting to a {@link Date} object, or else use
 * {@link DateUtils} to turn a UTC date String into a Date object.
 *
 * <pre>
 * Protocol protocol = Protocol.http;
 * String distributionDomain = "d1b2c3a4g5h6.cloudfront.net";
 * File privateKeyFile = new File("/path/to/cfcurlCloud/rsa-private-key.pem");
 * String s3ObjectKey = "a/b/images.jpeg";
 * String keyPairId = "APKAJCEOKRHC3XIVU5NA";
 * Date dateLessThan = DateUtils.parseISO8601Date("2012-11-14T22:20:00.000Z");
 * Date dateGreaterThan = DateUtils.parseISO8601Date("2011-11-14T22:20:00.000Z");
 * String ipRange = "192.168.0.1/24";
 *
 * String url1 = CloudFrontUrlSigner.getSignedURLWithCannedPolicy(
 *              protocol, distributionDomain, privateKeyFile,
 *              s3ObjectKey, keyPairId, dateLessThan);
 *
 * String url2 = CloudFrontUrlSigner.getSignedURLWithCustomPolicy(
 *              protocol, distributionDomain, privateKeyFile,
 *              s3ObjectKey, keyPairId, dateLessThan,
 *              dateGreaterThan, ipRange);
 * </pre>
 *
 * @see DateUtils
 */
public enum CloudFrontUrlSigner {
    ;

    /**
     * Returns a signed URL with a canned policy that grants universal access to
     * private content until a given date.
     * For more information, see <a href=
     * "http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-signed-urls-overview.html"
     * >Overview of Signed URLs</a>.
     *
     * @param protocol
     *            The protocol of the URL
     * @param distributionDomain
     *            The domain name of the distribution
     * @param s3ObjectKey
     *            The s3 key of the object, or the name of the stream for rtmp
     * @param privateKeyFile
     *            The private key file. RSA private key (.pem) and pkcs8 (.der)
     *            files are supported.
     * @param keyPairId
     *            The key pair id corresponding to the private key file given
     * @param dateLessThan
     *            The expiration date of the signed URL in UTC
     * @return The signed URL.
     */
    public static String getSignedUrlWithCannedPolicy(final SignerUtils.Protocol protocol,
                                                      final String distributionDomain,
                                                      final File privateKeyFile,
                                                      final String s3ObjectKey,
                                                      final String keyPairId,
                                                      final Date dateLessThan)
            throws InvalidKeySpecException, IOException {
        final String resourcePath = SignerUtils.generateResourcePath(protocol, distributionDomain, s3ObjectKey);
        PrivateKey privateKey = SignerUtils.loadPrivateKey(privateKeyFile);
        return getSignedUrlWithCannedPolicy(resourcePath, keyPairId, privateKey, dateLessThan);
    }

    /**
     * Returns a signed URL that provides tailored access to private content
     * based on an access time window and an ip range.
     * For more information, see <a href=
     * "http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-signed-urls-overview.html"
     * >Overview of Signed URLs</a>.
     *
     * @param protocol
     *            The protocol of the URL
     * @param distributionDomain
     *            The domain name of the distribution
     * @param privateKeyFile
     *            Your private key file. RSA private key (.pem) and pkcs8 (.der)
     *            files are supported.
     * @param s3ObjectKey
     *            The s3 key of the object, or the name of the stream for rtmp
     * @param keyPairId
     *            The key pair id corresponding to the private key file given
     * @param dateLessThan
     *            The expiration date of the signed URL in UTC
     * @param dateGreaterThan
     *            The beginning valid date of the signed URL in UTC
     * @param ipRange
     *            The allowed IP address range of the client making the GET
     *            request, in CIDR form (e.g. 192.168.0.1/24).
     * @return The signed URL.
     */
    public static String getSignedUrlWithCustomPolicy(final SignerUtils.Protocol protocol,
                                                      final String distributionDomain,
                                                      final File privateKeyFile,
                                                      final String s3ObjectKey,
                                                      final String keyPairId,
                                                      final Date dateLessThan,
                                                      final Date dateGreaterThan,
                                                      final String ipRange)
            throws InvalidKeySpecException, IOException {
        PrivateKey privateKey = SignerUtils.loadPrivateKey(privateKeyFile);
        final String resourcePath = SignerUtils.generateResourcePath(protocol, distributionDomain, s3ObjectKey);
        String policy = buildCustomPolicyForSignedUrl(resourcePath, dateLessThan, ipRange, dateGreaterThan);
        return getSignedUrlWithCustomPolicy(resourcePath, keyPairId, privateKey, policy);
    }

    /**
     * Generate a signed URL that allows access to distribution and S3 objects
     * by applying access restrictions specified in a custom policy document.
     * For more information, see <a href=
     * "http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-signed-urls-overview.html"
     * >Overview of Signed URLs</a>.
     *
     * @param resourceUrlOrPath
     *            The URL or path that uniquely identifies a resource within a
     *            distribution. For standard distributions the resource URL will
     *            be <tt>"http://" + distributionName + "/" + objectKey</tt>
     *            (may also include URL parameters. For distributions with the
     *            HTTPS required protocol, the resource URL must start with
     *            <tt>"https://"</tt>. RTMP resources do not take the form of a
     *            URL, and instead the resource path is nothing but the stream's
     *            name.
     * @param keyPairId
     *            Identifier of a public/private certificate keypair already
     *            configured in your Amazon Web Services account.
     * @param privateKey
     *            The RSA private key data that corresponding to the certificate
     *            keypair identified by keyPairId.
     * @param policy
     *            A policy document that describes the access permissions that
     *            will be applied by the signed URL. To generate a custom policy
     *            use
     * @return A signed URL that will permit access to distribution and S3
     *         objects as specified in the policy document.
     */
    public static String getSignedUrlWithCustomPolicy(String resourceUrlOrPath,
                                                      String keyPairId, PrivateKey privateKey, String policy) {
        try {
            byte[] signatureBytes = SignerUtils.signWithSha1Rsa(
                    policy.getBytes(UTF8), privateKey);
            String urlSafePolicy = SignerUtils.makeStringUrlSafe(policy);
            String urlSafeSignature = SignerUtils.makeBytesUrlSafe(signatureBytes);
            String signedUrl = resourceUrlOrPath
                               + (resourceUrlOrPath.indexOf('?') >= 0 ? "&" : "?")
                               + "Policy=" + urlSafePolicy
                               + "&Signature=" + urlSafeSignature
                               + "&Key-Pair-Id=" + keyPairId;
            return signedUrl;
        } catch (InvalidKeyException e) {
            throw new AmazonClientException("Coudln't sign url", e);
        }
    }

    /**
     * Generate a signed URL that allows access to a specific distribution and
     * S3 object by applying a access restrictions from a "canned" (simplified)
     * policy document.
     * For more information, see <a href=
     * "http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-signed-urls-overview.html"
     * >Overview of Signed URLs</a>.
     *
     * @param resourceUrlOrPath
     *            The URL or path that uniquely identifies a resource within a
     *            distribution. For standard distributions the resource URL will
     *            be <tt>"http://" + distributionName + "/" + objectKey</tt>
     *            (may also include URL parameters. For distributions with the
     *            HTTPS required protocol, the resource URL must start with
     *            <tt>"https://"</tt>. RTMP resources do not take the form of a
     *            URL, and instead the resource path is nothing but the stream's
     *            name.
     * @param keyPairId
     *            Identifier of a public/private certificate keypair already
     *            configured in your Amazon Web Services account.
     * @param privateKey
     *            The private key data that corresponding to the keypair
     *            identified by keyPairId
     * @param dateLessThan
     *            The UTC time and date when the signed URL will expire.
     *            REQUIRED.
     * @return A signed URL that will permit access to a specific distribution
     *         and S3 object.
     */
    public static String getSignedUrlWithCannedPolicy(String resourceUrlOrPath,
                                                      String keyPairId,
                                                      PrivateKey privateKey,
                                                      Date dateLessThan) {
        try {
            String cannedPolicy = SignerUtils.buildCannedPolicy(resourceUrlOrPath, dateLessThan);
            byte[] signatureBytes = SignerUtils.signWithSha1Rsa(cannedPolicy.getBytes(UTF8), privateKey);
            String urlSafeSignature = SignerUtils.makeBytesUrlSafe(signatureBytes);
            String signedUrl = resourceUrlOrPath
                               + (resourceUrlOrPath.indexOf('?') >= 0 ? "&" : "?")
                               + "Expires=" + MILLISECONDS.toSeconds(dateLessThan.getTime())
                               + "&Signature=" + urlSafeSignature
                               + "&Key-Pair-Id=" + keyPairId;
            return signedUrl;
        } catch (InvalidKeyException e) {
            throw new AmazonClientException("Couldn't sign url", e);
        }
    }

    /**
     * Generate a policy document that describes custom access permissions to
     * apply via a private distribution's signed URL.
     *
     * @param resourcePath
     *            An optional HTTP/S or RTMP resource path that restricts which
     *            distribution and S3 objects will be accessible in a signed
     *            URL. For standard distributions the resource URL will be
     *            <tt>"http://" + distributionName + "/" + objectKey</tt> (may
     *            also include URL parameters. For distributions with the HTTPS
     *            required protocol, the resource URL must start with
     *            <tt>"https://"</tt>. RTMP resources do not take the form of a
     *            URL, and instead the resource path is nothing but the stream's
     *            name. The '*' and '?' characters can be used as a wildcards to
     *            allow multi-character or single-character matches
     *            respectively:
     *            <ul>
     *            <li><tt>*</tt> : All distributions/objects will be accessible</li>
     *            <li><tt>a1b2c3d4e5f6g7.cloudfront.net/*</tt> : All objects
     *            within the distribution a1b2c3d4e5f6g7 will be accessible</li>
     *            <li><tt>a1b2c3d4e5f6g7.cloudfront.net/path/to/object.txt</tt>
     *            : Only the S3 object named <tt>path/to/object.txt</tt> in the
     *            distribution a1b2c3d4e5f6g7 will be accessible.</li>
     *            </ul>
     *            If this parameter is null the policy will permit access to all
     *            distributions and S3 objects associated with the certificate
     *            keypair used to generate the signed URL.
     * @param epochDateLessThan
     *            The UTC time and date when the signed URL will expire. REQUIRED.
     * @param limitToIpAddressCidr
     *            An optional range of client IP addresses that will be allowed
     *            to access the distribution, specified as a CIDR range. If
     *            null, the CIDR will be <tt>0.0.0.0/0</tt> and any client will
     *            be permitted.
     * @param epochDateGreaterThan
     *            An optional UTC time and date when the signed URL will become
     *            active. If null, the signed URL will be active as soon as it
     *            is created.
     * @return A policy document describing the access permission to apply when
     *         generating a signed URL.
     */
    public static String buildCustomPolicyForSignedUrl(String resourcePath,
                                                       Date epochDateLessThan,
                                                       String limitToIpAddressCidr,
                                                       Date epochDateGreaterThan) {
        if (epochDateLessThan == null) {
            throw new AmazonClientException("epochDateLessThan must be provided to sign CloudFront URLs");
        }
        if (resourcePath == null) {
            resourcePath = "*";
        }
        String ipAddress = (limitToIpAddressCidr == null
                            ? "0.0.0.0/0" // No IP restriction
                            : limitToIpAddressCidr);
        return SignerUtils.buildCustomPolicy(resourcePath, epochDateLessThan,
                                             epochDateGreaterThan, ipAddress);
    }

}
