/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import static org.jboss.netty.util.internal.PlatformDependentTest.*;
import static org.junit.Assert.*;

/**
 * The temporary files backing the disk based {@link HttpData} implementations
 * must not be readable by other local users (CVE-2021-21290).
 */
public class DiskFileUploadTest {

    private static final String SECRET = "secret uploaded content";

    @Test
    public void testFileUploadTempFileNotReadableByOthers() throws Exception {
        DiskFileUpload upload = newUpload();
        try {
            upload.setContent(ChannelBuffers.copiedBuffer(SECRET, CharsetUtil.UTF_8));
            File file = upload.getFile();
            assertTrue(file.exists());
            assertTrue(file.getName().startsWith(DiskFileUpload.prefix));
            assertTrue(file.getName().endsWith("_secret.txt"));
            assertEquals(SECRET, upload.getString(CharsetUtil.UTF_8));
            assertNotReadableByOthers(file);
        } finally {
            upload.delete();
        }
    }

    @Test
    public void testFileUploadAddContentTempFileNotReadableByOthers() throws Exception {
        DiskFileUpload upload = newUpload();
        try {
            upload.addContent(ChannelBuffers.copiedBuffer(SECRET, CharsetUtil.UTF_8), false);
            upload.addContent(ChannelBuffers.copiedBuffer(SECRET, CharsetUtil.UTF_8), true);
            File file = upload.getFile();
            assertTrue(file.exists());
            assertEquals(SECRET + SECRET, upload.getString(CharsetUtil.UTF_8));
            assertNotReadableByOthers(file);
        } finally {
            upload.delete();
        }
    }

    @Test
    public void testFileUploadInputStreamTempFileNotReadableByOthers() throws Exception {
        DiskFileUpload upload = newUpload();
        File file = null;
        try {
            upload.setContent(new ByteArrayInputStream(SECRET.getBytes("UTF-8")));
            file = upload.getFile();
            assertTrue(file.exists());
            assertEquals(SECRET, upload.getString(CharsetUtil.UTF_8));
            assertNotReadableByOthers(file);
        } finally {
            if (file != null) {
                file.delete();
            }
        }
    }

    @Test
    public void testFileUploadTempFileInBaseDirectoryNotReadableByOthers() throws Exception {
        File dir = createTempDirectory();
        String oldBaseDirectory = DiskFileUpload.baseDirectory;
        DiskFileUpload.baseDirectory = dir.getPath();
        try {
            DiskFileUpload upload = newUpload();
            try {
                upload.setContent(ChannelBuffers.copiedBuffer(SECRET, CharsetUtil.UTF_8));
                File file = upload.getFile();
                assertTrue(file.exists());
                assertEquals(dir.getAbsolutePath(), file.getParentFile().getAbsolutePath());
                assertNotReadableByOthers(file);
            } finally {
                upload.delete();
            }
        } finally {
            DiskFileUpload.baseDirectory = oldBaseDirectory;
            dir.delete();
        }
    }

    @Test
    public void testAttributeTempFileNotReadableByOthers() throws Exception {
        DiskAttribute attribute = new DiskAttribute("attribute", SECRET);
        try {
            File file = attribute.getFile();
            assertTrue(file.exists());
            assertTrue(file.getName().startsWith(DiskAttribute.prefix));
            assertEquals(SECRET, attribute.getValue());
            assertNotReadableByOthers(file);
        } finally {
            attribute.delete();
        }
    }

    @Test
    public void testAttributeTempFileInBaseDirectoryNotReadableByOthers() throws Exception {
        File dir = createTempDirectory();
        String oldBaseDirectory = DiskAttribute.baseDirectory;
        DiskAttribute.baseDirectory = dir.getPath();
        try {
            DiskAttribute attribute = new DiskAttribute("attribute", SECRET);
            try {
                File file = attribute.getFile();
                assertTrue(file.exists());
                assertEquals(dir.getAbsolutePath(), file.getParentFile().getAbsolutePath());
                assertNotReadableByOthers(file);
            } finally {
                attribute.delete();
            }
        } finally {
            DiskAttribute.baseDirectory = oldBaseDirectory;
            dir.delete();
        }
    }

    private static DiskFileUpload newUpload() {
        return new DiskFileUpload("file", "secret.txt", "text/plain", null, CharsetUtil.UTF_8, 0);
    }

    private static File createTempDirectory() throws IOException {
        File dir = File.createTempFile("netty-dir-", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());
        return dir;
    }
}
