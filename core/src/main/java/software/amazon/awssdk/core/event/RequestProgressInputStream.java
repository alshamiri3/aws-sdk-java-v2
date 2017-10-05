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

package software.amazon.awssdk.core.event;

import static software.amazon.awssdk.core.event.SdkProgressPublisher.publishRequestBytesTransferred;
import static software.amazon.awssdk.core.event.SdkProgressPublisher.publishRequestReset;

import java.io.InputStream;

/**
 * Used for request input stream progress tracking purposes.
 */
class RequestProgressInputStream extends ProgressInputStream {

    RequestProgressInputStream(InputStream is, ProgressListener listener) {
        super(is, listener);
    }

    @Override
    protected void onReset() {
        publishRequestReset(getListener(), getNotifiedByteCount());
    }

    @Override
    protected void onEof() {
        onNotifyBytesRead();
    }

    @Override
    protected void onNotifyBytesRead() {
        publishRequestBytesTransferred(getListener(), getUnnotifiedByteCount());
    }
}
